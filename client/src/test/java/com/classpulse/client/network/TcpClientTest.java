package com.classpulse.client.network;

import com.classpulse.common.protocol.Message;
import com.classpulse.common.protocol.MessageType;
import com.classpulse.common.protocol.ProtocolFramer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class TcpClientTest {

    @Test
    @DisplayName("1. Client can connect to a running test server and verify connection state")
    void testConnectAndIsConnected() throws Exception {
        try (SimpleTestServer server = new SimpleTestServer()) {
            server.start();

            try (TcpClient client = new TcpClient("127.0.0.1", server.getPort())) {
                assertFalse(client.isConnected());
                client.connect();
                assertTrue(client.isConnected());
                assertNotNull(client.getRemoteAddress());
                assertNotNull(client.getLocalAddress());
                assertEquals("127.0.0.1", client.getHost());
                assertEquals(server.getPort(), client.getPort());
            }
        }
    }

    @Test
    @DisplayName("2. Client can send a request and 3. receive the expected response with matching requestId")
    void testSendRequestAndReceiveResponse() throws Exception {
        try (SimpleTestServer server = new SimpleTestServer()) {
            server.setHandler(socket -> {
                Message req = ProtocolFramer.readMessage(socket.getInputStream());
                assertNotNull(req);
                assertEquals(MessageType.CREATE_CLASSROOM, req.getType());
                assertEquals("req-test-1", req.getRequestId());
                assertEquals("Prof. Alan", req.getPayloadString("lecturerName"));

                Message resp = new Message(MessageType.CLASSROOM_CREATED, req.getRequestId());
                resp.putPayload("classroomCode", "CS101");
                ProtocolFramer.writeMessage(socket.getOutputStream(), resp);
            });
            server.start();

            try (TcpClient client = new TcpClient("127.0.0.1", server.getPort())) {
                client.connect();

                Message request = new Message(MessageType.CREATE_CLASSROOM, "req-test-1");
                request.putPayload("lecturerName", "Prof. Alan");

                Message response = client.sendRequest(request);

                assertNotNull(response);
                assertEquals(MessageType.CLASSROOM_CREATED, response.getType());
                assertEquals("req-test-1", response.getRequestId());
                assertEquals("CS101", response.getPayloadString("classroomCode"));
            }
        }
    }

    @Test
    @DisplayName("4. Client can disconnect cleanly and release socket resources")
    void testDisconnect() throws Exception {
        try (SimpleTestServer server = new SimpleTestServer()) {
            server.start();

            TcpClient client = new TcpClient("127.0.0.1", server.getPort());
            client.connect();
            assertTrue(client.isConnected());

            client.disconnect();
            assertFalse(client.isConnected());

            // Calling disconnect multiple times should be safe
            assertDoesNotThrow(client::disconnect);
            assertDoesNotThrow(client::close);
        }
    }

    @Test
    @DisplayName("5. Client handles a server that is unavailable (connection refused)")
    void testServerUnavailable() throws IOException {
        // Allocate an ephemeral port then close it immediately
        int closedPort;
        try (ServerSocket temp = new ServerSocket(0)) {
            closedPort = temp.getLocalPort();
        }

        TcpClient client = new TcpClient("127.0.0.1", closedPort, 1000, 1000);
        ClientTransportException ex = assertThrows(ClientTransportException.class, client::connect);
        assertTrue(ex.getMessage().contains("Connection refused") || ex.getMessage().contains("Failed to connect"));
        assertFalse(client.isConnected());
    }

    @Test
    @DisplayName("6. Client handles an unexpected connection closure by the server")
    void testUnexpectedConnectionClosure() throws Exception {
        try (SimpleTestServer server = new SimpleTestServer()) {
            server.setHandler(Socket::close); // Close connection immediately on connect
            server.start();

            try (TcpClient client = new TcpClient("127.0.0.1", server.getPort())) {
                client.connect();

                Message request = new Message(MessageType.JOIN_CLASSROOM, "req-join-1");
                request.putPayload("studentName", "Bob");

                ClientTransportException ex = assertThrows(ClientTransportException.class, () -> client.sendRequest(request));
                assertNotNull(ex.getMessage());
                assertTrue(ex.getMessage().contains("disconnected unexpectedly")
                        || ex.getMessage().contains("Connection closed")
                        || ex.getMessage().contains("reset")
                        || ex.getMessage().contains("Failed to send"));
                assertFalse(client.isConnected());
            }
        }
    }

    @Test
    @DisplayName("7. Message framing conforms to shared protocol (4-byte big-endian header and UTF-8 JSON)")
    void testMessageFramingProtocol() throws Exception {
        try (SimpleTestServer server = new SimpleTestServer()) {
            AtomicReference<byte[]> rawReceivedBytes = new AtomicReference<>();
            CountDownLatch latch = new CountDownLatch(1);

            server.setHandler(socket -> {
                byte[] header = socket.getInputStream().readNBytes(4);
                int length = java.nio.ByteBuffer.wrap(header).getInt();
                byte[] body = socket.getInputStream().readNBytes(length);

                byte[] full = new byte[header.length + body.length];
                System.arraycopy(header, 0, full, 0, header.length);
                System.arraycopy(body, 0, full, header.length, body.length);
                rawReceivedBytes.set(full);

                // Echo decoded back
                Message decoded = ProtocolFramer.decodeMessage(full);
                ProtocolFramer.writeMessage(socket.getOutputStream(), Message.success(decoded.getRequestId(), "OK"));
                latch.countDown();
            });
            server.start();

            try (TcpClient client = new TcpClient("127.0.0.1", server.getPort())) {
                client.connect();
                Message msg = new Message(MessageType.SUBMIT_ANSWER, "req-framing-99");
                msg.putPayload("pollId", "p-1");
                msg.putPayload("selectedOption", 3);

                Message resp = client.sendRequest(msg);
                assertEquals(MessageType.SUCCESS, resp.getType());
                assertEquals("req-framing-99", resp.getRequestId());

                assertTrue(latch.await(3, TimeUnit.SECONDS));
                assertNotNull(rawReceivedBytes.get());

                // Verify using ProtocolFramer decode
                Message decodedFromWire = ProtocolFramer.decodeMessage(rawReceivedBytes.get());
                assertEquals(msg, decodedFromWire);
            }
        }
    }

    @Test
    @DisplayName("8. Rejects operations when client is disconnected")
    void testDisconnectedOperations() {
        TcpClient client = new TcpClient("127.0.0.1", 9999);
        Message msg = new Message(MessageType.HEARTBEAT, "req-h");

        assertThrows(ClientTransportException.class, () -> client.send(msg));
        assertThrows(ClientTransportException.class, client::receive);
        assertThrows(ClientTransportException.class, () -> client.sendRequest(msg));
    }

    @Test
    @DisplayName("9. Throws ClientTransportException on request-response ID mismatch")
    void testRequestIdMismatch() throws Exception {
        try (SimpleTestServer server = new SimpleTestServer()) {
            server.setHandler(socket -> {
                ProtocolFramer.readMessage(socket.getInputStream());
                // Deliberately respond with a mismatched requestId
                Message mismatched = new Message(MessageType.SUCCESS, "different-id");
                ProtocolFramer.writeMessage(socket.getOutputStream(), mismatched);
            });
            server.start();

            try (TcpClient client = new TcpClient("127.0.0.1", server.getPort())) {
                client.connect();
                Message req = new Message(MessageType.CREATE_CLASSROOM, "expected-id");
                ClientTransportException ex = assertThrows(ClientTransportException.class, () -> client.sendRequest(req));
                assertTrue(ex.getMessage().contains("Request correlation mismatch"));
            }
        }
    }

    @Test
    @DisplayName("10. Throws ClientTransportException on socket read timeout")
    void testReadTimeout() throws Exception {
        try (SimpleTestServer server = new SimpleTestServer()) {
            server.setHandler(socket -> {
                ProtocolFramer.readMessage(socket.getInputStream());
                // Sleep longer than read timeout without responding
                Thread.sleep(800);
            });
            server.start();

            // Client with 200ms read timeout
            try (TcpClient client = new TcpClient("127.0.0.1", server.getPort(), 2000, 200)) {
                client.connect();
                Message req = new Message(MessageType.HEARTBEAT, "req-timeout");
                ClientTransportException ex = assertThrows(ClientTransportException.class, () -> client.sendRequest(req));
                assertTrue(ex.getMessage().contains("Read timed out"));
            }
        }
    }

    @Test
    @DisplayName("11. Validates constructor parameters")
    void testConstructorValidation() {
        assertThrows(IllegalArgumentException.class, () -> new TcpClient(null, 5000));
        assertThrows(IllegalArgumentException.class, () -> new TcpClient("  ", 5000));
        assertThrows(IllegalArgumentException.class, () -> new TcpClient("localhost", 0));
        assertThrows(IllegalArgumentException.class, () -> new TcpClient("localhost", 65536));
        assertThrows(IllegalArgumentException.class, () -> new TcpClient("localhost", 5000, -1, 1000));
        assertThrows(IllegalArgumentException.class, () -> new TcpClient("localhost", 5000, 1000, -1));
    }

    @Test
    @DisplayName("12. Prevent duplicate connect calls on already connected client")
    void testDuplicateConnect() throws Exception {
        try (SimpleTestServer server = new SimpleTestServer()) {
            server.start();
            try (TcpClient client = new TcpClient("127.0.0.1", server.getPort())) {
                client.connect();
                assertThrows(IllegalStateException.class, client::connect);
            }
        }
    }

    /**
     * Minimal self-contained local test server for testing client transport without external dependencies.
     */
    private static class SimpleTestServer implements AutoCloseable {
        private final ServerSocket serverSocket;
        private final AtomicBoolean running = new AtomicBoolean(true);
        private SocketHandler handler = socket -> {};
        private Thread thread;

        interface SocketHandler {
            void handle(Socket socket) throws Exception;
        }

        public SimpleTestServer() throws IOException {
            this.serverSocket = new ServerSocket(0);
        }

        public int getPort() {
            return serverSocket.getLocalPort();
        }

        public void setHandler(SocketHandler handler) {
            this.handler = handler;
        }

        public void start() {
            thread = new Thread(() -> {
                while (running.get() && !serverSocket.isClosed()) {
                    try {
                        Socket socket = serverSocket.accept();
                        new Thread(() -> {
                            try (socket) {
                                handler.handle(socket);
                            } catch (Exception ignored) {
                            }
                        }).start();
                    } catch (IOException e) {
                        break;
                    }
                }
            });
            thread.setDaemon(true);
            thread.start();
        }

        @Override
        public void close() {
            running.set(false);
            try {
                serverSocket.close();
            } catch (IOException ignored) {
            }
            if (thread != null) {
                thread.interrupt();
            }
        }
    }
}
