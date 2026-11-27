package com.classpulse.server.security;

import com.classpulse.common.protocol.Message;
import com.classpulse.common.protocol.ProtocolFramer;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocket;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * JSSE/TLS-enabled server for secure classroom polling communication.
 * <p>
 * Manages {@link SSLServerSocket} lifecycle, executes the TLS handshake,
 * logs negotiated protocol and cipher suites, and processes length-prefixed
 * {@link Message} frames over encrypted TLS transport.
 */
public class TlsServer implements AutoCloseable {

    private final TlsConfig config;
    private final SSLContext sslContext;
    private final TlsMessageHandler messageHandler;
    private final ExecutorService executorService;
    private final Set<ClientSessionHandler> activeConnections = ConcurrentHashMap.newKeySet();

    private volatile boolean running;
    private volatile int boundPort = -1;
    private SSLServerSocket serverSocket;
    private Thread acceptThread;
    private Consumer<TlsSessionInfo> handshakeListener;

    public TlsServer(TlsConfig config, TlsMessageHandler messageHandler) {
        this(config, SslContextFactory.createServerContext(config), messageHandler,
                Executors.newCachedThreadPool(r -> {
                    Thread t = new Thread(r, "tls-worker-" + System.nanoTime());
                    t.setDaemon(true);
                    return t;
                }));
    }

    public TlsServer(TlsConfig config, SSLContext sslContext, TlsMessageHandler messageHandler) {
        this(config, sslContext, messageHandler,
                Executors.newCachedThreadPool(r -> {
                    Thread t = new Thread(r, "tls-worker-" + System.nanoTime());
                    t.setDaemon(true);
                    return t;
                }));
    }

    public TlsServer(TlsConfig config, SSLContext sslContext, TlsMessageHandler messageHandler,
                     ExecutorService executorService) {
        this.config = Objects.requireNonNull(config, "config must not be null");
        this.sslContext = Objects.requireNonNull(sslContext, "sslContext must not be null");
        this.messageHandler = Objects.requireNonNull(messageHandler, "messageHandler must not be null");
        this.executorService = Objects.requireNonNull(executorService, "executorService must not be null");
    }

    /**
     * Binds the secure server socket and starts accepting TLS connections.
     *
     * @throws IOException if binding fails
     */
    public synchronized void start() throws IOException {
        if (running) {
            throw new IllegalStateException("TLS Server is already running on port " + boundPort);
        }

        SSLServerSocketFactory ssf = sslContext.getServerSocketFactory();
        serverSocket = (SSLServerSocket) ssf.createServerSocket();
        serverSocket.setReuseAddress(true);

        if (config.getEnabledProtocols() != null && config.getEnabledProtocols().length > 0) {
            serverSocket.setEnabledProtocols(config.getEnabledProtocols());
        }
        if (config.getEnabledCipherSuites() != null && config.getEnabledCipherSuites().length > 0) {
            serverSocket.setEnabledCipherSuites(config.getEnabledCipherSuites());
        }
        if (config.isClientAuthRequired()) {
            serverSocket.setNeedClientAuth(true);
        }

        serverSocket.bind(new InetSocketAddress(config.getPort()));
        boundPort = serverSocket.getLocalPort();
        running = true;

        acceptThread = new Thread(this::runAcceptLoop, "tls-server-acceptor");
        acceptThread.setDaemon(true);
        acceptThread.start();

        System.out.printf("[TLS Server] Secure server listening on port %d (Protocols: %s)%n",
                boundPort, String.join(", ", serverSocket.getEnabledProtocols()));
    }

    private void runAcceptLoop() {
        while (running && serverSocket != null && !serverSocket.isClosed()) {
            try {
                SSLSocket clientSocket = (SSLSocket) serverSocket.accept();
                ClientSessionHandler handler = new ClientSessionHandler(clientSocket);
                activeConnections.add(handler);
                executorService.submit(handler);
            } catch (SocketException e) {
                if (!running || serverSocket.isClosed()) {
                    break; // Server was stopped intentionally
                }
                System.err.println("[TLS Server] Socket error in accept loop: " + e.getMessage());
            } catch (IOException e) {
                if (running) {
                    System.err.println("[TLS Server] I/O error accepting connection: " + e.getMessage());
                }
            }
        }
    }

    /**
     * Gracefully stops the TLS server and releases all socket and thread resources.
     */
    public synchronized void stop() {
        if (!running) {
            return;
        }
        running = false;

        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (IOException ignored) {
            }
        }

        for (ClientSessionHandler handler : activeConnections) {
            handler.close();
        }
        activeConnections.clear();

        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(2, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }

        System.out.println("[TLS Server] Secure server stopped.");
    }

    @Override
    public void close() {
        stop();
    }

    public boolean isRunning() {
        return running;
    }

    public int getPort() {
        return boundPort;
    }

    public int getActiveConnectionCount() {
        return activeConnections.size();
    }

    public void setHandshakeListener(Consumer<TlsSessionInfo> handshakeListener) {
        this.handshakeListener = handshakeListener;
    }

    /**
     * Internal connection handler managing the TLS lifecycle and request framing for a single client.
     */
    private class ClientSessionHandler implements Runnable, TlsClientConnection {
        private final SSLSocket socket;
        private final AtomicBoolean closed = new AtomicBoolean(false);
        private final String remoteAddress;
        private volatile TlsSessionInfo sessionInfo;
        private InputStream in;
        private OutputStream out;

        public ClientSessionHandler(SSLSocket socket) {
            this.socket = socket;
            this.remoteAddress = String.valueOf(socket.getRemoteSocketAddress());
        }

        @Override
        public void run() {
            try {
                // Perform TLS handshake
                socket.startHandshake();
                sessionInfo = TlsSessionInfo.from(socket.getSession());

                System.out.printf("[TLS Server] Handshake completed with %s: %s%n",
                        remoteAddress, sessionInfo);

                if (handshakeListener != null) {
                    handshakeListener.accept(sessionInfo);
                }

                in = new BufferedInputStream(socket.getInputStream());
                out = new BufferedOutputStream(socket.getOutputStream());

                while (running && !closed.get()) {
                    Message request;
                    try {
                        request = ProtocolFramer.readMessage(in);
                    } catch (EOFException e) {
                        break; // Normal client disconnect
                    } catch (SocketTimeoutException e) {
                        continue;
                    } catch (IOException e) {
                        if (!closed.get() && running) {
                            System.out.printf("[TLS Server] Connection reset by %s: %s%n",
                                    remoteAddress, e.getMessage());
                        }
                        break;
                    }

                    if (request == null) {
                        break;
                    }

                    // Process message via handler
                    Message response = null;
                    try {
                        response = messageHandler.handle(this, request);
                    } catch (Exception e) {
                        System.err.printf("[TLS Server] Error processing message %s from %s: %s%n",
                                request.getType(), remoteAddress, e.getMessage());
                        if (request.getRequestId() != null) {
                            response = Message.error(request.getRequestId(), "INTERNAL_ERROR",
                                    "Server error: " + e.getMessage());
                        }
                    }

                    if (response != null && !isClosed()) {
                        send(response);
                    }
                }
            } catch (Exception e) {
                if (running && !closed.get()) {
                    System.err.printf("[TLS Server] TLS handshake / connection error from %s: %s%n",
                            remoteAddress, e.getMessage());
                }
            } finally {
                close();
                activeConnections.remove(this);
                System.out.printf("[TLS Server] Client disconnected: %s%n", remoteAddress);
            }
        }

        @Override
        public synchronized void send(Message message) throws IOException {
            if (closed.get() || out == null) {
                throw new SocketException("Connection is closed");
            }
            ProtocolFramer.writeMessage(out, message);
        }

        @Override
        public boolean isClosed() {
            return closed.get() || socket.isClosed();
        }

        @Override
        public String getRemoteAddress() {
            return remoteAddress;
        }

        @Override
        public TlsSessionInfo getSessionInfo() {
            return sessionInfo;
        }

        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) {
                try {
                    if (in != null) in.close();
                } catch (IOException ignored) {}
                try {
                    if (out != null) out.close();
                } catch (IOException ignored) {}
                try {
                    if (!socket.isClosed()) socket.close();
                } catch (IOException ignored) {}
            }
        }
    }
}
