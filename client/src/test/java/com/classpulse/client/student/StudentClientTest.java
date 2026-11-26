package com.classpulse.client.student;

import com.classpulse.client.network.ClientTransportException;
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
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class StudentClientTest {

    private SimpleServer mockServer;
    private StudentClient client;

    @BeforeEach
    void setUp() throws IOException {
        mockServer = new SimpleServer();
        mockServer.start();
        client = new StudentClient("127.0.0.1", mockServer.getPort());
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
        int nonExistentPort = 65533;
        try (StudentClient failClient = new StudentClient("127.0.0.1", nonExistentPort)) {
            assertThrows(ClientTransportException.class, failClient::connect);
            assertFalse(failClient.isConnected());
        }
    }

    @Test
    @DisplayName("3. Successful classroom joining updates session")
    void testJoinClassroomSuccess() throws Exception {
        mockServer.setHandler(socket -> {
            Message req = ProtocolFramer.readMessage(socket.getInputStream());
            assertEquals(MessageType.JOIN_CLASSROOM, req.getType());
            assertEquals("CS101", req.getPayloadString("classroomCode"));
            assertEquals("Grace Hopper", req.getPayloadString("studentName"));

            Message resp = new Message(MessageType.CLASSROOM_JOINED, req.getRequestId());
            resp.putPayload("classroomCode", "CS101");
            resp.putPayload("studentId", "stu-hopper-1");
            resp.putPayload("studentName", "Grace Hopper");
            ProtocolFramer.writeMessage(socket.getOutputStream(), resp);
        });

        client.connect();
        Message response = client.joinClassroom("CS101", "Grace Hopper");

        assertNotNull(response);
        assertEquals(MessageType.CLASSROOM_JOINED, response.getType());
        assertEquals("CS101", response.getPayloadString("classroomCode"));
        assertEquals("stu-hopper-1", response.getPayloadString("studentId"));

        StudentSession session = client.getSession();
        assertTrue(session.isEnrolled());
        assertEquals("CS101", session.getClassroomCode());
        assertEquals("stu-hopper-1", session.getStudentId());
        assertEquals("Grace Hopper", session.getStudentName());
    }

    @Test
    @DisplayName("4. Classroom join input validation rejects null or blank parameters")
    void testJoinClassroomValidation() throws Exception {
        client.connect();
        assertThrows(IllegalArgumentException.class, () -> client.joinClassroom(null, "Grace"));
        assertThrows(IllegalArgumentException.class, () -> client.joinClassroom("   ", "Grace"));
        assertThrows(IllegalArgumentException.class, () -> client.joinClassroom("CS101", null));
        assertThrows(IllegalArgumentException.class, () -> client.joinClassroom("CS101", "   "));
    }

    @Test
    @DisplayName("5. Active poll retrieval updates session with poll details")
    void testGetActivePollSuccess() throws Exception {
        mockServer.setHandler(socket -> {
            Message req = ProtocolFramer.readMessage(socket.getInputStream());
            assertEquals(MessageType.GET_POLL, req.getType());
            assertEquals("CS101", req.getPayloadString("classroomCode"));

            Message resp = new Message(MessageType.POLL_CREATED, req.getRequestId());
            resp.putPayload("pollId", "poll-999");
            resp.putPayload("classroomCode", "CS101");
            resp.putPayload("question", "What is Java NIO?");
            resp.putPayload("options", Arrays.asList("Non-blocking I/O", "New I/O", "Neither"));
            ProtocolFramer.writeMessage(socket.getOutputStream(), resp);
        });

        client.connect();
        Message response = client.getActivePoll("CS101", null);

        assertNotNull(response);
        assertEquals(MessageType.POLL_CREATED, response.getType());
        assertEquals("poll-999", response.getPayloadString("pollId"));

        StudentSession session = client.getSession();
        assertTrue(session.hasActivePoll());
        assertEquals("poll-999", session.getActivePollId());
        assertEquals("What is Java NIO?", session.getActivePollQuestion());
        assertEquals(3, session.getActivePollOptions().size());
        assertFalse(session.hasAnswered());
    }

    @Test
    @DisplayName("6. Active poll validation requires at least classroomCode or pollId")
    void testGetActivePollValidation() throws Exception {
        client.connect();
        assertThrows(IllegalArgumentException.class, () -> client.getActivePoll(null, null));
        assertThrows(IllegalArgumentException.class, () -> client.getActivePoll("   ", "   "));
    }

    @Test
    @DisplayName("7. Successful answer submission updates session with chosen option")
    void testSubmitAnswerSuccess() throws Exception {
        mockServer.setHandler(socket -> {
            Message req = ProtocolFramer.readMessage(socket.getInputStream());
            assertEquals(MessageType.SUBMIT_ANSWER, req.getType());
            assertEquals("CS101", req.getPayloadString("classroomCode"));
            assertEquals("poll-999", req.getPayloadString("pollId"));
            assertEquals("stu-1", req.getPayloadString("studentId"));
            assertEquals(1, req.getPayloadInt("selectedOption"));

            Message resp = new Message(MessageType.ANSWER_SUBMITTED, req.getRequestId());
            resp.putPayload("pollId", "poll-999");
            resp.putPayload("status", "ACCEPTED");
            ProtocolFramer.writeMessage(socket.getOutputStream(), resp);
        });

        client.connect();
        Message response = client.submitAnswer("CS101", "poll-999", "stu-1", 1);

        assertNotNull(response);
        assertEquals(MessageType.ANSWER_SUBMITTED, response.getType());
        assertEquals("ACCEPTED", response.getPayloadString("status"));

        StudentSession session = client.getSession();
        assertTrue(session.hasAnswered());
        assertEquals(1, session.getSubmittedAnswerIndex());
    }

    @Test
    @DisplayName("8. Answer submission input validation rejects invalid fields")
    void testSubmitAnswerValidation() throws Exception {
        client.connect();
        assertThrows(IllegalArgumentException.class, () -> client.submitAnswer(null, "p1", "s1", 0));
        assertThrows(IllegalArgumentException.class, () -> client.submitAnswer("CS101", null, "s1", 0));
        assertThrows(IllegalArgumentException.class, () -> client.submitAnswer("CS101", "p1", null, 0));
    }

    @Test
    @DisplayName("9. Server ERROR responses are preserved and accessible")
    void testServerErrorResponses() throws Exception {
        mockServer.setHandler(socket -> {
            Message req = ProtocolFramer.readMessage(socket.getInputStream());
            Message err = Message.error(req.getRequestId(), "INVALID_CLASSROOM", "Classroom 'CS999' does not exist");
            ProtocolFramer.writeMessage(socket.getOutputStream(), err);
        });

        client.connect();
        Message response = client.joinClassroom("CS999", "Bob");

        assertNotNull(response);
        assertEquals(MessageType.ERROR, response.getType());
        assertEquals("INVALID_CLASSROOM", response.getPayloadString("code"));
    }

    @Test
    @DisplayName("10. Disconnect and close safely release resources")
    void testDisconnectAndClose() throws Exception {
        client.connect();
        assertTrue(client.isConnected());
        client.disconnect();
        assertFalse(client.isConnected());

        assertDoesNotThrow(() -> client.disconnect());
        assertDoesNotThrow(() -> client.close());
    }

    @Test
    @DisplayName("11. StudentSession state management and clear()")
    void testStudentSession() {
        StudentSession session = new StudentSession();
        assertFalse(session.isEnrolled());
        assertFalse(session.hasActivePoll());
        assertFalse(session.hasAnswered());

        session.setStudentName("Ada Lovelace");
        session.setStudentId("stu-ada");
        session.setClassroomCode("MATH200");
        assertTrue(session.isEnrolled());

        session.setActivePoll("p-42", "Question?", Arrays.asList("Opt1", "Opt2"));
        assertTrue(session.hasActivePoll());
        assertEquals("p-42", session.getActivePollId());
        assertEquals(2, session.getActivePollOptions().size());
        assertFalse(session.hasAnswered());

        session.setSubmittedAnswerIndex(0);
        assertTrue(session.hasAnswered());
        assertEquals(0, session.getSubmittedAnswerIndex());

        // Setting a new poll resets submitted answer
        session.setActivePoll("p-43", "New Question?", List.of("A", "B"));
        assertFalse(session.hasAnswered());

        session.clear();
        assertFalse(session.isEnrolled());
        assertFalse(session.hasActivePoll());
        assertFalse(session.hasAnswered());
    }

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
