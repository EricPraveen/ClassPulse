package com.classpulse.server;

import com.classpulse.server.nio.NioServer;

import java.io.IOException;

/**
 * Entry point for the ClassPulse central Java NIO server.
 */
public class MainServer {

    public static void main(String[] args) {
        int port = NioServer.DEFAULT_PORT;
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid port '" + args[0] + "', using default port " + NioServer.DEFAULT_PORT);
            }
        }

        NioServer server = new NioServer(port);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Shutting down ClassPulse NIO Server...");
            server.stop();
        }, "server-shutdown-hook"));

        try {
            server.start();
        } catch (IOException e) {
            System.err.println("Failed to start ClassPulse NIO Server on port " + port + ": " + e.getMessage());
            System.exit(1);
        }
    }
}
