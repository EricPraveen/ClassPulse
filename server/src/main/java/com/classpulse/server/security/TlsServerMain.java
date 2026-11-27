package com.classpulse.server.security;

import com.classpulse.common.protocol.Message;
import com.classpulse.server.classroom.ClassroomManager;
import com.classpulse.server.classroom.ClassroomMessageHandler;
import com.classpulse.server.poll.AnswerService;
import com.classpulse.server.poll.PollMessageHandler;
import com.classpulse.server.poll.PollService;

import java.io.File;

/**
 * Standalone entry point to start the ClassPulse secure JSSE/TLS server.
 * <p>
 * Demonstrates TLS handshake, certificate authentication, cipher suite negotiation,
 * and encrypted message exchange with connected clients.
 */
public class TlsServerMain {

    public static void main(String[] args) {
        System.out.println("=================================================");
        System.out.println("     ClassPulse Secure TLS Server (IN3111)       ");
        System.out.println("=================================================");

        TlsConfig config = TlsConfig.fromEnvironment();

        int port = config.getPort();
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid port '" + args[0] + "', using " + port);
            }
        }

        String keystorePath = config.getKeystorePath();
        if (keystorePath == null || keystorePath.isBlank()) {
            // Check default local paths
            if (new File("certs/server-keystore.p12").exists()) {
                keystorePath = "certs/server-keystore.p12";
            } else if (new File("../certs/server-keystore.p12").exists()) {
                keystorePath = "../certs/server-keystore.p12";
            } else {
                System.err.println("[ERROR] Server keystore not configured or found!");
                System.err.println("Please set CLASSPULSE_TLS_KEYSTORE_PATH or generate certificates via:");
                System.err.println("  keytool -genkeypair -alias classpulse-server -keyalg RSA -keysize 2048 \\");
                System.err.println("    -validity 365 -keystore certs/server-keystore.p12 -storetype PKCS12 \\");
                System.err.println("    -storepass changeit -dname \"CN=localhost, OU=ClassPulse, O=University, L=Colombo, C=LK\" \\");
                System.err.println("    -ext \"SAN=dns:localhost,ip:127.0.0.1\"");
                System.err.println("See docs/tls-setup.md for full instructions.");
                System.exit(1);
            }
        }

        char[] keystorePassword = config.getKeystorePassword();
        if (keystorePassword == null) {
            keystorePassword = "changeit".toCharArray();
        }

        TlsConfig resolvedConfig = TlsConfig.builder()
                .port(port)
                .keystorePath(keystorePath)
                .keystorePassword(keystorePassword)
                .keystoreType(config.getKeystoreType())
                .keyPassword(config.getKeyPassword() != null ? config.getKeyPassword() : keystorePassword)
                .enabledProtocols(config.getEnabledProtocols())
                .build();

        ClassroomManager classroomManager = new ClassroomManager();
        ClassroomMessageHandler classroomHandler = new ClassroomMessageHandler(classroomManager);
        PollService pollService = new PollService(classroomManager);
        AnswerService answerService = new AnswerService(pollService);
        PollMessageHandler pollHandler = new PollMessageHandler(pollService, answerService);

        TlsMessageHandler compositeHandler = (client, request) -> {
            Message response = classroomHandler.handleRequest(request);
            if (response == null) {
                response = pollHandler.handleRequest(request);
            }
            return response;
        };

        try {
            TlsServer server = new TlsServer(resolvedConfig, compositeHandler);
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("\nShutting down ClassPulse TLS Server...");
                server.stop();
            }, "tls-server-shutdown"));

            server.start();
            System.out.printf("[INFO] ClassPulse TLS Server is ready on port %d.%n", server.getPort());
            System.out.println("[INFO] Press Ctrl+C to terminate.");

            // Keep main thread alive
            Thread.currentThread().join();
        } catch (Exception e) {
            System.err.println("[FATAL] Failed to start TLS Server: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
