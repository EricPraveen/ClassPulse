package com.classpulse.server.classroom;

import com.classpulse.common.model.Classroom;
import com.classpulse.common.protocol.Message;
import com.classpulse.common.protocol.MessageType;
import com.classpulse.server.nio.ClientConnection;
import com.classpulse.server.nio.MessageHandler;

/**
 * Server-side request handler for classroom operations.
 * <p>
 * Decodes incoming {@link Message} requests for classroom creation and joining,
 * invokes {@link ClassroomManager}, and constructs protocol-compliant responses.
 */
public class ClassroomMessageHandler implements MessageHandler {

    private final ClassroomManager classroomManager;

    public ClassroomMessageHandler(ClassroomManager classroomManager) {
        if (classroomManager == null) {
            throw new IllegalArgumentException("ClassroomManager must not be null");
        }
        this.classroomManager = classroomManager;
    }

    @Override
    public void handle(ClientConnection client, Message message) {
        if (client == null || message == null) {
            return;
        }

        Message response = handleRequest(message);
        if (response != null && !client.isClosed()) {
            client.send(response);
        }
    }

    /**
     * Processes a request {@link Message} and returns the appropriate response {@link Message}.
     *
     * @param message the incoming request message
     * @return response message, or null if message type is not handled here
     */
    public Message handleRequest(Message message) {
        if (message == null || message.getType() == null) {
            return Message.error("unknown", "INVALID_PAYLOAD", "Message or message type cannot be null");
        }

        String requestId = message.getRequestId() != null ? message.getRequestId() : "req-unknown";

        if (message.getType() == MessageType.CREATE_CLASSROOM) {
            return handleCreateClassroom(message, requestId);
        } else if (message.getType() == MessageType.JOIN_CLASSROOM) {
            return handleJoinClassroom(message, requestId);
        }

        // Pass-through or unhandled
        return null;
    }

    private Message handleCreateClassroom(Message message, String requestId) {
        String lecturerName = message.getPayloadString("lecturerName");
        if (lecturerName == null || lecturerName.trim().isEmpty()) {
            return Message.error(requestId, "INVALID_PAYLOAD", "Field 'lecturerName' is required and cannot be blank");
        }

        try {
            Classroom classroom = classroomManager.createClassroom(lecturerName.trim());
            Message response = new Message(MessageType.CLASSROOM_CREATED, requestId);
            response.putPayload("classroomCode", classroom.getCode());
            response.putPayload("lecturerId", classroom.getLecturerId());
            return response;
        } catch (IllegalArgumentException e) {
            return Message.error(requestId, "INVALID_CLASSROOM", e.getMessage());
        }
    }

    private Message handleJoinClassroom(Message message, String requestId) {
        String classroomCode = message.getPayloadString("classroomCode");
        String studentName = message.getPayloadString("studentName");

        if (classroomCode == null || classroomCode.trim().isEmpty()) {
            return Message.error(requestId, "INVALID_PAYLOAD", "Field 'classroomCode' is required and cannot be blank");
        }
        if (studentName == null || studentName.trim().isEmpty()) {
            return Message.error(requestId, "INVALID_PAYLOAD", "Field 'studentName' is required and cannot be blank");
        }

        JoinResult result = classroomManager.joinClassroom(classroomCode, studentName);
        if (result.isSuccess()) {
            Message response = new Message(MessageType.CLASSROOM_JOINED, requestId);
            response.putPayload("classroomCode", result.getClassroom().getCode());
            response.putPayload("studentId", result.getStudent().getId());
            response.putPayload("studentName", result.getStudent().getDisplayName());
            return response;
        } else {
            return Message.error(requestId, result.getErrorCode(), result.getErrorMessage());
        }
    }

    public ClassroomManager getClassroomManager() {
        return classroomManager;
    }
}
