'use strict';
/* Utilidades HTTP para las funciones de api/ (Vercel). */

const MAX_BODY = 32 * 1024; // el pedido es solo texto; las fotos NO viajan por aquí

function allowMethod(req, res, method) {
  if (req.method === method) return true;
  res.setHeader('Allow', method);
  res.status(405).json({ ok: false, error: 'Método no permitido' });
  return false;
}

function readJson(req) {
  // Vercel ya entrega req.body parseado cuando el Content-Type es JSON.
  if (req.body && typeof req.body === 'object') return req.body;
  if (typeof req.body === 'string') {
    if (req.body.length > MAX_BODY) return null;
    try {
      return JSON.parse(req.body);
    } catch (_) {
      return null;
    }
  }
  return null;
}

/** Límite simple en memoria por IP. Sirve de freno básico; en producción usa un límite real (Vercel Firewall / Upstash). */
const MAX_TRACKED = 5000;
const hits = new Map();
function rateLimit(req, { max = 10, windowMs = 60_000 } = {}) {
  const ip = String((req.headers && (req.headers['x-forwarded-for'] || '')) || 'local').split(',')[0].trim();
  const now = Date.now();
  const recent = (hits.get(ip) || []).filter((t) => now - t < windowMs);
  recent.push(now);
  hits.delete(ip); // se reinserta al final: el Map queda ordenado por uso reciente y se expulsa al que hace más que no aparece
  hits.set(ip, recent);
  if (hits.size > MAX_TRACKED) {
    // Se sueltan los que hace más tiempo no aparecen; vaciar todo dejaría reiniciar sus contadores a quien llene el mapa.
    let drop = Math.ceil(MAX_TRACKED / 10);
    for (const k of hits.keys()) { if (drop-- <= 0) break; hits.delete(k); }
  }
  return recent.length <= max;
}

module.exports = { allowMethod, readJson, rateLimit, MAX_BODY, _hits: hits };
