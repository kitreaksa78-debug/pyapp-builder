import express from 'express';
import helmet from 'helmet';

const app = express();
app.use(helmet());
app.use(express.json({ limit: '8mb' }));

const PORT = process.env.PORT || 10000;
const GROQ_URL = 'https://api.groq.com/openai/v1/chat/completions';

function validKey(key) {
  return typeof key === 'string' && key.startsWith('gsk_') && key.length > 20;
}
function validModel(model) {
  return typeof model === 'string' && /^[a-zA-Z0-9._/-]{3,100}$/.test(model);
}
async function callGroq(apiKey, model, messages, maxTokens = 500) {
  const response = await fetch(GROQ_URL, {
    method: 'POST',
    headers: {
      'Authorization': `Bearer ${apiKey}`,
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({
      model,
      messages,
      temperature: 0.2,
      max_completion_tokens: maxTokens
    })
  });
  const raw = await response.json().catch(() => ({}));
  if (!response.ok) {
    const message = raw?.error?.message || `Groq HTTP ${response.status}`;
    const error = new Error(message);
    error.status = response.status;
    throw error;
  }
  return raw;
}

app.get('/health', (_req, res) => res.json({ ok: true, service: 'groq-vision-proxy' }));

app.post('/api/groq/test', async (req, res) => {
  try {
    const { apiKey, model } = req.body || {};
    if (!validKey(apiKey)) return res.status(400).json({ error: 'Invalid or missing Groq API key.' });
    if (!validModel(model)) return res.status(400).json({ error: 'Invalid or missing model.' });
    const raw = await callGroq(apiKey, model, [
      { role: 'user', content: 'Reply with exactly: GROQ_CONNECTED' }
    ], 30);
    return res.json({ ok: true, model, message: raw?.choices?.[0]?.message?.content || 'Connected' });
  } catch (error) {
    return res.status(error.status || 502).json({ error: error.message || 'Groq connection failed.' });
  }
});

app.post('/api/vision/analyze', async (req, res) => {
  try {
    const { apiKey, model, image } = req.body || {};
    if (!validKey(apiKey)) return res.status(400).json({ error: 'Invalid or missing Groq API key.' });
    if (!validModel(model)) return res.status(400).json({ error: 'Invalid or missing model.' });
    if (typeof image !== 'string' ||
        !/^data:image\/(jpeg|jpg|png|webp);base64,/.test(image)) {
      return res.status(400).json({ error: 'Send a supported image as a base64 data URL.' });
    }
    if (image.length > 7_000_000) return res.status(413).json({ error: 'Image too large.' });

    const prompt = `Describe the visible scene in this image for general practice and accessibility. If it shows a billiards table, identify the table area and describe clearly visible objects in ordinary language. Do not calculate shot angles, trajectories, bank shots, pocket paths, or recommend a target/aim point. Do not control or automate any game. Return concise JSON: {"scene":"...","visible_objects":[{"description":"...","confidence":"high|medium|low"}],"practice_notes":["..."],"limitations":["..."]}. Never claim perfect detection.`;
    const raw = await callGroq(apiKey, model, [{
      role: 'user',
      content: [
        { type: 'text', text: prompt },
        { type: 'image_url', image_url: { url: image } }
      ]
    }], 900);
    const content = raw?.choices?.[0]?.message?.content;
    if (!content) return res.status(502).json({ error: 'Groq returned an empty response.' });
    let analysis;
    try { analysis = JSON.parse(content); }
    catch { analysis = { scene: content, visible_objects: [], practice_notes: [], limitations: ['Model did not return structured JSON.'] }; }
    return res.json({ ok: true, model, analysis });
  } catch (error) {
    return res.status(error.status || 502).json({ error: error.message || 'Vision request failed.' });
  }
});

app.listen(PORT, '0.0.0.0', () => console.log(`Groq proxy listening on ${PORT}`));
