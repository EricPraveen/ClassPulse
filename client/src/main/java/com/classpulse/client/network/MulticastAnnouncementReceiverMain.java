package com.classpulse.client.network;

import java.io.IOException;
import java.time.Instant;

/**
 * Standalone console demonstration entry point for receiving UDP multicast
 * classroom announcements.
 */
public class MulticastAnnouncementReceiverMain {

    public static void main(String[] args) {
        String groupAddress = (args.length > 0 && !args[0].trim().isEmpty())
                ? args[0].trim()
                : MulticastAnnouncementReceiver.DEFAULT_GROUP_ADDRESS;
        int port = MulticastAnnouncementReceiver.DEFAULT_PORT;
        String interfaceName = (args.length > 2 && !args[2].trim().isEmpty())
                ? args[2].trim()
                : null;

        if (args.length > 1) {
            try {
                port = Integer.parseInt(args[1].trim());
            } catch (NumberFormatException e) {
                System.err.println("Invalid multicast port '" + args[1] + "', using default " + port);
            }
        }

        MulticastAnnouncementReceiver receiver = new MulticastAnnouncementReceiver(
                groupAddress,
                port,
                interfaceName,
                announcement -> {
                    String room = announcement.getClassroomCode() != null
                            ? "[" + announcement.getClassroomCode() + "] "
                            : "";
                    String sender = announcement.getSender() != null
                            ? announcement.getSender()
                            : "Server";
                    System.out.printf("%n[ANNOUNCEMENT %s] %s%s: %s%n",
                            Instant.ofEpochMilli(announcement.getTimestamp()),
                            room,
                            sender,
                            announcement.getMessage());
                });

        Runtime.getRuntime().addShutdownHook(new Thread(receiver::stop, "multicast-receiver-shutdown"));

        try {
            receiver.start();
        } catch (IOException | IllegalArgumentException e) {
            System.err.println("Failed to start UDP multicast receiver: " + e.getMessage());
            System.err.println("Ensure an IPv4 multicast-capable network interface is active.");
            System.exit(1);
        }

        System.out.printf("Listening for ClassPulse multicast announcements on %s:%d (interface=%s)%n",
                receiver.getGroupAddress(),
                receiver.getPort(),
                receiver.getNetworkInterface() != null ? receiver.getNetworkInterface().getName() : "default");
        System.out.println("Press Enter to stop...");

        try {
            while (System.in.read() != -1) {
                if (System.in.available() == 0) {
                    break;
                }
            }
        } catch (IOException ignored) {
        } finally {
            receiver.stop();
            System.out.printf("Multicast receiver stopped (received=%d).%n", receiver.getReceivedCount());
        }
    }
}
