package com.classpulse.client.network;

import com.classpulse.common.protocol.Message;
import com.classpulse.common.protocol.ProtocolFramer;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

/**
 * Reusable TCP client transport for the ClassPulse application.
 * <p>
 * Provides blocking socket-based request-response communication using the
 * length-prefixed JSON message framing defined in {@link ProtocolFramer}.
 * Implements {@link AutoCloseable} to support try-with-resources.
 */
public class TcpClient implements AutoCloseable {

    /** Default connection establishment timeout in milliseconds (5 seconds). */
    public static final int DEFAULT_CONNECT_TIMEOUT_MS = 5000;

    /** Default socket read timeout in milliseconds (10 seconds). */
    public static final int DEFAULT_READ_TIMEOUT_MS = 10000;

    private final String host;
    private final int port;
    private final int connectTimeoutMillis;
    private final int readTimeoutMillis;

    private Socket socket;
    private InputStream in;
    private OutputStream out;

    private final Object sendLock = new Object();
    private final Object receiveLock = new Object();

    /**
     * Constructs a {@link TcpClient} with default connect and read timeouts.
     *
     * @param host server hostname or IP address
     * @param port server listening port (1-65535)
     */
    public TcpClient(String host, int port) {
        this(host, port, DEFAULT_CONNECT_TIMEOUT_MS, DEFAULT_READ_TIMEOUT_MS);
    }

    /**
     * Constructs a {@link TcpClient} with custom connect and read timeouts.
     *
     * @param host                 server hostname or IP address
     * @param port                 server listening port (1-65535)
     * @param connectTimeoutMillis socket connection timeout in milliseconds
     * @param readTimeoutMillis    socket read timeout in milliseconds (0 for indefinite)
     */
    public TcpClient(String host, int port, int connectTimeoutMillis, int readTimeoutMillis) {
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
        this.connectTimeoutMillis = connectTimeoutMillis;
        this.readTimeoutMillis = readTimeoutMillis;
    }

    /**
     * Connects to the configured server host and port.
     *
     * @throws ClientTransportException if connection fails or times out
     * @throws IllegalStateException    if client is already connected
     */
    public synchronized void connect() throws ClientTransportException {
        if (isConnected()) {
            throw new IllegalStateException("Client is already connected to " + host + ":" + port);
        }

        try {
            socket = new Socket();
            if (readTimeoutMillis > 0) {
                socket.setSoTimeout(readTimeoutMillis);
            }
            socket.connect(new InetSocketAddress(host, port), connectTimeoutMillis);
            in = new BufferedInputStream(socket.getInputStream());
            out = new BufferedOutputStream(socket.getOutputStream());
        } catch (ConnectException e) {
            disconnect();
            throw new ClientTransportException("Failed to connect: Connection refused by server (" + host + ":" + port + ")", e);
        } catch (SocketTimeoutException e) {
            disconnect();
            throw new ClientTransportException("Connection attempt timed out connecting to " + host + ":" + port + " (" + connectTimeoutMillis + "ms)", e);
        } catch (UnknownHostException e) {
            disconnect();
            throw new ClientTransportException("Unknown server host: " + host, e);
        } catch (IOException e) {
            disconnect();
            throw new ClientTransportException("I/O error during connection to " + host + ":" + port + ": " + e.getMessage(), e);
        }
    }

    /**
     * Checks whether the TCP socket is currently connected and open.
     *
     * @return true if connected and healthy; false otherwise
     */
    public synchronized boolean isConnected() {
        return socket != null && socket.isConnected() && !socket.isClosed()
                && !socket.isInputShutdown() && !socket.isOutputShutdown();
    }

    /**
     * Sends a {@link Message} to the server.
     *
     * @param message the message to send
     * @throws ClientTransportException if the client is disconnected or an I/O error occurs
     * @throws IllegalArgumentException if message is null
     */
    public void send(Message message) throws ClientTransportException {
        if (message == null) {
            throw new IllegalArgumentException("Message cannot be null");
        }
        if (!isConnected()) {
            throw new ClientTransportException("Cannot send message: client is not connected to " + host + ":" + port);
        }

        synchronized (sendLock) {
            try {
                ProtocolFramer.writeMessage(out, message);
            } catch (IOException e) {
                disconnect();
                String err = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                if (err.contains("reset") || err.contains("Broken pipe") || err.contains("closed")) {
                    throw new ClientTransportException("Server disconnected unexpectedly during send: " + err, e);
                }
                throw new ClientTransportException("Failed to send message (" + message.getType() + "): " + err, e);
            }
        }
    }

    /**
     * Reads a single framed {@link Message} from the server.
     *
     * @return the decoded Message
     * @throws ClientTransportException if read fails, times out, or connection is closed
     */
    public Message receive() throws ClientTransportException {
        if (!isConnected()) {
            throw new ClientTransportException("Cannot receive message: client is not connected to " + host + ":" + port);
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
                throw new ClientTransportException("Received invalid or malformed message from server: " + e.getMessage(), e);
            } catch (IOException e) {
                disconnect();
                String err = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                if (err.contains("reset") || err.contains("Broken pipe") || err.contains("closed")) {
                    throw new ClientTransportException("Server disconnected unexpectedly while awaiting response: " + err, e);
                }
                throw new ClientTransportException("I/O error while reading response: " + err, e);
            }
        }
    }

    /**
     * Performs a synchronous request-response exchange.
     * <p>
     * Sends the provided request message and waits for the matching response.
     * If the response contains a {@code requestId}, it is verified against the request's ID.
     *
     * @param request the request message to send
     * @return the server response message
     * @throws ClientTransportException if sending, receiving, or correlation fails
     * @throws IllegalArgumentException if request is null
     */
    public synchronized Message sendRequest(Message request) throws ClientTransportException {
        if (request == null) {
            throw new IllegalArgumentException("Request message cannot be null");
        }
        if (!isConnected()) {
            throw new ClientTransportException("Cannot send request: client is not connected to " + host + ":" + port);
        }

        send(request);
        Message response = receive();

        // Verify request-response correlation if both have IDs
        if (request.getRequestId() != null && response.getRequestId() != null
                && !request.getRequestId().equals(response.getRequestId())) {
            throw new ClientTransportException("Request correlation mismatch: expected requestId '"
                    + request.getRequestId() + "' but received '" + response.getRequestId() + "'");
        }

        return response;
    }

    /**
     * Disconnects from the server and safely releases all underlying socket and stream resources.
     * Safe to call multiple times or when already disconnected.
     */
    public synchronized void disconnect() {
        try {
            if (in != null) {
                in.close();
            }
        } catch (IOException ignored) {
        }
        try {
            if (out != null) {
                out.close();
            }
        } catch (IOException ignored) {
        }
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException ignored) {
        }
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

    public int getConnectTimeoutMillis() {
        return connectTimeoutMillis;
    }

    public int getReadTimeoutMillis() {
        return readTimeoutMillis;
    }

    public InetSocketAddress getRemoteAddress() {
        return (socket != null && socket.isConnected()) ? (InetSocketAddress) socket.getRemoteSocketAddress() : null;
    }

    public InetSocketAddress getLocalAddress() {
        return (socket != null && socket.isConnected()) ? (InetSocketAddress) socket.getLocalSocketAddress() : null;
    }
}
