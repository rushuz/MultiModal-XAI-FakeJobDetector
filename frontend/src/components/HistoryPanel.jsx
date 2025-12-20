export default function HistoryPanel({ history, onSelect }) {
  return (
    <aside className="history-panel">
      <h3>History</h3>

      {history.length === 0 && (
        <p className="history-empty">No analysis yet</p>
      )}

      {history.map((item, index) => (
        <div
          key={index}
          className="history-item"
          onClick={() => onSelect(item)}
          title="Click to view analysis"
        >
          {item.preview}
        </div>
      ))}
    </aside>
  );
}
