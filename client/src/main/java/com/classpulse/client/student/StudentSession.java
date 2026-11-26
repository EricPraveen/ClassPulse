package com.classpulse.client.student;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Tracks student session state such as student credentials, enrolled classroom,
 * active poll details, and answer submission status.
 */
public class StudentSession {

    private String studentName;
    private String studentId;
    private String classroomCode;

    private String activePollId;
    private String activePollQuestion;
    private List<String> activePollOptions = new ArrayList<>();
    private Integer submittedAnswerIndex;

    public synchronized String getStudentName() {
        return studentName;
    }

    public synchronized void setStudentName(String studentName) {
        this.studentName = studentName;
    }

    public synchronized String getStudentId() {
        return studentId;
    }

    public synchronized void setStudentId(String studentId) {
        this.studentId = studentId;
    }

    public synchronized String getClassroomCode() {
        return classroomCode;
    }

    public synchronized void setClassroomCode(String classroomCode) {
        this.classroomCode = classroomCode;
    }

    public synchronized boolean isEnrolled() {
        return classroomCode != null && !classroomCode.trim().isEmpty() && studentId != null;
    }

    public synchronized String getActivePollId() {
        return activePollId;
    }

    public synchronized String getActivePollQuestion() {
        return activePollQuestion;
    }

    public synchronized List<String> getActivePollOptions() {
        return Collections.unmodifiableList(new ArrayList<>(activePollOptions));
    }

    public synchronized void setActivePoll(String pollId, String question, List<String> options) {
        this.activePollId = pollId;
        this.activePollQuestion = question;
        this.activePollOptions = (options != null) ? new ArrayList<>(options) : new ArrayList<>();
        this.submittedAnswerIndex = null;
    }

    public synchronized boolean hasActivePoll() {
        return activePollId != null && !activePollId.trim().isEmpty();
    }

    public synchronized Integer getSubmittedAnswerIndex() {
        return submittedAnswerIndex;
    }

    public synchronized void setSubmittedAnswerIndex(Integer submittedAnswerIndex) {
        this.submittedAnswerIndex = submittedAnswerIndex;
    }

    public synchronized boolean hasAnswered() {
        return submittedAnswerIndex != null;
    }

    public synchronized void clear() {
        studentName = null;
        studentId = null;
        classroomCode = null;
        activePollId = null;
        activePollQuestion = null;
        activePollOptions.clear();
        submittedAnswerIndex = null;
    }
}
