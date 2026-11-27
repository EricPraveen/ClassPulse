package com.classpulse.client.network;

import com.classpulse.common.protocol.Message;
import com.classpulse.common.protocol.MessageType;
import com.classpulse.common.protocol.ProtocolFramer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManagerFactory;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("TlsClient and Secure Communication Tests")
class TlsClientTest {

    private static File validKeystore;
    private static File validTruststore;
    private static File untrustedTruststore;
    private static File wrongHostKeystore;
    private static File wrongHostTruststore;

    private SimpleTlsServer activeServer;
    private ExecutorService serverExecutor;

    private static void runKeytool(String... args) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(args);
        Process p = pb.redirectErrorStream(true).start();
        assertTrue(p.waitFor(10, TimeUnit.SECONDS), "keytool timed out");
        assertEquals(0, p.exitValue(), "keytool command failed: " + String.join(" ", args));
    }

    private static void generateKeyStoreAndTrustStore(File keystore, File truststore, String password,
                                                     String alias, String dname, String san) throws Exception {
        File certFile = new File(keystore.getParentFile(), alias + "-cert.crt");

        // 1. Generate keypair & keystore
        runKeytool("keytool", "-genkeypair",
                "-alias", alias,
                "-keyalg", "RSA",
                "-keysize", "2048",
                "-validity", "1",
                "-keystore", keystore.getAbsolutePath(),
                "-storetype", "PKCS12",
                "-storepass", password,
                "-keypass", password,
                "-dname", dname,
                "-ext", san);

        // 2. Export certificate
        runKeytool("keytool", "-exportcert",
                "-alias", alias,
                "-keystore", keystore.getAbsolutePath(),
                "-storepass", password,
                "-file", certFile.getAbsolutePath(),
                "-rfc");

        // 3. Import certificate into truststore
        runKeytool("keytool", "-importcert",
                "-alias", alias,
                "-file", certFile.getAbsolutePath(),
                "-keystore", truststore.getAbsolutePath(),
                "-storetype", "PKCS12",
                "-storepass", password,
                "-noprompt");

        certFile.delete();
    }

    @BeforeAll
    static void initCerts(@TempDir Path tempDir) throws Exception {
        // 1. Valid certificates (CN=localhost, SAN=dns:localhost,ip:127.0.0.1)
        validKeystore = tempDir.resolve("valid-server.p12").toFile();
        validTruststore = tempDir.resolve("valid-client-ts.p12").toFile();
        generateKeyStoreAndTrustStore(validKeystore, validTruststore, "changeit",
                "valid-server", "CN=localhost, OU=ClassPulse", "SAN=dns:localhost,ip:127.0.0.1");

        // 2. Untrusted truststore (contains only a dummy cert)
        untrustedTruststore = tempDir.resolve("untrusted-ts.p12").toFile();
        File dummyKs = tempDir.resolve("dummy.p12").toFile();
        generateKeyStoreAndTrustStore(dummyKs, untrustedTruststore, "changeit",
                "dummy", "CN=dummy.example.com", "SAN=dns:dummy.example.com");

        // 3. Wrong host keystore (CN=unmatched.example.com, no localhost in SAN)
        wrongHostKeystore = tempDir.resolve("wronghost-server.p12").toFile();
        wrongHostTruststore = tempDir.resolve("wronghost-ts.p12").toFile();
        generateKeyStoreAndTrustStore(wrongHostKeystore, wrongHostTruststore, "changeit",
                "wronghost", "CN=unmatched.example.com", "SAN=dns:unmatched.example.com");
    }

    @BeforeEach
    void setUp() {
        serverExecutor = Executors.newCachedThreadPool();
    }

    @AfterEach
    void tearDown() {
        if (activeServer != null) {
            activeServer.close();
            activeServer = null;
        }
        if (serverExecutor != null) {
            serverExecutor.shutdownNow();
        }
    }

    private SSLContext createServerContext(File keystoreFile, String password) throws Exception {
        KeyStore ks = KeyStore.getInstance("PKCS12");
        try (InputStream is = new FileInputStream(keystoreFile)) {
            ks.load(is, password.toCharArray());
        }
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(ks, password.toCharArray());

        SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(kmf.getKeyManagers(), null, new SecureRandom());
        return ctx;
    }

    @Test
    @DisplayName("1. TLS server starts, 2. client connects, 3. handshake completes, and 5. TLS details displayed")
    void testSuccessfulTlsConnectAndSessionInfo() throws Exception {
        SSLContext serverCtx = createServerContext(validKeystore, "changeit");
        activeServer = new SimpleTlsServer(serverCtx);
        activeServer.start();

        try (TlsClient client = new TlsClient("localhost", activeServer.getPort(),
                validTruststore.getAbsolutePath(), "changeit".toCharArray())) {

            assertFalse(client.isConnected());
            client.connect();

            assertTrue(client.isConnected());
            TlsSessionInfo session = client.getSessionInfo();
            assertNotNull(session);
            assertTrue(session.getProtocol().startsWith("TLSv1."));
            assertNotNull(session.getCipherSuite());
            assertFalse(session.getCipherSuite().isEmpty());
            assertTrue(session.getPeerPrincipal().contains("CN=localhost"));
        }
    }

    @Test
    @DisplayName("4. Client and server exchange framed messages securely over TLS")
    void testSecureRequestResponseExchange() throws Exception {
        SSLContext serverCtx = createServerContext(validKeystore, "changeit");
        activeServer = new SimpleTlsServer(serverCtx);
        CountDownLatch requestReceived = new CountDownLatch(1);

        activeServer.setHandler((socket, in, out) -> {
            Message req = ProtocolFramer.readMessage(in);
            assertNotNull(req);
            assertEquals(MessageType.CREATE_CLASSROOM, req.getType());
            assertEquals("Prof. Turing", req.getPayloadString("lecturerName"));
            requestReceived.countDown();

            Message resp = Message.response(MessageType.CLASSROOM_CREATED, req.getRequestId());
            resp.putPayload("classroomCode", "NET201");
            ProtocolFramer.writeMessage(out, resp);
        });

        activeServer.start();

        try (TlsClient client = new TlsClient("localhost", activeServer.getPort(),
                validTruststore.getAbsolutePath(), "changeit".toCharArray())) {
            client.connect();

            Message request = new Message(MessageType.CREATE_CLASSROOM, "req-tls-001");
            request.putPayload("lecturerName", "Prof. Turing");

            Message response = client.sendRequest(request);

            assertTrue(requestReceived.await(5, TimeUnit.SECONDS));
            assertNotNull(response);
            assertEquals(MessageType.CLASSROOM_CREATED, response.getType());
            assertEquals("req-tls-001", response.getRequestId());
            assertEquals("NET201", response.getPayloadString("classroomCode"));
        }
    }

    @Test
    @DisplayName("6. An untrusted server certificate is rejected during TLS handshake")
    void testUntrustedServerCertificateRejected() throws Exception {
        // Server uses valid-server cert, but client truststore only trusts dummy cert
        SSLContext serverCtx = createServerContext(validKeystore, "changeit");
        activeServer = new SimpleTlsServer(serverCtx);
        activeServer.start();

        try (TlsClient client = new TlsClient("localhost", activeServer.getPort(),
                untrustedTruststore.getAbsolutePath(), "changeit".toCharArray())) {

            ClientTransportException ex = assertThrows(ClientTransportException.class, client::connect);
            assertNotNull(ex.getMessage());
            assertTrue(ex.getMessage().contains("TLS Handshake failed")
                    || ex.getMessage().contains("PKIX path building failed")
                    || ex.getMessage().contains("validator"),
                    "Expected certificate trust failure, but got: " + ex.getMessage());
            assertFalse(client.isConnected());
        }
    }

    @Test
    @DisplayName("7. An incorrect server identity (hostname mismatch) is rejected")
    void testIncorrectServerIdentityRejected() throws Exception {
        // Server cert is trusted by wrongHostTruststore, but cert is for 'unmatched.example.com', not 'localhost'
        SSLContext serverCtx = createServerContext(wrongHostKeystore, "changeit");
        activeServer = new SimpleTlsServer(serverCtx);
        activeServer.start();

        // When connecting to 'localhost' with default hostname verification enabled (verifyHostname = true)
        try (TlsClient client = new TlsClient("localhost", activeServer.getPort(),
                wrongHostTruststore.getAbsolutePath(), "changeit".toCharArray())) {

            ClientTransportException ex = assertThrows(ClientTransportException.class, client::connect);
            assertNotNull(ex.getMessage());
            assertTrue(ex.getMessage().contains("TLS Handshake failed")
                    || ex.getMessage().contains("No name matching")
                    || ex.getMessage().contains("Server identity unverified")
                    || ex.getMessage().contains("CertificateException"),
                    "Expected identity verification failure, but got: " + ex.getMessage());
            assertFalse(client.isConnected());
        }
    }

    @Test
    @DisplayName("8. Connections and resources close correctly")
    void testConnectionCloseAndResources() throws Exception {
        SSLContext serverCtx = createServerContext(validKeystore, "changeit");
        activeServer = new SimpleTlsServer(serverCtx);
        activeServer.start();

        TlsClient client = new TlsClient("localhost", activeServer.getPort(),
                validTruststore.getAbsolutePath(), "changeit".toCharArray());
        client.connect();
        assertTrue(client.isConnected());

        client.disconnect();
        assertFalse(client.isConnected());

        // Operations after close throw ClientTransportException
        Message req = new Message(MessageType.HEARTBEAT, "hb-1");
        ClientTransportException ex = assertThrows(ClientTransportException.class, () -> client.send(req));
        assertTrue(ex.getMessage().contains("not connected"));

        assertThrows(ClientTransportException.class, client::receive);
    }

    @Test
    @DisplayName("Constructor argument validations")
    void testConstructorValidations() {
        assertThrows(IllegalArgumentException.class, () -> new TlsClient(null, 5443));
        assertThrows(IllegalArgumentException.class, () -> new TlsClient("  ", 5443));
        assertThrows(IllegalArgumentException.class, () -> new TlsClient("localhost", 0));
        assertThrows(IllegalArgumentException.class, () -> new TlsClient("localhost", 70000));
        assertThrows(IllegalArgumentException.class, () -> new TlsClient("localhost", 5443, (SSLContext) null));
    }

    /**
     * Minimal TLS test server socket wrapper.
     */
    private static class SimpleTlsServer implements AutoCloseable {
        private final SSLContext sslContext;
        private SSLServerSocket serverSocket;
        private volatile boolean running;
        private TlsSocketConsumer handler;
        private Thread thread;

        public SimpleTlsServer(SSLContext sslContext) {
            this.sslContext = sslContext;
        }

        public void setHandler(TlsSocketConsumer handler) {
            this.handler = handler;
        }

        public synchronized void start() throws IOException {
            SSLServerSocketFactory ssf = sslContext.getServerSocketFactory();
            serverSocket = (SSLServerSocket) ssf.createServerSocket(0); // Ephemeral port
            serverSocket.setReuseAddress(true);
            running = true;

            thread = new Thread(() -> {
                while (running && !serverSocket.isClosed()) {
                    try {
                        SSLSocket socket = (SSLSocket) serverSocket.accept();
                        socket.startHandshake();
                        if (handler != null) {
                            handler.accept(socket, socket.getInputStream(), socket.getOutputStream());
                        }
                    } catch (SocketException e) {
                        break;
                    } catch (Exception ignored) {
                    }
                }
            }, "simple-tls-server");
            thread.setDaemon(true);
            thread.start();
        }

        public int getPort() {
            return serverSocket.getLocalPort();
        }

        @Override
        public void close() {
            running = false;
            if (serverSocket != null) {
                try {
                    serverSocket.close();
                } catch (IOException ignored) {}
            }
        }
    }

    @FunctionalInterface
    private interface TlsSocketConsumer {
        void accept(SSLSocket socket, InputStream in, java.io.OutputStream out) throws Exception;
    }
}
