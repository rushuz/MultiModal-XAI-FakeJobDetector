export default function ResultCard({ label, confidence, reasons }) {
  return (
    <div className={`result-card ${label.toLowerCase()}`}>
      <h3>{label}</h3>

      <p>
        Confidence: <strong>{(confidence * 100).toFixed(1)}%</strong>
      </p>

      <ul>
        {reasons.map((reason, index) => (
          <li key={index}>⚠️ {reason}</li>
        ))}
      </ul>
    </div>
  );
}
