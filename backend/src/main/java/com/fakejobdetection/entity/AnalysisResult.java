package com.fakejobdetection.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Document(collection = "analysis_results")
public class AnalysisResult {

    @Id
    private String id;

    // ===== INPUTS (Multimodal) =====
    private String inputText;          // Raw job description
    private String ocrText;            // Extracted via Tess4J
    private String audioTranscript;    // Generated via Vosk

    // ===== FEATURE SET =====
    private Features features;

    // ===== MODEL OUTPUT =====
    private Prediction prediction;

    // ===== XAI OUTPUT =====
    private Explanation explanation;

    private final Instant createdAt = Instant.now();

    // ===== GETTERS & SETTERS =====

    public String getId() {
        return id;
    }

    public String getInputText() {
        return inputText;
    }

    public void setInputText(String inputText) {
        this.inputText = inputText;
    }

    public String getOcrText() {
        return ocrText;
    }

    public void setOcrText(String ocrText) {
        this.ocrText = ocrText;
    }

    public String getAudioTranscript() {
        return audioTranscript;
    }

    public void setAudioTranscript(String audioTranscript) {
        this.audioTranscript = audioTranscript;
    }

    public Features getFeatures() {
        return features;
    }

    public void setFeatures(Features features) {
        this.features = features;
    }

    public Prediction getPrediction() {
        return prediction;
    }

    public void setPrediction(Prediction prediction) {
        this.prediction = prediction;
    }

    public Explanation getExplanation() {
        return explanation;
    }

    public void setExplanation(Explanation explanation) {
        this.explanation = explanation;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    // ===== INNER CLASSES =====

    /**
     * Multimodal feature representation
     */
    public static class Features {
        // Text features
        public boolean salaryAnomaly;
        public double keywordScore;

        // Image features
        public boolean fakeLogoDetected;
        public double ocrRiskScore;

        // Audio features
        public boolean voiceUrgencyDetected;
        public double audioRiskScore;
    }

    /**
     * JPMML / ML prediction output
     */
    public static class Prediction {
        public String label;        // REAL / FAKE
        public double confidence;   // Final fused confidence
    }

    /**
     * XAI Explanation (LIME-compatible)
     */
    public static class Explanation {
        public List<String> reasons;                     
        public Map<String, Double> weights;             
        public Map<String, Double> modalityContributions;
    }
}
