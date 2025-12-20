package com.fakejobdetection.ml;

import org.jpmml.evaluator.*;

import java.util.HashMap;
import java.util.Map;

public class PredictionEngine {

    /**
     * Predict probability using a fused multimodal PMML model
     */
    public static double predictProbability(Map<String, Object> features) {

        Evaluator evaluator = PMMLModelLoader.getHybridEvaluator();

        Map<String, FieldValue> arguments = new HashMap<>();

        for (InputField inputField : evaluator.getInputFields()) {
            String name = inputField.getName();
            Object rawValue = features.get(name);
            arguments.put(name, inputField.prepare(rawValue));
        }

        Map<String, ?> results =
                EvaluatorUtil.decodeAll(evaluator.evaluate(arguments));

        // Explicitly read target probability
        Object prob = results.get("probability(FAKE)");

        if (prob instanceof Number n) {
            return n.doubleValue();
        }

        return 0.0;
    }

    /**
     * Optional: modality-specific prediction (if separate models exist)
     */
    public static double predictProbability(
            Map<String, Object> features,
            Evaluator evaluator) {

        Map<String, FieldValue> arguments = new HashMap<>();

        for (InputField inputField : evaluator.getInputFields()) {
            String name = inputField.getName();
            Object rawValue = features.get(name);
            arguments.put(name, inputField.prepare(rawValue));
        }

        Map<String, ?> results =
                EvaluatorUtil.decodeAll(evaluator.evaluate(arguments));

        Object prob = results.get("probability(FAKE)");
        return prob instanceof Number n ? n.doubleValue() : 0.0;
    }
}
