package com.classpulse.server.multicast;

import com.classpulse.common.protocol.Message;
import com.classpulse.common.protocol.MessageDecoder;
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
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;

import static org.junit.jupiter.api.Assertions.*;

class MulticastAnnouncementSenderTest {

    private MulticastAnnouncementSender sender;

    @AfterEach
    void tearDown() {
        if (sender != null) {
            sender.close();
        }
    }

    @Test
    @DisplayName("Validates multicast group address, port, TTL, interface, and announcement constraints")
    void testValidationAndLifecycleGuards() {
        assertThrows(IllegalArgumentException.class,
                () -> new MulticastAnnouncementSender("127.0.0.1", 5002));
        assertThrows(IllegalArgumentException.class,
                () -> new MulticastAnnouncementSender("  ", 5002));
        assertThrows(IllegalArgumentException.class,
                () -> new MulticastAnnouncementSender(MulticastConfig.DEFAULT_GROUP_ADDRESS, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new MulticastAnnouncementSender(MulticastConfig.DEFAULT_GROUP_ADDRESS, 65536));
        assertThrows(IllegalArgumentException.class,
                () -> new MulticastAnnouncementSender(MulticastConfig.DEFAULT_GROUP_ADDRESS, 5002, -1, (String) null));
        assertThrows(IllegalArgumentException.class,
                () -> new MulticastAnnouncementSender(MulticastConfig.DEFAULT_GROUP_ADDRESS, 5002, 256, (String) null));

        MulticastAnnouncementSender badIfSender = new MulticastAnnouncementSender(
                MulticastConfig.DEFAULT_GROUP_ADDRESS,
                5002,
                1,
                "nonexistent-if-999");
        assertThrows(IOException.class, badIfSender::start);

        MulticastAnnouncementSender unstarted = new MulticastAnnouncementSender();
        assertThrows(IllegalStateException.class,
                () -> unstarted.sendAnnouncement("CS101", "Prof. Ada", "Hello"));
    }

    @Test
    @DisplayName("Rejects blank and oversized announcement messages after start")
    void testBlankAndOversizedMessages() throws Exception {
        NetworkInterface netIf = findMulticastInterface();
        Assumptions.assumeTrue(netIf != null, "No IPv4 multicast-capable interface available");

        sender = new MulticastAnnouncementSender(
                MulticastConfig.DEFAULT_GROUP_ADDRESS,
                findFreeUdpPort(),
                1,
                netIf);
        sender.start();

        assertThrows(IllegalArgumentException.class,
                () -> sender.sendAnnouncement("CS101", "Prof. Ada", "   "));

        String oversized = "A".repeat(MulticastConfig.MAX_DATAGRAM_BYTES + 100);
        assertThrows(IllegalArgumentException.class,
                () -> sender.sendAnnouncement("CS101", "Prof. Ada", oversized));

        sender.close();
        assertFalse(sender.isRunning());
        assertThrows(IllegalStateException.class,
                () -> sender.sendAnnouncement("CS101", "Prof. Ada", "After close"));
    }

    @Test
    @DisplayName("Broadcasts one announcement delivered to two multicast receivers on the same group")
    void testOneToManyMulticastDelivery() throws Exception {
        NetworkInterface netIf = findMulticastInterface();
        Assumptions.assumeTrue(netIf != null, "No IPv4 multicast-capable interface available");

        int port = findFreeUdpPort();
        InetAddress group = InetAddress.getByName(MulticastConfig.DEFAULT_GROUP_ADDRESS);
        InetSocketAddress groupSocketAddr = new InetSocketAddress(group, port);

        try (MulticastSocket r1 = new MulticastSocket(null);
             MulticastSocket r2 = new MulticastSocket(null)) {
            r1.setReuseAddress(true);
            r2.setReuseAddress(true);
            r1.setSoTimeout(3000);
            r2.setSoTimeout(3000);
            r1.bind(new InetSocketAddress(port));
            r2.bind(new InetSocketAddress(port));
            r1.joinGroup(groupSocketAddr, netIf);
            r2.joinGroup(groupSocketAddr, netIf);

            try {
                sender = new MulticastAnnouncementSender(
                        MulticastConfig.DEFAULT_GROUP_ADDRESS,
                        port,
                        1,
                        netIf);
                sender.start();

                MulticastAnnouncement sent = sender.sendAnnouncement(
                        "CS101",
                        "Prof. Ada",
                        "Poll #2 is now open!");
                assertEquals(1L, sender.getSentCount());

                byte[] buf1 = new byte[MulticastConfig.MAX_DATAGRAM_BYTES];
                byte[] buf2 = new byte[MulticastConfig.MAX_DATAGRAM_BYTES];
                DatagramPacket p1 = new DatagramPacket(buf1, buf1.length);
                DatagramPacket p2 = new DatagramPacket(buf2, buf2.length);

                r1.receive(p1);
                r2.receive(p2);

                Message m1 = MessageDecoder.decode(new String(p1.getData(), p1.getOffset(), p1.getLength(), StandardCharsets.UTF_8));
                Message m2 = MessageDecoder.decode(new String(p2.getData(), p2.getOffset(), p2.getLength(), StandardCharsets.UTF_8));

                assertEquals(MessageType.ANNOUNCEMENT, m1.getType());
                assertEquals(MessageType.ANNOUNCEMENT, m2.getType());
                assertEquals(sent.getRequestId(), m1.getRequestId());
                assertEquals(sent.getRequestId(), m2.getRequestId());
                assertEquals("Poll #2 is now open!", m1.getPayloadString("message"));
                assertEquals("Poll #2 is now open!", m2.getPayloadString("message"));
                assertEquals("CS101", m1.getPayloadString("classroomCode"));
                assertEquals("CS101", m2.getPayloadString("classroomCode"));
            } finally {
                r1.leaveGroup(groupSocketAddr, netIf);
                r2.leaveGroup(groupSocketAddr, netIf);
            }
        }
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
