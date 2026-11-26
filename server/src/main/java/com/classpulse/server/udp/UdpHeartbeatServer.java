package com.classpulse.server.udp;

import com.classpulse.common.protocol.Message;
import com.classpulse.common.protocol.MessageDecoder;
import com.classpulse.common.protocol.MessageType;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Standalone UDP receiver that listens for {@link MessageType#HEARTBEAT} datagrams
 * and records client presence in a {@link ClientStatusTracker}.
 */
public class UdpHeartbeatServer implements AutoCloseable {

    public static final int DEFAULT_PORT = 5001;
    public static final long DEFAULT_TIMEOUT_MILLIS = 15_000L;
    public static final int MAX_DATAGRAM_BYTES = 2048;

    private final int requestedPort;
    private final ClientStatusTracker tracker;
    private final AtomicLong malformedPacketCount = new AtomicLong(0);

    private volatile boolean running;
    private volatile int boundPort = -1;
    private volatile Consumer<ClientHeartbeatRecord> heartbeatListener;

    private DatagramSocket socket;
    private Thread receiverThread;

    public UdpHeartbeatServer() {
        this(DEFAULT_PORT, Duration.ofMillis(DEFAULT_TIMEOUT_MILLIS));
    }

    public UdpHeartbeatServer(int port) {
        this(port, Duration.ofMillis(DEFAULT_TIMEOUT_MILLIS));
    }

    public UdpHeartbeatServer(int port, Duration timeout) {
        this(port, new ClientStatusTracker(timeout));
    }

    public UdpHeartbeatServer(int port, ClientStatusTracker tracker) {
        if (port < 0 || port > 65535) {
            throw new IllegalArgumentException("Port must be between 0 and 65535: " + port);
        }
        this.requestedPort = port;
        this.tracker = Objects.requireNonNull(tracker, "tracker must not be null");
    }

    /**
     * Registers an optional listener notified whenever a valid heartbeat is recorded.
     */
    public void setHeartbeatListener(Consumer<ClientHeartbeatRecord> listener) {
        this.heartbeatListener = listener;
    }

    /**
     * Binds the UDP socket and starts the background receiver thread.
     *
     * @throws IOException if binding the UDP socket fails
     */
    public synchronized void start() throws IOException {
        if (running) {
            return;
        }
        socket = new DatagramSocket(requestedPort);
        boundPort = socket.getLocalPort();
        running = true;

        receiverThread = new Thread(this::receiveLoop, "udp-heartbeat-receiver");
        receiverThread.setDaemon(true);
        receiverThread.start();
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
                System.err.println("UDP heartbeat socket error: " + e.getMessage());
            } catch (IOException e) {
                if (!running) {
                    break;
                }
                System.err.println("UDP heartbeat I/O error: " + e.getMessage());
            }
        }
    }

    private void processPacket(DatagramPacket packet) {
        int length = packet.getLength();
        if (length == 0 || length > MAX_DATAGRAM_BYTES) {
            malformedPacketCount.incrementAndGet();
            System.err.println("Ignoring UDP heartbeat datagram with invalid length: " + length);
            return;
        }

        String json = new String(packet.getData(), packet.getOffset(), length, StandardCharsets.UTF_8);
        final Message message;
        try {
            message = MessageDecoder.decode(json);
        } catch (IllegalArgumentException e) {
            malformedPacketCount.incrementAndGet();
            System.err.println("Ignoring malformed UDP heartbeat datagram: " + e.getMessage());
            return;
        }

        if (message.getType() != MessageType.HEARTBEAT) {
            malformedPacketCount.incrementAndGet();
            System.err.println("Ignoring non-HEARTBEAT UDP message type: " + message.getType());
            return;
        }

        String clientId = message.getPayloadString("clientId");
        if (clientId == null || clientId.trim().isEmpty()) {
            malformedPacketCount.incrementAndGet();
            System.err.println("Ignoring HEARTBEAT datagram missing clientId");
            return;
        }

        String role = message.getPayloadString("role");
        String classroomCode = message.getPayloadString("classroomCode");
        InetSocketAddress remoteAddress = (packet.getSocketAddress() instanceof InetSocketAddress addr)
                ? addr
                : new InetSocketAddress(packet.getAddress(), packet.getPort());

        ClientHeartbeatRecord record = tracker.recordHeartbeat(clientId, role, classroomCode, remoteAddress);
        Consumer<ClientHeartbeatRecord> listener = this.heartbeatListener;
        if (listener != null) {
            try {
                listener.accept(record);
            } catch (RuntimeException e) {
                System.err.println("Heartbeat listener failed: " + e.getMessage());
            }
        }
    }

    /**
     * Stops the UDP heartbeat receiver and releases socket resources.
     */
    public void stop() {
        DatagramSocket socketToClose;
        Thread threadToJoin;
        synchronized (this) {
            if (!running && socket == null) {
                return;
            }
            running = false;
            socketToClose = socket;
            socket = null;
            threadToJoin = receiverThread;
            receiverThread = null;
        }

        if (socketToClose != null && !socketToClose.isClosed()) {
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

    public int getPort() {
        return boundPort;
    }

    public ClientStatusTracker getTracker() {
        return tracker;
    }

    public long getMalformedPacketCount() {
        return malformedPacketCount.get();
    }
}
