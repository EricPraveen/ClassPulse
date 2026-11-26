package com.classpulse.client.lecturer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Tracks local lecturer session state such as active classroom code,
 * lecturer identification, and created polls.
 */
public class LecturerSession {

    private String lecturerName;
    private String lecturerId;
    private String classroomCode;
    private final List<PollSummary> createdPolls = new ArrayList<>();

    public synchronized String getLecturerName() {
        return lecturerName;
    }

    public synchronized void setLecturerName(String lecturerName) {
        this.lecturerName = lecturerName;
    }

    public synchronized String getLecturerId() {
        return lecturerId;
    }

    public synchronized void setLecturerId(String lecturerId) {
        this.lecturerId = lecturerId;
    }

    public synchronized String getClassroomCode() {
        return classroomCode;
    }

    public synchronized void setClassroomCode(String classroomCode) {
        this.classroomCode = classroomCode;
    }

    public synchronized boolean hasActiveClassroom() {
        return classroomCode != null && !classroomCode.trim().isEmpty();
    }

    public synchronized void addPoll(PollSummary poll) {
        if (poll != null) {
            createdPolls.add(poll);
        }
    }

    public synchronized List<PollSummary> getCreatedPolls() {
        return Collections.unmodifiableList(new ArrayList<>(createdPolls));
    }

    public synchronized void clear() {
        lecturerName = null;
        lecturerId = null;
        classroomCode = null;
        createdPolls.clear();
    }
}
