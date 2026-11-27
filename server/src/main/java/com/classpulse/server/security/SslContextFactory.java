package com.classpulse.server.security;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.SecureRandom;

/**
 * Factory for creating configured JSSE {@link SSLContext} instances
 * for TLS servers and clients.
 * <p>
 * Does NOT permit trust-all managers; strict X.509 certificate validation
 * is enforced at all times.
 */
public final class SslContextFactory {

    private SslContextFactory() {
    }

    /**
     * Creates an initialized server-side {@link SSLContext} from the given {@link TlsConfig}.
     *
     * @param config the TLS configuration
     * @return initialized {@link SSLContext}
     * @throws TlsConfigurationException if loading keystore or initializing context fails
     */
    public static SSLContext createServerContext(TlsConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("TlsConfig cannot be null");
        }
        if (config.getKeystorePath() == null || config.getKeystorePath().isBlank()) {
            throw new TlsConfigurationException("Server keystore path is not configured. Set "
                    + TlsConfig.PROP_KEYSTORE_PATH + " or CLASSPULSE_TLS_KEYSTORE_PATH.");
        }

        char[] keystorePass = config.getKeystorePassword();
        char[] keyPass = config.getKeyPassword();
        if (keystorePass == null) {
            throw new TlsConfigurationException("Server keystore password is not configured. Set "
                    + TlsConfig.PROP_KEYSTORE_PASSWORD + " or CLASSPULSE_TLS_KEYSTORE_PASSWORD.");
        }

        try {
            KeyStore keyStore = loadKeyStore(config.getKeystorePath(), keystorePass, config.getKeystoreType());

            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(keyStore, keyPass != null ? keyPass : keystorePass);
            KeyManager[] keyManagers = kmf.getKeyManagers();

            TrustManager[] trustManagers = null;
            if (config.getTruststorePath() != null && !config.getTruststorePath().isBlank()) {
                char[] tsPass = config.getTruststorePassword();
                KeyStore trustStore = loadKeyStore(config.getTruststorePath(), tsPass, config.getTruststoreType());
                TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                tmf.init(trustStore);
                trustManagers = tmf.getTrustManagers();
            }

            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(keyManagers, trustManagers, new SecureRandom());
            return sslContext;
        } catch (GeneralSecurityException | IOException e) {
            throw new TlsConfigurationException("Failed to initialize server SSLContext: " + e.getMessage(), e);
        }
    }

    /**
     * Creates an initialized client-side {@link SSLContext} with the given truststore.
     *
     * @param truststorePath     path to truststore file or classpath resource
     * @param truststorePassword password for truststore (can be null/empty for public certs)
     * @param truststoreType     type such as "PKCS12" or "JKS" (defaults to PKCS12 if null)
     * @return initialized {@link SSLContext}
     * @throws TlsConfigurationException if loading truststore or initializing context fails
     */
    public static SSLContext createClientContext(String truststorePath, char[] truststorePassword, String truststoreType) {
        if (truststorePath == null || truststorePath.isBlank()) {
            throw new IllegalArgumentException("Truststore path cannot be null or empty");
        }

        try {
            KeyStore trustStore = loadKeyStore(truststorePath, truststorePassword,
                    truststoreType != null ? truststoreType : TlsConfig.DEFAULT_KEYSTORE_TYPE);

            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(trustStore);

            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, tmf.getTrustManagers(), new SecureRandom());
            return sslContext;
        } catch (GeneralSecurityException | IOException e) {
            throw new TlsConfigurationException("Failed to initialize client SSLContext: " + e.getMessage(), e);
        }
    }

    /**
     * Loads a {@link KeyStore} from file or classpath resource.
     */
    public static KeyStore loadKeyStore(String path, char[] password, String type)
            throws GeneralSecurityException, IOException {
        String storeType = type != null ? type : TlsConfig.DEFAULT_KEYSTORE_TYPE;
        KeyStore keyStore = KeyStore.getInstance(storeType);

        try (InputStream is = openInputStream(path)) {
            keyStore.load(is, password);
        }
        return keyStore;
    }

    private static InputStream openInputStream(String path) throws IOException {
        File file = new File(path);
        if (file.exists() && file.isFile()) {
            return new FileInputStream(file);
        }

        // Fallback: try loading from classpath
        String resourcePath = path.startsWith("/") ? path : "/" + path;
        InputStream resourceStream = SslContextFactory.class.getResourceAsStream(resourcePath);
        if (resourceStream != null) {
            return resourceStream;
        }

        throw new FileNotFoundException("Keystore/truststore file not found at filesystem path '"
                + file.getAbsolutePath() + "' or classpath resource '" + resourcePath + "'");
    }
}
