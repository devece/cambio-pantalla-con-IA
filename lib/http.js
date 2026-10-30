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

/** URL pública del sitio (sin barra final). Define SITE_URL en producción; si no, se deduce de la petición. */
function siteUrl(req) {
  if (process.env.SITE_URL) return process.env.SITE_URL.replace(/\/$/, '');
  const h = req.headers || {};
  const host = h.host || 'localhost:3000';
  const proto = h['x-forwarded-proto'] || (/^(localhost|127\.)/.test(host) ? 'http' : 'https');
  return `${proto}://${host}`;
}

/** Límite simple en memoria por IP. Sirve de freno básico; en producción usa un límite real (Vercel Firewall / Upstash). */
const hits = new Map();
function rateLimit(req, { max = 10, windowMs = 60_000 } = {}) {
  const ip = String((req.headers && (req.headers['x-forwarded-for'] || '')) || 'local').split(',')[0].trim();
  const now = Date.now();
  const recent = (hits.get(ip) || []).filter((t) => now - t < windowMs);
  recent.push(now);
  hits.set(ip, recent);
  if (hits.size > 5000) hits.clear();
  return recent.length <= max;
}

module.exports = { allowMethod, readJson, rateLimit, siteUrl, MAX_BODY };
