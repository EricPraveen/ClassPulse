package com.classpulse.common.model;

/** One student's selected option for a poll. */
public class Answer {
    private String pollId;
    private String studentId;
    private int selectedOptionIndex;

    public Answer() {
    }

    public Answer(String pollId, String studentId, int selectedOptionIndex) {
        this.pollId = pollId;
        this.studentId = studentId;
        this.selectedOptionIndex = selectedOptionIndex;
    }

    public String getPollId() {
        return pollId;
    }

    public String getStudentId() {
        return studentId;
    }

    public int getSelectedOptionIndex() {
        return selectedOptionIndex;
    }
}
