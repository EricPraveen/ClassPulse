package com.classpulse.client.network;

import com.classpulse.common.protocol.Message;
import com.classpulse.common.protocol.MessageEncoder;
import com.classpulse.common.protocol.MessageType;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Lightweight UDP heartbeat sender for ClassPulse clients.
 * <p>
 * Periodically sends {@link MessageType#HEARTBEAT} JSON datagrams to the server's
 * UDP heartbeat port independently of the TCP classroom/polling connection.
 */
public class UdpHeartbeatClient implements AutoCloseable {

    public static final String DEFAULT_HOST = "127.0.0.1";
    public static final int DEFAULT_PORT = 5001;
    public static final long DEFAULT_INTERVAL_MILLIS = 5_000L;

    private final String host;
    private final int port;
    private final String clientId;
    private final String role;
    private final String classroomCode;
    private final long intervalMillis;

    private final AtomicLong sequence = new AtomicLong(0);
    private final AtomicLong sentCount = new AtomicLong(0);
    private final Object socketLock = new Object();

    private DatagramSocket socket;
    private ScheduledExecutorService scheduler;
    private volatile boolean running;

    public UdpHeartbeatClient(String host, int port, String clientId) {
        this(host, port, clientId, null, null, DEFAULT_INTERVAL_MILLIS);
    }

    public UdpHeartbeatClient(
            String host,
            int port,
            String clientId,
            String role,
            String classroomCode,
            long intervalMillis) {
        if (host == null || host.trim().isEmpty()) {
            throw new IllegalArgumentException("Host must not be null or blank");
        }
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("Port must be between 1 and 65535: " + port);
        }
        if (clientId == null || clientId.trim().isEmpty()) {
            throw new IllegalArgumentException("clientId must not be null or blank");
        }
        if (intervalMillis <= 0) {
            throw new IllegalArgumentException("intervalMillis must be positive: " + intervalMillis);
        }
        this.host = host.trim();
        this.port = port;
        this.clientId = clientId.trim();
        this.role = trimToNull(role);
        this.classroomCode = trimToNull(classroomCode);
        this.intervalMillis = intervalMillis;
    }

    /**
     * Builds a protocol {@link MessageType#HEARTBEAT} envelope for this client.
     */
    public Message buildHeartbeatMessage() {
        Message message = new Message(MessageType.HEARTBEAT, "hb-" + sequence.incrementAndGet());
        message.putPayload("clientId", clientId);
        message.putPayload("timestamp", System.currentTimeMillis());
        if (role != null) {
            message.putPayload("role", role);
        }
        if (classroomCode != null) {
            message.putPayload("classroomCode", classroomCode);
        }
        return message;
    }

    /**
     * Sends a single UDP heartbeat datagram to the configured server host and port.
     *
     * @throws IOException if resolving the host or sending the datagram fails
     */
    public void sendHeartbeat() throws IOException {
        Message message = buildHeartbeatMessage();
        byte[] bytes = MessageEncoder.encode(message).getBytes(StandardCharsets.UTF_8);
        InetAddress address = InetAddress.getByName(host);
        DatagramPacket packet = new DatagramPacket(bytes, bytes.length, address, port);

        synchronized (socketLock) {
            if (socket == null || socket.isClosed()) {
                socket = new DatagramSocket();
            }
            socket.send(packet);
        }
        sentCount.incrementAndGet();
    }

    /**
     * Opens the UDP socket and begins sending periodic heartbeats immediately.
     *
     * @throws IOException if opening the UDP socket fails
     */
    public synchronized void start() throws IOException {
        if (running) {
            return;
        }
        synchronized (socketLock) {
            if (socket == null || socket.isClosed()) {
                socket = new DatagramSocket();
            }
        }
        running = true;
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "udp-heartbeat-sender");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleAtFixedRate(() -> {
            try {
                sendHeartbeat();
            } catch (IOException e) {
                if (running) {
                    System.err.println("Failed to send UDP heartbeat: " + e.getMessage());
                }
            }
        }, 0L, intervalMillis, TimeUnit.MILLISECONDS);
    }

    /**
     * Stops periodic heartbeat sending and closes the UDP socket.
     */
    public void stop() {
        ScheduledExecutorService schedulerToStop;
        synchronized (this) {
            running = false;
            schedulerToStop = scheduler;
            scheduler = null;
        }
        if (schedulerToStop != null) {
            schedulerToStop.shutdownNow();
        }
        synchronized (socketLock) {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
            socket = null;
        }
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

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public String getClientId() {
        return clientId;
    }

    public long getIntervalMillis() {
        return intervalMillis;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
