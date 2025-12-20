from flask import Flask, request, jsonify
import numpy as np
from lime.lime_tabular import LimeTabularExplainer

app = Flask(__name__)

FEATURE_NAMES = [
    # TEXT
    "keyword_score",
    "sentence_count",
    "text_length",
    "urgent_flag",
    "no_interview_flag",

    # IMAGE (OCR)
    "ocr_risk_score",
    "fake_company_flag",

    # AUDIO
    "audio_risk_score",
    "urgency_voice_flag",
    "payment_voice_flag"
]

# Dummy data ONLY to initialize explainer
training_data = np.array([
    [0.9, 2, 120, 1, 1, 0.8, 1, 0.7, 1, 1],
    [0.1, 6, 800, 0, 0, 0.1, 0, 0.2, 0, 0],
    [0.8, 3, 150, 1, 1, 0.6, 1, 0.5, 1, 0],
    [0.2, 8, 1000, 0, 0, 0.1, 0, 0.1, 0, 0]
])

explainer = LimeTabularExplainer(
    training_data,
    feature_names=FEATURE_NAMES,
    class_names=["REAL", "FAKE"],
    mode="classification"
)

@app.route("/explain", methods=["POST"])
def explain():
    try:
        data = request.get_json(force=True)

        features = data.get("features")
        fake_prob = float(data.get("fake_probability"))

        if features is None or len(features) != len(FEATURE_NAMES):
            return jsonify({"error": "Invalid feature vector"}), 400


        instance = np.array(features, dtype=float).reshape(1, -1)

        def predict_fn(x):
            probs = np.zeros((x.shape[0], 2))

            for i, row in enumerate(x):
                score = 0.0

                # TEXT contribution
                score += row[0] * 0.25
                score += row[3] * 0.2
                score += row[4] * 0.15
                score -= row[1] * 0.05
                score -= row[2] * 0.0001

                # IMAGE contribution
                score += row[5] * 0.15
                score += row[6] * 0.1

                # AUDIO contribution
                score += row[7] * 0.1
                score += row[8] * 0.05
                score += row[9] * 0.05

                score = max(0.0, min(1.0, score))

                probs[i, 1] = score      # FAKE
                probs[i, 0] = 1 - score  # REAL

            return probs


        exp = explainer.explain_instance(
            instance[0],
            predict_fn,
            num_features=5
        )

        return jsonify(dict(exp.as_list()))

    except Exception as e:
        # IMPORTANT: expose error during development
        return jsonify({"error": str(e)}), 500


if __name__ == "__main__":
    app.run(port=5000, debug=True)
