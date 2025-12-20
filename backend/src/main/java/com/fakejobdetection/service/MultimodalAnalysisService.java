package com.fakejobdetection.service;

import com.fakejobdetection.entity.AnalysisResult;
import com.fakejobdetection.repository.AnalysisResultRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

@Service
public class MultimodalAnalysisService {

    private final TextAnalysisService textService;
    private final ImageAnalysisService imageService;
    private final AudioAnalysisService audioService;
    private final AnalysisResultRepository repository;

    public MultimodalAnalysisService(
            TextAnalysisService textService,
            ImageAnalysisService imageService,
            AudioAnalysisService audioService,
            AnalysisResultRepository repository
    ) {
        this.textService = textService;
        this.imageService = imageService;
        this.audioService = audioService;
        this.repository = repository;
    }

    public AnalysisResult analyze(String text, MultipartFile image, MultipartFile audio) {

        AnalysisResult textResult =
                (text != null && !text.isBlank()) ? textService.analyze(text) : null;

        AnalysisResult imageResult =
                (image != null && !image.isEmpty()) ? imageService.analyze(image) : null;

        AnalysisResult audioResult =
                (audio != null && !audio.isEmpty()) ? audioService.analyze(audio) : null;

        // ===== DYNAMIC FUSION =====
        Map<String, Double> scores = new HashMap<>();
        if (textResult != null) scores.put("text", textResult.getPrediction().confidence);
        if (imageResult != null) scores.put("image", imageResult.getPrediction().confidence);
        if (audioResult != null) scores.put("audio", audioResult.getPrediction().confidence);

        double finalConfidence = scores.values().stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.0);

        String finalLabel = finalConfidence >= 0.5 ? "FAKE" : "REAL";

        AnalysisResult finalResult = mergeResults(
                textResult, imageResult, audioResult, finalLabel, finalConfidence
        );

        repository.save(finalResult);
        return finalResult;
    }

    // ===== MERGE LOGIC (FusionUtil replacement) =====
    private AnalysisResult mergeResults(
            AnalysisResult text,
            AnalysisResult image,
            AnalysisResult audio,
            String label,
            double confidence
    ) {

        AnalysisResult result = new AnalysisResult();

        if (text != null) result.setInputText(text.getInputText());
        if (image != null) result.setOcrText(image.getOcrText());
        if (audio != null) result.setAudioTranscript(audio.getAudioTranscript());

        AnalysisResult.Prediction p = new AnalysisResult.Prediction();
        p.label = label;
        p.confidence = confidence;

        AnalysisResult.Explanation e = new AnalysisResult.Explanation();
        e.reasons = new ArrayList<>();
        e.weights = new HashMap<>();
        e.modalityContributions = new HashMap<>();

        if (text != null) {
            e.reasons.addAll(text.getExplanation().reasons);
            e.weights.putAll(text.getExplanation().weights);
            e.modalityContributions.put("text", text.getPrediction().confidence);
        }
        if (image != null) {
            e.reasons.addAll(image.getExplanation().reasons);
            e.weights.putAll(image.getExplanation().weights);
            e.modalityContributions.put("image", image.getPrediction().confidence);
        }
        if (audio != null) {
            e.reasons.addAll(audio.getExplanation().reasons);
            e.weights.putAll(audio.getExplanation().weights);
            e.modalityContributions.put("audio", audio.getPrediction().confidence);
        }

        result.setPrediction(p);
        result.setExplanation(e);
        return result;
    }
}
