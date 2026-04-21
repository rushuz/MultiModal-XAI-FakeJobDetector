package com.example.random_major.model;

import java.util.List;

public class GroqAnalysisResult {
    private boolean isFake;
    private double scamScore;
    private String reasoning;
    private List<GroqRedFlag> redFlags;
    private long latencyMs;
    @com.fasterxml.jackson.annotation.JsonProperty("has_placement_history")
    private boolean hasPlacementHistory;

    @com.fasterxml.jackson.annotation.JsonProperty("placement_summary")
    private String placementSummary;

    public GroqAnalysisResult() {}

    public boolean isFake() { return isFake; }
    public void setFake(boolean fake) { isFake = fake; }

    public double getScamScore() { return scamScore; }
    public void setScamScore(double scamScore) { this.scamScore = scamScore; }

    public String getReasoning() { return reasoning; }
    public void setReasoning(String reasoning) { this.reasoning = reasoning; }

    public List<GroqRedFlag> getRedFlags() { return redFlags; }
    public void setRedFlags(List<GroqRedFlag> redFlags) { this.redFlags = redFlags; }

    public long getLatencyMs() { return latencyMs; }
    public void setLatencyMs(long latencyMs) { this.latencyMs = latencyMs; }

    public boolean isHasPlacementHistory() { return hasPlacementHistory; }
    public void setHasPlacementHistory(boolean hasPlacementHistory) { this.hasPlacementHistory = hasPlacementHistory; }

    public String getPlacementSummary() { return placementSummary; }
    public void setPlacementSummary(String placementSummary) { this.placementSummary = placementSummary; }

    public static class GroqRedFlag {
        private String category;
        private String description;
        private double severity;

        public GroqRedFlag() {}

        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }

        public double getSeverity() { return severity; }
        public void setSeverity(double severity) { this.severity = severity; }
    }
}
