package com.fakejobdetection.service;

import com.fakejobdetection.entity.AnalysisResult;
import com.fakejobdetection.ml.PredictionEngine;
import com.fakejobdetection.util.OpenNLPProcessor;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Service
public class TextAnalysisService {

    private static final RestTemplate REST_TEMPLATE = new RestTemplate();

    public AnalysisResult analyze(String text) {

        if (text == null || text.isBlank()) {
            return null;
        }

        String normalized = text.toLowerCase(Locale.ROOT);

        // ===============================
        // 1. NLP FEATURE EXTRACTION
        // ===============================

        List<String> tokens = OpenNLPProcessor.tokenize(normalized);
        int sentenceCount = OpenNLPProcessor.sentenceCount(text);

        double keywordScore = tokens.stream()
                .filter(t -> t.equals("urgent") || t.equals("interview"))
                .count() * 0.3;

        int urgentFlag = tokens.contains("urgent") ? 1 : 0;
        int noInterviewFlag = normalized.contains("no interview") ? 1 : 0;

        Map<String, Object> features = new LinkedHashMap<>();
        features.put("keyword_score", keywordScore);
        features.put("sentence_count", sentenceCount);
        features.put("text_length", text.length());
        features.put("urgent_flag", urgentFlag);
        features.put("no_interview_flag", noInterviewFlag);

        // ===============================
        // 2. ML PREDICTION
        // ===============================

        double fakeProbability = PredictionEngine.predictProbability(features);
        String label = fakeProbability >= 0.5 ? "FAKE" : "REAL";

        // ===============================
        // 3. LIME EXPLANATION
        // ===============================

        Map<String, Double> limeWeights =
                Optional.ofNullable(getLimeExplanation(features, fakeProbability))
                        .orElse(Collections.emptyMap());

        // ===============================
        // 4. BUILD RESULT
        // ===============================

        AnalysisResult result = new AnalysisResult();
        result.setInputText(text);

        AnalysisResult.Features f = new AnalysisResult.Features();
        f.salaryAnomaly = fakeProbability >= 0.5;
        f.keywordScore = keywordScore;

        AnalysisResult.Prediction p = new AnalysisResult.Prediction();
        p.label = label;
        p.confidence = fakeProbability;

        AnalysisResult.Explanation e = new AnalysisResult.Explanation();
        e.reasons = new ArrayList<>();
        e.weights = new LinkedHashMap<>();
        e.modalityContributions = Map.of("text", fakeProbability);

        for (Map.Entry<String, Double> entry : limeWeights.entrySet()) {
            String cleanKey = normalizeLimeKey(entry.getKey());
            e.reasons.add(cleanKey);
            e.weights.put(cleanKey, entry.getValue());
        }

        result.setFeatures(f);
        result.setPrediction(p);
        result.setExplanation(e);

        return result;
    }

    private String normalizeLimeKey(String key) {
        if (key.contains("urgent_flag")) return "Urgent language detected";
        if (key.contains("no_interview_flag")) return "No interview mentioned";
        if (key.contains("keyword_score")) return "Suspicious keywords detected";
        if (key.contains("sentence_count")) return "Unusually short description";
        if (key.contains("text_length")) return "Very short job description";
        return key;
    }

    @SuppressWarnings("UseSpecificCatch")
    private Map<String, Double> getLimeExplanation(
            Map<String, Object> features,
            double fakeProbability) {

        try {
            Map<String, Object> request = new HashMap<>();
            request.put("features", new Object[]{
                    features.get("keyword_score"),
                    features.get("sentence_count"),
                    features.get("text_length"),
                    features.get("urgent_flag"),
                    features.get("no_interview_flag")
            });
            request.put("fake_probability", fakeProbability);

            return REST_TEMPLATE.postForObject(
                    "http://localhost:5000/explain",
                    request,
                    Map.class
            );
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }
}
