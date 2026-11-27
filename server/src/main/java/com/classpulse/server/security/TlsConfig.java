package com.classpulse.server.security;

import java.util.Arrays;

/**
 * Configuration holder for JSSE/TLS endpoints on the ClassPulse server.
 * <p>
 * Configuration values can be supplied programmatically or resolved from
 * environment variables and system properties to prevent hardcoded credentials.
 */
public class TlsConfig {

    public static final int DEFAULT_TLS_PORT = 5443;
    public static final String DEFAULT_KEYSTORE_TYPE = "PKCS12";
    public static final String[] DEFAULT_PROTOCOLS = new String[]{"TLSv1.3", "TLSv1.2"};

    public static final String PROP_PORT = "classpulse.tls.port";
    public static final String PROP_KEYSTORE_PATH = "classpulse.tls.keystore.path";
    public static final String PROP_KEYSTORE_PASSWORD = "classpulse.tls.keystore.password";
    public static final String PROP_KEYSTORE_TYPE = "classpulse.tls.keystore.type";
    public static final String PROP_KEY_PASSWORD = "classpulse.tls.key.password";
    public static final String PROP_TRUSTSTORE_PATH = "classpulse.tls.truststore.path";
    public static final String PROP_TRUSTSTORE_PASSWORD = "classpulse.tls.truststore.password";
    public static final String PROP_TRUSTSTORE_TYPE = "classpulse.tls.truststore.type";
    public static final String PROP_CLIENT_AUTH = "classpulse.tls.client.auth";

    private final int port;
    private final String keystorePath;
    private final char[] keystorePassword;
    private final String keystoreType;
    private final char[] keyPassword;
    private final String truststorePath;
    private final char[] truststorePassword;
    private final String truststoreType;
    private final boolean clientAuthRequired;
    private final String[] enabledProtocols;
    private final String[] enabledCipherSuites;

    private TlsConfig(Builder builder) {
        this.port = builder.port;
        this.keystorePath = builder.keystorePath;
        this.keystorePassword = builder.keystorePassword != null ? builder.keystorePassword.clone() : null;
        this.keystoreType = builder.keystoreType != null ? builder.keystoreType : DEFAULT_KEYSTORE_TYPE;
        this.keyPassword = builder.keyPassword != null ? builder.keyPassword.clone() : (this.keystorePassword != null ? this.keystorePassword.clone() : null);
        this.truststorePath = builder.truststorePath;
        this.truststorePassword = builder.truststorePassword != null ? builder.truststorePassword.clone() : null;
        this.truststoreType = builder.truststoreType != null ? builder.truststoreType : DEFAULT_KEYSTORE_TYPE;
        this.clientAuthRequired = builder.clientAuthRequired;
        this.enabledProtocols = builder.enabledProtocols != null ? builder.enabledProtocols.clone() : DEFAULT_PROTOCOLS.clone();
        this.enabledCipherSuites = builder.enabledCipherSuites != null ? builder.enabledCipherSuites.clone() : null;
    }

    /**
     * Resolves configuration from system properties and environment variables,
     * with fallback to developer defaults.
     *
     * @return populated {@link TlsConfig}
     */
    public static TlsConfig fromEnvironment() {
        Builder builder = builder();

        String portStr = getPropertyOrEnv(PROP_PORT, "CLASSPULSE_TLS_PORT");
        if (portStr != null && !portStr.isBlank()) {
            try {
                builder.port(Integer.parseInt(portStr.trim()));
            } catch (NumberFormatException ignored) {
            }
        }

        String ksPath = getPropertyOrEnv(PROP_KEYSTORE_PATH, "CLASSPULSE_TLS_KEYSTORE_PATH");
        if (ksPath != null) {
            builder.keystorePath(ksPath);
        }

        String ksPass = getPropertyOrEnv(PROP_KEYSTORE_PASSWORD, "CLASSPULSE_TLS_KEYSTORE_PASSWORD");
        if (ksPass != null) {
            builder.keystorePassword(ksPass.toCharArray());
        }

        String ksType = getPropertyOrEnv(PROP_KEYSTORE_TYPE, "CLASSPULSE_TLS_KEYSTORE_TYPE");
        if (ksType != null) {
            builder.keystoreType(ksType);
        }

        String keyPass = getPropertyOrEnv(PROP_KEY_PASSWORD, "CLASSPULSE_TLS_KEY_PASSWORD");
        if (keyPass != null) {
            builder.keyPassword(keyPass.toCharArray());
        }

        String tsPath = getPropertyOrEnv(PROP_TRUSTSTORE_PATH, "CLASSPULSE_TLS_TRUSTSTORE_PATH");
        if (tsPath != null) {
            builder.truststorePath(tsPath);
        }

        String tsPass = getPropertyOrEnv(PROP_TRUSTSTORE_PASSWORD, "CLASSPULSE_TLS_TRUSTSTORE_PASSWORD");
        if (tsPass != null) {
            builder.truststorePassword(tsPass.toCharArray());
        }

        String tsType = getPropertyOrEnv(PROP_TRUSTSTORE_TYPE, "CLASSPULSE_TLS_TRUSTSTORE_TYPE");
        if (tsType != null) {
            builder.truststoreType(tsType);
        }

        String clientAuth = getPropertyOrEnv(PROP_CLIENT_AUTH, "CLASSPULSE_TLS_CLIENT_AUTH");
        if (clientAuth != null) {
            builder.clientAuthRequired(Boolean.parseBoolean(clientAuth.trim()));
        }

        return builder.build();
    }

    private static String getPropertyOrEnv(String propKey, String envKey) {
        String val = System.getProperty(propKey);
        if (val == null || val.isBlank()) {
            val = System.getenv(envKey);
        }
        return val;
    }

    public static Builder builder() {
        return new Builder();
    }

    public int getPort() {
        return port;
    }

    public String getKeystorePath() {
        return keystorePath;
    }

    public char[] getKeystorePassword() {
        return keystorePassword != null ? keystorePassword.clone() : null;
    }

    public String getKeystoreType() {
        return keystoreType;
    }

    public char[] getKeyPassword() {
        return keyPassword != null ? keyPassword.clone() : null;
    }

    public String getTruststorePath() {
        return truststorePath;
    }

    public char[] getTruststorePassword() {
        return truststorePassword != null ? truststorePassword.clone() : null;
    }

    public String getTruststoreType() {
        return truststoreType;
    }

    public boolean isClientAuthRequired() {
        return clientAuthRequired;
    }

    public String[] getEnabledProtocols() {
        return enabledProtocols.clone();
    }

    public String[] getEnabledCipherSuites() {
        return enabledCipherSuites != null ? enabledCipherSuites.clone() : null;
    }

    /**
     * Wipes password arrays in memory.
     */
    public void wipePasswords() {
        if (keystorePassword != null) {
            Arrays.fill(keystorePassword, '\0');
        }
        if (keyPassword != null) {
            Arrays.fill(keyPassword, '\0');
        }
        if (truststorePassword != null) {
            Arrays.fill(truststorePassword, '\0');
        }
    }

    @Override
    public String toString() {
        return "TlsConfig{" +
                "port=" + port +
                ", keystorePath='" + keystorePath + '\'' +
                ", keystoreType='" + keystoreType + '\'' +
                ", truststorePath='" + truststorePath + '\'' +
                ", clientAuthRequired=" + clientAuthRequired +
                ", enabledProtocols=" + Arrays.toString(enabledProtocols) +
                '}';
    }

    public static class Builder {
        private int port = DEFAULT_TLS_PORT;
        private String keystorePath;
        private char[] keystorePassword;
        private String keystoreType = DEFAULT_KEYSTORE_TYPE;
        private char[] keyPassword;
        private String truststorePath;
        private char[] truststorePassword;
        private String truststoreType = DEFAULT_KEYSTORE_TYPE;
        private boolean clientAuthRequired = false;
        private String[] enabledProtocols = DEFAULT_PROTOCOLS;
        private String[] enabledCipherSuites;

        public Builder port(int port) {
            if (port < 0 || port > 65535) {
                throw new IllegalArgumentException("Port must be between 0 and 65535: " + port);
            }
            this.port = port;
            return this;
        }

        public Builder keystorePath(String keystorePath) {
            this.keystorePath = keystorePath;
            return this;
        }

        public Builder keystorePassword(char[] keystorePassword) {
            this.keystorePassword = keystorePassword != null ? keystorePassword.clone() : null;
            return this;
        }

        public Builder keystoreType(String keystoreType) {
            this.keystoreType = keystoreType;
            return this;
        }

        public Builder keyPassword(char[] keyPassword) {
            this.keyPassword = keyPassword != null ? keyPassword.clone() : null;
            return this;
        }

        public Builder truststorePath(String truststorePath) {
            this.truststorePath = truststorePath;
            return this;
        }

        public Builder truststorePassword(char[] truststorePassword) {
            this.truststorePassword = truststorePassword != null ? truststorePassword.clone() : null;
            return this;
        }

        public Builder truststoreType(String truststoreType) {
            this.truststoreType = truststoreType;
            return this;
        }

        public Builder clientAuthRequired(boolean clientAuthRequired) {
            this.clientAuthRequired = clientAuthRequired;
            return this;
        }

        public Builder enabledProtocols(String... enabledProtocols) {
            this.enabledProtocols = enabledProtocols != null ? enabledProtocols.clone() : null;
            return this;
        }

        public Builder enabledCipherSuites(String... enabledCipherSuites) {
            this.enabledCipherSuites = enabledCipherSuites != null ? enabledCipherSuites.clone() : null;
            return this;
        }

        public TlsConfig build() {
            return new TlsConfig(this);
        }
    }
}
