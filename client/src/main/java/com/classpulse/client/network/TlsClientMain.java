package com.classpulse.client.network;

import com.classpulse.common.protocol.Message;
import com.classpulse.common.protocol.MessageType;

import java.io.File;
import java.util.Arrays;
import java.util.UUID;

/**
 * Standalone console application demonstrating secure JSSE/TLS communication
 * with the ClassPulse server.
 */
public class TlsClientMain {

    public static void main(String[] args) {
        System.out.println("=================================================");
        System.out.println("     ClassPulse Secure TLS Client (IN3111)       ");
        System.out.println("=================================================");

        String host = "localhost";
        int port = 5443;

        if (args.length > 0) {
            host = args[0];
        }
        if (args.length > 1) {
            try {
                port = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid port '" + args[1] + "', defaulting to 5443");
            }
        }

        String truststorePath = System.getProperty("classpulse.tls.truststore.path");
        if (truststorePath == null || truststorePath.isBlank()) {
            truststorePath = System.getenv("CLASSPULSE_TLS_TRUSTSTORE_PATH");
        }
        if (truststorePath == null || truststorePath.isBlank()) {
            if (new File("certs/client-truststore.p12").exists()) {
                truststorePath = "certs/client-truststore.p12";
            } else if (new File("../certs/client-truststore.p12").exists()) {
                truststorePath = "../certs/client-truststore.p12";
            }
        }

        if (truststorePath == null || !new File(truststorePath).exists()) {
            System.err.println("[ERROR] Client truststore not found!");
            System.err.println("Please configure CLASSPULSE_TLS_TRUSTSTORE_PATH or run certificate setup.");
            System.err.println("See docs/tls-setup.md for instructions.");
            System.exit(1);
        }

        String tsPasswordStr = System.getProperty("classpulse.tls.truststore.password");
        if (tsPasswordStr == null || tsPasswordStr.isBlank()) {
            tsPasswordStr = System.getenv("CLASSPULSE_TLS_TRUSTSTORE_PASSWORD");
        }
        char[] truststorePassword = tsPasswordStr != null ? tsPasswordStr.toCharArray() : "changeit".toCharArray();

        System.out.printf("[INFO] Connecting to TLS server at %s:%d using truststore %s...%n",
                host, port, truststorePath);

        try (TlsClient client = new TlsClient(host, port, truststorePath, truststorePassword)) {
            client.connect();

            TlsSessionInfo session = client.getSessionInfo();
            System.out.println("\n-------------------------------------------------");
            System.out.println("TLS Handshake Succeeded!");
            System.out.println("  Protocol:     " + session.getProtocol());
            System.out.println("  Cipher Suite: " + session.getCipherSuite());
            System.out.println("  Server Cert:  " + session.getPeerPrincipal());
            System.out.println("-------------------------------------------------\n");

            // 1. Create a classroom
            System.out.println("[Step 1] Creating classroom 'NET101' over TLS...");
            Message createRoom = new Message(MessageType.CREATE_CLASSROOM, "req-" + UUID.randomUUID());
            createRoom.putPayload("lecturerName", "Dr. Alan Turing");
            Message roomResp = client.sendRequest(createRoom);
            System.out.println("  Response Type: " + roomResp.getType());
            String roomCode = roomResp.getPayloadString("classroomCode");
            System.out.println("  Classroom Code: " + roomCode);

            // 2. Join the classroom
            System.out.println("\n[Step 2] Student joining classroom over TLS...");
            Message joinRoom = new Message(MessageType.JOIN_CLASSROOM, "req-" + UUID.randomUUID());
            joinRoom.putPayload("classroomCode", roomCode != null ? roomCode : "NET101");
            joinRoom.putPayload("studentName", "Ada Lovelace");
            Message joinResp = client.sendRequest(joinRoom);
            System.out.println("  Response Type: " + joinResp.getType());
            String studentId = joinResp.getPayloadString("studentId");
            System.out.println("  Student ID:     " + studentId);

            // 3. Create a poll
            System.out.println("\n[Step 3] Lecturer publishing poll over TLS...");
            Message createPoll = new Message(MessageType.CREATE_POLL, "req-" + UUID.randomUUID());
            createPoll.putPayload("classroomCode", roomCode != null ? roomCode : "NET101");
            createPoll.putPayload("question", "Does TLS use asymmetric encryption for data transfer?");
            createPoll.putPayload("options", Arrays.asList("Yes, always", "No, it uses symmetric session keys after handshake"));
            Message pollResp = client.sendRequest(createPoll);
            System.out.println("  Response Type: " + pollResp.getType());
            String pollId = pollResp.getPayloadString("pollId");
            System.out.println("  Poll ID:       " + pollId);

            // 4. Submit an answer
            System.out.println("\n[Step 4] Student submitting answer over TLS...");
            Message submitAnswer = new Message(MessageType.SUBMIT_ANSWER, "req-" + UUID.randomUUID());
            submitAnswer.putPayload("classroomCode", roomCode != null ? roomCode : "NET101");
            submitAnswer.putPayload("pollId", pollId);
            submitAnswer.putPayload("studentId", studentId);
            submitAnswer.putPayload("selectedOption", 1);
            Message ansResp = client.sendRequest(submitAnswer);
            System.out.println("  Response Type: " + ansResp.getType());
            System.out.println("  Answer Status: " + ansResp.getPayloadString("status"));

            System.out.println("\n[SUCCESS] All secure message exchanges completed successfully!");

        } catch (Exception e) {
            System.err.println("\n[ERROR] TLS Client operation failed: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
