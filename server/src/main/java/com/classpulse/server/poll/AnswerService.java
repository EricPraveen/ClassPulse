package com.classpulse.server.poll;

import com.classpulse.common.model.Answer;
import com.classpulse.common.model.Poll;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory student answer management service.
 * <p>
 * Validates answer submissions against active polls, enforces single-submission
 * rules per student, and calculates aggregated poll results with vote percentages.
 */
public class AnswerService {

    private final PollService pollService;
    // Map: pollId -> Map<studentId, Answer>
    private final Map<String, Map<String, Answer>> answersByPoll = new ConcurrentHashMap<>();

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

        if (selectedOptionIndex < 0 || selectedOptionIndex >= poll.getOptions().size()) {
            throw new IllegalArgumentException("Selected option index " + selectedOptionIndex +
                    " is out of bounds (allowed: 0.." + (poll.getOptions().size() - 1) + ")");
        }

        Map<String, Answer> pollAnswers = answersByPoll.computeIfAbsent(normalizedPollId, k -> new ConcurrentHashMap<>());

        Answer answer = new Answer(normalizedPollId, normalizedStudentId, selectedOptionIndex);
        Answer previous = pollAnswers.putIfAbsent(normalizedStudentId, answer);
        if (previous != null) {
            throw new IllegalStateException("Student '" + normalizedStudentId +
                    "' has already submitted an answer to poll '" + normalizedPollId + "'");
        }

        return answer;
    }

    /**
     * Checks whether a student has already submitted an answer to the given poll.
     */
    public boolean hasStudentAnswered(String pollId, String studentId) {
        if (pollId == null || studentId == null) {
            return false;
        }
        Map<String, Answer> answers = answersByPoll.get(pollId.trim());
        return answers != null && answers.containsKey(studentId.trim());
    }

    /**
     * Retrieves all submitted answers for a given poll.
     */
    public List<Answer> getAnswersForPoll(String pollId) {
        if (pollId == null) {
            return Collections.emptyList();
        }
        Map<String, Answer> answers = answersByPoll.get(pollId.trim());
        if (answers == null) {
            return Collections.emptyList();
        }
        return new ArrayList<>(answers.values());
    }

    /**
     * Calculates the aggregated result for a poll.
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

        Map<String, Answer> answers = answersByPoll.get(normalizedPollId);
        int totalResponses = (answers != null) ? answers.size() : 0;

        Map<Integer, Integer> counts = new LinkedHashMap<>();
        for (int i = 0; i < poll.getOptions().size(); i++) {
            counts.put(i, 0);
        }

        if (answers != null) {
            for (Answer a : answers.values()) {
                int option = a.getSelectedOptionIndex();
                counts.put(option, counts.getOrDefault(option, 0) + 1);
            }
        }

        Map<Integer, Double> percentages = new LinkedHashMap<>();
        for (int i = 0; i < poll.getOptions().size(); i++) {
            int voteCount = counts.get(i);
            double percentage = (totalResponses > 0)
                    ? Math.round((voteCount * 100.0 / totalResponses) * 10.0) / 10.0
                    : 0.0;
            percentages.put(i, percentage);
        }

        return new PollResult(poll.getId(), poll.getQuestion(), poll.getOptions(),
                totalResponses, counts, percentages);
    }

    /**
     * Clears all recorded answers.
     */
    public void clear() {
        answersByPoll.clear();
    }
}
