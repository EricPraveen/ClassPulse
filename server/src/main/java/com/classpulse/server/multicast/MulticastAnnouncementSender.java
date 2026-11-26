package com.classpulse.server.multicast;

import com.classpulse.common.protocol.MessageEncoder;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.net.StandardSocketOptions;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * UDP multicast sender that publishes {@link MulticastAnnouncement} datagrams
 * to a configured administratively scoped multicast group.
 */
public class MulticastAnnouncementSender implements AutoCloseable {

    private final InetAddress groupAddress;
    private final int port;
    private final int ttl;
    private final String requestedInterfaceName;
    private final NetworkInterface explicitInterface;

    private final AtomicLong sequence = new AtomicLong(0);
    private final AtomicLong sentCount = new AtomicLong(0);

    private MulticastSocket socket;
    private NetworkInterface activeInterface;
    private volatile boolean running;

    public MulticastAnnouncementSender() {
        this(MulticastConfig.DEFAULT_GROUP_ADDRESS, MulticastConfig.DEFAULT_PORT);
    }

    public MulticastAnnouncementSender(String groupAddress, int port) {
        this(groupAddress, port, MulticastConfig.DEFAULT_TTL, (String) null);
    }

    public MulticastAnnouncementSender(String groupAddress, int port, int ttl, String interfaceName) {
        this.groupAddress = MulticastConfig.resolveGroupAddress(groupAddress);
        this.port = MulticastConfig.validatePort(port);
        this.ttl = MulticastConfig.validateTtl(ttl);
        this.requestedInterfaceName = (interfaceName != null && !interfaceName.trim().isEmpty())
                ? interfaceName.trim()
                : null;
        this.explicitInterface = null;
    }

    public MulticastAnnouncementSender(String groupAddress, int port, int ttl, NetworkInterface networkInterface) {
        this.groupAddress = MulticastConfig.resolveGroupAddress(groupAddress);
        this.port = MulticastConfig.validatePort(port);
        this.ttl = MulticastConfig.validateTtl(ttl);
        this.requestedInterfaceName = null;
        this.explicitInterface = Objects.requireNonNull(networkInterface, "networkInterface must not be null");
    }

    /**
     * Opens the {@link MulticastSocket}, configures TTL and outgoing multicast interface,
     * and prepares the sender to publish announcements.
     *
     * @throws IOException if no usable multicast interface exists or socket setup fails
     */
    public synchronized void start() throws IOException {
        if (running) {
            return;
        }
        NetworkInterface resolved = (explicitInterface != null)
                ? explicitInterface
                : MulticastConfig.resolveNetworkInterface(requestedInterfaceName);

        MulticastSocket newSocket = new MulticastSocket();
        try {
            newSocket.setTimeToLive(ttl);
            try {
                newSocket.setOption(StandardSocketOptions.IP_MULTICAST_LOOP, true);
            } catch (IOException | UnsupportedOperationException ignored) {
                // Best-effort loopback enablement for single-host testing
            }
            newSocket.setOption(StandardSocketOptions.IP_MULTICAST_IF, resolved);
            this.socket = newSocket;
            this.activeInterface = resolved;
            this.running = true;
        } catch (IOException e) {
            newSocket.close();
            throw e;
        }
    }

    /**
     * Sends a classroom announcement datagram to the multicast group.
     *
     * @param classroomCode optional classroom code (e.g. {@code CS101})
     * @param sender        optional sender display name (e.g. {@code Prof. Ada})
     * @param text          non-blank announcement text
     * @return the sent {@link MulticastAnnouncement}
     * @throws IOException           if sending fails
     * @throws IllegalStateException if the sender has not been started
     */
    public synchronized MulticastAnnouncement sendAnnouncement(String classroomCode, String sender, String text)
            throws IOException {
        if (!running || socket == null || socket.isClosed()) {
            throw new IllegalStateException("MulticastAnnouncementSender is not running");
        }

        String requestId = "ann-" + sequence.incrementAndGet();
        MulticastAnnouncement announcement = new MulticastAnnouncement(
                requestId,
                classroomCode,
                sender,
                text,
                System.currentTimeMillis(),
                null);

        byte[] bytes = MessageEncoder.encode(announcement.toProtocolMessage())
                .getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MulticastConfig.MAX_DATAGRAM_BYTES) {
            throw new IllegalArgumentException(
                    "Announcement datagram exceeds max size of "
                            + MulticastConfig.MAX_DATAGRAM_BYTES + " bytes: " + bytes.length);
        }

        DatagramPacket packet = new DatagramPacket(bytes, bytes.length, groupAddress, port);
        socket.send(packet);
        sentCount.incrementAndGet();
        return announcement;
    }

    /**
     * Stops the sender and closes its underlying multicast socket.
     */
    public synchronized void stop() {
        running = false;
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
        socket = null;
    }

    @Override
    public void close() {
        stop();
    }

    public boolean isRunning() {
        return running;
    }

    public long getSentCount() {
        return sentCount.get();
    }

    public String getGroupAddress() {
        return groupAddress.getHostAddress();
    }

    public int getPort() {
        return port;
    }

    public int getTtl() {
        return ttl;
    }

    public NetworkInterface getNetworkInterface() {
        return activeInterface;
    }
}
