package com.classpulse.server.poll;

import com.classpulse.common.protocol.Message;
import com.classpulse.common.protocol.MessageType;
import com.classpulse.common.protocol.ProtocolFramer;
import com.classpulse.server.classroom.ClassroomManager;
import com.classpulse.server.classroom.ClassroomMessageHandler;
import com.classpulse.server.nio.MessageHandler;
import com.classpulse.server.nio.NioServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class PollIntegrationTest {

    private ClassroomManager classroomManager;
    private ClassroomMessageHandler classroomHandler;
    private PollService pollService;
    private AnswerService answerService;
    private PollMessageHandler pollHandler;
    private NioServer server;
    private Thread serverThread;
    private int serverPort;

    @BeforeEach
    void setUp() throws Exception {
        classroomManager = new ClassroomManager();
        classroomHandler = new ClassroomMessageHandler(classroomManager);
        pollService = new PollService(classroomManager);
        answerService = new AnswerService(pollService);
        pollHandler = new PollMessageHandler(pollService, answerService);

        MessageHandler compositeHandler = (client, message) -> {
            Message response = classroomHandler.handleRequest(message);
            if (response == null) {
                response = pollHandler.handleRequest(message);
            }
            if (response != null && !client.isClosed()) {
                client.send(response);
            }
        };

        server = new NioServer(0, compositeHandler);

        CountDownLatch startLatch = new CountDownLatch(1);
        serverThread = new Thread(() -> {
            try {
                server.start();
            } catch (Exception e) {
                // Stopped
            }
        });
        serverThread.setDaemon(true);
        serverThread.start();

        for (int i = 0; i < 50; i++) {
            if (server.getPort() > 0) {
                serverPort = server.getPort();
                startLatch.countDown();
                break;
            }
            Thread.sleep(20);
        }
        assertTrue(startLatch.await(2, TimeUnit.SECONDS), "NioServer did not bind in time");
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop();
        }
        if (serverThread != null) {
            serverThread.interrupt();
        }
    }

    @Test
    @DisplayName("End-to-End: Classroom created -> Poll created -> Answers submitted -> Results fetched over TCP")
    void testEndToEndPollWorkflowOverSocket() throws Exception {
        String classroomCode;
        String pollId;

        // 1. Create Classroom
        try (Socket socket = new Socket("127.0.0.1", serverPort)) {
            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();

            Message createClass = new Message(MessageType.CREATE_CLASSROOM, "req-c1");
            createClass.putPayload("lecturerName", "Prof. Barbara Liskov");
            ProtocolFramer.writeMessage(out, createClass);

            Message resp = ProtocolFramer.readMessage(in);
            assertNotNull(resp);
            assertEquals(MessageType.CLASSROOM_CREATED, resp.getType());
            classroomCode = resp.getPayloadString("classroomCode");
            assertNotNull(classroomCode);
        }

        // 2. Create Poll
        try (Socket socket = new Socket("127.0.0.1", serverPort)) {
            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();

            Message createPoll = new Message(MessageType.CREATE_POLL, "req-p1");
            createPoll.putPayload("classroomCode", classroomCode);
            createPoll.putPayload("question", "Which principle does 'L' in SOLID stand for?");
            createPoll.putPayload("options", Arrays.asList("Liskov Substitution", "Lazy Evaluation", "Linear Speedup"));
            ProtocolFramer.writeMessage(out, createPoll);

            Message resp = ProtocolFramer.readMessage(in);
            assertNotNull(resp);
            assertEquals(MessageType.POLL_CREATED, resp.getType());
            pollId = resp.getPayloadString("pollId");
            assertNotNull(pollId);
        }

        // 3. Student 1 votes for option 0 (Liskov Substitution)
        try (Socket socket = new Socket("127.0.0.1", serverPort)) {
            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();

            Message vote = new Message(MessageType.SUBMIT_ANSWER, "req-v1");
            vote.putPayload("pollId", pollId);
            vote.putPayload("studentId", "student-alice");
            vote.putPayload("selectedOption", 0);
            ProtocolFramer.writeMessage(out, vote);

            Message resp = ProtocolFramer.readMessage(in);
            assertNotNull(resp);
            assertEquals(MessageType.ANSWER_SUBMITTED, resp.getType());
            assertEquals("ACCEPTED", resp.getPayloadString("status"));
        }

        // 4. Student 2 votes for option 0 (Liskov Substitution)
        try (Socket socket = new Socket("127.0.0.1", serverPort)) {
            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();

            Message vote = new Message(MessageType.SUBMIT_ANSWER, "req-v2");
            vote.putPayload("pollId", pollId);
            vote.putPayload("studentId", "student-bob");
            vote.putPayload("selectedOption", 0);
            ProtocolFramer.writeMessage(out, vote);

            Message resp = ProtocolFramer.readMessage(in);
            assertNotNull(resp);
            assertEquals(MessageType.ANSWER_SUBMITTED, resp.getType());
        }

        // 5. Student 1 attempts to vote again (duplicate rejection)
        try (Socket socket = new Socket("127.0.0.1", serverPort)) {
            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();

            Message vote = new Message(MessageType.SUBMIT_ANSWER, "req-v3");
            vote.putPayload("pollId", pollId);
            vote.putPayload("studentId", "student-alice");
            vote.putPayload("selectedOption", 1);
            ProtocolFramer.writeMessage(out, vote);

            Message resp = ProtocolFramer.readMessage(in);
            assertNotNull(resp);
            assertEquals(MessageType.ERROR, resp.getType());
            assertEquals("DUPLICATE_ANSWER", resp.getPayloadString("code"));
        }

        // 6. Fetch Poll Results
        try (Socket socket = new Socket("127.0.0.1", serverPort)) {
            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();

            Message getResults = new Message(MessageType.POLL_RESULTS, "req-r1");
            getResults.putPayload("pollId", pollId);
            ProtocolFramer.writeMessage(out, getResults);

            Message resp = ProtocolFramer.readMessage(in);
            assertNotNull(resp);
            assertEquals(MessageType.POLL_RESULTS, resp.getType());
            assertEquals(pollId, resp.getPayloadString("pollId"));
            assertEquals(2, resp.getPayloadInt("totalResponses"));
        }
    }
}
