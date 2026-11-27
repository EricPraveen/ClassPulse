package com.classpulse.server.security;

import com.classpulse.common.protocol.Message;

import java.io.IOException;

/**
 * Interface representing an active secure TLS client connection on the server.
 */
public interface TlsClientConnection extends AutoCloseable {

    /**
     * Sends a framed protocol message securely over the TLS channel.
     *
     * @param message the message to send
     * @throws IOException if sending fails or the connection is closed
     */
    void send(Message message) throws IOException;

    /**
     * Checks whether the connection is closed.
     */
    boolean isClosed();

    /**
     * Returns the formatted remote IP and port of the client.
     */
    String getRemoteAddress();

    /**
     * Returns metadata for the negotiated TLS session.
     */
    TlsSessionInfo getSessionInfo();

    /**
     * Closes the underlying SSL socket and connection resources.
     */
    @Override
    void close();
}
