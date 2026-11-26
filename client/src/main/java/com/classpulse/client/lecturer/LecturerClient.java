package com.classpulse.client.lecturer;

import com.classpulse.client.network.ClientTransportException;
import com.classpulse.client.network.TcpClient;
import com.classpulse.common.protocol.Message;
import com.classpulse.common.protocol.MessageType;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Service client handling network requests and session state for the Lecturer.
 * Encapsulates {@link TcpClient} communication and protocol envelope creation.
 */
public class LecturerClient implements AutoCloseable {

    private final TcpClient tcpClient;
    private final LecturerSession session;
    private final AtomicLong requestCounter = new AtomicLong(1);

    public LecturerClient(TcpClient tcpClient) {
        this.tcpClient = Objects.requireNonNull(tcpClient, "tcpClient cannot be null");
        this.session = new LecturerSession();
    }

    public LecturerClient(String host, int port) {
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

    public LecturerSession getSession() {
        return session;
    }

    public TcpClient getTcpClient() {
        return tcpClient;
    }

    /**
     * Sends a request to create a classroom.
     * Updates the session with the new classroom code and lecturer ID if successful.
     *
     * @param lecturerName the display name of the lecturer
     * @return server response message
     * @throws ClientTransportException if a network transport failure occurs
     * @throws IllegalArgumentException if lecturerName is invalid
     */
    public Message createClassroom(String lecturerName) throws ClientTransportException {
        if (lecturerName == null || lecturerName.trim().isEmpty()) {
            throw new IllegalArgumentException("Lecturer name cannot be null or blank");
        }

        String requestId = nextRequestId();
        Message request = new Message(MessageType.CREATE_CLASSROOM, requestId);
        request.putPayload("lecturerName", lecturerName.trim());

        Message response = tcpClient.sendRequest(request);
        if (response != null && response.getType() == MessageType.CLASSROOM_CREATED) {
            String code = response.getPayloadString("classroomCode");
            String id = response.getPayloadString("lecturerId");
            session.setClassroomCode(code);
            session.setLecturerName(lecturerName.trim());
            session.setLecturerId(id);
        }
        return response;
    }

    /**
     * Sends a request to create a poll in the specified classroom.
     * Adds the poll to the session list if successful.
     *
     * @param classroomCode join code of the classroom
     * @param question      the poll question
     * @param options       list of answer options (minimum 2)
     * @return server response message
     * @throws ClientTransportException if a network transport failure occurs
     * @throws IllegalArgumentException if arguments are invalid
     */
    public Message createPoll(String classroomCode, String question, List<String> options) throws ClientTransportException {
        if (classroomCode == null || classroomCode.trim().isEmpty()) {
            throw new IllegalArgumentException("Classroom code cannot be null or blank");
        }
        if (question == null || question.trim().isEmpty()) {
            throw new IllegalArgumentException("Poll question cannot be null or blank");
        }
        if (options == null || options.size() < 2) {
            throw new IllegalArgumentException("Poll must contain at least 2 options");
        }

        String requestId = nextRequestId();
        Message request = new Message(MessageType.CREATE_POLL, requestId);
        request.putPayload("classroomCode", classroomCode.trim().toUpperCase());
        request.putPayload("question", question.trim());
        request.putPayload("options", options);

        Message response = tcpClient.sendRequest(request);
        if (response != null && response.getType() == MessageType.POLL_CREATED) {
            String pollId = response.getPayloadString("pollId");
            List<String> confirmedOptions = response.getPayloadList("options");
            session.addPoll(new PollSummary(pollId, question.trim(), confirmedOptions != null ? confirmedOptions : options));
        }
        return response;
    }

    /**
     * Sends a request to fetch current results for a given poll.
     *
     * @param pollId unique poll identifier
     * @return server response message
     * @throws ClientTransportException if a network transport failure occurs
     * @throws IllegalArgumentException if pollId is invalid
     */
    public Message getPollResults(String pollId) throws ClientTransportException {
        if (pollId == null || pollId.trim().isEmpty()) {
            throw new IllegalArgumentException("Poll ID cannot be null or blank");
        }

        String requestId = nextRequestId();
        Message request = new Message(MessageType.POLL_RESULTS, requestId);
        request.putPayload("pollId", pollId.trim());

        return tcpClient.sendRequest(request);
    }

    private String nextRequestId() {
        return "lec-" + System.currentTimeMillis() + "-" + requestCounter.getAndIncrement();
    }

    @Override
    public void close() {
        disconnect();
    }
}
