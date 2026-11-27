package com.classpulse.server.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.net.ssl.SSLContext;
import java.io.File;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("TlsServer and JSSE Configuration Tests")
class TlsServerTest {

    @TempDir
    Path tempDir;

    private static void generateKeyStore(String keystorePath, String password, String dname, String san) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(
                "keytool", "-genkeypair",
                "-alias", "server-key",
                "-keyalg", "RSA",
                "-keysize", "2048",
                "-validity", "1",
                "-keystore", keystorePath,
                "-storetype", "PKCS12",
                "-storepass", password,
                "-keypass", password,
                "-dname", dname,
                "-ext", san
        );
        Process p = pb.redirectErrorStream(true).start();
        assertTrue(p.waitFor(10, TimeUnit.SECONDS), "keytool timed out");
        assertEquals(0, p.exitValue(), "keytool failed to generate keystore");
    }

    @Test
    @DisplayName("TlsConfig builder sets defaults and overrides correctly")
    void testTlsConfigBuilderDefaults() {
        TlsConfig config = TlsConfig.builder()
                .port(5443)
                .keystorePath("certs/server.p12")
                .keystorePassword("secret".toCharArray())
                .build();

        assertEquals(5443, config.getPort());
        assertEquals("certs/server.p12", config.getKeystorePath());
        assertEquals("PKCS12", config.getKeystoreType());
        assertArrayEquals("secret".toCharArray(), config.getKeystorePassword());
        assertArrayEquals("secret".toCharArray(), config.getKeyPassword());
        assertFalse(config.isClientAuthRequired());
        assertArrayEquals(new String[]{"TLSv1.3", "TLSv1.2"}, config.getEnabledProtocols());
        assertNull(config.getEnabledCipherSuites());
    }

    @Test
    @DisplayName("TlsConfig wipes sensitive passwords from memory")
    void testTlsConfigWipePasswords() {
        char[] pass = "my-secret-pass".toCharArray();
        TlsConfig config = TlsConfig.builder()
                .keystorePassword(pass)
                .keyPassword(pass)
                .truststorePassword(pass)
                .build();

        config.wipePasswords();
        // Cloned arrays stored in config must be wiped to '\0'
        for (char c : config.getKeystorePassword()) {
            assertEquals('\0', c);
        }
        for (char c : config.getKeyPassword()) {
            assertEquals('\0', c);
        }
        for (char c : config.getTruststorePassword()) {
            assertEquals('\0', c);
        }
    }

    @Test
    @DisplayName("SslContextFactory rejects missing keystore path or password")
    void testSslContextFactoryMissingInputs() {
        TlsConfig noPath = TlsConfig.builder().build();
        assertThrows(TlsConfigurationException.class, () -> SslContextFactory.createServerContext(noPath));

        TlsConfig noPass = TlsConfig.builder().keystorePath("some/path.p12").build();
        assertThrows(TlsConfigurationException.class, () -> SslContextFactory.createServerContext(noPass));

        assertThrows(IllegalArgumentException.class, () -> SslContextFactory.createClientContext(null, null, null));
    }

    @Test
    @DisplayName("SslContextFactory throws on non-existent keystore file")
    void testSslContextFactoryNonExistentFile() {
        TlsConfig config = TlsConfig.builder()
                .keystorePath("invalid/path/that/does/not/exist.p12")
                .keystorePassword("changeit".toCharArray())
                .build();

        assertThrows(TlsConfigurationException.class, () -> SslContextFactory.createServerContext(config));
    }

    @Test
    @DisplayName("SslContextFactory loads valid keystore and creates server SSLContext")
    void testSslContextFactorySuccess() throws Exception {
        File ksFile = tempDir.resolve("server-test.p12").toFile();
        generateKeyStore(ksFile.getAbsolutePath(), "changeit", "CN=localhost, OU=ClassPulse", "SAN=dns:localhost,ip:127.0.0.1");

        TlsConfig config = TlsConfig.builder()
                .keystorePath(ksFile.getAbsolutePath())
                .keystorePassword("changeit".toCharArray())
                .build();

        SSLContext context = SslContextFactory.createServerContext(config);
        assertNotNull(context);
        assertEquals("TLS", context.getProtocol());
        assertNotNull(context.getServerSocketFactory());
    }

    @Test
    @DisplayName("TlsServer starts on ephemeral port and stops cleanly")
    void testTlsServerLifecycle() throws Exception {
        File ksFile = tempDir.resolve("lifecycle-server.p12").toFile();
        generateKeyStore(ksFile.getAbsolutePath(), "changeit", "CN=localhost", "SAN=dns:localhost,ip:127.0.0.1");

        TlsConfig config = TlsConfig.builder()
                .port(0) // Port 0 binds to a free ephemeral port
                .keystorePath(ksFile.getAbsolutePath())
                .keystorePassword("changeit".toCharArray())
                .build();

        try (TlsServer server = new TlsServer(config, (conn, msg) -> null)) {
            assertFalse(server.isRunning());
            assertEquals(-1, server.getPort());

            server.start();

            assertTrue(server.isRunning());
            assertTrue(server.getPort() > 0, "Bound port should be allocated");
            assertEquals(0, server.getActiveConnectionCount());

            server.stop();
            assertFalse(server.isRunning());
        }
    }

    @Test
    @DisplayName("TlsSessionInfo formats display string accurately")
    void testTlsSessionInfoDisplay() {
        TlsSessionInfo info = new TlsSessionInfo("TLSv1.3", "TLS_AES_256_GCM_SHA384",
                "CN=client", "CN=localhost", "127.0.0.1", 5443, System.currentTimeMillis());

        assertEquals("TLSv1.3", info.getProtocol());
        assertEquals("TLS_AES_256_GCM_SHA384", info.getCipherSuite());
        assertEquals("CN=client", info.getPeerPrincipal());
        assertEquals("CN=localhost", info.getLocalPrincipal());
        assertEquals("127.0.0.1", info.getPeerHost());
        assertEquals(5443, info.getPeerPort());
        assertTrue(info.toString().contains("TLSv1.3"));
        assertTrue(info.toString().contains("TLS_AES_256_GCM_SHA384"));
    }
}
