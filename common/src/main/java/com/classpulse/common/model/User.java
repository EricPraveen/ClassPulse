package com.classpulse.common.model;

/** Represents a participant in a classroom session. */
public class User {
    private String id;
    private String displayName;
    private UserRole role;

    public User() {
    }

    public User(String id, String displayName, UserRole role) {
        this.id = id;
        this.displayName = displayName;
        this.role = role;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public UserRole getRole() {
        return role;
    }
}
