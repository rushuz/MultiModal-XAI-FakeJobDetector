package com.fakejobdetection.ml;

import java.io.InputStream;

import org.dmg.pmml.PMML;
import org.jpmml.evaluator.Evaluator;
import org.jpmml.evaluator.ModelEvaluatorBuilder;
import org.jpmml.model.PMMLUtil;

public class PMMLModelLoader {

    private static final Evaluator HYBRID_EVALUATOR = load("fake_job_multimodal.pmml");

    private static Evaluator load(String model) {
        try (InputStream is =
                     PMMLModelLoader.class.getResourceAsStream("/models/" + model)) {

            if (is == null) {
                throw new RuntimeException("PMML model not found: " + model);
            }

            PMML pmml = PMMLUtil.unmarshal(is);
            Evaluator evaluator = new ModelEvaluatorBuilder(pmml).build();
            evaluator.verify();
            return evaluator;

        } catch (Exception e) {
            throw new RuntimeException("Failed to load PMML model: " + model, e);
        }
    }

    public static Evaluator getHybridEvaluator() {
        return HYBRID_EVALUATOR;
    }
}
