package com.classpulse.server.classroom;

import com.classpulse.common.protocol.Message;
import com.classpulse.common.protocol.MessageType;
import com.classpulse.common.protocol.ProtocolFramer;
import com.classpulse.server.nio.NioServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class ClassroomIntegrationTest {

    private ClassroomManager classroomManager;
    private ClassroomMessageHandler messageHandler;
    private NioServer server;
    private Thread serverThread;
    private int serverPort;

    @BeforeEach
    void setUp() throws Exception {
        classroomManager = new ClassroomManager();
        messageHandler = new ClassroomMessageHandler(classroomManager);
        server = new NioServer(0, messageHandler);

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

        // Wait briefly until bound port is allocated
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
    @DisplayName("End-to-end: Lecturer creates classroom and student joins over TCP socket")
    void testEndToEndClassroomCreationAndJoining() throws Exception {
        String classroomCode;

        // 1. Lecturer connects and sends CREATE_CLASSROOM
        try (Socket lecturerSocket = new Socket("127.0.0.1", serverPort)) {
            OutputStream out = lecturerSocket.getOutputStream();
            InputStream in = lecturerSocket.getInputStream();

            Message createReq = new Message(MessageType.CREATE_CLASSROOM, "req-create-100");
            createReq.putPayload("lecturerName", "Prof. Dijkstra");
            ProtocolFramer.writeMessage(out, createReq);

            Message createResp = ProtocolFramer.readMessage(in);
            assertNotNull(createResp);
            assertEquals(MessageType.CLASSROOM_CREATED, createResp.getType());
            assertEquals("req-create-100", createResp.getRequestId());
            classroomCode = createResp.getPayloadString("classroomCode");
            assertNotNull(classroomCode);
        }

        // 2. Student connects and sends JOIN_CLASSROOM
        try (Socket studentSocket = new Socket("127.0.0.1", serverPort)) {
            OutputStream out = studentSocket.getOutputStream();
            InputStream in = studentSocket.getInputStream();

            Message joinReq = new Message(MessageType.JOIN_CLASSROOM, "req-join-200");
            joinReq.putPayload("classroomCode", classroomCode);
            joinReq.putPayload("studentName", "Knuth");
            ProtocolFramer.writeMessage(out, joinReq);

            Message joinResp = ProtocolFramer.readMessage(in);
            assertNotNull(joinResp);
            assertEquals(MessageType.CLASSROOM_JOINED, joinResp.getType());
            assertEquals("req-join-200", joinResp.getRequestId());
            assertEquals(classroomCode, joinResp.getPayloadString("classroomCode"));
            assertEquals("Knuth", joinResp.getPayloadString("studentName"));
            assertNotNull(joinResp.getPayloadString("studentId"));
        }

        // Verify in-memory state
        assertEquals(1, classroomManager.getStudents(classroomCode).size());
        assertEquals("Knuth", classroomManager.getStudents(classroomCode).get(0).getDisplayName());
    }
}
