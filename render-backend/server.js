import express from 'express';
import helmet from 'helmet';

const app = express();
app.use(helmet());
app.use(express.json({ limit: '8mb' }));

const PORT = Number(process.env.PORT || 10000);
const GROQ_API_KEY = process.env.GROQ_API_KEY;
const GROQ_MODEL = process.env.GROQ_MODEL || 'qwen/qwen3.8-27b';

app.get('/health', (_req, res) => {
  res.json({ ok: true, service: 'pool-practice-vision', timestamp: new Date().toISOString() });
});

app.get('/groq/status', (_req, res) => {
  res.json({ configured: Boolean(GROQ_API_KEY), model: GROQ_MODEL, apiKeyExposed: false });
});

app.post('/api/vision/analyze', async (req, res) => {
  try {
    if (!GROQ_API_KEY) return res.status(503).json({ ok: false, error: 'GROQ_API_KEY is not configured on the server.' });
    const image = req.body?.image;
    if (typeof image !== 'string' || !/^data:image\/(jpeg|jpg|png|webp);base64,[A-Za-z0-9+/=]+$/.test(image)) {
      return res.status(400).json({ ok: false, error: 'Expected image as data:image/jpeg;base64,...' });
    }
    if (image.length > 7_000_000) return res.status(413).json({ ok: false, error: 'Image too large; resize it and retry.' });

    const prompt = `You are an image-analysis assistant for OFFLINE billiards practice. Inspect only what is clearly visible. Return ONLY JSON with this schema: {"table":{"left":number,"top":number,"right":number,"bottom":number},"cue_ball":{"x":number,"y":number,"confidence":number}|null,"balls":[{"number":number|null,"group":"solid|stripe|eight|unknown","x":number,"y":number,"confidence":number}],"notes":[string]}. Coordinates must be normalized 0..1000 relative to the full image. Do not invent hidden balls. If unsure, use null or low confidence and explain uncertainty. Do not control a game, interact with a live match, or claim perfect accuracy. This is visual practice feedback only.`;

    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), 55000);
    let upstream;
    try {
      upstream = await fetch('https://api.groq.com/openai/v1/chat/completions', {
        method: 'POST', signal: controller.signal,
        headers: { Authorization: `Bearer ${GROQ_API_KEY}`, 'Content-Type': 'application/json' },
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
    } finally { clearTimeout(timer); }

    const raw = await upstream.json().catch(() => ({}));
    if (!upstream.ok) {
      const status = upstream.status === 429 ? 429 : (upstream.status >= 500 ? 502 : upstream.status);
      return res.status(status).json({ ok: false, error: raw?.error?.message || `Groq request failed (${upstream.status}).` });
    }
    const content = raw?.choices?.[0]?.message?.content;
    if (typeof content !== 'string' || !content.trim()) return res.status(502).json({ ok: false, error: 'Groq returned an empty response.' });
    let analysis;
    try { analysis = JSON.parse(content); }
    catch { return res.status(502).json({ ok: false, error: 'Groq response was not valid JSON.' }); }
    return res.json({ ok: true, model: GROQ_MODEL, analysis });
  } catch (error) {
    const timeout = error?.name === 'AbortError';
    return res.status(timeout ? 504 : 500).json({ ok: false, error: timeout ? 'Groq request timed out.' : 'Server could not complete image analysis.' });
  }
});

app.listen(PORT, '0.0.0.0', () => console.log(`Pool practice vision API listening on ${PORT}`));
