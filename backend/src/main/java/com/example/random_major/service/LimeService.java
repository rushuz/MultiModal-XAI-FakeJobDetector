package com.example.random_major.service;

import com.example.random_major.model.GroqAnalysisResult;
import com.example.random_major.model.LimeExplanation;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * LimeService
 * ===========
 * Calls the Python LIME microservice to generate word-importance explanations
 * for job description text. Results are cached by Spring's Caffeine cache.
 *
 * If the Python service is unavailable, returns an empty list (non-fatal).
 */
@Service
public class LimeService {

    private static final Logger log = LoggerFactory.getLogger(LimeService.class);
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private static final int MAX_NUM_FEATURES = 30;

    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${lime.service.url:http://localhost:5001}")
    private String limeServiceUrl;

    @Value("${lime.num-features:10}")
    private int defaultNumFeatures;

    @Value("${lime.output-format:json}")
    private String defaultOutputFormat;

    @Value("${lime.timeout-ms:10000}")
    private int timeoutMs;

    public LimeService() {
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(5, TimeUnit.SECONDS)
                .build();
    }

    // ─────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────

    /**
     * Get LIME explanations using the default number of features.
     * Result is Spring-cached by text hash + numFeatures.
     */
    @Cacheable(value = "limeCache", key = "#root.target.cacheKey(#text, #root.target.defaultNumFeatures)")
    public LimeResult explain(String text) {
        return explain(text, defaultNumFeatures, defaultOutputFormat, null);
    }

    /**
     * Get LIME explanations with configurable depth and format.
     */
    @Cacheable(value = "limeCache", key = "#root.target.cacheKey(#text, #numFeatures)")
    public LimeResult explain(String text, int numFeatures, String outputFormat, String jobId) {
        int safeNumFeatures = Math.min(Math.max(numFeatures, 1), MAX_NUM_FEATURES);
        long t = System.currentTimeMillis();

        try {
            Map<String, Object> requestBody = new LinkedHashMap<>();
            requestBody.put("text", text);
            requestBody.put("num_features", safeNumFeatures);
            requestBody.put("output_format", outputFormat != null ? outputFormat : defaultOutputFormat);
            if (jobId != null && !jobId.isEmpty()) {
                requestBody.put("job_id", jobId);
            }

            String requestJson = objectMapper.writeValueAsString(requestBody);
            Request request = new Request.Builder()
                    .url(limeServiceUrl + "/explain")
                    .post(RequestBody.create(requestJson, JSON))
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                long latency = System.currentTimeMillis() - t;

                if (!response.isSuccessful()) {
                    log.warn("LIME service returned HTTP {}", response.code());
                    return LimeResult.error(latency);
                }

                String body = response.body() != null ? response.body().string() : "{}";
                JsonNode root = objectMapper.readTree(body);

                boolean success = root.path("success").asBoolean(false);
                if (!success) {
                    log.warn("LIME service returned success=false: {}", root.path("error").asText());
                    return LimeResult.error(latency);
                }

                // Parse feature weights
                List<LimeExplanation> explanations = objectMapper.convertValue(
                        root.path("explanation"),
                        new TypeReference<List<LimeExplanation>>() {}
                );

                String cacheStatus = root.path("cache_status").asText("MISS");
                String gcsUrl = root.path("gcs_url").asText("");

                log.info("LIME explanation received: {} features, cache={}, latency={}ms",
                        explanations.size(), cacheStatus, latency);

                return new LimeResult(explanations, cacheStatus, latency, gcsUrl, null);
            }

        } catch (Exception e) {
            long latency = System.currentTimeMillis() - t;
            log.error("LIME service call failed: {}", e.getMessage());
            return LimeResult.error(latency);
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // Cache key computation (public so Spring EL @Cacheable can use it)
    // ─────────────────────────────────────────────────────────────────
    public String cacheKey(String text, int numFeatures) {
        try {
            String raw = text.strip() + ":" + numFeatures;
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(raw.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return text.hashCode() + "_" + numFeatures;
        }
    }

    /**
     * Calls the Python microservice to get deep semantic analysis via Groq LLM.
     * This is used to boost detection accuracy for sophisticated scams.
     */
    public GroqAnalysisResult analyzeWithGroq(String text) {
        long t = System.currentTimeMillis();
        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("text", text);

            String requestJson = objectMapper.writeValueAsString(requestBody);
            Request request = new Request.Builder()
                    .url(limeServiceUrl + "/analyze/groq")
                    .post(RequestBody.create(requestJson, JSON))
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                long latency = System.currentTimeMillis() - t;

                if (!response.isSuccessful()) {
                    log.error("Groq analysis call failed with HTTP {}", response.code());
                    return createErrorGroqResult("HTTP " + response.code(), latency);
                }

                String body = response.body() != null ? response.body().string() : "{}";
                JsonNode root = objectMapper.readTree(body);

                if (!root.path("success").asBoolean(false)) {
                    log.error("Groq analysis reported failure: {}", root.path("error").asText());
                    return createErrorGroqResult(root.path("error").asText(), latency);
                }

                GroqAnalysisResult result = objectMapper.convertValue(
                        root.path("analysis"),
                        GroqAnalysisResult.class
                );
                result.setLatencyMs(latency);

                log.info("Groq analysis COMPLETED: scam_score={}, red_flags={}, latency={}ms",
                        result.getScamScore(), result.getRedFlags().size(), latency);

                return result;
            }
        } catch (Exception e) {
            long latency = System.currentTimeMillis() - t;
            log.error("Failed to call Groq analysis: {}", e.getMessage());
            return createErrorGroqResult(e.getMessage(), latency);
        }
    }

    private GroqAnalysisResult createErrorGroqResult(String error, long latency) {
        GroqAnalysisResult result = new GroqAnalysisResult();
        result.setReasoning("Error during Groq analysis: " + error);
        result.setScamScore(0.0);
        result.setFake(false);
        result.setRedFlags(Collections.emptyList());
        result.setLatencyMs(latency);
        return result;
    }

    // ─────────────────────────────────────────────────────────────────
    // Result container
    // ─────────────────────────────────────────────────────────────────
    public static class LimeResult {
        public final List<LimeExplanation> explanations;
        public final String cacheStatus;   // HIT | MISS | ERROR
        public final long latencyMs;
        public final String gcsUrl;
        public final String errorMessage;

        public LimeResult(List<LimeExplanation> explanations,
                          String cacheStatus,
                          long latencyMs,
                          String gcsUrl,
                          String errorMessage) {
            this.explanations = explanations != null ? explanations : Collections.emptyList();
            this.cacheStatus = cacheStatus;
            this.latencyMs = latencyMs;
            this.gcsUrl = gcsUrl != null ? gcsUrl : "";
            this.errorMessage = errorMessage;
        }

        public static LimeResult error(long latencyMs) {
            return new LimeResult(Collections.emptyList(), "ERROR", latencyMs, "", "LIME service unavailable");
        }

        public boolean isSuccess() {
            return !"ERROR".equals(cacheStatus) || !explanations.isEmpty();
        }
    }
}
