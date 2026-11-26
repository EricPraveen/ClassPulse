package com.classpulse.server.classroom;

import com.classpulse.common.model.Classroom;
import com.classpulse.common.model.User;
import com.classpulse.common.model.UserRole;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe, in-memory classroom management service.
 * <p>
 * Handles classroom creation with unique code generation, classroom lookup,
 * and student membership management preventing duplicates.
 */
public class ClassroomManager {

    private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int CODE_LENGTH = 6;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final Map<String, Classroom> classrooms = new ConcurrentHashMap<>();

    /**
     * Creates a new classroom with an automatically generated unique code.
     *
     * @param lecturerName name of the lecturer creating the classroom
     * @return the newly created {@link Classroom}
     * @throws IllegalArgumentException if lecturerName is null or blank
     */
    public Classroom createClassroom(String lecturerName) {
        if (lecturerName == null || lecturerName.trim().isEmpty()) {
            throw new IllegalArgumentException("Lecturer name must not be null or blank");
        }
        String code = generateUniqueCode();
        return createClassroom(lecturerName.trim(), code);
    }

    /**
     * Creates a new classroom with a specified join code.
     *
     * @param lecturerName name of the lecturer creating the classroom
     * @param code         custom join code
     * @return the newly created {@link Classroom}
     * @throws IllegalArgumentException if lecturerName or code is null/blank, or code is already in use
     */
    public Classroom createClassroom(String lecturerName, String code) {
        if (lecturerName == null || lecturerName.trim().isEmpty()) {
            throw new IllegalArgumentException("Lecturer name must not be null or blank");
        }
        if (code == null || code.trim().isEmpty()) {
            throw new IllegalArgumentException("Classroom code must not be null or blank");
        }

        String normalizedCode = code.trim().toUpperCase();
        String lecturerId = "lecturer-" + UUID.randomUUID().toString().substring(0, 8);
        Classroom classroom = new Classroom(normalizedCode, lecturerId);

        Classroom existing = classrooms.putIfAbsent(normalizedCode, classroom);
        if (existing != null) {
            throw new IllegalArgumentException("Classroom code '" + normalizedCode + "' is already in use");
        }

        return classroom;
    }

    /**
     * Finds a classroom by its join code (case-insensitive).
     *
     * @param code classroom join code
     * @return optional containing the classroom if found
     */
    public Optional<Classroom> findClassroom(String code) {
        if (code == null || code.trim().isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(classrooms.get(code.trim().toUpperCase()));
    }

    /**
     * Checks whether a classroom with the given code exists.
     *
     * @param code classroom join code
     * @return true if classroom exists; false otherwise
     */
    public boolean hasClassroom(String code) {
        return findClassroom(code).isPresent();
    }

    /**
     * Attempts to register a student into an existing classroom.
     * <p>
     * Synchronizes on the target classroom to prevent race conditions during concurrent join attempts.
     *
     * @param code        classroom join code
     * @param studentName display name of the student
     * @return {@link JoinResult} indicating success with student details or failure with error code
     */
    public JoinResult joinClassroom(String code, String studentName) {
        if (code == null || code.trim().isEmpty()) {
            return JoinResult.invalid("Classroom code must not be null or blank");
        }
        if (studentName == null || studentName.trim().isEmpty()) {
            return JoinResult.invalid("Student name must not be null or blank");
        }

        String normalizedCode = code.trim().toUpperCase();
        String trimmedName = studentName.trim();

        Classroom classroom = classrooms.get(normalizedCode);
        if (classroom == null) {
            return JoinResult.notFound("Classroom '" + normalizedCode + "' does not exist");
        }

        synchronized (classroom) {
            boolean alreadyJoined = classroom.getStudents().stream()
                    .anyMatch(u -> u.getDisplayName().equalsIgnoreCase(trimmedName));

            if (alreadyJoined) {
                return JoinResult.duplicate("Student '" + trimmedName + "' has already joined classroom '" + normalizedCode + "'");
            }

            String studentId = "student-" + UUID.randomUUID().toString().substring(0, 8);
            User student = new User(studentId, trimmedName, UserRole.STUDENT);
            classroom.getStudents().add(student);

            return JoinResult.success(classroom, student);
        }
    }

    /**
     * Returns a snapshot of all students currently enrolled in the specified classroom.
     *
     * @param code classroom join code
     * @return list of students, or empty list if classroom not found
     */
    public List<User> getStudents(String code) {
        Optional<Classroom> classroom = findClassroom(code);
        if (classroom.isEmpty()) {
            return Collections.emptyList();
        }
        synchronized (classroom.get()) {
            return new ArrayList<>(classroom.get().getStudents());
        }
    }

    /**
     * Returns the total count of currently active classrooms.
     */
    public int getActiveClassroomCount() {
        return classrooms.size();
    }

    /**
     * Removes a classroom by code.
     *
     * @param code classroom join code
     * @return removed classroom or null if not found
     */
    public Classroom removeClassroom(String code) {
        if (code == null) {
            return null;
        }
        return classrooms.remove(code.trim().toUpperCase());
    }

    /**
     * Clears all active classrooms in memory.
     */
    public void clear() {
        classrooms.clear();
    }

    private String generateUniqueCode() {
        while (true) {
            StringBuilder sb = new StringBuilder(CODE_LENGTH);
            for (int i = 0; i < CODE_LENGTH; i++) {
                int index = RANDOM.nextInt(CODE_ALPHABET.length());
                sb.append(CODE_ALPHABET.charAt(index));
            }
            String code = sb.toString();
            if (!classrooms.containsKey(code)) {
                return code;
            }
        }
    }
}
