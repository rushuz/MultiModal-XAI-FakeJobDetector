package com.example.random_major.service;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import org.dmg.pmml.PMML;
import org.jpmml.evaluator.Evaluator;
import org.jpmml.evaluator.InputField;
import org.jpmml.evaluator.ModelEvaluatorBuilder;
import org.jpmml.evaluator.TargetField;
import org.jpmml.model.PMMLUtil;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;

@Service
public class ModelEvaluatorService {

    private Evaluator evaluator;
    private boolean isMockMode = false;

    @PostConstruct
    public void init() {
        try {
            // Load PMML file from resources folder
            InputStream is = getClass()
                    .getClassLoader()
                    .getResourceAsStream("model.pmml");

            if (is == null) {
                isMockMode = true;
                System.err.println("⚠️  WARNING: model.pmml NOT FOUND in resources!");
                System.err.println("⚠️  Switching to MOCK MODE. Predictions will be neutral (0.5).");
                return;
            }

            PMML pmml = PMMLUtil.unmarshal(is);
            evaluator = new ModelEvaluatorBuilder(pmml).build();
            evaluator.verify();

            System.out.println("✅ PMML Model Loaded Successfully");

        } catch (Exception e) {
            System.err.println("❌ Critical: Failed to load PMML model, but system will attempt to continue in mock mode.");
            isMockMode = true;
        }
    }

    public Map<String, Object> predict(String text) {
        Map<String, Object> output = new HashMap<>();

        if (isMockMode) {
            output.put("label", "REAL");
            output.put("probability_fake", 0.5); // Neutral baseline
            return output;
        }

        try {
            InputField inputField = evaluator.getInputFields().get(0);
        Object preparedValue = inputField.prepare(text);

        Map<String, Object> arguments = new HashMap<>();
        arguments.put(inputField.getName(), preparedValue);

        Map<String, ?> results = evaluator.evaluate(arguments);

        TargetField targetField = evaluator.getTargetFields().get(0);
        Object prediction = results.get(targetField.getName());

        String labelValue = prediction.toString();

        double fakeProbability = 0.0;

        // 🔥 Get probability from probability distribution
        if (prediction instanceof org.jpmml.evaluator.ProbabilityDistribution) {
            org.jpmml.evaluator.ProbabilityDistribution dist =
                (org.jpmml.evaluator.ProbabilityDistribution) prediction;

            fakeProbability = dist.getProbability("1");
        }

        // Convert label
        if (labelValue.equals("1")) {
            output.put("label", "FAKE");
        } else {
            output.put("label", "REAL");
        }

        output.put("probability_fake", fakeProbability);

    } catch (Exception e) {
        e.printStackTrace();
        output.put("error", "Prediction failed");
    }

    return output;
}

}