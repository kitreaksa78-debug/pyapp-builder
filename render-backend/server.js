import express from 'express';
import helmet from 'helmet';

const app = express();
app.use(helmet({ crossOriginResourcePolicy: { policy: 'cross-origin' } }));
app.use(express.json({ limit: '8mb' }));

const PORT = process.env.PORT || 10000;
const GROQ_API_KEY = process.env.GROQ_API_KEY;
const GROQ_MODEL = process.env.GROQ_MODEL || 'qwen/qwen3.8-27b';

app.get('/health', (_req, res) => res.json({ ok: true, service: 'pool-trajectory-vision' }));

app.post('/api/vision/analyze', async (req, res) => {
  try {
    if (!GROQ_API_KEY) return res.status(503).json({ error: 'GROQ_API_KEY is not configured on Render.' });
    const image = req.body?.image;
    if (typeof image !== 'string' || !/^data:image\/(jpeg|jpg|png|webp);base64,/.test(image)) {
      return res.status(400).json({ error: 'Send JSON {"image":"data:image/jpeg;base64,..."}.' });
    }
    if (image.length > 7_000_000) return res.status(413).json({ error: 'Image too large. Resize screenshot before upload.' });

    const prompt = `Analyze this screenshot of a billiards/pool table for a practice and accessibility guide. Return ONLY valid JSON with this schema: {"table":{"left":number,"top":number,"right":number,"bottom":number},"pockets":[{"x":number,"y":number}],"cue_ball":{"x":number,"y":number,"confidence":number},"balls":[{"number":number|null,"group":"solid|stripe|eight|unknown","x":number,"y":number,"confidence":number}],"notes":[string]}. All x/y coordinates must be normalized from 0 to 1000 relative to the FULL image. Identify only balls clearly visible on the table. Do not invent obscured balls. If the cue ball cannot be identified, set cue_ball to null. Confidence is 0 to 1. If the image is not a pool table, return empty arrays and notes explaining that. Do not provide game automation or claim perfect accuracy.`;

    const response = await fetch('https://api.groq.com/openai/v1/chat/completions', {
      method: 'POST',
      headers: { 'Authorization': `Bearer ${GROQ_API_KEY}`, 'Content-Type': 'application/json' },
      body: JSON.stringify({
        model: GROQ_MODEL,
        messages: [{ role: 'user', content: [
          { type: 'text', text: prompt },
          { type: 'image_url', image_url: { url: image } }
        ] }],
        temperature: 0.1,
        max_completion_tokens: 1200,
        response_format: { type: 'json_object' }
      })
    });
    const raw = await response.json();
    if (!response.ok) return res.status(response.status).json({ error: raw?.error?.message || 'Groq request failed', details: raw?.error || null });
    const content = raw?.choices?.[0]?.message?.content;
    if (!content) return res.status(502).json({ error: 'Groq returned an empty response.' });
    let analysis;
    try { analysis = JSON.parse(content); }
    catch { return res.status(502).json({ error: 'Groq response was not valid JSON.', raw: content }); }
    return res.json({ ok: true, model: GROQ_MODEL, analysis });
  } catch (error) {
    console.error('vision endpoint error:', error);
    return res.status(500).json({ error: 'Internal server error.' });
  }
});

app.listen(PORT, '0.0.0.0', () => console.log(`Vision API listening on ${PORT}`));
