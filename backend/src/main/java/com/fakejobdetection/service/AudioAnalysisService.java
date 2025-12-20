package com.fakejobdetection.service;

import com.fakejobdetection.entity.AnalysisResult;
import com.fakejobdetection.ml.PredictionEngine;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

@Service
public class AudioAnalysisService {

    private static final String VOSK_API_URL = "http://localhost:6000/transcribe";

    public AnalysisResult analyze(MultipartFile audioFile) {

        if (audioFile == null || audioFile.isEmpty()) {
            return null;
        }

        // ===============================
        // SPEECH → TEXT (Python Vosk)
        // ===============================
        String transcript = speechToText(audioFile);
        String lower = transcript.toLowerCase(Locale.ROOT);

        boolean urgencyDetected = lower.contains("urgent") || lower.contains("immediately");
        boolean paymentMentioned = lower.contains("fee") || lower.contains("payment");

        double audioRiskScore =
                (urgencyDetected ? 0.4 : 0.0) +
                (paymentMentioned ? 0.5 : 0.0);

        Map<String, Object> features = new LinkedHashMap<>();
        features.put("audio_risk_score", audioRiskScore);
        features.put("urgency_voice_flag", urgencyDetected ? 1 : 0);
        features.put("payment_voice_flag", paymentMentioned ? 1 : 0);

        double fakeProbability = PredictionEngine.predictProbability(features);
        String label = fakeProbability >= 0.5 ? "FAKE" : "REAL";

        AnalysisResult result = new AnalysisResult();
        result.setAudioTranscript(transcript);

        AnalysisResult.Features f = new AnalysisResult.Features();
        f.voiceUrgencyDetected = urgencyDetected;
        f.audioRiskScore = audioRiskScore;

        AnalysisResult.Prediction p = new AnalysisResult.Prediction();
        p.label = label;
        p.confidence = fakeProbability;

        AnalysisResult.Explanation e = new AnalysisResult.Explanation();
        e.reasons = buildReasons(urgencyDetected, paymentMentioned);
        e.weights = buildWeights(urgencyDetected, paymentMentioned);
        e.modalityContributions = Map.of("audio", fakeProbability);

        result.setFeatures(f);
        result.setPrediction(p);
        result.setExplanation(e);

        return result;
    }

    @SuppressWarnings("UseSpecificCatch")
    private String speechToText(MultipartFile audioFile) {
        try {
            RestTemplate restTemplate = new RestTemplate();

            Map<String, Object> request = new HashMap<>();
            request.put("audio", audioFile.getBytes());

            Map response = restTemplate.postForObject(
                    VOSK_API_URL,
                    request,
                    Map.class
            );

            return response != null ? (String) response.get("text") : "";
        } catch (Exception e) {
            return "";
        }
    }

    private List<String> buildReasons(boolean urgency, boolean payment) {
        List<String> reasons = new ArrayList<>();
        if (urgency) reasons.add("Urgent tone detected in recruiter call");
        if (payment) reasons.add("Payment request mentioned in call");
        return reasons;
    }

    private Map<String, Double> buildWeights(boolean urgency, boolean payment) {
        Map<String, Double> w = new LinkedHashMap<>();
        if (urgency) w.put("Urgent tone detected in recruiter call", 0.45);
        if (payment) w.put("Payment request mentioned in call", 0.55);
        return w;
    }
}
