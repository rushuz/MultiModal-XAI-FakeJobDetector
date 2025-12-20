package com.fakejobdetection.dto;

import java.util.List;
import java.util.Map;

public class AnalyzeResponse {

    @SuppressWarnings("FieldMayBeFinal")
    private String result;              // REAL / FAKE
    @SuppressWarnings("FieldMayBeFinal")
    private double confidence;           // Final aggregated confidence

    // Explainable AI output
    @SuppressWarnings("FieldMayBeFinal")
    private List<String> explanation;    // Human-readable reasons
    @SuppressWarnings("FieldMayBeFinal")
    private Map<String, Double> weights; // Feature contribution weights

    // Multimodal contribution scores
    @SuppressWarnings("FieldMayBeFinal")
    private Map<String, Double> modalityScores; 
    // Example: { "text": 0.72, "image": 0.18, "audio": 0.10 }

    public AnalyzeResponse(
            String result,
            double confidence,
            List<String> explanation,
            Map<String, Double> weights,
            Map<String, Double> modalityScores
    ) {
        this.result = result;
        this.confidence = confidence;
        this.explanation = explanation;
        this.weights = weights;
        this.modalityScores = modalityScores;
    }

    public String getResult() {
        return result;
    }

    public double getConfidence() {
        return confidence;
    }

    public List<String> getExplanation() {
        return explanation;
    }

    public Map<String, Double> getWeights() {
        return weights;
    }

    public Map<String, Double> getModalityScores() {
        return modalityScores;
    }
}
