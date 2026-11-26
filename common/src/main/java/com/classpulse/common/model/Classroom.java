package com.classpulse.common.model;

import java.util.ArrayList;
import java.util.List;

/** In-memory classroom model for the assignment prototype. */
public class Classroom {
    private String code;
    private String lecturerId;
    private List<User> students = new ArrayList<>();

    public Classroom() {
    }

    public Classroom(String code, String lecturerId) {
        this.code = code;
        this.lecturerId = lecturerId;
    }

    public String getCode() {
        return code;
    }

    public String getLecturerId() {
        return lecturerId;
    }

    public List<User> getStudents() {
        return students;
    }
}
