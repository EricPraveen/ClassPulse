package com.classpulse.server.udp;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Standalone console demonstration entry point for the UDP heartbeat receiver
 * and client online/offline status tracker.
 */
public class UdpHeartbeatServerMain {

    private static final long STATUS_PRINT_INTERVAL_SECONDS = 5L;

    public static void main(String[] args) {
        int port = UdpHeartbeatServer.DEFAULT_PORT;
        long timeoutSeconds = UdpHeartbeatServer.DEFAULT_TIMEOUT_MILLIS / 1000L;

        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0].trim());
            } catch (NumberFormatException e) {
                System.err.println("Invalid UDP port '" + args[0] + "', using default " + port);
            }
        }
        if (args.length > 1) {
            try {
                long parsed = Long.parseLong(args[1].trim());
                if (parsed > 0) {
                    timeoutSeconds = parsed;
                }
            } catch (NumberFormatException e) {
                System.err.println("Invalid timeout seconds '" + args[1] + "', using default " + timeoutSeconds);
            }
        }

        Duration timeout = Duration.ofSeconds(timeoutSeconds);
        UdpHeartbeatServer server = new UdpHeartbeatServer(port, timeout);
        server.setHeartbeatListener(record ->
                System.out.printf("[UDP HEARTBEAT] clientId=%s role=%s room=%s from=%s count=%d%n",
                        record.getClientId(),
                        record.getRole() != null ? record.getRole() : "-",
                        record.getClassroomCode() != null ? record.getClassroomCode() : "-",
                        record.getRemoteAddress(),
                        record.getHeartbeatCount()));

        ScheduledExecutorService statusPrinter = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "udp-status-printer");
            t.setDaemon(true);
            return t;
        });

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            statusPrinter.shutdownNow();
            server.stop();
        }, "udp-heartbeat-shutdown"));

        try {
            server.start();
        } catch (IOException e) {
            System.err.println("Failed to start UDP heartbeat server on port " + port + ": " + e.getMessage());
            System.exit(1);
        }

        System.out.printf("ClassPulse UDP Heartbeat Server listening on UDP port %d (offline timeout: %ds)%n",
                server.getPort(), timeoutSeconds);
        System.out.println("Press Enter to stop...");

        statusPrinter.scheduleAtFixedRate(
                () -> printStatuses(server.getTracker()),
                STATUS_PRINT_INTERVAL_SECONDS,
                STATUS_PRINT_INTERVAL_SECONDS,
                TimeUnit.SECONDS);

        try {
            while (System.in.read() != -1) {
                if (System.in.available() == 0) {
                    break;
                }
            }
        } catch (IOException ignored) {
        } finally {
            statusPrinter.shutdownNow();
            server.stop();
            System.out.println("ClassPulse UDP Heartbeat Server stopped.");
        }
    }

    private static void printStatuses(ClientStatusTracker tracker) {
        Map<String, ClientPresenceStatus> statuses = tracker.getAllStatuses();
        if (statuses.isEmpty()) {
            System.out.println("[STATUS] No client heartbeats received yet.");
            return;
        }
        System.out.println("--- Client Presence Status ---");
        for (Map.Entry<String, ClientPresenceStatus> entry : statuses.entrySet()) {
            ClientHeartbeatRecord record = tracker.getRecord(entry.getKey()).orElse(null);
            System.out.printf("  %-20s %-8s lastSeen=%s count=%d%n",
                    entry.getKey(),
                    entry.getValue(),
                    record != null ? record.getLastSeen() : "-",
                    record != null ? record.getHeartbeatCount() : 0L);
        }
    }
}
