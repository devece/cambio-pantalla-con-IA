'use strict';
/*
 * POST /api/detect-lights   { image: "data:image/jpeg;base64,..." }
 * Ubica con Claude (visión) los faros del auto en la foto recortada del cliente. No guarda la imagen.
 * Requiere ANTHROPIC_API_KEY; sin ella responde 503 y el sitio deja que el cliente marque los faros tocando la foto.
 */
const Anthropic = require('@anthropic-ai/sdk');
const { allowMethod, readJson, rateLimit } = require('../lib/http.js');
const budget = require('../lib/budget.js');
const { parseImage } = require('../lib/radio-analysis.js');
const { SCHEMA, SYSTEM, cleanLights } = require('../lib/lights.js');

const MODEL = process.env.ANALYSIS_MODEL || 'claude-opus-5-5';

module.exports = async function handler(req, res) {
  if (!allowMethod(req, res, 'POST')) return;
  if (!rateLimit(req, { max: 6, windowMs: 10 * 60_000 })) {
    return res.status(429).json({ ok: false, error: 'Ya detectamos varias veces. Espera unos minutos o marca los faros tú.' });
  }
  if (!process.env.ANTHROPIC_API_KEY) {
    return res.status(503).json({ ok: false, error: 'La detección automática aún no está disponible. Marca los faros tocando la foto.' });
  }
  const img = parseImage((readJson(req) || {}).image);
  if (!img) return res.status(400).json({ ok: false, error: 'La imagen no es válida o pesa demasiado.' });

  // Tope diario: sin AI_DAILY_MAX la IA queda apagada, y al llegar al tope se deja de llamar hasta el día siguiente.
  const cupo = budget.take();
  if (!cupo.ok) {
    return cupo.reason === 'cap'
      ? res.status(429).json({ ok: false, error: 'Hoy ya se hicieron todos los análisis automáticos posibles. Inténtalo mañana.' })
      : res.status(503).json({ ok: false, error: 'La detección automática aún no está disponible. Marca los faros tocando la foto.' });
  }

  try {
    const client = new Anthropic();
    const response = await client.messages.create({
      model: MODEL,
      max_tokens: 1500,
      system: SYSTEM,
      output_config: { format: { type: 'json_schema', schema: SCHEMA } },
      messages: [
        {
          role: 'user',
          content: [
            { type: 'image', source: { type: 'base64', media_type: img.mediaType, data: img.data } },
            { type: 'text', text: 'Ubica las luces de este auto.' },
          ],
        },
      ],
    });
    if (response.stop_reason === 'refusal') return res.status(422).json({ ok: false, error: 'No pudimos analizar esta imagen. Marca los faros tú.' });
    const block = response.content.find((b) => b.type === 'text');
    let parsed = null;
    try {
      parsed = block ? JSON.parse(block.text) : null;
    } catch (_) {
      parsed = null;
    }
    if (!parsed) return res.status(502).json({ ok: false, error: 'No pudimos interpretar la respuesta. Marca los faros tú.' });
    return res.status(200).json({ ok: true, ...cleanLights(parsed) });
  } catch (err) {
    if (err instanceof Anthropic.RateLimitError) return res.status(429).json({ ok: false, error: 'La detección está muy ocupada. Inténtalo en un minuto.' });
    if (err instanceof Anthropic.APIError) return res.status(502).json({ ok: false, error: 'La detección falló. Marca los faros tocando la foto.' });
    return res.status(500).json({ ok: false, error: 'Error inesperado al detectar las luces.' });
  }
};
