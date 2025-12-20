import { useNavigate } from "react-router-dom";
import "../styles/HomePage.css";

export default function HomePage() {
  const navigate = useNavigate();

  return (
    <div className="home-wrapper">

      {/* HERO */}
      <section className="hero-section">
        <div className="hero-content">
          <h1>
            Detect <span>Fake Job Scams</span><br />with Explainable AI
          </h1>
          <p>
            Analyze job descriptions using AI models that don’t just predict —
            they <strong>explain why</strong>.
          </p>

          <div className="hero-actions">
            <button onClick={() => navigate("/detection")}>
              Analyze a Job
            </button>
            <span className="hero-subtext">
              Transparent • Secure • Research-grade AI
            </span>
          </div>
        </div>
      </section>

      {/* HOW IT WORKS */}
      <section className="how-section">
        <h2>How It Works</h2>

        <div className="steps">
          <div className="step">
            <span>01</span>
            <h3>Paste Job Content</h3>
            <p>
              Provide job descriptions from emails, messages, or postings.
            </p>
          </div>

          <div className="step">
            <span>02</span>
            <h3>AI Analysis</h3>
            <p>
              NLP + ML models evaluate keywords, urgency, salary patterns and more.
            </p>
          </div>

          <div className="step">
            <span>03</span>
            <h3>Explainable Result</h3>
            <p>
              LIME explains <em>why</em> a job is classified as real or fake.
            </p>
          </div>
        </div>
      </section>

      {/* XAI USP */}
      <section className="xai-section">
        <h2>Why Explainable AI Matters</h2>
        <p>
          Most AI systems give predictions without reasons.
          <br />
          <strong>We show the logic behind every decision.</strong>
        </p>

        <div className="xai-points">
          <div>Feature-level explanations</div>
          <div>Confidence-aware predictions</div>
          <div>Human-readable reasoning</div>
        </div>
      </section>

      {/* TRUST STRIP */}
      <section className="trust-section">
        <p>
          Built using <strong>Spring Boot, React, Python, PMML, and LIME</strong>
          <br />
          Designed for research, security, and real-world impact.
        </p>
      </section>

      {/* FINAL CTA */}
      <section className="cta-section">
        <h2>Try It Yourself</h2><br></br>
        <button onClick={() => navigate("/detection")}>
          Start Detection
        </button>
      </section>

    </div>
  );
}
