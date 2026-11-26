package com.classpulse.client.network;

import java.io.IOException;

/**
 * Standalone console demonstration entry point for sending UDP heartbeats
 * to the ClassPulse UDP heartbeat server.
 */
public class UdpHeartbeatClientMain {

    public static void main(String[] args) {
        String clientId = (args.length > 0 && !args[0].trim().isEmpty())
                ? args[0].trim()
                : "student-demo-1";
        String host = (args.length > 1 && !args[1].trim().isEmpty())
                ? args[1].trim()
                : UdpHeartbeatClient.DEFAULT_HOST;
        int port = UdpHeartbeatClient.DEFAULT_PORT;
        long intervalSeconds = UdpHeartbeatClient.DEFAULT_INTERVAL_MILLIS / 1000L;

        if (args.length > 2) {
            try {
                port = Integer.parseInt(args[2].trim());
            } catch (NumberFormatException e) {
                System.err.println("Invalid UDP port '" + args[2] + "', using default " + port);
            }
        }
        if (args.length > 3) {
            try {
                long parsed = Long.parseLong(args[3].trim());
                if (parsed > 0) {
                    intervalSeconds = parsed;
                }
            } catch (NumberFormatException e) {
                System.err.println("Invalid interval seconds '" + args[3] + "', using default " + intervalSeconds);
            }
        }

        UdpHeartbeatClient client = new UdpHeartbeatClient(
                host,
                port,
                clientId,
                "STUDENT",
                null,
                intervalSeconds * 1000L);

        Runtime.getRuntime().addShutdownHook(new Thread(client::stop, "udp-heartbeat-client-shutdown"));

        try {
            client.start();
        } catch (IOException e) {
            System.err.println("Failed to start UDP heartbeat client: " + e.getMessage());
            System.exit(1);
        }

        System.out.printf("Sending UDP heartbeats for '%s' to %s:%d every %ds.%n",
                clientId, host, port, intervalSeconds);
        System.out.println("Press Enter to stop...");

        try {
            while (System.in.read() != -1) {
                if (System.in.available() == 0) {
                    break;
                }
            }
        } catch (IOException ignored) {
        } finally {
            client.stop();
            System.out.printf("UDP heartbeat client stopped (total sent: %d).%n", client.getSentCount());
        }
    }
}
