"""
Flask LIME Microservice — Main Application
==========================================
Endpoints:
  POST /explain    — Generate LIME explanation for a job description
  GET  /health     — Service health, cache stats, performance metrics
  POST /cache/clear — Clear the explanation cache (admin use)
"""

import os
import time
import uuid
import logging

from dotenv import load_dotenv
from flask import Flask, request, jsonify
from flask_cors import CORS

from explainer import LimeExplainerService, GroqAnalyzer
from cache import ExplanationCache
from monitoring import PerformanceMonitor

# ──────────────────────────────────────────────────────────
# Configuration
# ──────────────────────────────────────────────────────────
load_dotenv()

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s — %(message)s"
)
logger = logging.getLogger("lime-service")

MODEL_PATH        = os.environ.get("MODEL_PATH", "")
CACHE_MAX_SIZE    = int(os.environ.get("CACHE_MAX_SIZE", 500))
CACHE_TTL         = int(os.environ.get("CACHE_TTL_SECONDS", 3600))
LIME_NUM_SAMPLES  = int(os.environ.get("LIME_NUM_SAMPLES", 500))
SERVICE_PORT      = int(os.environ.get("LIME_SERVICE_PORT") or os.environ.get("PORT") or 5002)

DEFAULT_NUM_FEATURES = 10
MAX_NUM_FEATURES     = 30
MIN_TEXT_LENGTH      = 20

GROQ_API_KEY = os.environ.get("GROQ_API_KEY")
GROQ_MODEL   = os.environ.get("GROQ_MODEL", "llama-3.1-70b-versatile")

# ──────────────────────────────────────────────────────────
# Initialise components
# ──────────────────────────────────────────────────────────
app = Flask(__name__)
CORS(app)

logger.info("Initialising LIME explainer service...")
explainer  = LimeExplainerService(model_path=MODEL_PATH or None, num_samples=LIME_NUM_SAMPLES)
cache      = ExplanationCache(max_size=CACHE_MAX_SIZE, ttl_seconds=CACHE_TTL)
monitor    = PerformanceMonitor()

groq_analyzer = None
if GROQ_API_KEY:
    logger.info("Initialising Groq analyzer service...")
    groq_analyzer = GroqAnalyzer(api_key=GROQ_API_KEY, model_name=GROQ_MODEL)
else:
    logger.warning("GROQ_API_KEY not found in environment — Groq analysis will be disabled.")

logger.info("LIME microservice ready on port %d", SERVICE_PORT)


# ──────────────────────────────────────────────────────────
# Helper utilities
# ──────────────────────────────────────────────────────────
def _validate_explain_request(body: dict):
    """Return (text, num_features, output_format, error_message)."""
    text = body.get("text", "").strip()
    if not text:
        return None, None, None, "Field 'text' is required and cannot be empty."
    if len(text) < MIN_TEXT_LENGTH:
        return None, None, None, f"Text too short — minimum {MIN_TEXT_LENGTH} characters required."

    raw_nf = body.get("num_features", DEFAULT_NUM_FEATURES)
    try:
        num_features = int(raw_nf)
        num_features = max(1, min(num_features, MAX_NUM_FEATURES))
    except (TypeError, ValueError):
        num_features = DEFAULT_NUM_FEATURES

    output_format = str(body.get("output_format", "json")).lower()
    if output_format not in ("json", "visual"):
        output_format = "json"

    return text, num_features, output_format, None


def _format_visual(explanation: list) -> str:
    """Return a simple ASCII bar chart string for 'visual' output format."""
    if not explanation:
        return "(no explanation)"
    max_w = max(abs(e["weight"]) for e in explanation) or 1
    lines = []
    for item in explanation:
        bar_len = int(abs(item["weight"]) / max_w * 20)
        bar = "█" * bar_len
        sign = "+" if item["weight"] > 0 else "-"
        lines.append(f"{item['word']:>20}  {sign}{bar:<20}  {item['weight']:+.4f}")
    return "\n".join(lines)


# ──────────────────────────────────────────────────────────
# Routes
# ──────────────────────────────────────────────────────────
@app.route("/explain", methods=["POST"])
def explain():
    """
    Generate a LIME explanation.

    Request body (JSON):
    {
        "text":         "job description...",   // required
        "num_features": 10,                     // optional, default=10 (max 30)
        "output_format": "json" | "visual",     // optional, default="json"
        "job_id":       "uuid-string"           // optional, for GCS upload tracking
    }

    Response (200):
    {
        "success":       true,
        "job_id":        "...",
        "explanation":   [{word, weight}, ...],
        "output_format": "json",
        "num_features":  10,
        "cache_status":  "HIT" | "MISS",
        "latency_ms":    42.3,
        "gcs_url":       "gs://..." | ""
    }
    """
    t_start = time.time()
    success = True

    try:
        body = request.get_json(force=True, silent=True) or {}
        text, num_features, output_format, err = _validate_explain_request(body)

        if err:
            return jsonify({"success": False, "error": err}), 400

        job_id = body.get("job_id") or str(uuid.uuid4())

        # ── Cache lookup ──
        cache_key = ExplanationCache.make_key(text, num_features)
        cached = cache.get(cache_key)
        if cached is not None:
            explanation = cached
            cache_status = "HIT"
        else:
            # ── Generate explanation ──
            explanation = explainer.explain(text, num_features=num_features, label_idx=1)
            cache.set(cache_key, explanation)
            cache_status = "MISS"

        # ── Format output ──
        latency_ms = round((time.time() - t_start) * 1000, 2)
        monitor.record_request(latency_ms, success=True)

        response_data = {
            "success": True,
            "job_id": job_id,
            "num_features": num_features,
            "cache_status": cache_status,
            "latency_ms": latency_ms,
            "output_format": output_format,
        }

        if output_format == "visual":
            response_data["explanation_visual"] = _format_visual(explanation)
            response_data["explanation"] = explanation  # still include raw for frontend
        else:
            response_data["explanation"] = explanation

        return jsonify(response_data), 200

    except Exception as exc:
        success = False
        latency_ms = round((time.time() - t_start) * 1000, 2)
        monitor.record_request(latency_ms, success=False)
        logger.exception("Unhandled error in /explain: %s", exc)
        return jsonify({
            "success": False,
            "error": f"Explanation generation failed: {str(exc)}",
            "latency_ms": latency_ms
        }), 500


@app.route("/analyze/groq", methods=["POST"])
def analyze_groq():
    """
    Perform deep semantic analysis using Groq LLM.
    Returns structured scam score and red flags.
    """
    if not groq_analyzer:
        return jsonify({"success": False, "error": "Groq analysis service is not configured."}), 503

    t_start = time.time()
    try:
        body = request.get_json(force=True, silent=True) or {}
        text = body.get("text", "").strip()

        if not text or len(text) < MIN_TEXT_LENGTH:
            return jsonify({
                "success": False, 
                "error": f"Field 'text' must be at least {MIN_TEXT_LENGTH} chars."
            }), 400

        # Run analysis
        analysis = groq_analyzer.analyze(text)
        latency_ms = round((time.time() - t_start) * 1000, 2)

        return jsonify({
            "success": True,
            "analysis": analysis.model_dump(),
            "latency_ms": latency_ms
        }), 200

    except Exception as exc:
        logger.exception("Error in /analyze/groq: %s", exc)
        return jsonify({
            "success": False,
            "error": str(exc),
            "latency_ms": round((time.time() - t_start) * 1000, 2)
        }), 500


@app.route("/health", methods=["GET"])
def health():
    """Health check with cache stats and performance metrics."""
    perf = monitor.get_stats()
    c_stats = cache.stats()
    return jsonify({
        "status": "ok",
        "model": "surrogate (TF-IDF + LogisticRegression)",
        "performance": perf,
        "cache": c_stats,
    }), 200


@app.route("/cache/clear", methods=["POST"])
def clear_cache():
    """Clear the explanation cache."""
    cache.clear()
    logger.info("Explanation cache cleared via API.")
    return jsonify({"success": True, "message": "Cache cleared."}), 200


# ──────────────────────────────────────────────────────────
# Entry point
# ──────────────────────────────────────────────────────────
if __name__ == "__main__":
    app.run(host="0.0.0.0", port=SERVICE_PORT, debug=False)
