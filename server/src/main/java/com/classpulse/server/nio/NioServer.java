package com.classpulse.server.nio;

import com.classpulse.common.protocol.Message;
import com.classpulse.server.concurrency.WorkerPool;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.RejectedExecutionException;

/**
 * Selector-based Java NIO central TCP server with bounded worker-thread request processing.
 * <p>
 * The selector event loop performs non-blocking accept, read, frame decoding, and socket write
 * operations, while delegating decoded {@link Message} processing to {@link WorkerPool}.
 * Worker threads queue outgoing responses back to the selector thread safely without touching
 * sockets or mutating {@link SelectionKey} interest operations directly.
 */
public class NioServer {

    public static final int DEFAULT_PORT = 5000;

    private final int requestedPort;
    private final MessageHandler messageHandler;
    private final WorkerPool workerPool;
    private final Set<ClientConnection> connections = ConcurrentHashMap.newKeySet();
    private final Queue<ClientConnection> pendingWriteRegistrations = new ConcurrentLinkedQueue<>();

    private volatile boolean running;
    private volatile int boundPort = -1;
    private Selector selector;
    private ServerSocketChannel serverChannel;

    public NioServer(int port) {
        this(port, (client, message) ->
                System.out.println("Received " + message.getType()
                        + " (requestId=" + message.getRequestId()
                        + ") from " + client.getRemoteAddress()));
    }

    public NioServer(int port, MessageHandler messageHandler) {
        this(port, messageHandler, new WorkerPool());
    }

    public NioServer(int port, MessageHandler messageHandler, WorkerPool workerPool) {
        this.requestedPort = port;
        this.messageHandler = Objects.requireNonNull(messageHandler, "messageHandler must not be null");
        this.workerPool = Objects.requireNonNull(workerPool, "workerPool must not be null");
    }

    /**
     * Opens the selector and server channel, binds to the configured port,
     * and runs the single-threaded NIO event loop until {@link #stop()} is called.
     *
     * @throws IOException if the server socket cannot be opened or bound
     */
    public void start() throws IOException {
        selector = Selector.open();
        serverChannel = ServerSocketChannel.open();
        serverChannel.configureBlocking(false);
        serverChannel.bind(new InetSocketAddress(requestedPort));
        boundPort = ((InetSocketAddress) serverChannel.getLocalAddress()).getPort();
        serverChannel.register(selector, SelectionKey.OP_ACCEPT);
        running = true;

        System.out.println("ClassPulse NIO Server listening on port " + boundPort);

        try {
            while (running) {
                processPendingWriteRegistrations();
                selector.select();
                if (!running) {
                    break;
                }
                processPendingWriteRegistrations();

                Iterator<SelectionKey> keys = selector.selectedKeys().iterator();
                while (keys.hasNext()) {
                    SelectionKey key = keys.next();
                    keys.remove();

                    if (!key.isValid()) {
                        continue;
                    }

                    if (key.isAcceptable()) {
                        handleAccept();
                    } else {
                        if (key.isReadable()) {
                            handleRead(key);
                        }
                        if (key.isValid() && key.isWritable()) {
                            handleWrite(key);
                        }
                    }
                }
            }
        } finally {
            shutdownResources();
        }
    }

    /**
     * Stops the event loop and closes all active connections, the selector, the server channel,
     * and the worker thread pool.
     */
    public void stop() {
        running = false;
        if (selector != null) {
            selector.wakeup();
        } else {
            workerPool.shutdown();
        }
    }

    public boolean isRunning() {
        return running;
    }

    public int getPort() {
        return boundPort;
    }

    public int getActiveConnectionCount() {
        return connections.size();
    }

    public WorkerPool getWorkerPool() {
        return workerPool;
    }

    private void handleAccept() {
        try {
            SocketChannel clientChannel = serverChannel.accept();
            if (clientChannel == null) {
                return;
            }
            clientChannel.configureBlocking(false);
            SelectionKey clientKey = clientChannel.register(selector, SelectionKey.OP_READ);
            ClientConnection connection = new ClientConnection(clientChannel, clientKey, this::requestWriteRegistration);
            clientKey.attach(connection);
            connections.add(connection);
            System.out.println("Client connected: " + connection.getRemoteAddress());
        } catch (IOException e) {
            System.err.println("Error accepting client connection: " + e.getMessage());
        }
    }

    private void handleRead(SelectionKey key) {
        ClientConnection connection = (ClientConnection) key.attachment();
        if (connection == null || connection.isClosed()) {
            return;
        }

        ByteBuffer buffer = connection.getReadBuffer();
        buffer.clear();

        try {
            int bytesRead = connection.getChannel().read(buffer);
            if (bytesRead == -1) {
                disconnectClient(connection, "Client closed connection");
                return;
            }
            if (bytesRead == 0) {
                return;
            }

            buffer.flip();
            List<Message> messages = connection.getFrameDecoder().decode(buffer);
            for (Message message : messages) {
                dispatchMessage(connection, message);
            }
        } catch (IOException e) {
            disconnectClient(connection, "I/O error: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            System.err.println("Invalid frame from " + connection.getRemoteAddress() + ": " + e.getMessage());
            disconnectClient(connection, "Invalid protocol frame");
        }
    }

    private void dispatchMessage(ClientConnection connection, Message message) {
        try {
            workerPool.execute(() -> {
                if (connection.isClosed()) {
                    return;
                }
                try {
                    messageHandler.handle(connection, message);
                } catch (Exception e) {
                    System.err.println("Worker error handling " + message.getType()
                            + " from " + connection.getRemoteAddress() + ": " + e.getMessage());
                    if (!connection.isClosed() && message.getRequestId() != null) {
                        connection.send(Message.error(
                                message.getRequestId(),
                                "INTERNAL_ERROR",
                                "Internal server error while processing request"));
                    }
                }
            });
        } catch (RejectedExecutionException e) {
            System.err.println("Worker pool rejected " + message.getType()
                    + " from " + connection.getRemoteAddress() + ": " + e.getMessage());
            if (running && !connection.isClosed() && message.getRequestId() != null) {
                connection.send(Message.error(
                        message.getRequestId(),
                        "SERVER_BUSY",
                        "Server is busy; please retry"));
            }
        }
    }

    private void requestWriteRegistration(ClientConnection connection) {
        if (connection == null || connection.isClosed() || !running) {
            return;
        }
        pendingWriteRegistrations.add(connection);
        Selector activeSelector = this.selector;
        if (activeSelector != null) {
            activeSelector.wakeup();
        }
    }

    private void processPendingWriteRegistrations() {
        ClientConnection connection;
        while ((connection = pendingWriteRegistrations.poll()) != null) {
            connection.enableWriteInterest();
        }
    }

    private void handleWrite(SelectionKey key) {
        ClientConnection connection = (ClientConnection) key.attachment();
        if (connection == null || connection.isClosed()) {
            return;
        }

        try {
            connection.flushWriteQueue();
        } catch (IOException e) {
            disconnectClient(connection, "Write error: " + e.getMessage());
        }
    }

    private void disconnectClient(ClientConnection connection, String reason) {
        if (connections.remove(connection)) {
            System.out.println("Client disconnected (" + connection.getRemoteAddress() + "): " + reason);
        }
        connection.close();
    }

    private void shutdownResources() {
        pendingWriteRegistrations.clear();

        for (ClientConnection connection : connections) {
            connection.close();
        }
        connections.clear();

        if (selector != null) {
            try {
                selector.close();
            } catch (IOException ignored) {
            }
        }
        if (serverChannel != null) {
            try {
                serverChannel.close();
            } catch (IOException ignored) {
            }
        }

        workerPool.shutdown();
        System.out.println("ClassPulse NIO Server stopped.");
    }
}
