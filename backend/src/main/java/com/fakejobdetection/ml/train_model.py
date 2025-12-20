import pandas as pd
from sklearn.ensemble import GradientBoostingClassifier
from sklearn.preprocessing import StandardScaler
from sklearn.model_selection import train_test_split
from sklearn.metrics import classification_report
from sklearn2pmml import PMMLPipeline, sklearn2pmml

# ---------------------------
# LOAD DATASET
# ---------------------------
# Dataset must already contain extracted features
# (You can generate them using feature_engineering.py)

data = pd.read_csv("training_dataset.csv")

FEATURE_COLUMNS = [
    "keyword_score",
    "sentence_count",
    "text_length",
    "urgent_flag",
    "no_interview_flag",
    "ocr_risk_score",
    "fake_company_flag",
    "audio_risk_score",
    "urgency_voice_flag",
    "payment_voice_flag"
]

X = data[FEATURE_COLUMNS]
y = data["label"]  # 1 = FAKE, 0 = REAL

# ---------------------------
# TRAIN / TEST SPLIT
# ---------------------------
X_train, X_test, y_train, y_test = train_test_split(
    X, y, test_size=0.2, random_state=42, stratify=y
)

# ---------------------------
# MODEL PIPELINE (PMML SAFE)
# ---------------------------
pipeline = PMMLPipeline([
    ("scaler", StandardScaler()),
    ("classifier", GradientBoostingClassifier(
        n_estimators=300,
        learning_rate=0.05,
        max_depth=5,
        subsample=0.9,
        random_state=42
    ))
])

pipeline.fit(X_train, y_train)

# ---------------------------
# EVALUATION
# ---------------------------
y_pred = pipeline.predict(X_test)
print("\n=== MODEL PERFORMANCE ===")
print(classification_report(y_test, y_pred))

# ---------------------------
# EXPORT PMML
# ---------------------------
sklearn2pmml(
    pipeline,
    "fake_job_multimodal.pmml",
    with_repr=True
)

print("\nPMML model exported: fake_job_multimodal.pmml")
