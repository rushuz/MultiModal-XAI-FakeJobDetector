export default function HighlightedText({ text, keywords }) {
  if (!text) return null;

  let highlighted = text;

  keywords.forEach(({ word, weight }) => {
    const level =
      weight > 0.7 ? "high-risk" :
      weight > 0.4 ? "medium-risk" :
      "low-risk";

    const regex = new RegExp(`(${word})`, "gi");

    highlighted = highlighted.replace(
      regex,
      `<span class="xai-highlight ${level}">$1</span>`
    );
  });

  return (
    <div className="highlighted-preview">
      <h4>Explainable AI – Risk Highlights</h4>
      <div
        className="highlighted-text"
        dangerouslySetInnerHTML={{ __html: highlighted }}
      />
    </div>
  );
}
