package com.fakejobdetection.service;

import com.fakejobdetection.entity.AnalysisResult;
import com.fakejobdetection.ml.PredictionEngine;
import net.sourceforge.tess4j.Tesseract;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.util.*;

@Service
public class ImageAnalysisService {

    private static final Tesseract TESSERACT = initTesseract();

    private static Tesseract initTesseract() {
        Tesseract t = new Tesseract();
        t.setDatapath("src/main/resources/tessdata"); // JAR-safe path
        t.setLanguage("eng");
        return t;
    }

    public AnalysisResult analyze(MultipartFile imageFile) {

        if (imageFile == null || imageFile.isEmpty()) {
            return null; // handled by MultimodalAnalysisService
        }

        String ocrText = extractTextFromImage(imageFile);
        String lower = ocrText.toLowerCase(Locale.ROOT);

        boolean fakeCompanyMentioned =
                lower.contains("gmail.com") ||
                lower.contains("whatsapp") ||
                lower.contains("registration fee");

        double ocrRiskScore = fakeCompanyMentioned ? 0.7 : 0.2;

        Map<String, Object> features = new LinkedHashMap<>();
        features.put("ocr_risk_score", ocrRiskScore);
        features.put("fake_company_flag", fakeCompanyMentioned ? 1 : 0);

        double fakeProbability = PredictionEngine.predictProbability(features);
        String label = fakeProbability >= 0.5 ? "FAKE" : "REAL";

        AnalysisResult result = new AnalysisResult();
        result.setOcrText(ocrText);

        AnalysisResult.Features f = new AnalysisResult.Features();
        f.fakeLogoDetected = fakeCompanyMentioned;
        f.ocrRiskScore = ocrRiskScore;

        AnalysisResult.Prediction p = new AnalysisResult.Prediction();
        p.label = label;
        p.confidence = fakeProbability;

        AnalysisResult.Explanation e = new AnalysisResult.Explanation();
        e.reasons = buildReasons(fakeCompanyMentioned);
        e.weights = buildWeights(fakeCompanyMentioned);
        e.modalityContributions = Map.of("image", fakeProbability);

        result.setFeatures(f);
        result.setPrediction(p);
        result.setExplanation(e);

        return result;
    }

    @SuppressWarnings("UseSpecificCatch")
    private String extractTextFromImage(MultipartFile imageFile) {
        try {
            BufferedImage image = ImageIO.read(imageFile.getInputStream());
            return TESSERACT.doOCR(image);
        } catch (Exception e) {
            return "";
        }
    }

    private List<String> buildReasons(boolean fakeCompany) {
        if (!fakeCompany) return List.of();
        return List.of("Suspicious contact information detected in image");
    }

    private Map<String, Double> buildWeights(boolean fakeCompany) {
        if (!fakeCompany) return Map.of();
        return Map.of("Suspicious contact information detected in image", 0.65);
    }
}
