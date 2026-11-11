'use strict';
/*
 * POST /api/analyze-radio   { image: "data:image/jpeg;base64,..." }
 * Analiza con Claude (visión) una captura de la pantalla de la radio y devuelve qué tan compatible es
 * y cómo conviene configurar el launcher. La captura no se guarda en ningún lado.
 *
 * Requiere ANTHROPIC_API_KEY. Sin ella responde 503 y el sitio sigue funcionando con el análisis local
 * (resolución y proporción de la captura).
 */
const Anthropic = require('@anthropic-ai/sdk');
const { allowMethod, readJson, rateLimit } = require('../lib/http.js');
const budget = require('../lib/budget.js');
const { SCHEMA, SYSTEM, parseImage, cleanAnalysis } = require('../lib/radio-analysis.js');

const MODEL = process.env.ANALYSIS_MODEL || 'claude-opus-5-5';

module.exports = async function handler(req, res) {
  if (!allowMethod(req, res, 'POST')) return;
  if (!rateLimit(req, { max: 4, windowMs: 10 * 60_000 })) {
    return res.status(429).json({ ok: false, error: 'Ya analizamos varias capturas. Espera unos minutos e inténtalo de nuevo.' });
  }
  if (!process.env.ANTHROPIC_API_KEY) {
    return res.status(503).json({ ok: false, error: 'El análisis automático aún no está disponible.' });
  }

  const body = readJson(req) || {};
  const img = parseImage(body.image);
  if (!img) return res.status(400).json({ ok: false, error: 'La imagen no es válida o pesa demasiado. Usa una captura en PNG, JPG o WebP.' });

  // Tope diario: sin AI_DAILY_MAX la IA queda apagada, y al llegar al tope se deja de llamar hasta el día siguiente.
  const cupo = budget.take();
  if (!cupo.ok) {
    return cupo.reason === 'cap'
      ? res.status(429).json({ ok: false, error: 'Hoy ya se hicieron todos los análisis automáticos posibles. Inténtalo mañana.' })
      : res.status(503).json({ ok: false, error: 'El análisis automático aún no está disponible.' });
  }

  try {
    const client = new Anthropic();
    const response = await client.messages.create({
      model: MODEL,
      max_tokens: 2000,
      system: SYSTEM,
      output_config: { format: { type: 'json_schema', schema: SCHEMA } },
      messages: [
        {
          role: 'user',
          content: [
            { type: 'image', source: { type: 'base64', media_type: img.mediaType, data: img.data } },
            { type: 'text', text: 'Analiza esta captura de la pantalla de mi radio y dime cómo adaptar el launcher.' },
          ],
        },
      ],
    });

    if (response.stop_reason === 'refusal') {
      return res.status(422).json({ ok: false, error: 'No pudimos analizar esta imagen. Prueba con otra captura de la pantalla de tu radio.' });
    }
    const block = response.content.find((b) => b.type === 'text');
    let parsed = null;
    try {
      parsed = block ? JSON.parse(block.text) : null;
    } catch (_) {
      parsed = null;
    }
    if (!parsed) return res.status(502).json({ ok: false, error: 'No pudimos interpretar el análisis. Inténtalo de nuevo.' });
    return res.status(200).json({ ok: true, analysis: cleanAnalysis(parsed) });
  } catch (err) {
    if (err instanceof Anthropic.RateLimitError) return res.status(429).json({ ok: false, error: 'El análisis está muy ocupado. Inténtalo en un minuto.' });
    if (err instanceof Anthropic.APIError) return res.status(502).json({ ok: false, error: 'El análisis falló. Inténtalo de nuevo más tarde.' });
    return res.status(500).json({ ok: false, error: 'Error inesperado al analizar la captura.' });
  }
};
