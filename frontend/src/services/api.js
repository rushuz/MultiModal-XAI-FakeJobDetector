export async function analyzeJob(text) {
  const response = await fetch("http://localhost:8080/predict", {
    method: "POST",
    headers: {
      "Content-Type": "application/json"
    },
    body: JSON.stringify({ text })
  });

  if (!response.ok) {
    throw new Error("Failed to analyze job");
  }

  return response.json();
}
