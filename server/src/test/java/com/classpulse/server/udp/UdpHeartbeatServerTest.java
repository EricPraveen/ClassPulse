package com.classpulse.server.udp;

import com.classpulse.common.protocol.Message;
import com.classpulse.common.protocol.MessageEncoder;
import com.classpulse.common.protocol.MessageType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class UdpHeartbeatServerTest {

    private UdpHeartbeatServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.close();
        }
    }

    @Test
    @DisplayName("Receives valid HEARTBEAT datagram and marks client ONLINE then OFFLINE after timeout")
    void testValidHeartbeatAndTimeout() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        server = new UdpHeartbeatServer(0, Duration.ofMillis(150));
        server.setHeartbeatListener(record -> latch.countDown());
        server.start();

        assertTrue(server.isRunning());
        int port = server.getPort();
        assertTrue(port > 0);

        Message heartbeat = new Message(MessageType.HEARTBEAT, "hb-1")
                .putPayload("clientId", "student-42")
                .putPayload("role", "STUDENT")
                .putPayload("classroomCode", "CS101")
                .putPayload("timestamp", System.currentTimeMillis());
        byte[] payload = MessageEncoder.encode(heartbeat).getBytes(StandardCharsets.UTF_8);

        try (DatagramSocket sender = new DatagramSocket()) {
            DatagramPacket packet = new DatagramPacket(
                    payload,
                    payload.length,
                    InetAddress.getByName("127.0.0.1"),
                    port);
            sender.send(packet);
        }

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertEquals(ClientPresenceStatus.ONLINE, server.getTracker().getStatus("student-42"));
        ClientHeartbeatRecord record = server.getTracker().getRecord("student-42").orElseThrow();
        assertEquals("STUDENT", record.getRole());
        assertEquals("CS101", record.getClassroomCode());
        assertEquals(1L, record.getHeartbeatCount());

        Thread.sleep(220);
        assertEquals(ClientPresenceStatus.OFFLINE, server.getTracker().getStatus("student-42"));
    }

    @Test
    @DisplayName("Ignores malformed, wrong-type, empty, and missing-clientId datagrams without crashing")
    void testMalformedDatagramsHandledSafely() throws Exception {
        CountDownLatch validLatch = new CountDownLatch(1);
        server = new UdpHeartbeatServer(0, Duration.ofSeconds(10));
        server.setHeartbeatListener(record -> validLatch.countDown());
        server.start();

        int port = server.getPort();
        InetAddress localhost = InetAddress.getByName("127.0.0.1");

        try (DatagramSocket sender = new DatagramSocket()) {
            // 1. Empty datagram
            sender.send(new DatagramPacket(new byte[0], 0, localhost, port));

            // 2. Malformed JSON
            byte[] badJson = "{not-valid-json".getBytes(StandardCharsets.UTF_8);
            sender.send(new DatagramPacket(badJson, badJson.length, localhost, port));

            // 3. Wrong message type
            Message wrongType = new Message(MessageType.CREATE_CLASSROOM, "req-1")
                    .putPayload("lecturerName", "Prof. Ada");
            byte[] wrongTypeBytes = MessageEncoder.encode(wrongType).getBytes(StandardCharsets.UTF_8);
            sender.send(new DatagramPacket(wrongTypeBytes, wrongTypeBytes.length, localhost, port));

            // 4. HEARTBEAT missing clientId
            Message missingClient = new Message(MessageType.HEARTBEAT, "hb-missing");
            byte[] missingClientBytes = MessageEncoder.encode(missingClient).getBytes(StandardCharsets.UTF_8);
            sender.send(new DatagramPacket(missingClientBytes, missingClientBytes.length, localhost, port));

            // Wait briefly for invalid packets to be processed before sending valid one
            long deadline = System.currentTimeMillis() + 2000;
            while (server.getMalformedPacketCount() < 4 && System.currentTimeMillis() < deadline) {
                Thread.sleep(15);
            }
            assertTrue(server.getMalformedPacketCount() >= 4);
            assertTrue(server.isRunning());

            // 5. Valid heartbeat still works
            Message valid = new Message(MessageType.HEARTBEAT, "hb-ok")
                    .putPayload("clientId", "student-ok");
            byte[] validBytes = MessageEncoder.encode(valid).getBytes(StandardCharsets.UTF_8);
            sender.send(new DatagramPacket(validBytes, validBytes.length, localhost, port));
        }

        assertTrue(validLatch.await(2, TimeUnit.SECONDS));
        assertEquals(ClientPresenceStatus.ONLINE, server.getTracker().getStatus("student-ok"));

        // Verify stop/close idempotency
        server.stop();
        server.close();
        assertFalse(server.isRunning());
    }
}
