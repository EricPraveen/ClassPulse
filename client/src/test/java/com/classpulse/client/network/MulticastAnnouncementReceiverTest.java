package com.classpulse.client.network;

import com.classpulse.common.protocol.Message;
import com.classpulse.common.protocol.MessageEncoder;
import com.classpulse.common.protocol.MessageType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.net.StandardSocketOptions;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class MulticastAnnouncementReceiverTest {

    private MulticastAnnouncementReceiver receiver1;
    private MulticastAnnouncementReceiver receiver2;

    @AfterEach
    void tearDown() {
        if (receiver1 != null) {
            receiver1.close();
        }
        if (receiver2 != null) {
            receiver2.close();
        }
    }

    @Test
    @DisplayName("Validates multicast address, port, listener, and interface existence")
    void testValidation() {
        assertThrows(IllegalArgumentException.class,
                () -> new MulticastAnnouncementReceiver("127.0.0.1", 5002, a -> {}));
        assertThrows(IllegalArgumentException.class,
                () -> new MulticastAnnouncementReceiver(MulticastAnnouncementReceiver.DEFAULT_GROUP_ADDRESS, 0, a -> {}));
        assertThrows(IllegalArgumentException.class,
                () -> new MulticastAnnouncementReceiver(MulticastAnnouncementReceiver.DEFAULT_GROUP_ADDRESS, 65536, a -> {}));
        assertThrows(NullPointerException.class,
                () -> new MulticastAnnouncementReceiver(MulticastAnnouncementReceiver.DEFAULT_GROUP_ADDRESS, 5002, null));

        MulticastAnnouncementReceiver badIf = new MulticastAnnouncementReceiver(
                MulticastAnnouncementReceiver.DEFAULT_GROUP_ADDRESS,
                5002,
                "nonexistent-if-999",
                a -> {});
        assertThrows(IOException.class, badIf::start);
    }

    @Test
    @DisplayName("Two receivers join same group and both receive valid announcement while ignoring malformed packets")
    void testTwoReceiversAndMalformedHandling() throws Exception {
        NetworkInterface netIf = findMulticastInterface();
        Assumptions.assumeTrue(netIf != null, "No IPv4 multicast-capable interface available");

        int port = findFreeUdpPort();
        String group = MulticastAnnouncementReceiver.DEFAULT_GROUP_ADDRESS;

        List<ReceivedAnnouncement> r1Items = new CopyOnWriteArrayList<>();
        List<ReceivedAnnouncement> r2Items = new CopyOnWriteArrayList<>();
        CountDownLatch bothReceived = new CountDownLatch(2);

        receiver1 = new MulticastAnnouncementReceiver(group, port, netIf, ann -> {
            r1Items.add(ann);
            bothReceived.countDown();
        });
        receiver2 = new MulticastAnnouncementReceiver(group, port, netIf, ann -> {
            r2Items.add(ann);
            bothReceived.countDown();
        });

        receiver1.start();
        receiver2.start();
        assertTrue(receiver1.isRunning());
        assertTrue(receiver2.isRunning());

        InetAddress groupAddr = InetAddress.getByName(group);
        try (MulticastSocket senderSocket = new MulticastSocket()) {
            try {
                senderSocket.setOption(StandardSocketOptions.IP_MULTICAST_LOOP, true);
            } catch (IOException | UnsupportedOperationException ignored) {
            }
            senderSocket.setOption(StandardSocketOptions.IP_MULTICAST_IF, netIf);

            // 1. Malformed JSON
            byte[] badJson = "{not-json".getBytes(StandardCharsets.UTF_8);
            senderSocket.send(new DatagramPacket(badJson, badJson.length, groupAddr, port));

            // 2. Wrong message type
            byte[] wrongType = MessageEncoder.encode(
                    new Message(MessageType.HEARTBEAT, "hb-1").putPayload("clientId", "s-1"))
                    .getBytes(StandardCharsets.UTF_8);
            senderSocket.send(new DatagramPacket(wrongType, wrongType.length, groupAddr, port));

            // 3. ANNOUNCEMENT missing message text
            byte[] emptyText = MessageEncoder.encode(
                    new Message(MessageType.ANNOUNCEMENT, "ann-empty").putPayload("classroomCode", "CS101"))
                    .getBytes(StandardCharsets.UTF_8);
            senderSocket.send(new DatagramPacket(emptyText, emptyText.length, groupAddr, port));

            long deadline = System.currentTimeMillis() + 2000;
            while ((receiver1.getMalformedPacketCount() < 3 || receiver2.getMalformedPacketCount() < 3)
                    && System.currentTimeMillis() < deadline) {
                Thread.sleep(15);
            }
            assertTrue(receiver1.getMalformedPacketCount() >= 3);
            assertTrue(receiver2.getMalformedPacketCount() >= 3);

            // 4. Valid announcement
            Message valid = new Message(MessageType.ANNOUNCEMENT, "ann-1")
                    .putPayload("classroomCode", "CS101")
                    .putPayload("sender", "Prof. Grace")
                    .putPayload("message", "Quiz starts in 2 minutes!")
                    .putPayload("timestamp", 1764200000000L);
            byte[] validBytes = MessageEncoder.encode(valid).getBytes(StandardCharsets.UTF_8);
            senderSocket.send(new DatagramPacket(validBytes, validBytes.length, groupAddr, port));
        }

        assertTrue(bothReceived.await(3, TimeUnit.SECONDS));
        assertEquals(1, r1Items.size());
        assertEquals(1, r2Items.size());
        assertEquals("CS101", r1Items.get(0).getClassroomCode());
        assertEquals("Prof. Grace", r1Items.get(0).getSender());
        assertEquals("Quiz starts in 2 minutes!", r1Items.get(0).getMessage());
        assertEquals("Quiz starts in 2 minutes!", r2Items.get(0).getMessage());

        receiver1.stop();
        receiver2.close();
        assertFalse(receiver1.isRunning());
        assertFalse(receiver2.isRunning());
    }

    private static int findFreeUdpPort() throws IOException {
        try (DatagramSocket socket = new DatagramSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static NetworkInterface findMulticastInterface() throws IOException {
        Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
        NetworkInterface loopbackFallback = null;
        while (interfaces != null && interfaces.hasMoreElements()) {
            NetworkInterface ni = interfaces.nextElement();
            if (!ni.isUp() || !ni.supportsMulticast()) {
                continue;
            }
            boolean hasIpv4 = false;
            Enumeration<InetAddress> addrs = ni.getInetAddresses();
            while (addrs.hasMoreElements()) {
                if (addrs.nextElement() instanceof Inet4Address) {
                    hasIpv4 = true;
                    break;
                }
            }
            if (!hasIpv4) {
                continue;
            }
            if (!ni.isLoopback()) {
                return ni;
            }
            if (loopbackFallback == null) {
                loopbackFallback = ni;
            }
        }
        return loopbackFallback;
    }
}
