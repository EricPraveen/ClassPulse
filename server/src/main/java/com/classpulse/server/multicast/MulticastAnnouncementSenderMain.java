package com.classpulse.server.multicast;

import java.io.IOException;
import java.util.Scanner;

/**
 * Standalone console demonstration entry point for broadcasting
 * classroom announcements over UDP multicast.
 */
public class MulticastAnnouncementSenderMain {

    public static void main(String[] args) {
        String groupAddress = (args.length > 0 && !args[0].trim().isEmpty())
                ? args[0].trim()
                : MulticastConfig.DEFAULT_GROUP_ADDRESS;
        int port = MulticastConfig.DEFAULT_PORT;
        int ttl = MulticastConfig.DEFAULT_TTL;
        String interfaceName = (args.length > 3 && !args[3].trim().isEmpty())
                ? args[3].trim()
                : null;

        if (args.length > 1) {
            try {
                port = Integer.parseInt(args[1].trim());
            } catch (NumberFormatException e) {
                System.err.println("Invalid multicast port '" + args[1] + "', using default " + port);
            }
        }
        if (args.length > 2) {
            try {
                ttl = Integer.parseInt(args[2].trim());
            } catch (NumberFormatException e) {
                System.err.println("Invalid TTL '" + args[2] + "', using default " + ttl);
            }
        }

        MulticastAnnouncementSender sender = new MulticastAnnouncementSender(groupAddress, port, ttl, interfaceName);
        Runtime.getRuntime().addShutdownHook(new Thread(sender::stop, "multicast-sender-shutdown"));

        try {
            sender.start();
        } catch (IOException | IllegalArgumentException e) {
            System.err.println("Failed to start UDP multicast sender: " + e.getMessage());
            System.err.println("Check that multicast is enabled on the selected network interface.");
            System.exit(1);
        }

        System.out.printf("ClassPulse Multicast Sender ready (group=%s port=%d ttl=%d interface=%s)%n",
                sender.getGroupAddress(),
                sender.getPort(),
                sender.getTtl(),
                sender.getNetworkInterface() != null ? sender.getNetworkInterface().getName() : "default");

        try (Scanner scanner = new Scanner(System.in)) {
            System.out.print("Enter classroom code (optional, press Enter to skip): ");
            String classroomCode = scanner.hasNextLine() ? scanner.nextLine().trim() : "";

            System.out.print("Enter sender name (default 'Lecturer'): ");
            String senderName = scanner.hasNextLine() ? scanner.nextLine().trim() : "";
            if (senderName.isEmpty()) {
                senderName = "Lecturer";
            }

            System.out.println("Type announcement message lines to broadcast (type 'exit' to quit):");
            while (scanner.hasNextLine()) {
                System.out.print("> ");
                String line = scanner.nextLine().trim();
                if ("exit".equalsIgnoreCase(line) || "quit".equalsIgnoreCase(line)) {
                    break;
                }
                if (line.isEmpty()) {
                    continue;
                }
                try {
                    MulticastAnnouncement sent = sender.sendAnnouncement(
                            classroomCode.isEmpty() ? null : classroomCode,
                            senderName,
                            line);
                    System.out.printf("[SENT #%d] (%s) %s%n",
                            sender.getSentCount(),
                            sent.getRequestId(),
                            sent.getMessage());
                } catch (IOException | IllegalArgumentException e) {
                    System.err.println("Failed to send announcement: " + e.getMessage());
                }
            }
        } finally {
            sender.stop();
            System.out.println("Multicast sender stopped.");
        }
    }
}
