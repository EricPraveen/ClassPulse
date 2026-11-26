package com.classpulse.client.lecturer;

/**
 * Entry point for launching the ClassPulse Lecturer Console application directly.
 */
public class LecturerMain {

    public static final String DEFAULT_HOST = "127.0.0.1";
    public static final int DEFAULT_PORT = 5000;

    public static void main(String[] args) {
        String host = DEFAULT_HOST;
        int port = DEFAULT_PORT;

        if (args.length > 0) {
            host = args[0].trim();
        }
        if (args.length > 1) {
            try {
                port = Integer.parseInt(args[1].trim());
            } catch (NumberFormatException e) {
                System.err.printf("Invalid port '%s', defaulting to %d%n", args[1], DEFAULT_PORT);
            }
        }

        LecturerConsoleApp app = new LecturerConsoleApp(host, port);
        app.run();
    }
}
