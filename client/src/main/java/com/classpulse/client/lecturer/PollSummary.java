package com.classpulse.client.lecturer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Summary of a poll created during the lecturer's session.
 */
public final class PollSummary {

    private final String pollId;
    private final String question;
    private final List<String> options;

    public PollSummary(String pollId, String question, List<String> options) {
        this.pollId = Objects.requireNonNull(pollId, "pollId cannot be null");
        this.question = Objects.requireNonNull(question, "question cannot be null");
        this.options = (options != null) ? Collections.unmodifiableList(new ArrayList<>(options)) : Collections.emptyList();
    }

    public String getPollId() {
        return pollId;
    }

    public String getQuestion() {
        return question;
    }

    public List<String> getOptions() {
        return options;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PollSummary that = (PollSummary) o;
        return Objects.equals(pollId, that.pollId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(pollId);
    }

    @Override
    public String toString() {
        return pollId + " - " + question;
    }
}
