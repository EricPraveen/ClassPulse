package com.classpulse.client;

import com.classpulse.client.lecturer.LecturerConsoleApp;
import com.classpulse.client.lecturer.LecturerMain;

import java.util.Scanner;

/**
 * Main client entry point.
 * <p>
 * Allows launching the Lecturer Console or Student Console (when implemented).
 */
public class Main {

    public static void main(String[] args) {
        String host = LecturerMain.DEFAULT_HOST;
        int port = LecturerMain.DEFAULT_PORT;

        if (args.length > 0 && !args[0].startsWith("-")) {
            host = args[0].trim();
        }
        if (args.length > 1) {
            try {
                port = Integer.parseInt(args[1].trim());
            } catch (NumberFormatException ignored) {
            }
        }

        // Direct flag check: --lecturer
        for (String arg : args) {
            if ("--lecturer".equalsIgnoreCase(arg) || "-l".equalsIgnoreCase(arg)) {
                new LecturerConsoleApp(host, port).run();
                return;
            }
        }

        System.out.println("==================================================");
        System.out.println("            Welcome to ClassPulse Client          ");
        System.out.println("==================================================");
        System.out.println("  1. Lecturer Console");
        System.out.println("  2. Student Console (Issue #11 - In progress)");
        System.out.println("  3. Exit");
        System.out.println("--------------------------------------------------");
        System.out.print("Select client mode (1-3): ");

        Scanner scanner = new Scanner(System.in);
        if (scanner.hasNextLine()) {
            String choice = scanner.nextLine().trim();
            if ("1".equals(choice)) {
                new LecturerConsoleApp(host, port).run();
            } else if ("2".equals(choice)) {
                System.out.println("Student console is not yet implemented (scheduled for Issue #11).");
            } else {
                System.out.println("Exiting ClassPulse client.");
            }
        }
    }
}
