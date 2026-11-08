'use strict';
/*
 * GET  /api/stats            → { ok, visits, downloads, downloadsDesign, downloadsPlain }   (el pie del sitio muestra visitas y el total de descargas)
 * POST /api/stats {kind:'visit'} → suma una visita (una por sesión, la manda el sitio; los robots no cuentan)
 * Las descargas las cuenta api/app.js; aquí no se pueden sumar desde el navegador.
 */
const { rateLimit, readJson } = require('../lib/http.js');
const stats = require('../lib/stats.js');

module.exports = async function handler(req, res) {
  if (req.method === 'GET') {
    const t = await stats.totals();
    // El número cambia poco: se guarda un minuto en la red de Vercel y no se consulta la base en cada visita.
    res.setHeader('Cache-Control', 'public, s-maxage=60, stale-while-revalidate=300');
    if (!t) return res.status(503).json({ ok: false });
    return res.status(200).json({ ok: true, visits: t.visits, downloads: t.downloads, downloadsDesign: t.downloadsDesign, downloadsPlain: t.downloadsPlain });
  }
  if (req.method === 'POST') {
    if (!rateLimit(req, { max: 30 })) return res.status(429).json({ ok: false });
    const body = readJson(req) || {};
    if (body.kind !== 'visit') return res.status(400).json({ ok: false, error: 'Tipo inválido.' });
    if (!stats.isBot(req)) await stats.hit('visits');
    return res.status(204).end();
  }
  res.setHeader('Allow', 'GET, POST');
  return res.status(405).json({ ok: false, error: 'Método no permitido' });
};
