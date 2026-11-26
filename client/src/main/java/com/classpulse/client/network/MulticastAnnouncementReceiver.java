package com.classpulse.client.network;

import com.classpulse.common.protocol.Message;
import com.classpulse.common.protocol.MessageDecoder;
import com.classpulse.common.protocol.MessageType;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * UDP multicast receiver that joins the ClassPulse announcement group and
 * delivers incoming {@link MessageType#ANNOUNCEMENT} messages to a listener.
 */
public class MulticastAnnouncementReceiver implements AutoCloseable {

    public static final String DEFAULT_GROUP_ADDRESS = "239.255.42.99";
    public static final int DEFAULT_PORT = 5002;
    public static final int MAX_DATAGRAM_BYTES = 2048;

    private final InetAddress groupAddress;
    private final int port;
    private final String requestedInterfaceName;
    private final NetworkInterface explicitInterface;
    private final Consumer<ReceivedAnnouncement> listener;

    private final AtomicLong receivedCount = new AtomicLong(0);
    private final AtomicLong malformedPacketCount = new AtomicLong(0);

    private MulticastSocket socket;
    private InetSocketAddress groupSocketAddress;
    private NetworkInterface activeInterface;
    private Thread receiverThread;
    private volatile boolean running;

    public MulticastAnnouncementReceiver(Consumer<ReceivedAnnouncement> listener) {
        this(DEFAULT_GROUP_ADDRESS, DEFAULT_PORT, (String) null, listener);
    }

    public MulticastAnnouncementReceiver(
            String groupAddress,
            int port,
            Consumer<ReceivedAnnouncement> listener) {
        this(groupAddress, port, (String) null, listener);
    }

    public MulticastAnnouncementReceiver(
            String groupAddress,
            int port,
            String interfaceName,
            Consumer<ReceivedAnnouncement> listener) {
        this.groupAddress = resolveGroupAddress(groupAddress);
        this.port = validatePort(port);
        this.requestedInterfaceName = trimToNull(interfaceName);
        this.explicitInterface = null;
        this.listener = Objects.requireNonNull(listener, "listener must not be null");
    }

    public MulticastAnnouncementReceiver(
            String groupAddress,
            int port,
            NetworkInterface networkInterface,
            Consumer<ReceivedAnnouncement> listener) {
        this.groupAddress = resolveGroupAddress(groupAddress);
        this.port = validatePort(port);
        this.requestedInterfaceName = null;
        this.explicitInterface = Objects.requireNonNull(networkInterface, "networkInterface must not be null");
        this.listener = Objects.requireNonNull(listener, "listener must not be null");
    }

    /**
     * Binds a reusable {@link MulticastSocket}, joins the configured multicast group,
     * and starts a background receiver thread.
     *
     * @throws IOException if no multicast interface is available or joining fails
     */
    public synchronized void start() throws IOException {
        if (running) {
            return;
        }

        NetworkInterface resolved = (explicitInterface != null)
                ? verifyExplicitInterface(explicitInterface)
                : resolveNetworkInterface(requestedInterfaceName);

        MulticastSocket newSocket = new MulticastSocket(null);
        InetSocketAddress groupAddr = new InetSocketAddress(groupAddress, port);
        try {
            newSocket.setReuseAddress(true);
            newSocket.bind(new InetSocketAddress(port));
            newSocket.joinGroup(groupAddr, resolved);

            this.socket = newSocket;
            this.groupSocketAddress = groupAddr;
            this.activeInterface = resolved;
            this.running = true;

            this.receiverThread = new Thread(this::receiveLoop, "udp-multicast-receiver");
            this.receiverThread.setDaemon(true);
            this.receiverThread.start();
        } catch (IOException e) {
            newSocket.close();
            throw e;
        }
    }

    private void receiveLoop() {
        byte[] buffer = new byte[MAX_DATAGRAM_BYTES + 1];
        while (running) {
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            try {
                socket.receive(packet);
                processPacket(packet);
            } catch (SocketException e) {
                if (!running) {
                    break;
                }
                System.err.println("UDP multicast socket error: " + e.getMessage());
            } catch (IOException e) {
                if (!running) {
                    break;
                }
                System.err.println("UDP multicast I/O error: " + e.getMessage());
            }
        }
    }

    private void processPacket(DatagramPacket packet) {
        int length = packet.getLength();
        if (length == 0 || length > MAX_DATAGRAM_BYTES) {
            malformedPacketCount.incrementAndGet();
            System.err.println("Ignoring multicast datagram with invalid length: " + length);
            return;
        }

        String json = new String(packet.getData(), packet.getOffset(), length, StandardCharsets.UTF_8);
        final Message message;
        try {
            message = MessageDecoder.decode(json);
        } catch (IllegalArgumentException e) {
            malformedPacketCount.incrementAndGet();
            System.err.println("Ignoring malformed multicast datagram: " + e.getMessage());
            return;
        }

        if (message.getType() != MessageType.ANNOUNCEMENT) {
            malformedPacketCount.incrementAndGet();
            System.err.println("Ignoring non-ANNOUNCEMENT multicast message type: " + message.getType());
            return;
        }

        String text = trimToNull(message.getPayloadString("message"));
        if (text == null) {
            malformedPacketCount.incrementAndGet();
            System.err.println("Ignoring ANNOUNCEMENT multicast datagram missing message text");
            return;
        }

        Object tsField = message.getPayloadField("timestamp");
        long timestamp = (tsField instanceof Number number) ? number.longValue() : System.currentTimeMillis();
        InetSocketAddress sourceAddress = (packet.getSocketAddress() instanceof InetSocketAddress addr)
                ? addr
                : new InetSocketAddress(packet.getAddress(), packet.getPort());

        ReceivedAnnouncement announcement = new ReceivedAnnouncement(
                message.getRequestId(),
                trimToNull(message.getPayloadString("classroomCode")),
                trimToNull(message.getPayloadString("sender")),
                text,
                timestamp,
                sourceAddress);

        receivedCount.incrementAndGet();
        try {
            listener.accept(announcement);
        } catch (RuntimeException e) {
            System.err.println("Multicast announcement listener failed: " + e.getMessage());
        }
    }

    /**
     * Leaves the multicast group, closes the socket, and stops the receiver thread.
     */
    public void stop() {
        MulticastSocket socketToClose;
        InetSocketAddress groupToLeave;
        NetworkInterface iface;
        Thread threadToJoin;

        synchronized (this) {
            if (!running && socket == null) {
                return;
            }
            running = false;
            socketToClose = socket;
            socket = null;
            groupToLeave = groupSocketAddress;
            groupSocketAddress = null;
            iface = activeInterface;
            threadToJoin = receiverThread;
            receiverThread = null;
        }

        if (socketToClose != null && !socketToClose.isClosed()) {
            if (groupToLeave != null && iface != null) {
                try {
                    socketToClose.leaveGroup(groupToLeave, iface);
                } catch (IOException ignored) {
                }
            }
            socketToClose.close();
        }
        if (threadToJoin != null && threadToJoin != Thread.currentThread()) {
            try {
                threadToJoin.join(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @Override
    public void close() {
        stop();
    }

    public boolean isRunning() {
        return running;
    }

    public long getReceivedCount() {
        return receivedCount.get();
    }

    public long getMalformedPacketCount() {
        return malformedPacketCount.get();
    }

    public String getGroupAddress() {
        return groupAddress.getHostAddress();
    }

    public int getPort() {
        return port;
    }

    public NetworkInterface getNetworkInterface() {
        return activeInterface;
    }

    private static InetAddress resolveGroupAddress(String address) {
        if (address == null || address.trim().isEmpty()) {
            throw new IllegalArgumentException("Multicast group address must not be null or blank");
        }
        try {
            InetAddress resolved = InetAddress.getByName(address.trim());
            if (!resolved.isMulticastAddress()) {
                throw new IllegalArgumentException("Address is not a multicast address: " + address);
            }
            return resolved;
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("Unable to resolve multicast group address: " + address, e);
        }
    }

    private static int validatePort(int port) {
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("Port must be between 1 and 65535: " + port);
        }
        return port;
    }

    private static NetworkInterface verifyExplicitInterface(NetworkInterface ni) throws IOException {
        if (!ni.isUp()) {
            throw new IOException("Multicast network interface is down: " + ni.getName());
        }
        if (!ni.supportsMulticast()) {
            throw new IOException("Network interface does not support multicast: " + ni.getName());
        }
        return ni;
    }

    private static NetworkInterface resolveNetworkInterface(String interfaceName) throws IOException {
        if (interfaceName != null) {
            NetworkInterface explicit = NetworkInterface.getByName(interfaceName);
            if (explicit == null) {
                throw new IOException("Multicast network interface not found: " + interfaceName);
            }
            return verifyExplicitInterface(explicit);
        }

        Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
        NetworkInterface loopbackFallback = null;

        while (interfaces != null && interfaces.hasMoreElements()) {
            NetworkInterface ni = interfaces.nextElement();
            if (!ni.isUp() || !ni.supportsMulticast() || !hasIpv4Address(ni)) {
                continue;
            }
            if (!ni.isLoopback()) {
                return ni;
            }
            if (loopbackFallback == null) {
                loopbackFallback = ni;
            }
        }

        if (loopbackFallback != null) {
            return loopbackFallback;
        }
        throw new IOException("No active IPv4 multicast-capable network interface is available");
    }

    private static boolean hasIpv4Address(NetworkInterface ni) {
        Enumeration<InetAddress> addresses = ni.getInetAddresses();
        while (addresses.hasMoreElements()) {
            if (addresses.nextElement() instanceof Inet4Address) {
                return true;
            }
        }
        return false;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
