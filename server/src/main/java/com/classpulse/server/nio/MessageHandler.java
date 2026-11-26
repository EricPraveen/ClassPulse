package com.classpulse.server.nio;

import com.classpulse.common.protocol.Message;

/**
 * Handles decoded {@link Message} objects received from connected clients.
 */
@FunctionalInterface
public interface MessageHandler {

    /**
     * Processes a decoded message from a client connection.
     *
     * @param client  the client connection that sent the message
     * @param message the decoded protocol message
     */
    void handle(ClientConnection client, Message message);
}
