package com.classpulse.client.lecturer;

import com.classpulse.client.network.ClientTransportException;
import com.classpulse.client.network.TcpClient;
import com.classpulse.common.protocol.Message;
import com.classpulse.common.protocol.MessageType;
import com.classpulse.common.protocol.ProtocolFramer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class LecturerClientTest {

    private SimpleServer mockServer;
    private LecturerClient client;

    @BeforeEach
    void setUp() throws IOException {
        mockServer = new SimpleServer();
        mockServer.start();
        client = new LecturerClient("127.0.0.1", mockServer.getPort());
    }

    @AfterEach
    void tearDown() {
        if (client != null) {
            client.disconnect();
        }
        if (mockServer != null) {
            mockServer.close();
        }
    }

    @Test
    @DisplayName("1. Successful connection establishment and state tracking")
    void testConnectSuccess() throws Exception {
        assertFalse(client.isConnected());
        client.connect();
        assertTrue(client.isConnected());
        assertEquals("127.0.0.1", client.getHost());
        assertEquals(mockServer.getPort(), client.getPort());
    }

    @Test
    @DisplayName("2. Connection failure when server is unreachable")
    void testConnectFailure() {
        int nonExistentPort = 65534;
        try (LecturerClient failClient = new LecturerClient("127.0.0.1", nonExistentPort)) {
            assertThrows(ClientTransportException.class, failClient::connect);
            assertFalse(failClient.isConnected());
        }
    }

    @Test
    @DisplayName("3. Successful classroom creation updates session")
    void testCreateClassroomSuccess() throws Exception {
        mockServer.setHandler(socket -> {
            Message req = ProtocolFramer.readMessage(socket.getInputStream());
            assertEquals(MessageType.CREATE_CLASSROOM, req.getType());
            assertEquals("Dr. Turing", req.getPayloadString("lecturerName"));

            Message resp = new Message(MessageType.CLASSROOM_CREATED, req.getRequestId());
            resp.putPayload("classroomCode", "CS101");
            resp.putPayload("lecturerId", "lec-999");
            ProtocolFramer.writeMessage(socket.getOutputStream(), resp);
        });

        client.connect();
        Message response = client.createClassroom("Dr. Turing");

        assertNotNull(response);
        assertEquals(MessageType.CLASSROOM_CREATED, response.getType());
        assertEquals("CS101", response.getPayloadString("classroomCode"));

        LecturerSession session = client.getSession();
        assertTrue(session.hasActiveClassroom());
        assertEquals("CS101", session.getClassroomCode());
        assertEquals("Dr. Turing", session.getLecturerName());
        assertEquals("lec-999", session.getLecturerId());
    }

    @Test
    @DisplayName("4. Classroom creation input validation rejects null or blank name")
    void testCreateClassroomValidation() throws Exception {
        client.connect();
        assertThrows(IllegalArgumentException.class, () -> client.createClassroom(null));
        assertThrows(IllegalArgumentException.class, () -> client.createClassroom("   "));
    }

    @Test
    @DisplayName("5. Successful poll creation records poll in session")
    void testCreatePollSuccess() throws Exception {
        mockServer.setHandler(socket -> {
            Message req = ProtocolFramer.readMessage(socket.getInputStream());
            assertEquals(MessageType.CREATE_POLL, req.getType());
            assertEquals("CS101", req.getPayloadString("classroomCode"));
            assertEquals("What is Java NIO?", req.getPayloadString("question"));

            Message resp = new Message(MessageType.POLL_CREATED, req.getRequestId());
            resp.putPayload("pollId", "poll-123");
            resp.putPayload("classroomCode", "CS101");
            resp.putPayload("question", "What is Java NIO?");
            resp.putPayload("options", Arrays.asList("Non-blocking I/O", "New I/O", "Neither"));
            ProtocolFramer.writeMessage(socket.getOutputStream(), resp);
        });

        client.connect();
        List<String> options = Arrays.asList("Non-blocking I/O", "New I/O", "Neither");
        Message response = client.createPoll("CS101", "What is Java NIO?", options);

        assertNotNull(response);
        assertEquals(MessageType.POLL_CREATED, response.getType());
        assertEquals("poll-123", response.getPayloadString("pollId"));

        LecturerSession session = client.getSession();
        List<PollSummary> createdPolls = session.getCreatedPolls();
        assertEquals(1, createdPolls.size());
        assertEquals("poll-123", createdPolls.get(0).getPollId());
        assertEquals("What is Java NIO?", createdPolls.get(0).getQuestion());
        assertEquals(3, createdPolls.get(0).getOptions().size());
    }

    @Test
    @DisplayName("6. Poll creation input validation rejects invalid inputs")
    void testCreatePollValidation() throws Exception {
        client.connect();
        List<String> validOptions = Arrays.asList("A", "B");

        assertThrows(IllegalArgumentException.class, () -> client.createPoll(null, "Q?", validOptions));
        assertThrows(IllegalArgumentException.class, () -> client.createPoll("   ", "Q?", validOptions));
        assertThrows(IllegalArgumentException.class, () -> client.createPoll("CS101", null, validOptions));
        assertThrows(IllegalArgumentException.class, () -> client.createPoll("CS101", "   ", validOptions));
        assertThrows(IllegalArgumentException.class, () -> client.createPoll("CS101", "Q?", null));
        assertThrows(IllegalArgumentException.class, () -> client.createPoll("CS101", "Q?", List.of("Only one")));
    }

    @Test
    @DisplayName("7. Retrieve poll results returns aggregated statistics")
    void testGetPollResultsSuccess() throws Exception {
        mockServer.setHandler(socket -> {
            Message req = ProtocolFramer.readMessage(socket.getInputStream());
            assertEquals(MessageType.POLL_RESULTS, req.getType());
            assertEquals("poll-456", req.getPayloadString("pollId"));

            Message resp = new Message(MessageType.POLL_RESULTS, req.getRequestId());
            resp.putPayload("pollId", "poll-456");
            resp.putPayload("question", "Which option?");
            resp.putPayload("totalResponses", 10);
            resp.putPayload("options", Arrays.asList("Alpha", "Beta"));

            Map<String, Integer> counts = new LinkedHashMap<>();
            counts.put("0", 6);
            counts.put("1", 4);
            resp.putPayload("optionResults", counts);

            Map<String, Double> pcts = new LinkedHashMap<>();
            pcts.put("0", 60.0);
            pcts.put("1", 40.0);
            resp.putPayload("optionPercentages", pcts);

            ProtocolFramer.writeMessage(socket.getOutputStream(), resp);
        });

        client.connect();
        Message response = client.getPollResults("poll-456");

        assertNotNull(response);
        assertEquals(MessageType.POLL_RESULTS, response.getType());
        assertEquals("poll-456", response.getPayloadString("pollId"));
        assertEquals(10, response.getPayloadInt("totalResponses"));
    }

    @Test
    @DisplayName("8. Retrieve poll results when zero responses exist")
    void testGetPollResultsZeroResponses() throws Exception {
        mockServer.setHandler(socket -> {
            Message req = ProtocolFramer.readMessage(socket.getInputStream());
            assertEquals(MessageType.POLL_RESULTS, req.getType());

            Message resp = new Message(MessageType.POLL_RESULTS, req.getRequestId());
            resp.putPayload("pollId", "poll-zero");
            resp.putPayload("question", "No responses yet?");
            resp.putPayload("totalResponses", 0);
            resp.putPayload("options", Arrays.asList("X", "Y"));

            Map<String, Integer> counts = new LinkedHashMap<>();
            counts.put("0", 0);
            counts.put("1", 0);
            resp.putPayload("optionResults", counts);

            Map<String, Double> pcts = new LinkedHashMap<>();
            pcts.put("0", 0.0);
            pcts.put("1", 0.0);
            resp.putPayload("optionPercentages", pcts);

            ProtocolFramer.writeMessage(socket.getOutputStream(), resp);
        });

        client.connect();
        Message response = client.getPollResults("poll-zero");

        assertNotNull(response);
        assertEquals(0, response.getPayloadInt("totalResponses"));
    }

    @Test
    @DisplayName("9. Server returns ERROR response when poll does not exist")
    void testServerErrorResponse() throws Exception {
        mockServer.setHandler(socket -> {
            Message req = ProtocolFramer.readMessage(socket.getInputStream());
            Message err = Message.error(req.getRequestId(), "INVALID_POLL", "Poll 'poll-unknown' does not exist");
            ProtocolFramer.writeMessage(socket.getOutputStream(), err);
        });

        client.connect();
        Message response = client.getPollResults("poll-unknown");

        assertNotNull(response);
        assertEquals(MessageType.ERROR, response.getType());
        assertEquals("INVALID_POLL", response.getPayloadString("code"));
        assertTrue(response.getPayloadString("message").contains("does not exist"));
    }

    @Test
    @DisplayName("10. Disconnect and close safely releases resources")
    void testDisconnectAndClose() throws Exception {
        client.connect();
        assertTrue(client.isConnected());
        client.disconnect();
        assertFalse(client.isConnected());

        // Calling disconnect again should be safe
        assertDoesNotThrow(() -> client.disconnect());
        assertDoesNotThrow(() -> client.close());
    }

    @Test
    @DisplayName("11. LecturerSession management and immutability")
    void testLecturerSession() {
        LecturerSession session = new LecturerSession();
        assertFalse(session.hasActiveClassroom());

        session.setClassroomCode("MATH101");
        session.setLecturerName("Prof. Gauss");
        session.setLecturerId("gauss-1");
        assertTrue(session.hasActiveClassroom());
        assertEquals("MATH101", session.getClassroomCode());

        PollSummary p1 = new PollSummary("p1", "Q1?", Arrays.asList("A", "B"));
        session.addPoll(p1);
        assertEquals(1, session.getCreatedPolls().size());
        assertEquals("p1 - Q1?", p1.toString());

        // Immutability of returned list
        assertThrows(UnsupportedOperationException.class, () ->
                session.getCreatedPolls().add(new PollSummary("p2", "Q2?", List.of("C", "D"))));

        session.clear();
        assertFalse(session.hasActiveClassroom());
        assertEquals(0, session.getCreatedPolls().size());
    }

    /**
     * Simple internal socket server for mocking protocol responses.
     */
    private static class SimpleServer implements AutoCloseable {
        private final ServerSocket serverSocket;
        private final AtomicBoolean running = new AtomicBoolean(true);
        private SocketHandler handler = socket -> {};
        private Thread thread;

        interface SocketHandler {
            void handle(Socket socket) throws Exception;
        }

        public SimpleServer() throws IOException {
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
