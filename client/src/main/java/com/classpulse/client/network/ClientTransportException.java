package com.classpulse.client.network;

import java.io.IOException;

/**
 * Exception thrown when a network communication error occurs in the TCP client transport.
 */
public class ClientTransportException extends IOException {

    public ClientTransportException(String message) {
        super(message);
    }

    public ClientTransportException(String message, Throwable cause) {
        super(message, cause);
    }
}
