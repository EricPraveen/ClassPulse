package com.classpulse.server.poll;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable representation of calculated poll results, including
 * vote counts and percentages for each option.
 */
public final class PollResult {

    private final String pollId;
    private final String question;
    private final List<String> options;
    private final int totalResponses;
    private final Map<Integer, Integer> voteCounts;
    private final Map<Integer, Double> votePercentages;

    public PollResult(String pollId, String question, List<String> options,
                      int totalResponses, Map<Integer, Integer> voteCounts,
                      Map<Integer, Double> votePercentages) {
        this.pollId = Objects.requireNonNull(pollId, "pollId cannot be null");
        this.question = Objects.requireNonNull(question, "question cannot be null");
        this.options = (options != null) ? Collections.unmodifiableList(new ArrayList<>(options)) : Collections.emptyList();
        this.totalResponses = totalResponses;
        this.voteCounts = (voteCounts != null) ? Collections.unmodifiableMap(new LinkedHashMap<>(voteCounts)) : Collections.emptyMap();
        this.votePercentages = (votePercentages != null) ? Collections.unmodifiableMap(new LinkedHashMap<>(votePercentages)) : Collections.emptyMap();
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

    public int getTotalResponses() {
        return totalResponses;
    }

    public Map<Integer, Integer> getVoteCounts() {
        return voteCounts;
    }

    public int getVotesForOption(int optionIndex) {
        return voteCounts.getOrDefault(optionIndex, 0);
    }

    public Map<Integer, Double> getVotePercentages() {
        return votePercentages;
    }

    public double getPercentageForOption(int optionIndex) {
        return votePercentages.getOrDefault(optionIndex, 0.0);
    }

    /**
     * Converts the poll result into a protocol-compliant payload map.
     */
    public Map<String, Object> toPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("pollId", pollId);
        payload.put("question", question);
        payload.put("totalResponses", totalResponses);
        payload.put("options", options);

        Map<String, Integer> optionResults = new LinkedHashMap<>();
        Map<String, Double> optionPercentages = new LinkedHashMap<>();

        for (int i = 0; i < options.size(); i++) {
            optionResults.put(String.valueOf(i), getVotesForOption(i));
            optionPercentages.put(String.valueOf(i), getPercentageForOption(i));
        }

        payload.put("optionResults", optionResults);
        payload.put("optionPercentages", optionPercentages);
        return payload;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PollResult that = (PollResult) o;
        return totalResponses == that.totalResponses &&
                Objects.equals(pollId, that.pollId) &&
                Objects.equals(question, that.question) &&
                Objects.equals(options, that.options) &&
                Objects.equals(voteCounts, that.voteCounts) &&
                Objects.equals(votePercentages, that.votePercentages);
    }

    @Override
    public int hashCode() {
        return Objects.hash(pollId, question, options, totalResponses, voteCounts, votePercentages);
    }

    @Override
    public String toString() {
        return "PollResult{" +
                "pollId='" + pollId + '\'' +
                ", question='" + question + '\'' +
                ", totalResponses=" + totalResponses +
                ", voteCounts=" + voteCounts +
                ", votePercentages=" + votePercentages +
                '}';
    }
}
