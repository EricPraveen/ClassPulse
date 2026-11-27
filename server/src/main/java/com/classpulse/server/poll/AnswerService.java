package com.classpulse.server.poll;

import com.classpulse.common.model.Answer;
import com.classpulse.common.model.Poll;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe in-memory student answer management service.
 * <p>
 * Validates answer submissions against active polls, delegates per-poll concurrency
 * coordination to {@link PollResponseState}, enforces single-submission rules per
 * student, and produces consistent aggregated {@link PollResult} snapshots.
 */
public class AnswerService {

    private final PollService pollService;
    private final Map<String, PollResponseState> pollStates = new ConcurrentHashMap<>();

    public AnswerService(PollService pollService) {
        this.pollService = Objects.requireNonNull(pollService, "pollService cannot be null");
    }

    /**
     * Submits an answer to an active poll.
     *
     * @param pollId              target poll ID
     * @param studentId           unique student identifier
     * @param selectedOptionIndex 0-based option index
     * @return the recorded {@link Answer}
     * @throws IllegalArgumentException if poll doesn't exist or option index is invalid
     * @throws IllegalStateException    if student has already submitted an answer to this poll
     */
    public Answer submitAnswer(String pollId, String studentId, int selectedOptionIndex) {
        if (pollId == null || pollId.trim().isEmpty()) {
            throw new IllegalArgumentException("Poll ID cannot be null or blank");
        }
        if (studentId == null || studentId.trim().isEmpty()) {
            throw new IllegalArgumentException("Student ID cannot be null or blank");
        }

        String normalizedPollId = pollId.trim();
        String normalizedStudentId = studentId.trim();

        Poll poll = pollService.findPoll(normalizedPollId)
                .orElseThrow(() -> new IllegalArgumentException("Poll '" + normalizedPollId + "' does not exist"));

        PollResponseState state = pollStates.computeIfAbsent(normalizedPollId, k -> new PollResponseState(poll));
        return state.recordAnswer(normalizedStudentId, selectedOptionIndex);
    }

    /**
     * Checks whether a student has already submitted an answer to the given poll.
     */
    public boolean hasStudentAnswered(String pollId, String studentId) {
        if (pollId == null || studentId == null) {
            return false;
        }
        PollResponseState state = pollStates.get(pollId.trim());
        return state != null && state.hasStudentAnswered(studentId.trim());
    }

    /**
     * Retrieves a snapshot of all submitted answers for a given poll.
     */
    public List<Answer> getAnswersForPoll(String pollId) {
        if (pollId == null) {
            return Collections.emptyList();
        }
        PollResponseState state = pollStates.get(pollId.trim());
        return (state != null) ? state.getAnswers() : Collections.emptyList();
    }

    /**
     * Calculates a consistent aggregated result snapshot for a poll.
     *
     * @param pollId target poll ID
     * @return calculated {@link PollResult}
     * @throws IllegalArgumentException if the poll does not exist
     */
    public PollResult calculateResults(String pollId) {
        if (pollId == null || pollId.trim().isEmpty()) {
            throw new IllegalArgumentException("Poll ID cannot be null or blank");
        }
        String normalizedPollId = pollId.trim();

        Poll poll = pollService.findPoll(normalizedPollId)
                .orElseThrow(() -> new IllegalArgumentException("Poll '" + normalizedPollId + "' does not exist"));

        PollResponseState state = pollStates.get(normalizedPollId);
        if (state != null) {
            return state.snapshotResult();
        }

        Map<Integer, Integer> counts = new LinkedHashMap<>();
        Map<Integer, Double> percentages = new LinkedHashMap<>();
        for (int i = 0; i < poll.getOptions().size(); i++) {
            counts.put(i, 0);
            percentages.put(i, 0.0);
        }
        return new PollResult(poll.getId(), poll.getQuestion(), poll.getOptions(), 0, counts, percentages);
    }

    /**
     * Clears all recorded answers.
     */
    public void clear() {
        pollStates.clear();
    }
}
