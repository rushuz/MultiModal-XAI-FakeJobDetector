export default function ConfidenceBar({ confidence }) {
  const percentage = Math.round(confidence * 100);

  return (
    <div className="confidence-wrapper">
      <div className="confidence-label">
        Model Confidence: {percentage}%
      </div>

      <div className="confidence-bar">
        <div
          className="confidence-fill"
          style={{ width: `${percentage}%` }}
        />
      </div>
    </div>
  );
}
