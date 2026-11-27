package com.classpulse.server.poll;

import com.classpulse.common.model.Answer;
import com.classpulse.common.model.Poll;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Thread-safe per-poll response holder that coordinates concurrent student answer
 * submissions and consistent result snapshots.
 * <p>
 * Combines {@link ConcurrentHashMap} for single-submission enforcement per student,
 * {@link AtomicInteger} / {@link AtomicIntegerArray} counters for vote tallies, and a
 * per-poll {@link ReentrantReadWriteLock} so multiple readers obtain mutually consistent
 * vote counts, totals, and percentages while writers update state atomically.
 */
public class PollResponseState {

    private final Poll poll;
    private final ConcurrentHashMap<String, Answer> answersByStudent = new ConcurrentHashMap<>();
    private final AtomicIntegerArray optionCounts;
    private final AtomicInteger totalResponses = new AtomicInteger(0);
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    public PollResponseState(Poll poll) {
        this.poll = Objects.requireNonNull(poll, "poll cannot be null");
        this.optionCounts = new AtomicIntegerArray(poll.getOptions().size());
    }

    /**
     * Records a student's answer atomically if they have not already voted on this poll.
     *
     * @param studentId           normalized non-blank student identifier
     * @param selectedOptionIndex 0-based option index
     * @return the recorded {@link Answer}
     * @throws IllegalArgumentException if {@code selectedOptionIndex} is out of bounds
     * @throws IllegalStateException    if the student has already submitted an answer
     */
    public Answer recordAnswer(String studentId, int selectedOptionIndex) {
        if (selectedOptionIndex < 0 || selectedOptionIndex >= poll.getOptions().size()) {
            throw new IllegalArgumentException("Selected option index " + selectedOptionIndex +
                    " is out of bounds (allowed: 0.." + (poll.getOptions().size() - 1) + ")");
        }

        lock.writeLock().lock();
        try {
            Answer answer = new Answer(poll.getId(), studentId, selectedOptionIndex);
            Answer previous = answersByStudent.putIfAbsent(studentId, answer);
            if (previous != null) {
                throw new IllegalStateException("Student '" + studentId +
                        "' has already submitted an answer to poll '" + poll.getId() + "'");
            }
            optionCounts.incrementAndGet(selectedOptionIndex);
            totalResponses.incrementAndGet();
            return answer;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Checks whether the given student has already recorded an answer for this poll.
     */
    public boolean hasStudentAnswered(String studentId) {
        return studentId != null && answersByStudent.containsKey(studentId);
    }

    /**
     * Returns a snapshot list of all recorded answers for this poll.
     */
    public List<Answer> getAnswers() {
        lock.readLock().lock();
        try {
            return new ArrayList<>(answersByStudent.values());
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Produces a consistent {@link PollResult} snapshot of vote counts, total responses,
     * and rounded percentages across all options.
     */
    public PollResult snapshotResult() {
        lock.readLock().lock();
        try {
            int optionCount = poll.getOptions().size();
            int total = totalResponses.get();

            Map<Integer, Integer> counts = new LinkedHashMap<>();
            Map<Integer, Double> percentages = new LinkedHashMap<>();

            for (int i = 0; i < optionCount; i++) {
                int voteCount = optionCounts.get(i);
                counts.put(i, voteCount);
                double percentage = (total > 0)
                        ? Math.round((voteCount * 100.0 / total) * 10.0) / 10.0
                        : 0.0;
                percentages.put(i, percentage);
            }

            return new PollResult(
                    poll.getId(),
                    poll.getQuestion(),
                    poll.getOptions(),
                    total,
                    counts,
                    percentages
            );
        } finally {
            lock.readLock().unlock();
        }
    }
}
