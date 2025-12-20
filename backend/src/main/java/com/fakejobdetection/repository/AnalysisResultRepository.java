package com.fakejobdetection.repository;

import com.fakejobdetection.entity.AnalysisResult;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.Instant;
import java.util.List;

public interface AnalysisResultRepository
        extends MongoRepository<AnalysisResult, String> {

    // Fetch all FAKE or REAL predictions
    List<AnalysisResult> findByPrediction_Label(String label);

    // Time-based analysis (for audits & reports)
    List<AnalysisResult> findByCreatedAtAfter(Instant time);

    // High-confidence fraud detection
    List<AnalysisResult> findByPrediction_ConfidenceGreaterThan(double confidence);
}
