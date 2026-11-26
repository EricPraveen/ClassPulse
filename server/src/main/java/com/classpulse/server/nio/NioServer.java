package com.classpulse.server.nio;

import com.classpulse.common.protocol.Message;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Single-threaded, selector-based Java NIO central TCP server.
 * <p>
 * Handles accepting multiple clients, non-blocking reads with {@link com.classpulse.common.protocol.FrameDecoder},
 * and non-blocking writes via {@link ClientConnection}.
 */
public class NioServer {

    public static final int DEFAULT_PORT = 5000;

    private final int requestedPort;
    private final MessageHandler messageHandler;
    private final Set<ClientConnection> connections = ConcurrentHashMap.newKeySet();

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
        this.requestedPort = port;
        this.messageHandler = messageHandler;
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
                selector.select();
                if (!running) {
                    break;
                }

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
     * Stops the event loop and closes all active connections, the selector, and the server channel.
     */
    public void stop() {
        running = false;
        if (selector != null) {
            selector.wakeup();
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

    private void handleAccept() {
        try {
            SocketChannel clientChannel = serverChannel.accept();
            if (clientChannel == null) {
                return;
            }
            clientChannel.configureBlocking(false);
            SelectionKey clientKey = clientChannel.register(selector, SelectionKey.OP_READ);
            ClientConnection connection = new ClientConnection(clientChannel, clientKey);
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
                messageHandler.handle(connection, message);
            }
        } catch (IOException e) {
            disconnectClient(connection, "I/O error: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            System.err.println("Invalid frame from " + connection.getRemoteAddress() + ": " + e.getMessage());
            disconnectClient(connection, "Invalid protocol frame");
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
        System.out.println("ClassPulse NIO Server stopped.");
    }
}
