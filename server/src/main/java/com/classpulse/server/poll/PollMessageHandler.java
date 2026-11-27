package com.classpulse.server.poll;

import com.classpulse.common.model.Poll;
import com.classpulse.common.protocol.Message;
import com.classpulse.common.protocol.MessageType;
import com.classpulse.server.nio.ClientConnection;
import com.classpulse.server.nio.MessageHandler;

import java.util.List;

/**
 * Server-side request handler for poll operations.
 * <p>
 * Decodes incoming {@link Message} requests for poll creation, answer submission,
 * and poll result queries, delegates to {@link PollService} and {@link AnswerService},
 * and constructs protocol-compliant responses.
 */
public class PollMessageHandler implements MessageHandler {

    private final PollService pollService;
    private final AnswerService answerService;

    public PollMessageHandler(PollService pollService, AnswerService answerService) {
        if (pollService == null) {
            throw new IllegalArgumentException("PollService must not be null");
        }
        if (answerService == null) {
            throw new IllegalArgumentException("AnswerService must not be null");
        }
        this.pollService = pollService;
        this.answerService = answerService;
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

        if (message.getType() == MessageType.CREATE_POLL) {
            return handleCreatePoll(message, requestId);
        } else if (message.getType() == MessageType.SUBMIT_ANSWER) {
            return handleSubmitAnswer(message, requestId);
        } else if (message.getType() == MessageType.POLL_RESULTS) {
            return handlePollResults(message, requestId);
        } else if (message.getType() == MessageType.GET_POLL) {
            return handleGetPoll(message, requestId);
        }

        return null;
    }

    private Message handleGetPoll(Message message, String requestId) {
        String pollId = message.getPayloadString("pollId");
        String classroomCode = message.getPayloadString("classroomCode");

        if (pollId != null && !pollId.trim().isEmpty()) {
            return pollService.findPoll(pollId.trim())
                    .map(p -> createPollDetailsResponse(p, requestId))
                    .orElseGet(() -> Message.error(requestId, "INVALID_POLL", "Poll '" + pollId.trim() + "' does not exist"));
        }

        if (classroomCode != null && !classroomCode.trim().isEmpty()) {
            String normalizedCode = classroomCode.trim().toUpperCase();
            if (pollService.getClassroomManager() != null &&
                    !pollService.getClassroomManager().hasClassroom(normalizedCode)) {
                return Message.error(requestId, "INVALID_CLASSROOM", "Classroom '" + normalizedCode + "' does not exist");
            }

            List<Poll> polls = pollService.getPollsForClassroom(normalizedCode);
            if (polls.isEmpty()) {
                return Message.error(requestId, "NO_ACTIVE_POLL", "No active poll found in classroom '" + normalizedCode + "'");
            }

            Poll latest = polls.get(polls.size() - 1);
            return createPollDetailsResponse(latest, requestId);
        }

        return Message.error(requestId, "INVALID_PAYLOAD", "Either 'classroomCode' or 'pollId' must be provided");
    }

    private Message createPollDetailsResponse(Poll poll, String requestId) {
        Message response = new Message(MessageType.POLL_CREATED, requestId);
        response.putPayload("pollId", poll.getId());
        response.putPayload("classroomCode", poll.getClassroomCode());
        response.putPayload("question", poll.getQuestion());
        response.putPayload("options", poll.getOptions());
        return response;
    }

    private Message handleCreatePoll(Message message, String requestId) {
        String classroomCode = message.getPayloadString("classroomCode");
        String question = message.getPayloadString("question");
        List<String> options = message.getPayloadList("options");

        if (classroomCode == null || classroomCode.trim().isEmpty()) {
            return Message.error(requestId, "INVALID_PAYLOAD", "Field 'classroomCode' is required and cannot be blank");
        }
        if (question == null || question.trim().isEmpty()) {
            return Message.error(requestId, "INVALID_PAYLOAD", "Field 'question' is required and cannot be blank");
        }
        if (options == null || options.size() < 2) {
            return Message.error(requestId, "INVALID_PAYLOAD", "Field 'options' must contain at least 2 answer options");
        }
        for (int i = 0; i < options.size(); i++) {
            String opt = options.get(i);
            if (opt == null || opt.trim().isEmpty()) {
                return Message.error(requestId, "INVALID_PAYLOAD", "Option at index " + i + " cannot be blank");
            }
        }

        if (pollService.getClassroomManager() != null &&
                !pollService.getClassroomManager().hasClassroom(classroomCode.trim().toUpperCase())) {
            return Message.error(requestId, "INVALID_CLASSROOM",
                    "Classroom '" + classroomCode.trim().toUpperCase() + "' does not exist");
        }

        try {
            Poll poll = pollService.createPoll(classroomCode, question, options);
            Message response = new Message(MessageType.POLL_CREATED, requestId);
            response.putPayload("pollId", poll.getId());
            response.putPayload("classroomCode", poll.getClassroomCode());
            response.putPayload("question", poll.getQuestion());
            response.putPayload("options", poll.getOptions());
            return response;
        } catch (IllegalArgumentException e) {
            String code = (e.getMessage() != null && e.getMessage().contains("does not exist"))
                    ? "INVALID_CLASSROOM"
                    : "INVALID_PAYLOAD";
            return Message.error(requestId, code, e.getMessage());
        }
    }

    private Message handleSubmitAnswer(Message message, String requestId) {
        String pollId = message.getPayloadString("pollId");
        String studentId = message.getPayloadString("studentId");
        Integer selectedOption = message.getPayloadInt("selectedOption");

        if (pollId == null || pollId.trim().isEmpty()) {
            return Message.error(requestId, "INVALID_PAYLOAD", "Field 'pollId' is required and cannot be blank");
        }
        if (studentId == null || studentId.trim().isEmpty()) {
            return Message.error(requestId, "INVALID_PAYLOAD", "Field 'studentId' is required and cannot be blank");
        }
        if (selectedOption == null) {
            return Message.error(requestId, "INVALID_PAYLOAD", "Field 'selectedOption' is required and must be an integer");
        }

        if (!pollService.hasPoll(pollId)) {
            return Message.error(requestId, "INVALID_POLL", "Poll '" + pollId.trim() + "' does not exist");
        }

        try {
            answerService.submitAnswer(pollId, studentId, selectedOption);
            Message response = new Message(MessageType.ANSWER_SUBMITTED, requestId);
            response.putPayload("pollId", pollId.trim());
            response.putPayload("status", "ACCEPTED");
            return response;
        } catch (IllegalArgumentException e) {
            String code;
            if (e.getMessage() != null && e.getMessage().contains("out of bounds")) {
                code = "INVALID_OPTION";
            } else if (e.getMessage() != null && e.getMessage().contains("does not exist")) {
                code = "INVALID_POLL";
            } else {
                code = "INVALID_PAYLOAD";
            }
            return Message.error(requestId, code, e.getMessage());
        } catch (IllegalStateException e) {
            return Message.error(requestId, "DUPLICATE_ANSWER", e.getMessage());
        }
    }

    private Message handlePollResults(Message message, String requestId) {
        String pollId = message.getPayloadString("pollId");
        if (pollId == null || pollId.trim().isEmpty()) {
            return Message.error(requestId, "INVALID_PAYLOAD", "Field 'pollId' is required and cannot be blank");
        }

        if (!pollService.hasPoll(pollId)) {
            return Message.error(requestId, "INVALID_POLL", "Poll '" + pollId.trim() + "' does not exist");
        }

        try {
            PollResult result = answerService.calculateResults(pollId);
            Message response = new Message(MessageType.POLL_RESULTS, requestId);
            response.setPayload(result.toPayload());
            return response;
        } catch (IllegalArgumentException e) {
            return Message.error(requestId, "INVALID_POLL", e.getMessage());
        }
    }

    public PollService getPollService() {
        return pollService;
    }

    public AnswerService getAnswerService() {
        return answerService;
    }
}
