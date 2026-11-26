package com.classpulse.client;

import com.classpulse.client.lecturer.LecturerConsoleApp;
import com.classpulse.client.lecturer.LecturerMain;
import com.classpulse.client.student.StudentConsoleApp;
import com.classpulse.client.student.StudentMain;

import java.util.Scanner;

/**
 * Main client entry point.
 * <p>
 * Provides interactive role selection for launching either Lecturer Console
 * or Student Console.
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

        // Direct flag check: --lecturer or --student
        for (String arg : args) {
            if ("--lecturer".equalsIgnoreCase(arg) || "-l".equalsIgnoreCase(arg)) {
                new LecturerConsoleApp(host, port).run();
                return;
            } else if ("--student".equalsIgnoreCase(arg) || "-s".equalsIgnoreCase(arg)) {
                new StudentConsoleApp(host, port).run();
                return;
            }
        }

        System.out.println("==================================================");
        System.out.println("            Welcome to ClassPulse Client          ");
        System.out.println("==================================================");
        System.out.println("  1. Lecturer Console");
        System.out.println("  2. Student Console");
        System.out.println("  3. Exit");
        System.out.println("--------------------------------------------------");
        System.out.print("Select client mode (1-3): ");

        Scanner scanner = new Scanner(System.in);
        if (scanner.hasNextLine()) {
            String choice = scanner.nextLine().trim();
            if ("1".equals(choice)) {
                new LecturerConsoleApp(host, port).run();
            } else if ("2".equals(choice)) {
                new StudentConsoleApp(host, port).run();
            } else {
                System.out.println("Exiting ClassPulse client.");
            }
        }
    }
}
