package com.classpulse.server.classroom;

import com.classpulse.common.model.Classroom;
import com.classpulse.common.model.User;

/**
 * Result of a student attempting to join a classroom.
 */
public final class JoinResult {

    private final boolean success;
    private final Classroom classroom;
    private final User student;
    private final String errorCode;
    private final String errorMessage;

    private JoinResult(boolean success, Classroom classroom, User student, String errorCode, String errorMessage) {
        this.success = success;
        this.classroom = classroom;
        this.student = student;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }

    public static JoinResult success(Classroom classroom, User student) {
        return new JoinResult(true, classroom, student, null, null);
    }

    public static JoinResult notFound(String message) {
        return new JoinResult(false, null, null, "INVALID_CLASSROOM", message);
    }

    public static JoinResult duplicate(String message) {
        return new JoinResult(false, null, null, "DUPLICATE_STUDENT", message);
    }

    public static JoinResult invalid(String message) {
        return new JoinResult(false, null, null, "INVALID_PAYLOAD", message);
    }

    public boolean isSuccess() {
        return success;
    }

    public Classroom getClassroom() {
        return classroom;
    }

    public User getStudent() {
        return student;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
