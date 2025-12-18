import { useEffect, useState } from "react";
import "../styles/DetectionPage.css";

import HistoryPanel from "../components/HistoryPanel";
import ResultCard from "../components/ResultCard";
import ConfidenceBar from "../components/ConfidenceBar";
import ExplanationList from "../components/ExplanationList";
import HighlightedText from "../components/HighlightedText";

import { analyzeJob } from "../services/api";

export default function DetectionPage() {
  const [text, setText] = useState("");
  const [loading, setLoading] = useState(false);
  const [result, setResult] = useState(null);
  const [error, setError] = useState("");
  const [history, setHistory] = useState([]);

  /* Load history on mount */
  useEffect(() => {
    const saved = JSON.parse(localStorage.getItem("analysisHistory")) || [];
    setHistory(saved);
  }, []);

  /* Save history */
  const saveToHistory = (text, result) => {
    const entry = {
      text,
      preview: text.slice(0, 40) + "...",
      result
    };

    const updated = [entry, ...history].slice(0, 10); // limit 10
    setHistory(updated);
    localStorage.setItem("analysisHistory", JSON.stringify(updated));
  };

  const handleAnalyze = async () => {
    if (!text.trim()) return;

    setLoading(true);
    setError("");
    setResult(null);

    try {
      const data = await analyzeJob(text);
      setResult(data);
      saveToHistory(text, data);
    } catch {
      setError("Unable to analyze job. Please try again.");
    } finally {
      setLoading(false);
    }
  };

  const handleHistorySelect = (item) => {
    setText(item.text);
    setResult(item.result);
  };

  return (
    <div className="detection-wrapper">

      <HistoryPanel
        history={history}
        onSelect={handleHistorySelect}
      />

      <main className="detection-container">
        <h2>Analyze Job Description</h2>

        <textarea
          rows="6"
          placeholder="Paste job description here..."
          value={text}
          onChange={(e) => setText(e.target.value)}
        />

        <button onClick={handleAnalyze} disabled={loading}>
          {loading ? "Analyzing..." : "Analyze"}
        </button>

        {error && <p className="error-text">{error}</p>}

        {result && (
          <>
            <ResultCard
              label={result.label}
              confidence={result.confidence}
              reasons={result.reasons}
            />

            <ConfidenceBar confidence={result.confidence} />

            <ExplanationList reasons={result.reasons} />

            {result.keywords && (
              <HighlightedText
                text={text}
                keywords={result.keywords}
              />
            )}
          </>
        )}

      </main>
    </div>
  );
}
