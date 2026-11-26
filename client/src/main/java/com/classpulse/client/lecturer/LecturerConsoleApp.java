package com.classpulse.client.lecturer;

import com.classpulse.client.network.ClientTransportException;
import com.classpulse.common.protocol.Message;
import com.classpulse.common.protocol.MessageType;

import java.io.InputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Scanner;

/**
 * Interactive console application for the ClassPulse Lecturer workflow.
 * <p>
 * Provides interactive menus for classroom creation, poll authoring, and
 * real-time poll result retrieval. Separates console I/O from network transport.
 */
public class LecturerConsoleApp {

    private final LecturerClient client;
    private final Scanner scanner;
    private final PrintStream out;
    private boolean running = true;

    public LecturerConsoleApp(LecturerClient client, InputStream in, PrintStream out) {
        this.client = Objects.requireNonNull(client, "client cannot be null");
        this.scanner = new Scanner(Objects.requireNonNull(in, "in cannot be null"));
        this.out = Objects.requireNonNull(out, "out cannot be null");
    }

    public LecturerConsoleApp(LecturerClient client) {
        this(client, System.in, System.out);
    }

    public LecturerConsoleApp(String host, int port) {
        this(new LecturerClient(host, port), System.in, System.out);
    }

    /**
     * Starts the main console event loop.
     */
    public void run() {
        printBanner();
        ensureConnected();

        while (running) {
            try {
                printMenu();
                String choice = readInput("Select an option (1-6): ").trim();
                handleMenuChoice(choice);
            } catch (ClientTransportException e) {
                out.println();
                out.println("[ERROR] Connection issue: " + e.getMessage());
                out.println("        Please use option 5 to reconnect to the server.");
            } catch (NoSuchElementException e) {
                // End of input stream reached (e.g., scripted tests or stdin closed)
                running = false;
            } catch (Exception e) {
                out.println("[ERROR] Unexpected error: " + e.getMessage());
            }
        }

        cleanup();
    }

    private void printBanner() {
        out.println("==================================================");
        out.println("          ClassPulse - Lecturer Console           ");
        out.println("==================================================");
    }

    private void ensureConnected() {
        if (!client.isConnected()) {
            out.printf("Connecting to server at %s:%d...%n", client.getHost(), client.getPort());
            try {
                client.connect();
                out.println("[SUCCESS] Connected to ClassPulse server!");
            } catch (ClientTransportException e) {
                out.println("[WARN] Initial connection failed: " + e.getMessage());
                out.println("       You can retry connecting using menu option 5.");
            }
        }
    }

    private void printMenu() {
        LecturerSession session = client.getSession();
        String connStatus = client.isConnected() ? "Connected" : "Disconnected";
        String roomStatus = session.hasActiveClassroom() ? session.getClassroomCode() : "None";
        int pollCount = session.getCreatedPolls().size();

        out.println();
        out.println("--------------------------------------------------");
        out.printf(" Server: %s:%d [%s] | Active Room: %s | Polls: %d%n",
                client.getHost(), client.getPort(), connStatus, roomStatus, pollCount);
        out.println("--------------------------------------------------");
        out.println("  1. Create a Classroom");
        out.println("  2. Create a Poll");
        out.println("  3. View Poll Results");
        out.println("  4. View Session Information");
        out.println("  5. Reconnect to Server");
        out.println("  6. Exit");
        out.println("--------------------------------------------------");
    }

    private void handleMenuChoice(String choice) throws ClientTransportException {
        switch (choice) {
            case "1":
                handleCreateClassroom();
                break;
            case "2":
                handleCreatePoll();
                break;
            case "3":
                handleViewPollResults();
                break;
            case "4":
                handleViewSession();
                break;
            case "5":
                handleReconnect();
                break;
            case "6":
                handleExit();
                break;
            default:
                out.println("[!] Invalid option '" + choice + "'. Please enter a number between 1 and 6.");
        }
    }

    /**
     * Prompts for lecturer details and issues a CREATE_CLASSROOM request.
     */
    public void handleCreateClassroom() throws ClientTransportException {
        if (!checkConnection()) return;

        out.println();
        out.println("--- Create Classroom ---");
        String lecturerName = readInput("Enter your name (Lecturer): ").trim();
        if (lecturerName.isEmpty()) {
            out.println("[!] Lecturer name cannot be blank.");
            return;
        }

        out.println("Sending classroom creation request...");
        Message response = client.createClassroom(lecturerName);
        if (response != null && response.getType() == MessageType.CLASSROOM_CREATED) {
            String code = response.getPayloadString("classroomCode");
            String id = response.getPayloadString("lecturerId");
            out.println();
            out.println("[SUCCESS] Classroom created successfully!");
            out.println("==================================================");
            out.println("  Classroom Code: " + code);
            out.println("  Lecturer Name:  " + lecturerName);
            if (id != null) {
                out.println("  Lecturer ID:    " + id);
            }
            out.println("==================================================");
            out.println("Share the classroom code with your students to allow them to join.");
        } else if (response != null && response.getType() == MessageType.ERROR) {
            printServerError("Failed to create classroom", response);
        } else {
            out.println("[ERROR] Unexpected server response: " + (response != null ? response.getType() : "null"));
        }
    }

    /**
     * Prompts for question and answer options and issues a CREATE_POLL request.
     */
    public void handleCreatePoll() throws ClientTransportException {
        if (!checkConnection()) return;

        out.println();
        out.println("--- Create Poll ---");

        String classroomCode;
        LecturerSession session = client.getSession();
        if (session.hasActiveClassroom()) {
            out.println("Active classroom detected: " + session.getClassroomCode());
            String override = readInput("Press Enter to use active classroom, or enter another code: ").trim();
            classroomCode = override.isEmpty() ? session.getClassroomCode() : override.toUpperCase();
        } else {
            classroomCode = readInput("Enter Classroom Code: ").trim().toUpperCase();
            if (classroomCode.isEmpty()) {
                out.println("[!] Classroom code cannot be blank.");
                return;
            }
        }

        String question = readInput("Enter Poll Question: ").trim();
        if (question.isEmpty()) {
            out.println("[!] Poll question cannot be blank.");
            return;
        }

        out.println("Enter answer options (minimum 2 options). Press Enter on an empty line when done:");
        List<String> options = new ArrayList<>();
        int optionIndex = 1;
        while (true) {
            String opt = readInput("  Option " + optionIndex + ": ").trim();
            if (opt.isEmpty()) {
                if (options.size() < 2) {
                    out.println("  [!] A poll must have at least 2 options. Please enter another option.");
                    continue;
                }
                break;
            }
            options.add(opt);
            optionIndex++;
        }

        out.println("Sending poll creation request...");
        Message response = client.createPoll(classroomCode, question, options);
        if (response != null && response.getType() == MessageType.POLL_CREATED) {
            String pollId = response.getPayloadString("pollId");
            out.println();
            out.println("[SUCCESS] Poll created successfully!");
            out.println("==================================================");
            out.println("  Poll ID:    " + pollId);
            out.println("  Classroom:  " + classroomCode);
            out.println("  Question:   " + question);
            out.println("  Options:");
            for (int i = 0; i < options.size(); i++) {
                out.printf("    [%d] %s%n", i, options.get(i));
            }
            out.println("==================================================");
            out.println("Students can now submit answers for this poll.");
        } else if (response != null && response.getType() == MessageType.ERROR) {
            printServerError("Failed to create poll", response);
        } else {
            out.println("[ERROR] Unexpected server response: " + (response != null ? response.getType() : "null"));
        }
    }

    /**
     * Prompts for target poll ID and displays aggregated response statistics.
     */
    public void handleViewPollResults() throws ClientTransportException {
        if (!checkConnection()) return;

        out.println();
        out.println("--- View Poll Results ---");

        List<PollSummary> polls = client.getSession().getCreatedPolls();
        String targetPollId;

        if (!polls.isEmpty()) {
            out.println("Recent polls in this session:");
            for (int i = 0; i < polls.size(); i++) {
                PollSummary p = polls.get(i);
                out.printf("  [%d] %s (%s)%n", i + 1, p.getQuestion(), p.getPollId());
            }
            String input = readInput("Select poll number (1-" + polls.size() + ") or enter Poll ID: ").trim();
            if (input.isEmpty()) {
                out.println("[!] Selection cancelled.");
                return;
            }
            try {
                int selectedIndex = Integer.parseInt(input) - 1;
                if (selectedIndex >= 0 && selectedIndex < polls.size()) {
                    targetPollId = polls.get(selectedIndex).getPollId();
                } else {
                    targetPollId = input;
                }
            } catch (NumberFormatException e) {
                targetPollId = input;
            }
        } else {
            targetPollId = readInput("Enter Poll ID: ").trim();
            if (targetPollId.isEmpty()) {
                out.println("[!] Poll ID cannot be blank.");
                return;
            }
        }

        out.println("Fetching results from server for " + targetPollId + "...");
        Message response = client.getPollResults(targetPollId);
        if (response != null && response.getType() == MessageType.POLL_RESULTS) {
            displayPollResults(response);
        } else if (response != null && response.getType() == MessageType.ERROR) {
            printServerError("Failed to fetch poll results", response);
        } else {
            out.println("[ERROR] Unexpected server response: " + (response != null ? response.getType() : "null"));
        }
    }

    private void displayPollResults(Message response) {
        String pollId = response.getPayloadString("pollId");
        String question = response.getPayloadString("question");
        Integer totalResponses = response.getPayloadInt("totalResponses");
        int total = totalResponses != null ? totalResponses : 0;
        List<String> options = response.getPayloadList("options");

        Object rawResults = response.getPayloadField("optionResults");
        Map<?, ?> optionResults = (rawResults instanceof Map<?, ?>) ? (Map<?, ?>) rawResults : null;

        Object rawPercentages = response.getPayloadField("optionPercentages");
        Map<?, ?> optionPercentages = (rawPercentages instanceof Map<?, ?>) ? (Map<?, ?>) rawPercentages : null;

        out.println();
        out.println("==================================================");
        out.println("                   POLL RESULTS                   ");
        out.println("==================================================");
        out.println("  Poll ID:         " + pollId);
        out.println("  Question:        " + question);
        out.println("  Total Responses: " + total);
        out.println("--------------------------------------------------");

        if (options != null && !options.isEmpty()) {
            for (int i = 0; i < options.size(); i++) {
                String key = String.valueOf(i);
                int count = getNumericInt(optionResults, key);
                double pct = getNumericDouble(optionPercentages, key);
                out.printf("  [%d] %-28s | %3d vote(s) (%5.1f%%)%n", i, options.get(i), count, pct);
            }
        } else {
            out.println("  (No option descriptions available)");
        }

        if (total == 0) {
            out.println("--------------------------------------------------");
            out.println("  [Notice] No responses have been submitted yet.");
        }
        out.println("==================================================");
    }

    private int getNumericInt(Map<?, ?> map, String key) {
        if (map == null || !map.containsKey(key)) return 0;
        Object val = map.get(key);
        if (val instanceof Number) {
            return ((Number) val).intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(val));
        } catch (Exception e) {
            return 0;
        }
    }

    private double getNumericDouble(Map<?, ?> map, String key) {
        if (map == null || !map.containsKey(key)) return 0.0;
        Object val = map.get(key);
        if (val instanceof Number) {
            return ((Number) val).doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(val));
        } catch (Exception e) {
            return 0.0;
        }
    }

    /**
     * Displays summary of current session information.
     */
    public void handleViewSession() {
        LecturerSession session = client.getSession();
        out.println();
        out.println("==================================================");
        out.println("               SESSION INFORMATION                ");
        out.println("==================================================");
        out.println("  Lecturer Name:    " + (session.getLecturerName() != null ? session.getLecturerName() : "Not set"));
        out.println("  Lecturer ID:      " + (session.getLecturerId() != null ? session.getLecturerId() : "Not assigned"));
        out.println("  Active Classroom: " + (session.getClassroomCode() != null ? session.getClassroomCode() : "None"));
        out.println("  Connection:       " + (client.isConnected() ? "Connected to " + client.getHost() + ":" + client.getPort() : "Disconnected"));

        List<PollSummary> polls = session.getCreatedPolls();
        out.println("  Created Polls:    " + polls.size());
        for (int i = 0; i < polls.size(); i++) {
            PollSummary p = polls.get(i);
            out.printf("    %d. %s - \"%s\" (%d options)%n",
                    i + 1, p.getPollId(), p.getQuestion(), p.getOptions().size());
        }
        out.println("==================================================");
    }

    /**
     * Reconnects the TCP client to the server.
     */
    public void handleReconnect() {
        out.println();
        out.println("--- Reconnect to Server ---");
        if (client.isConnected()) {
            client.disconnect();
            out.println("Closed existing connection.");
        }

        try {
            client.connect();
            out.printf("[SUCCESS] Successfully reconnected to %s:%d!%n", client.getHost(), client.getPort());
        } catch (ClientTransportException e) {
            out.println("[ERROR] Reconnection failed: " + e.getMessage());
        }
    }

    /**
     * Gracefully exits the application.
     */
    public void handleExit() {
        out.println();
        out.println("Disconnecting from server and exiting...");
        running = false;
    }

    private boolean checkConnection() {
        if (!client.isConnected()) {
            out.println("[!] You are not connected to the server. Please reconnect via option 5.");
            return false;
        }
        return true;
    }

    private void printServerError(String action, Message errorResponse) {
        String code = errorResponse.getPayloadString("code");
        String msg = errorResponse.getPayloadString("message");
        out.println();
        out.printf("[ERROR] %s: %s [%s]%n", action, (msg != null ? msg : "Unknown error"), (code != null ? code : "ERROR"));
    }

    private String readInput(String prompt) {
        out.print(prompt);
        if (scanner.hasNextLine()) {
            return scanner.nextLine();
        }
        throw new NoSuchElementException("End of input reached");
    }

    private void cleanup() {
        client.disconnect();
        out.println("Goodbye from ClassPulse Lecturer Console!");
    }

    public LecturerClient getClient() {
        return client;
    }

    public boolean isRunning() {
        return running;
    }
}
