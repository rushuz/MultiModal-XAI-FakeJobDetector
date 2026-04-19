package com.example.random_major.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.example.random_major.model.CompanyVerificationResponse;
import com.example.random_major.model.DomainValidationResponse;

/**
 * PredictionService: Applies post-processing logic to PMML model predictions
 * 
 * STRICT REQUIREMENT: DO NOT MODIFY MODEL INPUT OR RETRAIN
 * Only applies adjustment factors after model prediction
 * 
 * Post-Processing Logic:
 * Case 1: Company NOT FOUND
 *   Adjustment = +0.4 * (1 - modelScore)
 * 
 * Case 2: Company INACTIVE
 *   Adjustment = +0.25 * (1 - modelScore)
 * 
 * Case 3: Company ACTIVE + domain mismatch
 *   Adjustment = +0.2 * (1 - modelScore)
 * 
 * Case 4: Company ACTIVE + domain match
 *   Adjustment = -0.1 * modelScore
 * 
 * Case 5: UNKNOWN status (API failure)
 *   Adjustment = 0 (no modification)
 */
@Service
public class PredictionService {

    private static final Logger log = LoggerFactory.getLogger(PredictionService.class);

    /**
     * Applies post-processing logic to model score based on company verification
     * and domain validation results
     * 
     * @param baseModelScore Original PMML model prediction score (0-1)
     * @param companyVerification Company verification response
     * @param domainValidation Domain validation response
     * @return PostProcessingResult with adjusted score and explanation
     */
    public PostProcessingResult applyPostProcessing(
        double baseModelScore,
        CompanyVerificationResponse companyVerification,
        DomainValidationResponse domainValidation
    ) {
        try {
            // Validate input score
            if (baseModelScore < 0.0 || baseModelScore > 1.0) {
                log.warn("Model score out of range [0,1]: {}, clamping...", baseModelScore);
                baseModelScore = Math.max(0.0, Math.min(1.0, baseModelScore));
            }

            double adjustedScore = baseModelScore;
            double adjustmentFactor = 0.0;
            String adjustmentReason = "";

            // ──────────────────────────────────────────────────────
            // CASE 1: Company NOT FOUND
            // ──────────────────────────────────────────────────────
            if (companyVerification != null && !companyVerification.isExists()) {
                adjustmentFactor = 0.4 * (1 - baseModelScore);
                adjustedScore = baseModelScore + adjustmentFactor;
                adjustmentReason = "Company not found in corporate database - INCREASED risk";
                
                log.info("📊 POST-PROCESSING (Case 1 - Company Not Found):");
                log.info("   Base Score: {}", baseModelScore);
                log.info("   Adjustment: +{} (0.4 × (1 - {}))", adjustmentFactor, baseModelScore);
                log.info("   Adjusted Score: {}", adjustedScore);
            }
            // ──────────────────────────────────────────────────────
            // CASE 2: Company INACTIVE
            // ──────────────────────────────────────────────────────
            else if (companyVerification != null && 
                    "INACTIVE".equalsIgnoreCase(companyVerification.getStatus())) {
                adjustmentFactor = 0.25 * (1 - baseModelScore);
                adjustedScore = baseModelScore + adjustmentFactor;
                adjustmentReason = "Company is inactive - MODERATE increase in risk";
                
                log.info("📊 POST-PROCESSING (Case 2 - Company Inactive):");
                log.info("   Base Score: {}", baseModelScore);
                log.info("   Adjustment: +{} (0.25 × (1 - {}))", adjustmentFactor, baseModelScore);
                log.info("   Adjusted Score: {}", adjustedScore);
            }
            // ──────────────────────────────────────────────────────
            // CASE 3: Company ACTIVE + Domain Mismatch
            // ──────────────────────────────────────────────────────
            else if (companyVerification != null && 
                    "ACTIVE".equalsIgnoreCase(companyVerification.getStatus()) &&
                    domainValidation != null && !domainValidation.isMatch()) {
                adjustmentFactor = 0.2 * (1 - baseModelScore);
                adjustedScore = baseModelScore + adjustmentFactor;
                adjustmentReason = "Company active but domain mismatches - Domain spoofing suspected";
                
                log.info("📊 POST-PROCESSING (Case 3 - Active Company + Domain Mismatch):");
                log.info("   Base Score: {}", baseModelScore);
                log.info("   Adjustment: +{} (0.2 × (1 - {}))", adjustmentFactor, baseModelScore);
                log.info("   Adjusted Score: {}", adjustedScore);
            }
            // ──────────────────────────────────────────────────────
            // CASE 4: Company ACTIVE + Domain Match
            // ──────────────────────────────────────────────────────
            else if (companyVerification != null && 
                    "ACTIVE".equalsIgnoreCase(companyVerification.getStatus()) &&
                    domainValidation != null && domainValidation.isMatch()) {
                adjustmentFactor = -0.1 * baseModelScore;
                adjustedScore = baseModelScore + adjustmentFactor;
                adjustmentReason = "Company verified active and domain matches - DECREASED risk";
                
                log.info("📊 POST-PROCESSING (Case 4 - Active Company + Matching Domain):");
                log.info("   Base Score: {}", baseModelScore);
                log.info("   Adjustment: {} (-0.1 × {})", adjustmentFactor, baseModelScore);
                log.info("   Adjusted Score: {}", adjustedScore);
            }
            // ──────────────────────────────────────────────────────
            // CASE 5: UNKNOWN status (API failure or incomplete data)
            // ──────────────────────────────────────────────────────
            else if (companyVerification == null || 
                    "UNKNOWN".equalsIgnoreCase(companyVerification.getStatus())) {
                adjustmentFactor = 0.0;
                adjustedScore = baseModelScore;
                adjustmentReason = "Company status unknown - No adjustment applied";
                
                log.warn("⚠️  POST-PROCESSING (Case 5 - Unknown Status):");
                log.warn("   Base Score: {}", baseModelScore);
                log.warn("   Adjustment: {} (no modification)", adjustmentFactor);
                log.warn("   Adjusted Score: {}", adjustedScore);
            }

            // ──────────────────────────────────────────────────────
            // CLAMP FINAL SCORE TO [0, 1]
            // ──────────────────────────────────────────────────────
            adjustedScore = Math.max(0.0, Math.min(1.0, adjustedScore));

            log.info("✅ FINAL adjusted score (clamped): {}", adjustedScore);

            return new PostProcessingResult(
                baseModelScore,
                adjustedScore,
                adjustmentFactor,
                adjustmentReason,
                "External validation (company + domain) influenced final prediction"
            );

        } catch (Exception e) {
            log.error("Error in post-processing: {}", e.getMessage(), e);
            // Return original score if post-processing fails
            return new PostProcessingResult(
                baseModelScore,
                baseModelScore,
                0.0,
                "Post-processing error - fallback to original score",
                "Post-processing failed"
            );
        }
    }

    /**
     * Combines the structural (PMML + Post-processed) score with the semantic (Groq LLM) score.
     * Use a weighted average ensemble where the LLM is given higher weight for semantic patterns.
     *
     * @param structuralScore Score from PMML and structural adjustments (0-1)
     * @param semanticScore Score from Groq LLM semantic analysis (0-1)
     * @return Final unified risk score (0-1)
     */
    public double ensembleScores(double structuralScore, double semanticScore) {
        // High-confidence semantic weighting (Groq is better at catching "fake" language)
        double structuralWeight = 0.4;
        double semanticWeight = 0.6;

        double finalScore = (structuralScore * structuralWeight) + (semanticScore * semanticWeight);

        // Agreement Boost: If both scores are high, boost the final result
        if (structuralScore > 0.7 && semanticScore > 0.7) {
            finalScore = Math.min(1.0, finalScore + 0.1);
            log.info("🚀 ENSEMBLE BOOST: Both models agree on HIGH RISK (+0.1)");
        }
        // Agreement Reduction: If both scores are very low, reduce the final result
        else if (structuralScore < 0.2 && semanticScore < 0.2) {
            finalScore = Math.max(0.0, finalScore - 0.05);
            log.info("🛡️ ENSEMBLE TRUST: Both models agree on LOW RISK (-0.05)");
        }

        log.info("📈 Final Ensemble Score: {} (Structural: {}, Semantic: {})", 
                String.format("%.4f", finalScore), 
                String.format("%.4f", structuralScore), 
                String.format("%.4f", semanticScore));

        return finalScore;
    }

    /**
     * Inner class for post-processing results
     */
    public static class PostProcessingResult {
        public double baseScore;
        public double adjustedScore;
        public double adjustmentFactor;
        public String adjustmentReason;
        public String externalValidationNote;

        public PostProcessingResult(
            double baseScore,
            double adjustedScore,
            double adjustmentFactor,
            String adjustmentReason,
            String externalValidationNote
        ) {
            this.baseScore = baseScore;
            this.adjustedScore = adjustedScore;
            this.adjustmentFactor = adjustmentFactor;
            this.adjustmentReason = adjustmentReason;
            this.externalValidationNote = externalValidationNote;
        }

        public double getBaseScore() {
            return baseScore;
        }

        public double getAdjustedScore() {
            return adjustedScore;
        }

        public double getAdjustmentFactor() {
            return adjustmentFactor;
        }

        public String getAdjustmentReason() {
            return adjustmentReason;
        }

        public String getExternalValidationNote() {
            return externalValidationNote;
        }
    }
}
