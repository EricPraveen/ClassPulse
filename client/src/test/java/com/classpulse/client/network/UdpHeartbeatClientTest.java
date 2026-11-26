package com.classpulse.client.network;

import com.classpulse.common.protocol.Message;
import com.classpulse.common.protocol.MessageDecoder;
import com.classpulse.common.protocol.MessageType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class UdpHeartbeatClientTest {

    private UdpHeartbeatClient client;
    private DatagramSocket receiver;

    @AfterEach
    void tearDown() {
        if (client != null) {
            client.close();
        }
        if (receiver != null && !receiver.isClosed()) {
            receiver.close();
        }
    }

    @Test
    @DisplayName("Validates constructor arguments")
    void testConstructorValidation() {
        assertThrows(IllegalArgumentException.class,
                () -> new UdpHeartbeatClient("  ", 5001, "student-1"));
        assertThrows(IllegalArgumentException.class,
                () -> new UdpHeartbeatClient("127.0.0.1", 0, "student-1"));
        assertThrows(IllegalArgumentException.class,
                () -> new UdpHeartbeatClient("127.0.0.1", 65536, "student-1"));
        assertThrows(IllegalArgumentException.class,
                () -> new UdpHeartbeatClient("127.0.0.1", 5001, "  "));
        assertThrows(IllegalArgumentException.class,
                () -> new UdpHeartbeatClient("127.0.0.1", 5001, "student-1", "STUDENT", null, 0L));
    }

    @Test
    @DisplayName("Sends single valid HEARTBEAT datagram decodable with MessageDecoder")
    void testSendSingleHeartbeat() throws Exception {
        receiver = new DatagramSocket(0);
        receiver.setSoTimeout(2000);
        int port = receiver.getLocalPort();

        client = new UdpHeartbeatClient("127.0.0.1", port, "student-7", "STUDENT", "NET101", 5000L);
        client.sendHeartbeat();
        assertEquals(1L, client.getSentCount());

        byte[] buf = new byte[2048];
        DatagramPacket packet = new DatagramPacket(buf, buf.length);
        receiver.receive(packet);

        String json = new String(packet.getData(), packet.getOffset(), packet.getLength(), StandardCharsets.UTF_8);
        Message decoded = MessageDecoder.decode(json);

        assertEquals(MessageType.HEARTBEAT, decoded.getType());
        assertEquals("hb-1", decoded.getRequestId());
        assertEquals("student-7", decoded.getPayloadString("clientId"));
        assertEquals("STUDENT", decoded.getPayloadString("role"));
        assertEquals("NET101", decoded.getPayloadString("classroomCode"));
        assertNotNull(decoded.getPayloadField("timestamp"));
    }

    @Test
    @DisplayName("Periodic start sends multiple heartbeats and stops cleanly")
    void testPeriodicHeartbeatSending() throws Exception {
        receiver = new DatagramSocket(0);
        receiver.setSoTimeout(2000);
        int port = receiver.getLocalPort();

        client = new UdpHeartbeatClient("127.0.0.1", port, "student-periodic", "STUDENT", null, 40L);
        client.start();
        assertTrue(client.isRunning());

        List<Message> received = new ArrayList<>();
        byte[] buf = new byte[2048];
        for (int i = 0; i < 3; i++) {
            DatagramPacket packet = new DatagramPacket(buf, buf.length);
            receiver.receive(packet);
            String json = new String(packet.getData(), packet.getOffset(), packet.getLength(), StandardCharsets.UTF_8);
            received.add(MessageDecoder.decode(json));
        }

        assertEquals(3, received.size());
        for (Message msg : received) {
            assertEquals(MessageType.HEARTBEAT, msg.getType());
            assertEquals("student-periodic", msg.getPayloadString("clientId"));
        }

        client.close();
        assertFalse(client.isRunning());
        assertTrue(client.getSentCount() >= 3L);
    }
}
