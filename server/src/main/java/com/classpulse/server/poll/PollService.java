package com.classpulse.server.poll;

import com.classpulse.common.model.Poll;
import com.classpulse.server.classroom.ClassroomManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * In-memory poll management service.
 * <p>
 * Handles poll creation, classroom association, validation of questions
 * and answer options, and thread-safe poll storage.
 */
public class PollService {

    private final ClassroomManager classroomManager;
    private final Map<String, Poll> pollsById = new ConcurrentHashMap<>();
    private final Map<String, List<String>> pollIdsByClassroom = new ConcurrentHashMap<>();

    /**
     * Constructs a {@link PollService} with an optional {@link ClassroomManager} for room validation.
     *
     * @param classroomManager classroom manager instance, or null if room validation is bypassed
     */
    public PollService(ClassroomManager classroomManager) {
        this.classroomManager = classroomManager;
    }

    /**
     * Creates a new multiple-choice poll for an existing classroom.
     *
     * @param classroomCode target classroom join code
     * @param question      the poll question
     * @param options       list of answer options (minimum 2 required)
     * @return the created {@link Poll}
     * @throws IllegalArgumentException if input is invalid or classroom does not exist
     */
    public Poll createPoll(String classroomCode, String question, List<String> options) {
        if (classroomCode == null || classroomCode.trim().isEmpty()) {
            throw new IllegalArgumentException("Classroom code cannot be null or blank");
        }
        String normalizedCode = classroomCode.trim().toUpperCase();

        if (classroomManager != null && !classroomManager.hasClassroom(normalizedCode)) {
            throw new IllegalArgumentException("Classroom '" + normalizedCode + "' does not exist");
        }

        if (question == null || question.trim().isEmpty()) {
            throw new IllegalArgumentException("Poll question cannot be null or blank");
        }

        if (options == null || options.size() < 2) {
            throw new IllegalArgumentException("Poll must contain at least 2 options");
        }

        List<String> cleanedOptions = new ArrayList<>();
        for (String opt : options) {
            if (opt == null || opt.trim().isEmpty()) {
                throw new IllegalArgumentException("Poll option cannot be null or blank");
            }
            cleanedOptions.add(opt.trim());
        }

        String pollId = "poll-" + UUID.randomUUID().toString().substring(0, 8);
        Poll poll = new Poll(pollId, normalizedCode, question.trim(), cleanedOptions);

        pollsById.put(pollId, poll);
        pollIdsByClassroom.computeIfAbsent(normalizedCode, k -> new CopyOnWriteArrayList<>())
                .add(pollId);

        return poll;
    }

    /**
     * Finds a poll by its unique identifier.
     *
     * @param pollId unique poll ID
     * @return optional containing the Poll if found
     */
    public Optional<Poll> findPoll(String pollId) {
        if (pollId == null || pollId.trim().isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(pollsById.get(pollId.trim()));
    }

    /**
     * Checks whether a poll with the given ID exists.
     */
    public boolean hasPoll(String pollId) {
        return findPoll(pollId).isPresent();
    }

    /**
     * Returns all polls associated with the specified classroom.
     */
    public List<Poll> getPollsForClassroom(String classroomCode) {
        if (classroomCode == null || classroomCode.trim().isEmpty()) {
            return Collections.emptyList();
        }
        String normalized = classroomCode.trim().toUpperCase();
        List<String> ids = pollIdsByClassroom.get(normalized);
        if (ids == null) {
            return Collections.emptyList();
        }

        List<Poll> result = new ArrayList<>();
        for (String id : ids) {
            Poll p = pollsById.get(id);
            if (p != null) {
                result.add(p);
            }
        }
        return result;
    }

    /**
     * Returns total count of active polls.
     */
    public int getActivePollCount() {
        return pollsById.size();
    }

    /**
     * Clears all polls from memory.
     */
    public void clear() {
        pollsById.clear();
        pollIdsByClassroom.clear();
    }

    public ClassroomManager getClassroomManager() {
        return classroomManager;
    }
}
