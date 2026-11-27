package com.classpulse.server.security;

import com.classpulse.common.protocol.Message;

/**
 * Functional handler for processing messages received over a secure TLS connection.
 */
@FunctionalInterface
public interface TlsMessageHandler {

    /**
     * Handles an incoming protocol {@link Message} from a TLS client.
     *
     * @param connection the client connection
     * @param message    the incoming message
     * @return an optional response {@link Message} to return to the client, or {@code null} if no response
     */
    Message handle(TlsClientConnection connection, Message message);
}
