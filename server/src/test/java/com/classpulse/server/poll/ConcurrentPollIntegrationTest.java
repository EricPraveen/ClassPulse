package com.classpulse.server.poll;

import com.classpulse.common.protocol.Message;
import com.classpulse.common.protocol.MessageType;
import com.classpulse.common.protocol.ProtocolFramer;
import com.classpulse.server.classroom.ClassroomManager;
import com.classpulse.server.classroom.ClassroomMessageHandler;
import com.classpulse.server.concurrency.WorkerPool;
import com.classpulse.server.nio.MessageHandler;
import com.classpulse.server.nio.NioServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class ConcurrentPollIntegrationTest {

    private NioServer server;
    private Thread serverThread;

    @AfterEach
    void tearDown() throws InterruptedException {
        if (server != null) {
            server.stop();
        }
        if (serverThread != null) {
            serverThread.join(3000);
        }
    }

    private int startServer(MessageHandler handler, WorkerPool workerPool) throws InterruptedException {
        server = new NioServer(0, handler, workerPool);
        serverThread = new Thread(() -> {
            try {
                server.start();
            } catch (Exception ignored) {
            }
        }, "test-concurrent-nio-server");
        serverThread.start();

        long deadline = System.currentTimeMillis() + 3000;
        while (!server.isRunning() || server.getPort() <= 0) {
            if (System.currentTimeMillis() > deadline) {
                fail("NioServer did not start in time");
            }
            Thread.sleep(10);
        }
        return server.getPort();
    }

    private MessageHandler createApplicationHandler(ClassroomManager classroomManager,
                                                    PollService pollService,
                                                    AnswerService answerService) {
        ClassroomMessageHandler classroomHandler = new ClassroomMessageHandler(classroomManager);
        PollMessageHandler pollHandler = new PollMessageHandler(pollService, answerService);
        return (client, message) -> {
            Message response = classroomHandler.handleRequest(message);
            if (response == null) {
                response = pollHandler.handleRequest(message);
            }
            if (response != null && !client.isClosed()) {
                client.send(response);
            }
        };
    }

    @Test
    @DisplayName("Simultaneous student poll submissions over TCP produce accurate totals and reject duplicates")
    void testConcurrentStudentSubmissionsOverTcp() throws Exception {
        ClassroomManager classroomManager = new ClassroomManager();
        PollService pollService = new PollService(classroomManager);
        AnswerService answerService = new AnswerService(pollService);
        WorkerPool workerPool = new WorkerPool(6, 256, 2000);

        int port = startServer(createApplicationHandler(classroomManager, pollService, answerService), workerPool);

        // 1. Lecturer creates classroom and poll
        String classroomCode;
        String pollId;
        try (Socket lecturer = new Socket("127.0.0.1", port)) {
            OutputStream out = lecturer.getOutputStream();
            InputStream in = lecturer.getInputStream();

            Message createRoom = new Message(MessageType.CREATE_CLASSROOM, "req-room");
            createRoom.putPayload("lecturerName", "Prof. Ada");
            ProtocolFramer.writeMessage(out, createRoom);

            Message roomResp = ProtocolFramer.readMessage(in);
            assertNotNull(roomResp);
            assertEquals(MessageType.CLASSROOM_CREATED, roomResp.getType());
            classroomCode = roomResp.getPayloadString("classroomCode");

            Message createPoll = new Message(MessageType.CREATE_POLL, "req-poll");
            createPoll.putPayload("classroomCode", classroomCode);
            createPoll.putPayload("question", "Best concurrency primitive?");
            createPoll.putPayload("options", Arrays.asList("ExecutorService", "ConcurrentHashMap", "AtomicInteger"));
            ProtocolFramer.writeMessage(out, createPoll);

            Message pollResp = ProtocolFramer.readMessage(in);
            assertNotNull(pollResp);
            assertEquals(MessageType.POLL_CREATED, pollResp.getType());
            pollId = pollResp.getPayloadString("pollId");
        }

        // 2. 30 unique students connect concurrently, join classroom, and submit answers simultaneously
        int studentCount = 30;
        ExecutorService clientPool = Executors.newFixedThreadPool(15);
        CountDownLatch readyToVote = new CountDownLatch(1);
        CountDownLatch votesDone = new CountDownLatch(studentCount);
        AtomicInteger acceptedCount = new AtomicInteger();
        AtomicReference<Throwable> unexpectedError = new AtomicReference<>();

        for (int i = 0; i < studentCount; i++) {
            final int index = i;
            final int chosenOption = i % 3; // 10 votes for each of options 0, 1, 2
            clientPool.submit(() -> {
                try (Socket studentSocket = new Socket("127.0.0.1", port)) {
                    OutputStream out = studentSocket.getOutputStream();
                    InputStream in = studentSocket.getInputStream();

                    Message join = new Message(MessageType.JOIN_CLASSROOM, "req-join-" + index);
                    join.putPayload("classroomCode", classroomCode);
                    join.putPayload("studentName", "Student-" + index);
                    ProtocolFramer.writeMessage(out, join);

                    Message joinResp = ProtocolFramer.readMessage(in);
                    assertNotNull(joinResp);
                    assertEquals(MessageType.CLASSROOM_JOINED, joinResp.getType());
                    String studentId = joinResp.getPayloadString("studentId");

                    readyToVote.await(3, TimeUnit.SECONDS);

                    Message vote = new Message(MessageType.SUBMIT_ANSWER, "req-vote-" + index);
                    vote.putPayload("classroomCode", classroomCode);
                    vote.putPayload("pollId", pollId);
                    vote.putPayload("studentId", studentId);
                    vote.putPayload("selectedOption", chosenOption);
                    ProtocolFramer.writeMessage(out, vote);

                    Message voteResp = ProtocolFramer.readMessage(in);
                    assertNotNull(voteResp);
                    assertEquals(MessageType.ANSWER_SUBMITTED, voteResp.getType());
                    assertEquals("ACCEPTED", voteResp.getPayloadString("status"));
                    acceptedCount.incrementAndGet();
                } catch (Throwable t) {
                    unexpectedError.compareAndSet(null, t);
                } finally {
                    votesDone.countDown();
                }
            });
        }

        readyToVote.countDown();
        assertTrue(votesDone.await(10, TimeUnit.SECONDS), "All concurrent student votes should finish");
        clientPool.shutdown();
        assertTrue(clientPool.awaitTermination(3, TimeUnit.SECONDS));

        assertNull(unexpectedError.get(), "Unexpected error during concurrent voting");
        assertEquals(studentCount, acceptedCount.get());

        // 3. Query poll results and verify exact counts
        try (Socket lecturer = new Socket("127.0.0.1", port)) {
            Message getResults = new Message(MessageType.POLL_RESULTS, "req-results");
            getResults.putPayload("pollId", pollId);
            ProtocolFramer.writeMessage(lecturer.getOutputStream(), getResults);

            Message resultsResp = ProtocolFramer.readMessage(lecturer.getInputStream());
            assertNotNull(resultsResp);
            assertEquals(MessageType.POLL_RESULTS, resultsResp.getType());
            assertEquals(studentCount, resultsResp.getPayloadInt("totalResponses"));

            @SuppressWarnings("unchecked")
            Map<String, Object> optionResults = (Map<String, Object>) resultsResp.getPayloadField("optionResults");
            assertNotNull(optionResults);
            assertEquals(10, ((Number) optionResults.get("0")).intValue());
            assertEquals(10, ((Number) optionResults.get("1")).intValue());
            assertEquals(10, ((Number) optionResults.get("2")).intValue());
        }

        // 4. Concurrent duplicate submissions from the same student over TCP accept only one
        int duplicateAttempts = 5;
        ExecutorService dupPool = Executors.newFixedThreadPool(duplicateAttempts);
        CountDownLatch dupReady = new CountDownLatch(1);
        CountDownLatch dupDone = new CountDownLatch(duplicateAttempts);
        AtomicInteger dupAccepted = new AtomicInteger();
        AtomicInteger dupRejected = new AtomicInteger();

        for (int i = 0; i < duplicateAttempts; i++) {
            final int attempt = i;
            dupPool.submit(() -> {
                try (Socket s = new Socket("127.0.0.1", port)) {
                    dupReady.await(3, TimeUnit.SECONDS);
                    Message vote = new Message(MessageType.SUBMIT_ANSWER, "req-dup-" + attempt);
                    vote.putPayload("classroomCode", classroomCode);
                    vote.putPayload("pollId", pollId);
                    vote.putPayload("studentId", "student-dup-check");
                    vote.putPayload("selectedOption", attempt % 3);
                    ProtocolFramer.writeMessage(s.getOutputStream(), vote);

                    Message resp = ProtocolFramer.readMessage(s.getInputStream());
                    assertNotNull(resp);
                    if (resp.getType() == MessageType.ANSWER_SUBMITTED) {
                        dupAccepted.incrementAndGet();
                    } else if (resp.getType() == MessageType.ERROR
                            && "DUPLICATE_ANSWER".equals(resp.getPayloadString("code"))) {
                        dupRejected.incrementAndGet();
                    }
                } catch (Throwable t) {
                    unexpectedError.compareAndSet(null, t);
                } finally {
                    dupDone.countDown();
                }
            });
        }

        dupReady.countDown();
        assertTrue(dupDone.await(5, TimeUnit.SECONDS));
        dupPool.shutdown();
        assertTrue(dupPool.awaitTermination(2, TimeUnit.SECONDS));

        assertNull(unexpectedError.get());
        assertEquals(1, dupAccepted.get());
        assertEquals(duplicateAttempts - 1, dupRejected.get());

        // 5. Verify clean worker pool shutdown when server stops
        server.stop();
        serverThread.join(3000);
        assertTrue(workerPool.isShutdown());
        assertTrue(workerPool.isTerminated());
    }

    @Test
    @DisplayName("Slow worker task does not block NIO selector from serving other clients")
    void testSelectorNotBlockedBySlowWorkerTask() throws Exception {
        CountDownLatch slowStarted = new CountDownLatch(1);
        CountDownLatch releaseSlow = new CountDownLatch(1);

        MessageHandler handler = (client, message) -> {
            if ("slow".equals(message.getRequestId())) {
                slowStarted.countDown();
                try {
                    releaseSlow.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                client.send(Message.success(message.getRequestId(), "slow-done"));
            } else {
                client.send(Message.success(message.getRequestId(), "fast-done"));
            }
        };

        int port = startServer(handler, new WorkerPool(4, 32, 2000));

        try (Socket slowClient = new Socket("127.0.0.1", port);
             Socket fastClient = new Socket("127.0.0.1", port)) {
            fastClient.setSoTimeout(2000);

            // Send slow request that blocks one worker thread
            ProtocolFramer.writeMessage(slowClient.getOutputStream(), new Message(MessageType.HEARTBEAT, "slow"));
            assertTrue(slowStarted.await(2, TimeUnit.SECONDS));

            // Fast client must still receive its response immediately while slow worker is blocked
            ProtocolFramer.writeMessage(fastClient.getOutputStream(), new Message(MessageType.HEARTBEAT, "fast"));
            Message fastResp = ProtocolFramer.readMessage(fastClient.getInputStream());
            assertNotNull(fastResp);
            assertEquals(MessageType.SUCCESS, fastResp.getType());
            assertEquals("fast", fastResp.getRequestId());
            assertEquals("fast-done", fastResp.getPayloadString("message"));

            // Now release slow worker and confirm its response arrives too
            releaseSlow.countDown();
            Message slowResp = ProtocolFramer.readMessage(slowClient.getInputStream());
            assertNotNull(slowResp);
            assertEquals(MessageType.SUCCESS, slowResp.getType());
            assertEquals("slow", slowResp.getRequestId());
            assertEquals("slow-done", slowResp.getPayloadString("message"));
        }
    }

    @Test
    @DisplayName("Exception in one worker task returns error without crashing server or other requests")
    void testWorkerExceptionDoesNotCrashServer() throws Exception {
        MessageHandler handler = (client, message) -> {
            if ("boom".equals(message.getRequestId())) {
                throw new RuntimeException("Simulated worker failure");
            }
            client.send(Message.success(message.getRequestId(), "healthy"));
        };

        int port = startServer(handler, new WorkerPool(2, 16, 2000));

        try (Socket client = new Socket("127.0.0.1", port)) {
            client.setSoTimeout(2000);

            ProtocolFramer.writeMessage(client.getOutputStream(), new Message(MessageType.HEARTBEAT, "boom"));
            Message errResp = ProtocolFramer.readMessage(client.getInputStream());
            assertNotNull(errResp);
            assertEquals(MessageType.ERROR, errResp.getType());
            assertEquals("boom", errResp.getRequestId());
            assertEquals("INTERNAL_ERROR", errResp.getPayloadString("code"));

            ProtocolFramer.writeMessage(client.getOutputStream(), new Message(MessageType.HEARTBEAT, "ok"));
            Message okResp = ProtocolFramer.readMessage(client.getInputStream());
            assertNotNull(okResp);
            assertEquals(MessageType.SUCCESS, okResp.getType());
            assertEquals("ok", okResp.getRequestId());
            assertEquals("healthy", okResp.getPayloadString("message"));
        }
    }
}
