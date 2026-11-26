package com.classpulse.server.poll;

import com.classpulse.common.model.Classroom;
import com.classpulse.common.model.Poll;
import com.classpulse.common.protocol.Message;
import com.classpulse.common.protocol.MessageType;
import com.classpulse.server.classroom.ClassroomManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PollMessageHandlerTest {

    private ClassroomManager classroomManager;
    private PollService pollService;
    private AnswerService answerService;
    private PollMessageHandler handler;
    private Classroom classroom;

    @BeforeEach
    void setUp() {
        classroomManager = new ClassroomManager();
        pollService = new PollService(classroomManager);
        answerService = new AnswerService(pollService);
        handler = new PollMessageHandler(pollService, answerService);
        classroom = classroomManager.createClassroom("Prof. Donald Knuth");
    }

    @Test
    @DisplayName("handleRequest with CREATE_POLL succeeds and returns POLL_CREATED")
    void testHandleCreatePollSuccess() {
        Message req = new Message(MessageType.CREATE_POLL, "req-poll-1");
        req.putPayload("classroomCode", classroom.getCode());
        req.putPayload("question", "What is an algorithm?");
        req.putPayload("options", Arrays.asList("Step-by-step procedure", "A computer chip", "A coffee brand"));

        Message resp = handler.handleRequest(req);
        assertNotNull(resp);
        assertEquals(MessageType.POLL_CREATED, resp.getType());
        assertEquals("req-poll-1", resp.getRequestId());
        assertNotNull(resp.getPayloadString("pollId"));
        assertEquals(classroom.getCode(), resp.getPayloadString("classroomCode"));
        assertEquals("What is an algorithm?", resp.getPayloadString("question"));
        List<String> options = resp.getPayloadList("options");
        assertEquals(3, options.size());
    }

    @Test
    @DisplayName("handleRequest with CREATE_POLL returns ERROR for non-existent classroom")
    void testHandleCreatePollInvalidClassroom() {
        Message req = new Message(MessageType.CREATE_POLL, "req-poll-2");
        req.putPayload("classroomCode", "NO_ROOM");
        req.putPayload("question", "Valid question?");
        req.putPayload("options", Arrays.asList("Option 1", "Option 2"));

        Message resp = handler.handleRequest(req);
        assertNotNull(resp);
        assertEquals(MessageType.ERROR, resp.getType());
        assertEquals("req-poll-2", resp.getRequestId());
        assertEquals("INVALID_CLASSROOM", resp.getPayloadString("code"));
    }

    @Test
    @DisplayName("handleRequest with CREATE_POLL returns ERROR for invalid payload")
    void testHandleCreatePollInvalidPayload() {
        Message reqNoQuestion = new Message(MessageType.CREATE_POLL, "req-poll-3");
        reqNoQuestion.putPayload("classroomCode", classroom.getCode());
        reqNoQuestion.putPayload("options", Arrays.asList("Option 1", "Option 2"));

        Message resp = handler.handleRequest(reqNoQuestion);
        assertNotNull(resp);
        assertEquals(MessageType.ERROR, resp.getType());
        assertEquals("INVALID_PAYLOAD", resp.getPayloadString("code"));

        Message reqFewOptions = new Message(MessageType.CREATE_POLL, "req-poll-4");
        reqFewOptions.putPayload("classroomCode", classroom.getCode());
        reqFewOptions.putPayload("question", "Question?");
        reqFewOptions.putPayload("options", List.of("Single"));

        Message respFew = handler.handleRequest(reqFewOptions);
        assertNotNull(respFew);
        assertEquals(MessageType.ERROR, respFew.getType());
        assertEquals("INVALID_PAYLOAD", respFew.getPayloadString("code"));
    }

    @Test
    @DisplayName("handleRequest with SUBMIT_ANSWER succeeds and returns ANSWER_SUBMITTED")
    void testHandleSubmitAnswerSuccess() {
        Poll poll = pollService.createPoll(classroom.getCode(), "Question?", Arrays.asList("Opt A", "Opt B"));

        Message req = new Message(MessageType.SUBMIT_ANSWER, "req-ans-1");
        req.putPayload("pollId", poll.getId());
        req.putPayload("studentId", "student-42");
        req.putPayload("selectedOption", 1);

        Message resp = handler.handleRequest(req);
        assertNotNull(resp);
        assertEquals(MessageType.ANSWER_SUBMITTED, resp.getType());
        assertEquals("req-ans-1", resp.getRequestId());
        assertEquals(poll.getId(), resp.getPayloadString("pollId"));
        assertEquals("ACCEPTED", resp.getPayloadString("status"));
    }

    @Test
    @DisplayName("handleRequest with SUBMIT_ANSWER returns ERROR for invalid poll")
    void testHandleSubmitAnswerInvalidPoll() {
        Message req = new Message(MessageType.SUBMIT_ANSWER, "req-ans-2");
        req.putPayload("pollId", "poll-non-existent");
        req.putPayload("studentId", "student-42");
        req.putPayload("selectedOption", 0);

        Message resp = handler.handleRequest(req);
        assertNotNull(resp);
        assertEquals(MessageType.ERROR, resp.getType());
        assertEquals("INVALID_POLL", resp.getPayloadString("code"));
    }

    @Test
    @DisplayName("handleRequest with SUBMIT_ANSWER returns ERROR for out of bounds option")
    void testHandleSubmitAnswerInvalidOption() {
        Poll poll = pollService.createPoll(classroom.getCode(), "Question?", Arrays.asList("Opt A", "Opt B"));

        Message req = new Message(MessageType.SUBMIT_ANSWER, "req-ans-3");
        req.putPayload("pollId", poll.getId());
        req.putPayload("studentId", "student-42");
        req.putPayload("selectedOption", 99);

        Message resp = handler.handleRequest(req);
        assertNotNull(resp);
        assertEquals(MessageType.ERROR, resp.getType());
        assertEquals("INVALID_OPTION", resp.getPayloadString("code"));
    }

    @Test
    @DisplayName("handleRequest with SUBMIT_ANSWER returns ERROR for duplicate answer")
    void testHandleSubmitAnswerDuplicate() {
        Poll poll = pollService.createPoll(classroom.getCode(), "Question?", Arrays.asList("Opt A", "Opt B"));

        Message req1 = new Message(MessageType.SUBMIT_ANSWER, "req-ans-4a");
        req1.putPayload("pollId", poll.getId());
        req1.putPayload("studentId", "student-42");
        req1.putPayload("selectedOption", 0);
        Message resp1 = handler.handleRequest(req1);
        assertEquals(MessageType.ANSWER_SUBMITTED, resp1.getType());

        Message req2 = new Message(MessageType.SUBMIT_ANSWER, "req-ans-4b");
        req2.putPayload("pollId", poll.getId());
        req2.putPayload("studentId", "student-42");
        req2.putPayload("selectedOption", 1);
        Message resp2 = handler.handleRequest(req2);
        assertNotNull(resp2);
        assertEquals(MessageType.ERROR, resp2.getType());
        assertEquals("DUPLICATE_ANSWER", resp2.getPayloadString("code"));
    }

    @Test
    @DisplayName("handleRequest with POLL_RESULTS returns aggregated counts and percentages")
    void testHandlePollResultsSuccess() {
        Poll poll = pollService.createPoll(classroom.getCode(), "Favorite animal?", Arrays.asList("Cat", "Dog"));
        answerService.submitAnswer(poll.getId(), "student-1", 0);
        answerService.submitAnswer(poll.getId(), "student-2", 0);
        answerService.submitAnswer(poll.getId(), "student-3", 1);

        Message req = new Message(MessageType.POLL_RESULTS, "req-res-1");
        req.putPayload("pollId", poll.getId());

        Message resp = handler.handleRequest(req);
        assertNotNull(resp);
        assertEquals(MessageType.POLL_RESULTS, resp.getType());
        assertEquals("req-res-1", resp.getRequestId());
        assertEquals(poll.getId(), resp.getPayloadString("pollId"));
        assertEquals(3, resp.getPayloadInt("totalResponses"));

        Map<String, Object> payload = resp.getPayload();
        assertNotNull(payload.get("optionResults"));
        assertNotNull(payload.get("optionPercentages"));
    }

    @Test
    @DisplayName("handleRequest returns null for unhandled message type")
    void testHandleRequestUnhandledType() {
        Message unhandled = new Message(MessageType.DISCONNECT, "req-disc");
        assertNull(handler.handleRequest(unhandled));
    }
}
