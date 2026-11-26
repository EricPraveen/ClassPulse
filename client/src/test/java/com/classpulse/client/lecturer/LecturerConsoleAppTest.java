package com.classpulse.client.lecturer;

import com.classpulse.common.protocol.Message;
import com.classpulse.common.protocol.MessageType;
import com.classpulse.common.protocol.ProtocolFramer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class LecturerConsoleAppTest {

    private MockServer server;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockServer();
        server.start();
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.close();
        }
    }

    private String runConsole(String simulatedInput, MockServer.SocketHandler handler) {
        if (handler != null) {
            server.setHandler(handler);
        }

        ByteArrayInputStream in = new ByteArrayInputStream(simulatedInput.getBytes(StandardCharsets.UTF_8));
        ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(outBytes, true, StandardCharsets.UTF_8);

        LecturerClient client = new LecturerClient("127.0.0.1", server.getPort());
        LecturerConsoleApp app = new LecturerConsoleApp(client, in, out);
        app.run();

        return outBytes.toString(StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("1. Full classroom creation workflow via console menu")
    void testCreateClassroomWorkflow() {
        String input = "1\nProf. Donald Knuth\n6\n";

        String output = runConsole(input, socket -> {
            Message req = ProtocolFramer.readMessage(socket.getInputStream());
            if (req != null && req.getType() == MessageType.CREATE_CLASSROOM) {
                Message resp = new Message(MessageType.CLASSROOM_CREATED, req.getRequestId());
                resp.putPayload("classroomCode", "DK888");
                resp.putPayload("lecturerId", "lec-1");
                ProtocolFramer.writeMessage(socket.getOutputStream(), resp);
            }
        });

        assertTrue(output.contains("Classroom created successfully!"), "Should announce success");
        assertTrue(output.contains("DK888"), "Should display generated classroom code");
        assertTrue(output.contains("Prof. Donald Knuth"), "Should display lecturer name");
        assertTrue(output.contains("Goodbye from ClassPulse Lecturer Console!"), "Should exit cleanly");
    }

    @Test
    @DisplayName("2. Classroom creation rejects blank lecturer name")
    void testCreateClassroomBlankName() {
        String input = "1\n   \n6\n";
        String output = runConsole(input, null);

        assertTrue(output.contains("[!] Lecturer name cannot be blank."));
    }

    @Test
    @DisplayName("3. Full poll creation workflow using active classroom")
    void testCreatePollWorkflow() {
        String input = "1\nDr. Shannon\n2\n\nIs entropy measure of uncertainty?\nYes\nNo\nMaybe\n\n6\n";

        String output = runConsole(input, socket -> {
            while (!socket.isClosed()) {
                Message req = ProtocolFramer.readMessage(socket.getInputStream());
                if (req == null) break;

                if (req.getType() == MessageType.CREATE_CLASSROOM) {
                    Message resp = new Message(MessageType.CLASSROOM_CREATED, req.getRequestId());
                    resp.putPayload("classroomCode", "INFO1");
                    resp.putPayload("lecturerId", "shannon-1");
                    ProtocolFramer.writeMessage(socket.getOutputStream(), resp);
                } else if (req.getType() == MessageType.CREATE_POLL) {
                    Message resp = new Message(MessageType.POLL_CREATED, req.getRequestId());
                    resp.putPayload("pollId", "poll-info-101");
                    resp.putPayload("classroomCode", "INFO1");
                    resp.putPayload("question", "Is entropy measure of uncertainty?");
                    resp.putPayload("options", Arrays.asList("Yes", "No", "Maybe"));
                    ProtocolFramer.writeMessage(socket.getOutputStream(), resp);
                }
            }
        });

        assertTrue(output.contains("Poll created successfully!"));
        assertTrue(output.contains("poll-info-101"));
        assertTrue(output.contains("Is entropy measure of uncertainty?"));
        assertTrue(output.contains("[0] Yes"));
        assertTrue(output.contains("[1] No"));
        assertTrue(output.contains("[2] Maybe"));
    }

    @Test
    @DisplayName("4. Poll creation requires at least 2 options")
    void testCreatePollMinimumOptionsValidation() {
        String input = "2\nCS101\nValid Question?\nOnly Option 1\n\nOption 2\n\n6\n";

        String output = runConsole(input, socket -> {
            Message req = ProtocolFramer.readMessage(socket.getInputStream());
            if (req != null && req.getType() == MessageType.CREATE_POLL) {
                Message resp = new Message(MessageType.POLL_CREATED, req.getRequestId());
                resp.putPayload("pollId", "poll-2opt");
                resp.putPayload("classroomCode", "CS101");
                resp.putPayload("question", "Valid Question?");
                resp.putPayload("options", Arrays.asList("Only Option 1", "Option 2"));
                ProtocolFramer.writeMessage(socket.getOutputStream(), resp);
            }
        });

        assertTrue(output.contains("[!] A poll must have at least 2 options."));
        assertTrue(output.contains("Poll created successfully!"));
    }

    @Test
    @DisplayName("5. View poll results with active votes shows counts and percentages")
    void testViewPollResultsWithResponses() {
        String input = "3\npoll-active\n6\n";

        String output = runConsole(input, socket -> {
            Message req = ProtocolFramer.readMessage(socket.getInputStream());
            if (req != null && req.getType() == MessageType.POLL_RESULTS) {
                Message resp = new Message(MessageType.POLL_RESULTS, req.getRequestId());
                resp.putPayload("pollId", "poll-active");
                resp.putPayload("question", "Best Sorting Algorithm?");
                resp.putPayload("totalResponses", 20);
                resp.putPayload("options", Arrays.asList("Quicksort", "Mergesort"));

                Map<String, Integer> counts = new LinkedHashMap<>();
                counts.put("0", 15);
                counts.put("1", 5);
                resp.putPayload("optionResults", counts);

                Map<String, Double> pcts = new LinkedHashMap<>();
                pcts.put("0", 75.0);
                pcts.put("1", 25.0);
                resp.putPayload("optionPercentages", pcts);

                ProtocolFramer.writeMessage(socket.getOutputStream(), resp);
            }
        });

        assertTrue(output.contains("POLL RESULTS"));
        assertTrue(output.contains("Best Sorting Algorithm?"));
        assertTrue(output.contains("Total Responses: 20"));
        assertTrue(output.contains("15 vote(s) ( 75.0%)"));
        assertTrue(output.contains("5 vote(s) ( 25.0%)"));
    }

    @Test
    @DisplayName("6. View poll results with zero responses shows notice without crash")
    void testViewPollResultsZeroResponses() {
        String input = "3\npoll-empty\n6\n";

        String output = runConsole(input, socket -> {
            Message req = ProtocolFramer.readMessage(socket.getInputStream());
            if (req != null && req.getType() == MessageType.POLL_RESULTS) {
                Message resp = new Message(MessageType.POLL_RESULTS, req.getRequestId());
                resp.putPayload("pollId", "poll-empty");
                resp.putPayload("question", "Unanswered Question");
                resp.putPayload("totalResponses", 0);
                resp.putPayload("options", Arrays.asList("A", "B"));

                Map<String, Integer> counts = new LinkedHashMap<>();
                counts.put("0", 0);
                counts.put("1", 0);
                resp.putPayload("optionResults", counts);

                Map<String, Double> pcts = new LinkedHashMap<>();
                pcts.put("0", 0.0);
                pcts.put("1", 0.0);
                resp.putPayload("optionPercentages", pcts);

                ProtocolFramer.writeMessage(socket.getOutputStream(), resp);
            }
        });

        assertTrue(output.contains("Total Responses: 0"));
        assertTrue(output.contains("[Notice] No responses have been submitted yet."));
    }

    @Test
    @DisplayName("7. Server ERROR response is displayed clearly without terminating app")
    void testServerErrorHandledGracefully() {
        String input = "3\npoll-invalid-99\n6\n";

        String output = runConsole(input, socket -> {
            Message req = ProtocolFramer.readMessage(socket.getInputStream());
            if (req != null) {
                Message err = Message.error(req.getRequestId(), "INVALID_POLL", "Poll 'poll-invalid-99' was not found");
                ProtocolFramer.writeMessage(socket.getOutputStream(), err);
            }
        });

        assertTrue(output.contains("[ERROR] Failed to fetch poll results: Poll 'poll-invalid-99' was not found [INVALID_POLL]"));
        assertTrue(output.contains("Goodbye from ClassPulse Lecturer Console!"));
    }

    @Test
    @DisplayName("8. Session information view (menu option 4)")
    void testViewSessionInformation() {
        String input = "4\n6\n";
        String output = runConsole(input, null);

        assertTrue(output.contains("SESSION INFORMATION"));
        assertTrue(output.contains("Lecturer Name:    Not set"));
        assertTrue(output.contains("Active Classroom: None"));
    }

    @Test
    @DisplayName("9. Reconnect option (menu option 5)")
    void testReconnectOption() {
        String input = "5\n6\n";
        String output = runConsole(input, null);

        assertTrue(output.contains("Successfully reconnected to 127.0.0.1"));
    }

    @Test
    @DisplayName("10. Invalid menu selection handled gracefully")
    void testInvalidMenuChoice() {
        String input = "99\ninvalid\n6\n";
        String output = runConsole(input, null);

        assertTrue(output.contains("[!] Invalid option '99'"));
        assertTrue(output.contains("[!] Invalid option 'invalid'"));
    }

    @Test
    @DisplayName("11. Disconnection / transport failure caught without crashing")
    void testDisconnectionHandling() {
        String input = "1\nProf. Test\n6\n";

        String output = runConsole(input, socket -> {
            // Abruptly close socket upon reading
            socket.close();
        });

        assertTrue(output.contains("[ERROR] Connection issue:"));
        assertTrue(output.contains("Please use option 5 to reconnect"));
    }

    private static class MockServer implements AutoCloseable {
        private final ServerSocket serverSocket;
        private final AtomicBoolean running = new AtomicBoolean(true);
        private SocketHandler handler = socket -> {};
        private Thread thread;

        interface SocketHandler {
            void handle(Socket socket) throws Exception;
        }

        public MockServer() throws IOException {
            this.serverSocket = new ServerSocket(0);
        }

        public int getPort() {
            return serverSocket.getLocalPort();
        }

        public void setHandler(SocketHandler handler) {
            this.handler = handler;
        }

        public void start() {
            thread = new Thread(() -> {
                while (running.get() && !serverSocket.isClosed()) {
                    try {
                        Socket socket = serverSocket.accept();
                        new Thread(() -> {
                            try (socket) {
                                handler.handle(socket);
                            } catch (Exception ignored) {
                            }
                        }).start();
                    } catch (IOException e) {
                        break;
                    }
                }
            });
            thread.setDaemon(true);
            thread.start();
        }

        @Override
        public void close() {
            running.set(false);
            try {
                serverSocket.close();
            } catch (IOException ignored) {
            }
            if (thread != null) {
                thread.interrupt();
            }
        }
    }
}
