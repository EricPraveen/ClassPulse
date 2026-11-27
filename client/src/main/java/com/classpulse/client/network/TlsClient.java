package com.classpulse.client.network;

import com.classpulse.common.protocol.Message;
import com.classpulse.common.protocol.ProtocolFramer;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.Objects;

/**
 * JSSE/TLS client transport for secure communication with the ClassPulse server.
 * <p>
 * Implements strict TLS certificate validation and server identity (hostname)
 * verification using Java JSSE. Encapsulates handshake negotiation and exchange
 * of length-prefixed {@link Message} frames over encrypted TLS channels.
 */
public class TlsClient implements AutoCloseable {

    public static final int DEFAULT_CONNECT_TIMEOUT_MS = 5000;
    public static final int DEFAULT_READ_TIMEOUT_MS = 10000;
    public static final String[] DEFAULT_ENABLED_PROTOCOLS = new String[]{"TLSv1.3", "TLSv1.2"};

    private final String host;
    private final int port;
    private final SSLContext sslContext;
    private final int connectTimeoutMillis;
    private final int readTimeoutMillis;
    private final boolean verifyHostname;
    private final String[] enabledProtocols;

    private SSLSocket socket;
    private InputStream in;
    private OutputStream out;
    private TlsSessionInfo sessionInfo;

    private final Object sendLock = new Object();
    private final Object receiveLock = new Object();

    public TlsClient(String host, int port) {
        this(host, port, getDefaultSslContext(), DEFAULT_CONNECT_TIMEOUT_MS, DEFAULT_READ_TIMEOUT_MS, true, DEFAULT_ENABLED_PROTOCOLS);
    }

    public TlsClient(String host, int port, SSLContext sslContext) {
        this(host, port, sslContext, DEFAULT_CONNECT_TIMEOUT_MS, DEFAULT_READ_TIMEOUT_MS, true, DEFAULT_ENABLED_PROTOCOLS);
    }

    public TlsClient(String host, int port, String truststorePath, char[] truststorePassword) {
        this(host, port, createTruststoreContext(truststorePath, truststorePassword, "PKCS12"),
                DEFAULT_CONNECT_TIMEOUT_MS, DEFAULT_READ_TIMEOUT_MS, true, DEFAULT_ENABLED_PROTOCOLS);
    }

    public TlsClient(String host, int port, SSLContext sslContext, int connectTimeoutMillis,
                     int readTimeoutMillis, boolean verifyHostname) {
        this(host, port, sslContext, connectTimeoutMillis, readTimeoutMillis, verifyHostname, DEFAULT_ENABLED_PROTOCOLS);
    }

    public TlsClient(String host, int port, SSLContext sslContext, int connectTimeoutMillis,
                     int readTimeoutMillis, boolean verifyHostname, String[] enabledProtocols) {
        if (host == null || host.trim().isEmpty()) {
            throw new IllegalArgumentException("Host cannot be null or empty");
        }
        if (port <= 0 || port > 65535) {
            throw new IllegalArgumentException("Port must be between 1 and 65535: " + port);
        }
        if (connectTimeoutMillis < 0) {
            throw new IllegalArgumentException("Connect timeout cannot be negative: " + connectTimeoutMillis);
        }
        if (readTimeoutMillis < 0) {
            throw new IllegalArgumentException("Read timeout cannot be negative: " + readTimeoutMillis);
        }
        this.host = host.trim();
        this.port = port;
        if (sslContext == null) {
            throw new IllegalArgumentException("sslContext must not be null");
        }
        this.sslContext = sslContext;
        this.connectTimeoutMillis = connectTimeoutMillis;
        this.readTimeoutMillis = readTimeoutMillis;
        this.verifyHostname = verifyHostname;
        this.enabledProtocols = enabledProtocols != null ? enabledProtocols.clone() : DEFAULT_ENABLED_PROTOCOLS;
    }

    /**
     * Connects to the TLS server, applies SSL parameters, and executes the TLS handshake.
     *
     * @throws ClientTransportException if connection or handshake fails
     * @throws IllegalStateException    if client is already connected
     */
    public synchronized void connect() throws ClientTransportException {
        if (isConnected()) {
            throw new IllegalStateException("TLS Client is already connected to " + host + ":" + port);
        }

        try {
            SSLSocketFactory factory = sslContext.getSocketFactory();
            socket = (SSLSocket) factory.createSocket();

            if (readTimeoutMillis > 0) {
                socket.setSoTimeout(readTimeoutMillis);
            }

            if (enabledProtocols != null && enabledProtocols.length > 0) {
                socket.setEnabledProtocols(enabledProtocols);
            }

            SSLParameters params = socket.getSSLParameters();
            if (verifyHostname) {
                // Strict hostname identity verification (RFC 2818 / HTTPS endpoint identification)
                params.setEndpointIdentificationAlgorithm("HTTPS");
            }
            socket.setSSLParameters(params);

            // Connect TCP transport
            socket.connect(new InetSocketAddress(host, port), connectTimeoutMillis);

            // Trigger and complete TLS handshake
            socket.startHandshake();

            sessionInfo = TlsSessionInfo.from(socket.getSession());
            in = new BufferedInputStream(socket.getInputStream());
            out = new BufferedOutputStream(socket.getOutputStream());

            System.out.printf("[TLS Client] Connected securely to %s:%d %s%n",
                    host, port, sessionInfo);

        } catch (SSLHandshakeException e) {
            disconnect();
            throw new ClientTransportException("TLS Handshake failed with server (" + host + ":" + port
                    + "): " + e.getMessage(), e);
        } catch (SSLPeerUnverifiedException e) {
            disconnect();
            throw new ClientTransportException("Server identity unverified: " + e.getMessage(), e);
        } catch (ConnectException e) {
            disconnect();
            throw new ClientTransportException("Connection refused by server (" + host + ":" + port + ")", e);
        } catch (SocketTimeoutException e) {
            disconnect();
            throw new ClientTransportException("Connection attempt timed out connecting to " + host + ":" + port, e);
        } catch (UnknownHostException e) {
            disconnect();
            throw new ClientTransportException("Unknown server host: " + host, e);
        } catch (IOException e) {
            disconnect();
            throw new ClientTransportException("I/O error during TLS connection to " + host + ":" + port + ": " + e.getMessage(), e);
        }
    }

    /**
     * Checks whether the TLS connection is open and active.
     */
    public synchronized boolean isConnected() {
        return socket != null && socket.isConnected() && !socket.isClosed()
                && !socket.isInputShutdown() && !socket.isOutputShutdown();
    }

    /**
     * Sends a framed protocol message securely over the TLS channel.
     *
     * @param message the message to send
     * @throws ClientTransportException if send fails
     */
    public void send(Message message) throws ClientTransportException {
        if (message == null) {
            throw new IllegalArgumentException("Message cannot be null");
        }
        if (!isConnected()) {
            throw new ClientTransportException("Cannot send message: TLS client is not connected to " + host + ":" + port);
        }

        synchronized (sendLock) {
            try {
                ProtocolFramer.writeMessage(out, message);
            } catch (IOException e) {
                disconnect();
                String err = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                String lower = err.toLowerCase();
                if (lower.contains("reset") || lower.contains("broken pipe") || lower.contains("closed") || lower.contains("abort")) {
                    throw new ClientTransportException("Server disconnected unexpectedly during send: " + err, e);
                }
                throw new ClientTransportException("Failed to send TLS message (" + message.getType() + "): " + err, e);
            }
        }
    }

    /**
     * Reads a framed protocol message from the TLS stream.
     *
     * @return the decoded Message
     * @throws ClientTransportException if reading fails
     */
    public Message receive() throws ClientTransportException {
        if (!isConnected()) {
            throw new ClientTransportException("Cannot receive message: TLS client is not connected to " + host + ":" + port);
        }

        synchronized (receiveLock) {
            try {
                Message message = ProtocolFramer.readMessage(in);
                if (message == null) {
                    disconnect();
                    throw new ClientTransportException("Connection closed by server while awaiting response");
                }
                return message;
            } catch (SocketTimeoutException e) {
                throw new ClientTransportException("Read timed out waiting for server response (" + readTimeoutMillis + "ms)", e);
            } catch (EOFException e) {
                disconnect();
                throw new ClientTransportException("Server disconnected unexpectedly (EOF reached)", e);
            } catch (IllegalArgumentException e) {
                throw new ClientTransportException("Received invalid protocol frame: " + e.getMessage(), e);
            } catch (IOException e) {
                disconnect();
                String err = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                String lower = err.toLowerCase();
                if (lower.contains("reset") || lower.contains("broken pipe") || lower.contains("closed") || lower.contains("abort")) {
                    throw new ClientTransportException("Server disconnected unexpectedly while awaiting response: " + err, e);
                }
                throw new ClientTransportException("I/O error while reading response: " + err, e);
            }
        }
    }

    /**
     * Performs a synchronous request-response exchange over TLS with correlation checking.
     */
    public synchronized Message sendRequest(Message request) throws ClientTransportException {
        if (request == null) {
            throw new IllegalArgumentException("Request message cannot be null");
        }
        if (!isConnected()) {
            throw new ClientTransportException("Cannot send request: TLS client is not connected to " + host + ":" + port);
        }

        send(request);
        Message response = receive();

        if (request.getRequestId() != null && response.getRequestId() != null
                && !request.getRequestId().equals(response.getRequestId())) {
            throw new ClientTransportException("Request correlation mismatch: expected requestId '"
                    + request.getRequestId() + "' but received '" + response.getRequestId() + "'");
        }

        return response;
    }

    /**
     * Safely disconnects and releases all socket and stream resources.
     */
    public synchronized void disconnect() {
        try {
            if (in != null) in.close();
        } catch (IOException ignored) {}
        try {
            if (out != null) out.close();
        } catch (IOException ignored) {}
        try {
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException ignored) {}

        socket = null;
        in = null;
        out = null;
    }

    @Override
    public void close() {
        disconnect();
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public TlsSessionInfo getSessionInfo() {
        return sessionInfo;
    }

    public boolean isVerifyHostname() {
        return verifyHostname;
    }

    private static SSLContext getDefaultSslContext() {
        try {
            return SSLContext.getDefault();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to load default SSLContext", e);
        }
    }

    /**
     * Helper to create a client {@link SSLContext} initialized with a specified truststore.
     */
    public static SSLContext createTruststoreContext(String truststorePath, char[] password, String type) {
        if (truststorePath == null || truststorePath.isBlank()) {
            throw new IllegalArgumentException("Truststore path cannot be null or empty");
        }

        try {
            KeyStore trustStore = KeyStore.getInstance(type != null ? type : "PKCS12");
            File file = new File(truststorePath);
            try (InputStream is = file.exists() ? new FileInputStream(file) : TlsClient.class.getResourceAsStream(truststorePath.startsWith("/") ? truststorePath : "/" + truststorePath)) {
                if (is == null) {
                    throw new FileNotFoundException("Truststore file not found at " + truststorePath);
                }
                trustStore.load(is, password);
            }

            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(trustStore);

            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, tmf.getTrustManagers(), new SecureRandom());
            return sslContext;
        } catch (GeneralSecurityException | IOException e) {
            throw new RuntimeException("Failed to initialize truststore SSLContext from " + truststorePath + ": " + e.getMessage(), e);
        }
    }
}
