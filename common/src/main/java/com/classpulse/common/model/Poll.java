package com.classpulse.common.model;

import java.util.ArrayList;
import java.util.List;

/** Multiple-choice poll model. */
public class Poll {
    private String id;
    private String classroomCode;
    private String question;
    private List<String> options = new ArrayList<>();

    public Poll() {
    }

    public Poll(String id, String classroomCode, String question, List<String> options) {
        this.id = id;
        this.classroomCode = classroomCode;
        this.question = question;
        this.options = new ArrayList<>(options);
    }

    public String getId() {
        return id;
    }

    public String getClassroomCode() {
        return classroomCode;
    }

    public String getQuestion() {
        return question;
    }

    public List<String> getOptions() {
        return options;
    }
}
