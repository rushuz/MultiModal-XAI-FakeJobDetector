export default function ExplanationList({ reasons }) {
  return (
    <div className="explanation-box">
      <h4>Why this result?</h4>

      <ul>
        {reasons.map((reason, index) => (
          <li key={index}>{reason}</li>
        ))}
      </ul>
    </div>
  );
}
