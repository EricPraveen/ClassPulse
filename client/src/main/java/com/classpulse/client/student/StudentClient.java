package com.classpulse.client.student;

import com.classpulse.client.network.ClientTransportException;
import com.classpulse.client.network.TcpClient;
import com.classpulse.common.protocol.Message;
import com.classpulse.common.protocol.MessageType;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Service client handling network requests and session state for the Student.
 * Encapsulates {@link TcpClient} communication and protocol envelope creation.
 */
public class StudentClient implements AutoCloseable {

    private final TcpClient tcpClient;
    private final StudentSession session;
    private final AtomicLong requestCounter = new AtomicLong(1);

    public StudentClient(TcpClient tcpClient) {
        this.tcpClient = Objects.requireNonNull(tcpClient, "tcpClient cannot be null");
        this.session = new StudentSession();
    }

    public StudentClient(String host, int port) {
        this(new TcpClient(host, port));
    }

    public void connect() throws ClientTransportException {
        if (!tcpClient.isConnected()) {
            tcpClient.connect();
        }
    }

    public void disconnect() {
        tcpClient.disconnect();
    }

    public boolean isConnected() {
        return tcpClient.isConnected();
    }

    public String getHost() {
        return tcpClient.getHost();
    }

    public int getPort() {
        return tcpClient.getPort();
    }

    public StudentSession getSession() {
        return session;
    }

    public TcpClient getTcpClient() {
        return tcpClient;
    }

    /**
     * Sends a request to join an active classroom.
     * Updates session with confirmed room code, student ID, and display name on success.
     *
     * @param classroomCode the join code for the target classroom
     * @param studentName   the display name of the student
     * @return server response message
     * @throws ClientTransportException if a network transport failure occurs
     * @throws IllegalArgumentException if arguments are invalid
     */
    public Message joinClassroom(String classroomCode, String studentName) throws ClientTransportException {
        if (classroomCode == null || classroomCode.trim().isEmpty()) {
            throw new IllegalArgumentException("Classroom code cannot be null or blank");
        }
        if (studentName == null || studentName.trim().isEmpty()) {
            throw new IllegalArgumentException("Student name cannot be null or blank");
        }

        String requestId = nextRequestId();
        Message request = new Message(MessageType.JOIN_CLASSROOM, requestId);
        request.putPayload("classroomCode", classroomCode.trim().toUpperCase());
        request.putPayload("studentName", studentName.trim());

        Message response = tcpClient.sendRequest(request);
        if (response != null && response.getType() == MessageType.CLASSROOM_JOINED) {
            String code = response.getPayloadString("classroomCode");
            String studentId = response.getPayloadString("studentId");
            String confirmedName = response.getPayloadString("studentName");

            session.setClassroomCode(code);
            session.setStudentId(studentId);
            session.setStudentName(confirmedName != null ? confirmedName : studentName.trim());
        }
        return response;
    }

    /**
     * Fetches active poll details for a given classroom code or poll ID.
     * Updates session with active poll details on success.
     *
     * @param classroomCode optional classroom code
     * @param pollId        optional poll ID
     * @return server response message
     * @throws ClientTransportException if a network transport failure occurs
     * @throws IllegalArgumentException if neither classroomCode nor pollId is provided
     */
    public Message getActivePoll(String classroomCode, String pollId) throws ClientTransportException {
        if ((classroomCode == null || classroomCode.trim().isEmpty()) &&
                (pollId == null || pollId.trim().isEmpty())) {
            throw new IllegalArgumentException("Either classroomCode or pollId must be provided");
        }

        String requestId = nextRequestId();
        Message request = new Message(MessageType.GET_POLL, requestId);
        if (classroomCode != null && !classroomCode.trim().isEmpty()) {
            request.putPayload("classroomCode", classroomCode.trim().toUpperCase());
        }
        if (pollId != null && !pollId.trim().isEmpty()) {
            request.putPayload("pollId", pollId.trim());
        }

        Message response = tcpClient.sendRequest(request);
        if (response != null && response.getType() == MessageType.POLL_CREATED) {
            String id = response.getPayloadString("pollId");
            String question = response.getPayloadString("question");
            List<String> options = response.getPayloadList("options");
            session.setActivePoll(id, question, options);
        }
        return response;
    }

    /**
     * Submits an answer to an active poll.
     * Updates session with the submitted answer index upon server confirmation.
     *
     * @param classroomCode  classroom code
     * @param pollId         active poll ID
     * @param studentId      unique student identifier
     * @param selectedOption 0-based index of chosen option
     * @return server response message
     * @throws ClientTransportException if a network transport failure occurs
     * @throws IllegalArgumentException if arguments are invalid
     */
    public Message submitAnswer(String classroomCode, String pollId, String studentId, int selectedOption) throws ClientTransportException {
        if (classroomCode == null || classroomCode.trim().isEmpty()) {
            throw new IllegalArgumentException("Classroom code cannot be null or blank");
        }
        if (pollId == null || pollId.trim().isEmpty()) {
            throw new IllegalArgumentException("Poll ID cannot be null or blank");
        }
        if (studentId == null || studentId.trim().isEmpty()) {
            throw new IllegalArgumentException("Student ID cannot be null or blank");
        }

        String requestId = nextRequestId();
        Message request = new Message(MessageType.SUBMIT_ANSWER, requestId);
        request.putPayload("classroomCode", classroomCode.trim().toUpperCase());
        request.putPayload("pollId", pollId.trim());
        request.putPayload("studentId", studentId.trim());
        request.putPayload("selectedOption", selectedOption);

        Message response = tcpClient.sendRequest(request);
        if (response != null && response.getType() == MessageType.ANSWER_SUBMITTED) {
            session.setSubmittedAnswerIndex(selectedOption);
        }
        return response;
    }

    private String nextRequestId() {
        return "stu-" + System.currentTimeMillis() + "-" + requestCounter.getAndIncrement();
    }

    @Override
    public void close() {
        disconnect();
    }
}
