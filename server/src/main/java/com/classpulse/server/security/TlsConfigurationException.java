package com.classpulse.server.security;

/**
 * Exception thrown when TLS configuration, keystore/truststore loading,
 * or SSLContext initialization fails.
 */
public class TlsConfigurationException extends RuntimeException {

    public TlsConfigurationException(String message) {
        super(message);
    }

    public TlsConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}
