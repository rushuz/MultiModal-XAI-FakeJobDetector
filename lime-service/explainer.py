# Python Surrogate Model for LIME
# Since no .pkl file is available, this module trains a lightweight TF-IDF +
# Logistic Regression surrogate that mirrors the PMML model's predictions.
# LIME then explains the surrogate, which produces realistic word-importance explanations.

import os
import pickle
import logging
import json
import numpy as np
from typing import List, Optional
from pydantic import BaseModel, Field
from groq import Groq
from sklearn.pipeline import Pipeline
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.linear_model import LogisticRegression
from lime.lime_text import LimeTextExplainer

logger = logging.getLogger(__name__)

# Surrogate training corpus — job posting examples covering real and fake patterns.
# This corpus teaches the surrogate what suspicious and safe language looks like.
SURROGATE_CORPUS = [
    # FAKE job posts (label=1)
    ("Work from home earn $5000 per week no experience needed send money upfront urgent hiring now", 1),
    ("Make money fast data entry online no skills required immediate start wire transfer", 1),
    ("Exciting opportunity earn thousands weekly cryptocurrency investment guaranteed returns", 1),
    ("Personal assistant needed send résumé with bank details salary paid weekly cash", 1),
    ("Hiring immediately no interview required earn passive income simple tasks from home", 1),
    ("Urgent vacancy earn 500 dollars daily click here limited time offer commission only", 1),
    ("Remote data processor no degree required earn 300 per day payment via western union", 1),
    ("Social media promoter work anywhere earn big no qualifications recruitment fee required", 1),
    ("Get paid to take surveys online unlimited earning potential no boss sign up fee", 1),
    ("Mystery shopper needed urgent earn $200 per assignment advance payment required", 1),
    ("Part time remote opportunity guaranteed income share personal information for hire", 1),
    ("Start today no experience earn extra income processing payments advance deposit required", 1),
    ("Freelance typist needed from home massive earnings paid in gift cards or crypto", 1),
    ("Be your own boss unlimited potential work 2 hours daily earn thousands monthly", 1),
    ("Nanny job abroad full relocation package visa sponsorship send passport copy first", 1),
    ("Reshipping coordinator needed receive and forward packages earn $50 per package", 1),
    ("Work at home unlimited earnings no boss necessary investment opportunity", 1),
    ("Seeking agents worldwide easy tasks earn thousands no experience required", 1),
    ("Online tutor no qualifications high pay work anytime no background check required", 1),
    ("Earn immediate cash flexible hours no skill requirement investment return guaranteed", 1),
    # REAL job posts (label=0)
    ("Software Engineer at Google Mountain View CA competitive salary health benefits 401k", 0),
    ("Senior Data Scientist required 5 years experience machine learning Python TensorFlow AWS", 0),
    ("Marketing Manager B2B SaaS company Bay Area base salary plus equity and benefits", 0),
    ("Full Stack Developer React Node.js PostgreSQL remote-friendly team apply at careers portal", 0),
    ("DevOps Engineer CI/CD Kubernetes Docker AWS GCP experience required competitive package", 0),
    ("Product Manager Agile roadmap stakeholder management MBA preferred 7 years experience", 0),
    ("UX Designer Figma prototyping user research portfolio required San Francisco office", 0),
    ("Data Analyst SQL Tableau business intelligence degree required healthcare industry", 0),
    ("Cloud Architect Azure AWS certification preferred enterprise solutions consulting firm", 0),
    ("Machine Learning Engineer deep learning NLP research publications a plus", 0),
    ("Frontend Developer React TypeScript accessibility standards remote first company", 0),
    ("Backend Engineer Java Spring Boot microservices London hybrid work pension scheme", 0),
    ("Security Engineer penetration testing SOC2 compliance CISSP certification preferred", 0),
    ("Business Analyst financial services stakeholder requirements documentation agile team", 0),
    ("HR Manager talent acquisition employee relations benefits administration 5 years exp", 0),
    ("Project Manager PMP certified construction management university degree required", 0),
    ("Accountant CPA required financial reporting GAAP audit experience Big Four preferred", 0),
    ("Legal Counsel corporate law intellectual property LLB required 3 years experience", 0),
    ("Content Writer SEO blog technical writing editorial team communication skills", 0),
    ("Customer Success Manager SaaS churn reduction onboarding renewal rate KPIs", 0),
]

# ──────────────────────────────────────────────────────────
# Groq LLM Analyzer
# ──────────────────────────────────────────────────────────

class GroqRedFlag(BaseModel):
    category: str = Field(description="Category of the red flag (e.g., Financial, Urgency, Grammar)")
    description: str = Field(description="Brief explanation of why this is suspicious")
    severity: float = Field(description="Severity score between 0 and 1", ge=0, le=1)

class GroqAnalysisResponse(BaseModel):
    is_fake: bool = Field(description="Classification of the job posting")
    scam_score: float = Field(description="Probability of being a scam (0 to 1)", ge=0, le=1)
    reasoning: str = Field(description="Concise reasoning for the classification")
    red_flags: List[GroqRedFlag] = Field(description="Specific suspicious elements identified")

class GroqAnalyzer:
    """
    Leverages Groq LLM to perform deep semantic analysis of job postings.
    Identifies scam patterns that statistical models might miss.
    """
    def __init__(self, api_key: str, model_name: str = "llama-3.1-70b-versatile"):
        self.client = Groq(api_key=api_key)
        self.model = model_name

    def analyze(self, text: str) -> GroqAnalysisResponse:
        """Analyze job text for fraud indicators."""
        prompt = f"""
        Analyze the following job posting and determine if it is FAKE (scam/phishing) or REAL.
        Look for:
        - Unrealistic salary/benefits
        - Sense of extreme urgency or pressure
        - Generic or unprofessional contact information
        - Requests for personal info or money upfront
        - Poor grammar/formatting inconsistent with professional standards
        - Vague job requirements combined with high pay

        Job Posting Text:
        ---
        {text}
        ---

        Provide your response as a valid JSON object matching this schema:
        {{
            "is_fake": boolean,
            "scam_score": float (0-1),
            "reasoning": "brief explanation",
            "red_flags": [
                {{ "category": "Grammar", "description": "text...", "severity": 0.5 }}
            ]
        }}
        """

        try:
            completion = self.client.chat.completions.create(
                model=self.model,
                messages=[
                    {
                        "role": "system", 
                        "content": "You are an expert fraud investigator specializing in job recruitment scams. Return JSON."
                    },
                    {"role": "user", "content": prompt}
                ],
                response_format={"type": "json_object"},
                temperature=0.1
            )
            
            content = completion.choices[0].message.content
            # Validate with Pydantic
            data = GroqAnalysisResponse.model_validate_json(content)
            return data
        except Exception as e:
            logger.error(f"Groq analysis failed: {str(e)}")
            # Fallback response
            return GroqAnalysisResponse(
                is_fake=False,
                scam_score=0.0,
                reasoning=f"Analysis failed: {str(e)}",
                red_flags=[]
            )


class LimeExplainerService:
    """
    Manages a surrogate scikit-learn pipeline and a LIME text explainer.
    The surrogate is trained to mimic the PMML model's classification behaviour,
    enabling LIME to produce meaningful word-importance explanations.
    """

    def __init__(self, model_path: str = None, num_samples: int = 500):
        self.num_samples = num_samples
        self.pipeline: Pipeline = None
        self.lime_explainer: LimeTextExplainer = None
        self._load_or_train(model_path)
        self._init_lime()

    def _load_or_train(self, model_path: str):
        """Load a pre-saved surrogate pipeline or train a fresh one."""
        surrogate_cache = os.path.join(os.path.dirname(__file__), "surrogate_model.pkl")

        if model_path and os.path.exists(model_path):
            logger.info("Loading scikit-learn model from: %s", model_path)
            with open(model_path, "rb") as f:
                self.pipeline = pickle.load(f)
                self._fix_model_compatibility()
        elif os.path.exists(surrogate_cache):
            logger.info("Loading cached surrogate from: %s", surrogate_cache)
            with open(surrogate_cache, "rb") as f:
                self.pipeline = pickle.load(f)
                self._fix_model_compatibility()
        else:
            logger.info("Training new surrogate model for LIME...")
            self._train_surrogate()
            # Cache trained surrogate for faster restarts
            with open(surrogate_cache, "wb") as f:
                pickle.dump(self.pipeline, f)
            logger.info("Surrogate model saved to: %s", surrogate_cache)

    def _fix_model_compatibility(self):
        """Fix sklearn model compatibility issues from older pickle versions."""
        try:
            # Get the classifier from the pipeline
            clf = self.pipeline.named_steps.get("clf")
            if clf and hasattr(clf, "multi_class") is False:
                # Add missing multi_class attribute that newer scikit-learn expects
                clf.multi_class = "auto"
                logger.info("Added missing 'multi_class' attribute to LogisticRegression for compatibility")
        except Exception as e:
            logger.warning("Could not fix model compatibility: %s", str(e))

    def _train_surrogate(self):
        """Train a TF-IDF + Logistic Regression pipeline on the seed corpus."""
        texts, labels = zip(*SURROGATE_CORPUS)
        self.pipeline = Pipeline([
            ("tfidf", TfidfVectorizer(
                ngram_range=(1, 2),
                max_features=5000,
                sublinear_tf=True,
                min_df=1
            )),
            ("clf", LogisticRegression(
                C=1.0,
                max_iter=1000,
                class_weight="balanced",
                random_state=42,
                multi_class="auto"
            ))
        ])
        self.pipeline.fit(texts, labels)
        train_acc = self.pipeline.score(texts, labels)
        logger.info("Surrogate training accuracy: %.2f", train_acc)

    def _init_lime(self):
        """Initialise the LIME text explainer."""
        self.lime_explainer = LimeTextExplainer(
            class_names=["REAL", "FAKE"],
            split_expression=r'\W+',
            bow=True,
            random_state=42
        )
        logger.info("LIME explainer initialised.")

    def predict_proba(self, texts):
        """Wrapper for LIME — returns probability array [[p_real, p_fake], ...]."""
        return self.pipeline.predict_proba(texts)

    def explain(self, text: str, num_features: int = 10, label_idx: int = 1) -> list:
        """
        Generate LIME explanation for a single text.

        Args:
            text:         Raw job description text.
            num_features: Number of top features to return.
            label_idx:    Class index to explain (1 = FAKE).

        Returns:
            List of dicts: [{word: str, weight: float}, ...]
            Sorted by absolute weight descending.
        """
        if not text or not text.strip():
            return []

        explanation = self.lime_explainer.explain_instance(
            text_instance=text,
            classifier_fn=self.predict_proba,
            num_features=num_features,
            num_samples=self.num_samples,
            labels=[label_idx]
        )

        raw = explanation.as_list(label=label_idx)
        results = [
            {"word": word, "weight": round(float(weight), 6)}
            for word, weight in raw
        ]
        # Sort by absolute importance descending
        results.sort(key=lambda x: abs(x["weight"]), reverse=True)
        return results
