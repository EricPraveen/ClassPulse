package com.classpulse.client.student;

import com.classpulse.client.network.ClientTransportException;
import com.classpulse.common.protocol.Message;
import com.classpulse.common.protocol.MessageType;

import java.io.InputStream;
import java.io.PrintStream;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Scanner;

/**
 * Interactive console application for the ClassPulse Student workflow.
 * <p>
 * Provides interactive menus for classroom joining, viewing active polls,
 * submitting poll answers, and viewing session status.
 */
public class StudentConsoleApp {

    private final StudentClient client;
    private final Scanner scanner;
    private final PrintStream out;
    private boolean running = true;

    public StudentConsoleApp(StudentClient client, InputStream in, PrintStream out) {
        this.client = Objects.requireNonNull(client, "client cannot be null");
        this.scanner = new Scanner(Objects.requireNonNull(in, "in cannot be null"));
        this.out = Objects.requireNonNull(out, "out cannot be null");
    }

    public StudentConsoleApp(StudentClient client) {
        this(client, System.in, System.out);
    }

    public StudentConsoleApp(String host, int port) {
        this(new StudentClient(host, port), System.in, System.out);
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
                String choice = readInput("Select an option (1-7): ").trim();
                handleMenuChoice(choice);
            } catch (ClientTransportException e) {
                out.println();
                out.println("[ERROR] Connection issue: " + e.getMessage());
                out.println("        Please use option 6 to reconnect to the server.");
            } catch (NoSuchElementException e) {
                // End of input stream reached (e.g. automated tests or stdin EOF)
                running = false;
            } catch (Exception e) {
                out.println("[ERROR] Unexpected error: " + e.getMessage());
            }
        }

        cleanup();
    }

    private void printBanner() {
        out.println("==================================================");
        out.println("           ClassPulse - Student Console           ");
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
                out.println("       You can retry connecting using menu option 6.");
            }
        }
    }

    private void printMenu() {
        StudentSession session = client.getSession();
        String connStatus = client.isConnected() ? "Connected" : "Disconnected";
        String studentStatus = session.getStudentName() != null ? session.getStudentName() : "Not set";
        String roomStatus = session.isEnrolled() ? session.getClassroomCode() : "None";
        String pollStatus = session.hasActivePoll() ? session.getActivePollQuestion() : "None";

        out.println();
        out.println("--------------------------------------------------");
        out.printf(" Server: %s:%d [%s]%n", client.getHost(), client.getPort(), connStatus);
        out.printf(" Student: %s (ID: %s) | Classroom: %s | Poll: %s%n",
                studentStatus, (session.getStudentId() != null ? session.getStudentId() : "unassigned"), roomStatus, pollStatus);
        out.println("--------------------------------------------------");
        out.println("  1. Set / Change Display Name");
        out.println("  2. Join a Classroom");
        out.println("  3. View Active Poll");
        out.println("  4. Submit Answer to Poll");
        out.println("  5. View Session Status");
        out.println("  6. Reconnect to Server");
        out.println("  7. Exit");
        out.println("--------------------------------------------------");
    }

    private void handleMenuChoice(String choice) throws ClientTransportException {
        switch (choice) {
            case "1":
                handleSetDisplayName();
                break;
            case "2":
                handleJoinClassroom();
                break;
            case "3":
                handleViewActivePoll();
                break;
            case "4":
                handleSubmitAnswer();
                break;
            case "5":
                handleViewSessionStatus();
                break;
            case "6":
                handleReconnect();
                break;
            case "7":
                handleExit();
                break;
            default:
                out.println("[!] Invalid option '" + choice + "'. Please enter a number between 1 and 7.");
        }
    }

    /**
     * Prompts for and sets student display name in the local session.
     */
    public void handleSetDisplayName() {
        out.println();
        out.println("--- Set Display Name ---");
        String name = readInput("Enter your display name: ").trim();
        if (name.isEmpty()) {
            out.println("[!] Display name cannot be blank.");
            return;
        }

        client.getSession().setStudentName(name);
        out.println("[SUCCESS] Display name set to: " + name);
    }

    /**
     * Prompts for classroom join code and issues a JOIN_CLASSROOM request.
     */
    public void handleJoinClassroom() throws ClientTransportException {
        if (!checkConnection()) return;

        out.println();
        out.println("--- Join Classroom ---");

        String studentName = client.getSession().getStudentName();
        if (studentName == null || studentName.trim().isEmpty()) {
            studentName = readInput("Enter your display name: ").trim();
            if (studentName.isEmpty()) {
                out.println("[!] Student name cannot be blank.");
                return;
            }
            client.getSession().setStudentName(studentName);
        }

        String roomCode = readInput("Enter Classroom Join Code: ").trim().toUpperCase();
        if (roomCode.isEmpty()) {
            out.println("[!] Classroom code cannot be blank.");
            return;
        }

        out.println("Joining classroom " + roomCode + "...");
        Message response = client.joinClassroom(roomCode, studentName);
        if (response != null && response.getType() == MessageType.CLASSROOM_JOINED) {
            String code = response.getPayloadString("classroomCode");
            String id = response.getPayloadString("studentId");
            out.println();
            out.println("[SUCCESS] Successfully joined classroom!");
            out.println("==================================================");
            out.println("  Classroom Code: " + code);
            out.println("  Student Name:   " + studentName);
            out.println("  Student ID:     " + id);
            out.println("==================================================");
            out.println("You are now enrolled. Use option 3 to view active polls.");
        } else if (response != null && response.getType() == MessageType.ERROR) {
            printServerError("Failed to join classroom", response);
        } else {
            out.println("[ERROR] Unexpected server response: " + (response != null ? response.getType() : "null"));
        }
    }

    /**
     * Requests active poll details for the enrolled classroom and displays them.
     */
    public void handleViewActivePoll() throws ClientTransportException {
        if (!checkConnection()) return;

        StudentSession session = client.getSession();
        if (!session.isEnrolled()) {
            out.println();
            out.println("[!] You must join a classroom first before viewing polls (Option 2).");
            return;
        }

        out.println();
        out.println("Fetching active poll for classroom " + session.getClassroomCode() + "...");
        Message response = client.getActivePoll(session.getClassroomCode(), null);
        if (response != null && response.getType() == MessageType.POLL_CREATED) {
            displayActivePoll(session);
        } else if (response != null && response.getType() == MessageType.ERROR) {
            String code = response.getPayloadString("code");
            if ("NO_ACTIVE_POLL".equals(code)) {
                out.println();
                out.println("[Notice] No active poll is currently available in classroom " + session.getClassroomCode() + ".");
                out.println("         Please wait for your lecturer to launch a poll.");
            } else {
                printServerError("Failed to fetch active poll", response);
            }
        } else {
            out.println("[ERROR] Unexpected server response: " + (response != null ? response.getType() : "null"));
        }
    }

    private void displayActivePoll(StudentSession session) {
        out.println();
        out.println("==================================================");
        out.println("                   ACTIVE POLL                    ");
        out.println("==================================================");
        out.println("  Classroom:  " + session.getClassroomCode());
        out.println("  Poll ID:    " + session.getActivePollId());
        out.println("  Question:   " + session.getActivePollQuestion());
        out.println("  Options:");
        List<String> options = session.getActivePollOptions();
        for (int i = 0; i < options.size(); i++) {
            out.printf("    [%d] %s%n", i, options.get(i));
        }
        out.println("--------------------------------------------------");

        if (session.hasAnswered()) {
            int ans = session.getSubmittedAnswerIndex();
            String ansText = (ans >= 0 && ans < options.size()) ? options.get(ans) : "Option " + ans;
            out.printf("  [Notice] You have already submitted answer [%d] (%s).%n", ans, ansText);
        } else {
            out.println("  Use option 4 to submit your answer.");
        }
        out.println("==================================================");
    }

    /**
     * Prompts for option selection and issues a SUBMIT_ANSWER request.
     */
    public void handleSubmitAnswer() throws ClientTransportException {
        if (!checkConnection()) return;

        StudentSession session = client.getSession();
        if (!session.isEnrolled()) {
            out.println();
            out.println("[!] You must join a classroom first before submitting answers (Option 2).");
            return;
        }

        // Fetch active poll if not yet fetched
        if (!session.hasActivePoll()) {
            out.println();
            out.println("Checking for active poll in classroom " + session.getClassroomCode() + "...");
            Message getResp = client.getActivePoll(session.getClassroomCode(), null);
            if (getResp == null || getResp.getType() != MessageType.POLL_CREATED) {
                out.println("[Notice] No active poll is available in classroom " + session.getClassroomCode() + ".");
                return;
            }
        }

        if (session.hasAnswered()) {
            out.println();
            out.printf("[!] You have already submitted answer [%d] for this poll.%n", session.getSubmittedAnswerIndex());
            out.println("    Duplicate submissions are not permitted.");
            return;
        }

        List<String> options = session.getActivePollOptions();
        out.println();
        out.println("--- Submit Answer ---");
        out.println("Question: " + session.getActivePollQuestion());
        for (int i = 0; i < options.size(); i++) {
            out.printf("  [%d] %s%n", i, options.get(i));
        }

        String input = readInput("Select option number (0 - " + (options.size() - 1) + "): ").trim();
        int choice;
        try {
            choice = Integer.parseInt(input);
        } catch (NumberFormatException e) {
            out.println("[!] Invalid input '" + input + "'. Please enter an option number.");
            return;
        }

        if (choice < 0 || choice >= options.size()) {
            out.printf("[!] Option %d is out of bounds (allowed: 0 to %d).%n", choice, options.size() - 1);
            return;
        }

        out.println("Submitting answer to server...");
        Message response = client.submitAnswer(session.getClassroomCode(), session.getActivePollId(),
                session.getStudentId(), choice);

        if (response != null && response.getType() == MessageType.ANSWER_SUBMITTED) {
            out.println();
            out.println("[SUCCESS] Answer successfully submitted to server!");
            out.println("==================================================");
            out.println("  Poll:     " + session.getActivePollQuestion());
            out.printf("  Selected: [%d] %s%n", choice, options.get(choice));
            out.println("  Status:   ACCEPTED");
            out.println("==================================================");
            out.println("Thank you! Your vote has been recorded.");
        } else if (response != null && response.getType() == MessageType.ERROR) {
            printServerError("Failed to submit answer", response);
        } else {
            out.println("[ERROR] Unexpected server response: " + (response != null ? response.getType() : "null"));
        }
    }

    /**
     * Displays summary of current student session status.
     */
    public void handleViewSessionStatus() {
        StudentSession session = client.getSession();
        out.println();
        out.println("==================================================");
        out.println("               SESSION INFORMATION                ");
        out.println("==================================================");
        out.println("  Student Name:       " + (session.getStudentName() != null ? session.getStudentName() : "Not set"));
        out.println("  Student ID:         " + (session.getStudentId() != null ? session.getStudentId() : "Unassigned"));
        out.println("  Classroom Code:     " + (session.getClassroomCode() != null ? session.getClassroomCode() : "Not joined"));
        out.println("  Connection:         " + (client.isConnected() ? "Connected to " + client.getHost() + ":" + client.getPort() : "Disconnected"));
        out.println("  Active Poll:        " + (session.hasActivePoll() ? session.getActivePollQuestion() : "None"));
        out.println("  Answer Submitted:   " + (session.hasAnswered() ? "Option [" + session.getSubmittedAnswerIndex() + "]" : "None"));
        out.println("==================================================");
    }

    /**
     * Reconnects to the ClassPulse server.
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
            out.println("[!] You are not connected to the server. Please reconnect via option 6.");
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
        out.println("Goodbye from ClassPulse Student Console!");
    }

    public StudentClient getClient() {
        return client;
    }

    public boolean isRunning() {
        return running;
    }
}
