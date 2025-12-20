package com.fakejobdetection.controller;

import com.fakejobdetection.dto.AnalyzeResponse;
import com.fakejobdetection.entity.AnalysisResult;
import com.fakejobdetection.service.MultimodalAnalysisService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class AnalyzeController {

    private final MultimodalAnalysisService service;

    public AnalyzeController(MultimodalAnalysisService service) {
        this.service = service;
    }

    @PostMapping(value = "/analyze", consumes = "multipart/form-data")
    public AnalyzeResponse analyze(
            @RequestParam(required = false) String text,
            @RequestParam(required = false) MultipartFile image,
            @RequestParam(required = false) MultipartFile audio
    ) {

        if ((text == null || text.isBlank()) && image == null && audio == null) {
            throw new IllegalArgumentException(
                    "At least one input (text, image, or audio) is required"
            );
        }

        AnalysisResult result = service.analyze(text, image, audio);

        return new AnalyzeResponse(
                result.getPrediction().label,
                result.getPrediction().confidence,
                result.getExplanation().reasons,
                result.getExplanation().weights,
                result.getExplanation().modalityContributions
        );
    }

    @GetMapping("/health")
    public String health() {
        return "Backend is running 🚀";
    }
}
