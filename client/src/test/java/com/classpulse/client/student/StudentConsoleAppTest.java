package com.classpulse.client.student;

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
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class StudentConsoleAppTest {

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

        StudentClient client = new StudentClient("127.0.0.1", server.getPort());
        StudentConsoleApp app = new StudentConsoleApp(client, in, out);
        app.run();

        return outBytes.toString(StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("1. Set display name workflow via console menu (Option 1)")
    void testSetDisplayNameWorkflow() {
        String input = "1\nAda Lovelace\n7\n";
        String output = runConsole(input, null);

        assertTrue(output.contains("Display name set to: Ada Lovelace"));
        assertTrue(output.contains("Goodbye from ClassPulse Student Console!"));
    }

    @Test
    @DisplayName("2. Set display name rejects blank input")
    void testSetDisplayNameBlankValidation() {
        String input = "1\n   \n7\n";
        String output = runConsole(input, null);

        assertTrue(output.contains("[!] Display name cannot be blank."));
    }

    @Test
    @DisplayName("3. Join classroom workflow (Option 2)")
    void testJoinClassroomSuccess() {
        String input = "1\nAlan Turing\n2\nCS101\n7\n";

        String output = runConsole(input, socket -> {
            Message req = ProtocolFramer.readMessage(socket.getInputStream());
            if (req != null && req.getType() == MessageType.JOIN_CLASSROOM) {
                Message resp = new Message(MessageType.CLASSROOM_JOINED, req.getRequestId());
                resp.putPayload("classroomCode", "CS101");
                resp.putPayload("studentId", "stu-turing-1");
                resp.putPayload("studentName", "Alan Turing");
                ProtocolFramer.writeMessage(socket.getOutputStream(), resp);
            }
        });

        assertTrue(output.contains("Successfully joined classroom!"));
        assertTrue(output.contains("Classroom Code: CS101"));
        assertTrue(output.contains("Student Name:   Alan Turing"));
        assertTrue(output.contains("Student ID:     stu-turing-1"));
    }

    @Test
    @DisplayName("4. Join classroom displays server error for invalid room code")
    void testJoinClassroomInvalidRoomCode() {
        String input = "1\nAlan Turing\n2\nNO_ROOM\n7\n";

        String output = runConsole(input, socket -> {
            Message req = ProtocolFramer.readMessage(socket.getInputStream());
            if (req != null) {
                Message err = Message.error(req.getRequestId(), "INVALID_CLASSROOM", "Classroom 'NO_ROOM' does not exist");
                ProtocolFramer.writeMessage(socket.getOutputStream(), err);
            }
        });

        assertTrue(output.contains("[ERROR] Failed to join classroom: Classroom 'NO_ROOM' does not exist [INVALID_CLASSROOM]"));
    }

    @Test
    @DisplayName("5. Join classroom prompts for student name if not yet set")
    void testJoinClassroomPromptsForName() {
        String input = "2\nGrace Hopper\nCS101\n7\n";

        String output = runConsole(input, socket -> {
            Message req = ProtocolFramer.readMessage(socket.getInputStream());
            if (req != null && req.getType() == MessageType.JOIN_CLASSROOM) {
                Message resp = new Message(MessageType.CLASSROOM_JOINED, req.getRequestId());
                resp.putPayload("classroomCode", "CS101");
                resp.putPayload("studentId", "stu-hopper-1");
                resp.putPayload("studentName", "Grace Hopper");
                ProtocolFramer.writeMessage(socket.getOutputStream(), resp);
            }
        });

        assertTrue(output.contains("Enter your display name:"));
        assertTrue(output.contains("Successfully joined classroom!"));
        assertTrue(output.contains("Grace Hopper"));
    }

    @Test
    @DisplayName("6. View active poll displays question and options (Option 3)")
    void testViewActivePollSuccess() {
        String input = "1\nBob\n2\nCS101\n3\n7\n";

        String output = runConsole(input, socket -> {
            while (!socket.isClosed()) {
                Message req = ProtocolFramer.readMessage(socket.getInputStream());
                if (req == null) break;

                if (req.getType() == MessageType.JOIN_CLASSROOM) {
                    Message resp = new Message(MessageType.CLASSROOM_JOINED, req.getRequestId());
                    resp.putPayload("classroomCode", "CS101");
                    resp.putPayload("studentId", "stu-bob-1");
                    resp.putPayload("studentName", "Bob");
                    ProtocolFramer.writeMessage(socket.getOutputStream(), resp);
                } else if (req.getType() == MessageType.GET_POLL) {
                    Message resp = new Message(MessageType.POLL_CREATED, req.getRequestId());
                    resp.putPayload("pollId", "poll-java-1");
                    resp.putPayload("classroomCode", "CS101");
                    resp.putPayload("question", "Which Java package provides NIO?");
                    resp.putPayload("options", Arrays.asList("java.io", "java.nio", "java.net"));
                    ProtocolFramer.writeMessage(socket.getOutputStream(), resp);
                }
            }
        });

        assertTrue(output.contains("ACTIVE POLL"));
        assertTrue(output.contains("Which Java package provides NIO?"));
        assertTrue(output.contains("[0] java.io"));
        assertTrue(output.contains("[1] java.nio"));
        assertTrue(output.contains("[2] java.net"));
    }

    @Test
    @DisplayName("7. View active poll requires joining classroom first")
    void testViewActivePollNotEnrolled() {
        String input = "3\n7\n";
        String output = runConsole(input, null);

        assertTrue(output.contains("[!] You must join a classroom first before viewing polls"));
    }

    @Test
    @DisplayName("8. View active poll handles NO_ACTIVE_POLL notice gracefully")
    void testViewActivePollNoneActive() {
        String input = "1\nBob\n2\nCS101\n3\n7\n";

        String output = runConsole(input, socket -> {
            while (!socket.isClosed()) {
                Message req = ProtocolFramer.readMessage(socket.getInputStream());
                if (req == null) break;

                if (req.getType() == MessageType.JOIN_CLASSROOM) {
                    Message resp = new Message(MessageType.CLASSROOM_JOINED, req.getRequestId());
                    resp.putPayload("classroomCode", "CS101");
                    resp.putPayload("studentId", "stu-bob-1");
                    ProtocolFramer.writeMessage(socket.getOutputStream(), resp);
                } else if (req.getType() == MessageType.GET_POLL) {
                    Message err = Message.error(req.getRequestId(), "NO_ACTIVE_POLL", "No active poll in CS101");
                    ProtocolFramer.writeMessage(socket.getOutputStream(), err);
                }
            }
        });

        assertTrue(output.contains("[Notice] No active poll is currently available in classroom CS101."));
    }

    @Test
    @DisplayName("9. Submit answer workflow (Option 4)")
    void testSubmitAnswerSuccess() {
        String input = "1\nBob\n2\nCS101\n4\n1\n7\n";

        String output = runConsole(input, socket -> {
            while (!socket.isClosed()) {
                Message req = ProtocolFramer.readMessage(socket.getInputStream());
                if (req == null) break;

                if (req.getType() == MessageType.JOIN_CLASSROOM) {
                    Message resp = new Message(MessageType.CLASSROOM_JOINED, req.getRequestId());
                    resp.putPayload("classroomCode", "CS101");
                    resp.putPayload("studentId", "stu-bob-1");
                    ProtocolFramer.writeMessage(socket.getOutputStream(), resp);
                } else if (req.getType() == MessageType.GET_POLL) {
                    Message resp = new Message(MessageType.POLL_CREATED, req.getRequestId());
                    resp.putPayload("pollId", "poll-java-1");
                    resp.putPayload("classroomCode", "CS101");
                    resp.putPayload("question", "Which Java package provides NIO?");
                    resp.putPayload("options", Arrays.asList("java.io", "java.nio", "java.net"));
                    ProtocolFramer.writeMessage(socket.getOutputStream(), resp);
                } else if (req.getType() == MessageType.SUBMIT_ANSWER) {
                    Message resp = new Message(MessageType.ANSWER_SUBMITTED, req.getRequestId());
                    resp.putPayload("pollId", "poll-java-1");
                    resp.putPayload("status", "ACCEPTED");
                    ProtocolFramer.writeMessage(socket.getOutputStream(), resp);
                }
            }
        });

        assertTrue(output.contains("Answer successfully submitted to server!"));
        assertTrue(output.contains("Selected: [1] java.nio"));
        assertTrue(output.contains("Status:   ACCEPTED"));
    }

    @Test
    @DisplayName("10. Submit answer rejects out of bounds choice before sending")
    void testSubmitAnswerOutOfBounds() {
        String input = "1\nBob\n2\nCS101\n4\n99\n7\n";

        String output = runConsole(input, socket -> {
            while (!socket.isClosed()) {
                Message req = ProtocolFramer.readMessage(socket.getInputStream());
                if (req == null) break;

                if (req.getType() == MessageType.JOIN_CLASSROOM) {
                    Message resp = new Message(MessageType.CLASSROOM_JOINED, req.getRequestId());
                    resp.putPayload("classroomCode", "CS101");
                    resp.putPayload("studentId", "stu-bob-1");
                    ProtocolFramer.writeMessage(socket.getOutputStream(), resp);
                } else if (req.getType() == MessageType.GET_POLL) {
                    Message resp = new Message(MessageType.POLL_CREATED, req.getRequestId());
                    resp.putPayload("pollId", "poll-java-1");
                    resp.putPayload("classroomCode", "CS101");
                    resp.putPayload("question", "Which Java package?");
                    resp.putPayload("options", Arrays.asList("java.io", "java.nio"));
                    ProtocolFramer.writeMessage(socket.getOutputStream(), resp);
                }
            }
        });

        assertTrue(output.contains("[!] Option 99 is out of bounds (allowed: 0 to 1)."));
    }

    @Test
    @DisplayName("11. Submit answer rejects duplicate submissions from same student")
    void testSubmitAnswerDuplicateRejected() {
        String input = "1\nBob\n2\nCS101\n4\n1\n4\n7\n";

        String output = runConsole(input, socket -> {
            while (!socket.isClosed()) {
                Message req = ProtocolFramer.readMessage(socket.getInputStream());
                if (req == null) break;

                if (req.getType() == MessageType.JOIN_CLASSROOM) {
                    Message resp = new Message(MessageType.CLASSROOM_JOINED, req.getRequestId());
                    resp.putPayload("classroomCode", "CS101");
                    resp.putPayload("studentId", "stu-bob-1");
                    ProtocolFramer.writeMessage(socket.getOutputStream(), resp);
                } else if (req.getType() == MessageType.GET_POLL) {
                    Message resp = new Message(MessageType.POLL_CREATED, req.getRequestId());
                    resp.putPayload("pollId", "poll-java-1");
                    resp.putPayload("classroomCode", "CS101");
                    resp.putPayload("question", "Which Java package?");
                    resp.putPayload("options", Arrays.asList("java.io", "java.nio"));
                    ProtocolFramer.writeMessage(socket.getOutputStream(), resp);
                } else if (req.getType() == MessageType.SUBMIT_ANSWER) {
                    Message resp = new Message(MessageType.ANSWER_SUBMITTED, req.getRequestId());
                    resp.putPayload("pollId", "poll-java-1");
                    resp.putPayload("status", "ACCEPTED");
                    ProtocolFramer.writeMessage(socket.getOutputStream(), resp);
                }
            }
        });

        assertTrue(output.contains("Answer successfully submitted to server!"));
        assertTrue(output.contains("[!] You have already submitted answer [1] for this poll."));
        assertTrue(output.contains("Duplicate submissions are not permitted."));
    }

    @Test
    @DisplayName("12. View session information (Option 5)")
    void testViewSessionInformation() {
        String input = "1\nMargaret Hamilton\n5\n7\n";
        String output = runConsole(input, null);

        assertTrue(output.contains("SESSION INFORMATION"));
        assertTrue(output.contains("Student Name:       Margaret Hamilton"));
    }

    @Test
    @DisplayName("13. Reconnect to server option (Option 6)")
    void testReconnectOption() {
        String input = "6\n7\n";
        String output = runConsole(input, null);

        assertTrue(output.contains("Successfully reconnected to 127.0.0.1"));
    }

    @Test
    @DisplayName("14. Disconnection handling without crash")
    void testDisconnectionHandling() {
        String input = "1\nTest\n2\nCS101\n7\n";

        String output = runConsole(input, socket -> {
            socket.close();
        });

        assertTrue(output.contains("[ERROR] Connection issue:"));
        assertTrue(output.contains("Please use option 6 to reconnect"));
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
