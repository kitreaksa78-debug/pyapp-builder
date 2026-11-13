const express = require("express");
const app = express();
app.use(express.json({ limit: "12mb" }));
app.get("/health", (_req, res) => res.json({ status: "ok" }));

app.post("/analyze", async (req, res) => {
  try {
    const { apiKey, model, image } = req.body || {};
    if (!apiKey || !model || !image) return res.status(400).json({ error: "apiKey, model and image are required" });
    if (!/^gsk_/.test(apiKey)) return res.status(400).json({ error: "Groq API key format looks invalid" });
    const prompt = `You are a visual assistant for billiards practice. Inspect this screenshot. Return ONLY valid JSON:
{"cue":{"x":0.5,"y":0.5},"target":{"x":0.6,"y":0.4},"balls":[{"x":0.6,"y":0.4,"kind":"target"}],"confidence":0.0,"note":"short note"}
Coordinates must be normalized 0..1 relative to the FULL image. Identify the white cue ball and one clearly visible object ball. If you cannot confidently identify both, return {"cue":null,"target":null,"balls":[],"confidence":0,"note":"unclear image"}. Do not guess. This is practice guidance; no game control or automation.`;
    const upstream = await fetch("https://api.groq.com/openai/v1/chat/completions", {
      method: "POST",
      headers: { "Authorization": `Bearer ${apiKey}`, "Content-Type": "application/json" },
      body: JSON.stringify({
        model,
        temperature: 0.1,
        max_tokens: 500,
        messages: [{ role: "user", content: [
          { type: "text", text: prompt },
          { type: "image_url", image_url: { url: `data:image/jpeg;base64,${image}` } }
        ]}]
      })
    });
    const raw = await upstream.text();
    if (!upstream.ok) return res.status(upstream.status).json({ error: "Groq request failed", detail: raw.slice(0, 1000) });
    let data;
    try { data = JSON.parse(raw); } catch { return res.status(502).json({ error: "Groq returned non-JSON response" }); }
    const content = data.choices?.[0]?.message?.content || "";
    const match = content.match(/\{[\s\S]*\}/);
    if (!match) return res.status(502).json({ error: "Model did not return JSON", detail: content.slice(0, 500) });
    const result = JSON.parse(match[0]);
    return res.json(result);
  } catch (e) {
    return res.status(500).json({ error: "Backend error", detail: String(e.message || e) });
  }
});
const port = process.env.PORT || 3000;
app.listen(port, "0.0.0.0", () => console.log(`Listening on ${port}`));
