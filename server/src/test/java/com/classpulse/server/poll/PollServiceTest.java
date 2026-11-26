package com.classpulse.server.poll;

import com.classpulse.common.model.Answer;
import com.classpulse.common.model.Classroom;
import com.classpulse.common.model.Poll;
import com.classpulse.server.classroom.ClassroomManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class PollServiceTest {

    private ClassroomManager classroomManager;
    private PollService pollService;
    private AnswerService answerService;
    private Classroom activeClassroom;

    @BeforeEach
    void setUp() {
        classroomManager = new ClassroomManager();
        pollService = new PollService(classroomManager);
        answerService = new AnswerService(pollService);
        activeClassroom = classroomManager.createClassroom("Prof. Alan Turing");
    }

    @Test
    @DisplayName("Create poll successfully with valid classroom and options")
    void testCreatePollSuccess() {
        List<String> options = Arrays.asList("Option A", "Option B", "Option C");
        Poll poll = pollService.createPoll(activeClassroom.getCode(), "What is Java NIO?", options);

        assertNotNull(poll);
        assertNotNull(poll.getId());
        assertTrue(poll.getId().startsWith("poll-"));
        assertEquals(activeClassroom.getCode(), poll.getClassroomCode());
        assertEquals("What is Java NIO?", poll.getQuestion());
        assertEquals(options, poll.getOptions());

        assertTrue(pollService.hasPoll(poll.getId()));
        assertTrue(pollService.findPoll(poll.getId()).isPresent());
        assertEquals(poll, pollService.findPoll(poll.getId()).get());

        List<Poll> roomPolls = pollService.getPollsForClassroom(activeClassroom.getCode());
        assertEquals(1, roomPolls.size());
        assertEquals(poll, roomPolls.get(0));
    }

    @Test
    @DisplayName("Create poll rejects non-existent classroom")
    void testCreatePollInvalidClassroom() {
        List<String> options = Arrays.asList("Yes", "No");
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                pollService.createPoll("NON_EXISTENT", "Does this room exist?", options));
        assertTrue(ex.getMessage().contains("does not exist"));
    }

    @Test
    @DisplayName("Create poll rejects null, empty or invalid question")
    void testCreatePollInvalidQuestion() {
        List<String> options = Arrays.asList("Option A", "Option B");

        assertThrows(IllegalArgumentException.class, () ->
                pollService.createPoll(activeClassroom.getCode(), null, options));
        assertThrows(IllegalArgumentException.class, () ->
                pollService.createPoll(activeClassroom.getCode(), "   ", options));
    }

    @Test
    @DisplayName("Create poll rejects less than 2 options or blank options")
    void testCreatePollInvalidOptions() {
        assertThrows(IllegalArgumentException.class, () ->
                pollService.createPoll(activeClassroom.getCode(), "Question?", null));
        assertThrows(IllegalArgumentException.class, () ->
                pollService.createPoll(activeClassroom.getCode(), "Question?", Collections.emptyList()));
        assertThrows(IllegalArgumentException.class, () ->
                pollService.createPoll(activeClassroom.getCode(), "Question?", Collections.singletonList("Only one")));

        assertThrows(IllegalArgumentException.class, () ->
                pollService.createPoll(activeClassroom.getCode(), "Question?", Arrays.asList("Valid", "  ")));
    }

    @Test
    @DisplayName("Submit answer successfully records student response")
    void testSubmitAnswerSuccess() {
        Poll poll = pollService.createPoll(activeClassroom.getCode(), "Favorite language?", Arrays.asList("Java", "Kotlin", "Go"));

        Answer answer = answerService.submitAnswer(poll.getId(), "student-1", 0);
        assertNotNull(answer);
        assertEquals(poll.getId(), answer.getPollId());
        assertEquals("student-1", answer.getStudentId());
        assertEquals(0, answer.getSelectedOptionIndex());

        assertTrue(answerService.hasStudentAnswered(poll.getId(), "student-1"));
        assertFalse(answerService.hasStudentAnswered(poll.getId(), "student-2"));
        assertEquals(1, answerService.getAnswersForPoll(poll.getId()).size());
    }

    @Test
    @DisplayName("Submit answer rejects invalid option index")
    void testSubmitAnswerInvalidOptionIndex() {
        Poll poll = pollService.createPoll(activeClassroom.getCode(), "Binary choice?", Arrays.asList("0", "1"));

        IllegalArgumentException exNegative = assertThrows(IllegalArgumentException.class, () ->
                answerService.submitAnswer(poll.getId(), "student-1", -1));
        assertTrue(exNegative.getMessage().contains("out of bounds"));

        IllegalArgumentException exOverflow = assertThrows(IllegalArgumentException.class, () ->
                answerService.submitAnswer(poll.getId(), "student-1", 2));
        assertTrue(exOverflow.getMessage().contains("out of bounds"));
    }

    @Test
    @DisplayName("Submit answer rejects duplicate submission from same student")
    void testSubmitAnswerDuplicateRejection() {
        Poll poll = pollService.createPoll(activeClassroom.getCode(), "Pick one", Arrays.asList("Alpha", "Beta"));

        answerService.submitAnswer(poll.getId(), "student-1", 0);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                answerService.submitAnswer(poll.getId(), "student-1", 1));
        assertTrue(ex.getMessage().contains("already submitted"));
    }

    @Test
    @DisplayName("Result calculation correctly aggregates votes and percentages")
    void testResultCalculationAggregation() {
        List<String> options = Arrays.asList("Option A", "Option B", "Option C", "Option D");
        Poll poll = pollService.createPoll(activeClassroom.getCode(), "Choose one", options);

        // 10 students vote:
        // Option 0: 5 votes (50.0%)
        // Option 1: 3 votes (30.0%)
        // Option 2: 2 votes (20.0%)
        // Option 3: 0 votes (0.0%)
        for (int i = 1; i <= 5; i++) {
            answerService.submitAnswer(poll.getId(), "student-" + i, 0);
        }
        for (int i = 6; i <= 8; i++) {
            answerService.submitAnswer(poll.getId(), "student-" + i, 1);
        }
        for (int i = 9; i <= 10; i++) {
            answerService.submitAnswer(poll.getId(), "student-" + i, 2);
        }

        PollResult result = answerService.calculateResults(poll.getId());
        assertNotNull(result);
        assertEquals(poll.getId(), result.getPollId());
        assertEquals("Choose one", result.getQuestion());
        assertEquals(10, result.getTotalResponses());

        assertEquals(5, result.getVotesForOption(0));
        assertEquals(3, result.getVotesForOption(1));
        assertEquals(2, result.getVotesForOption(2));
        assertEquals(0, result.getVotesForOption(3));

        assertEquals(50.0, result.getPercentageForOption(0), 0.01);
        assertEquals(30.0, result.getPercentageForOption(1), 0.01);
        assertEquals(20.0, result.getPercentageForOption(2), 0.01);
        assertEquals(0.0, result.getPercentageForOption(3), 0.01);

        var payload = result.toPayload();
        assertEquals(10, payload.get("totalResponses"));
        assertTrue(payload.containsKey("optionResults"));
        assertTrue(payload.containsKey("optionPercentages"));
    }

    @Test
    @DisplayName("Zero-response poll calculates results safely without division by zero")
    void testZeroResponsePollSafeCalculation() {
        Poll poll = pollService.createPoll(activeClassroom.getCode(), "Nobody answered yet", Arrays.asList("X", "Y"));

        PollResult result = answerService.calculateResults(poll.getId());
        assertNotNull(result);
        assertEquals(0, result.getTotalResponses());
        assertEquals(0, result.getVotesForOption(0));
        assertEquals(0, result.getVotesForOption(1));
        assertEquals(0.0, result.getPercentageForOption(0), 0.001);
        assertEquals(0.0, result.getPercentageForOption(1), 0.001);
        assertFalse(Double.isNaN(result.getPercentageForOption(0)));
        assertFalse(Double.isInfinite(result.getPercentageForOption(0)));
    }

    @Test
    @DisplayName("Concurrent answer submissions are handled safely")
    void testConcurrentAnswerSubmissions() throws Exception {
        Poll poll = pollService.createPoll(activeClassroom.getCode(), "Thread safety poll", Arrays.asList("Option 1", "Option 2"));

        int studentCount = 50;
        ExecutorService executor = Executors.newFixedThreadPool(8);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(studentCount);
        AtomicInteger successCount = new AtomicInteger();

        for (int i = 0; i < studentCount; i++) {
            final String studentId = "student-concurrent-" + i;
            final int option = i % 2;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    answerService.submitAnswer(poll.getId(), studentId, option);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    // Ignore
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await();
        executor.shutdown();

        assertEquals(studentCount, successCount.get());
        PollResult result = answerService.calculateResults(poll.getId());
        assertEquals(studentCount, result.getTotalResponses());
        assertEquals(25, result.getVotesForOption(0));
        assertEquals(25, result.getVotesForOption(1));
        assertEquals(50.0, result.getPercentageForOption(0), 0.01);
        assertEquals(50.0, result.getPercentageForOption(1), 0.01);
    }
}
