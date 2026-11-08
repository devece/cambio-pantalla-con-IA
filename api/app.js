'use strict';
/*
 * GET /api/app?d=<diseño en base64url>
 * Entrega la APK de Tablero con el diseño que el cliente armó en el sitio ya adentro (en el bloque de firmas, sin tocar la
 * firma: ver lib/apk-inject.js). Al abrirla por primera vez, la app lee su propio archivo y aplica ese diseño: sin códigos.
 *
 * El diseño pasa por aquí una sola vez y no se guarda. Si algo falla (no hay diseño válido, no se puede bajar la APK o no
 * trae bloque de firmas), se redirige a la APK normal: el cliente igual puede instalar la app y elegir todo en Ajustes.
 * La foto del auto no viaja: se elige en la app (Ajustes → Foto de tu auto).
 */
const { rateLimit } = require('../lib/http.js');
const { DESIGN_ID, injectPair } = require('../lib/apk-inject.js');
const { validateOrder } = require('../public/shared/order.js');

const APK_URL = process.env.TABLERO_APK_URL || 'https://github.com/devece/cambio-pantalla-con-IA/releases/download/app-latest/tablero.apk';
const MAX_PARAM = 6000;
const MAX_APK = 12 * 1024 * 1024;
const TTL_MS = 5 * 60 * 1000;

let cache = { at: 0, buf: null };

async function baseApk() {
  if (cache.buf && Date.now() - cache.at < TTL_MS) return cache.buf;
  const r = await fetch(APK_URL, { redirect: 'follow' });
  if (!r.ok) throw new Error('No se pudo bajar la APK (' + r.status + ')');
  const buf = Buffer.from(await r.arrayBuffer());
  if (buf.length < 64 || buf.length > MAX_APK) throw new Error('La APK tiene un tamaño inesperado.');
  cache = { at: Date.now(), buf };
  return buf;
}

/** El diseño limpio (solo campos conocidos) en el formato que lee la app, o null si el parámetro no sirve. */
function designFromParam(d) {
  if (typeof d !== 'string' || !d || d.length > MAX_PARAM || !/^[A-Za-z0-9_-]+$/.test(d)) return null;
  let raw;
  try {
    raw = JSON.parse(Buffer.from(d, 'base64url').toString('utf8'));
  } catch (_) {
    return null;
  }
  if (!raw || typeof raw !== 'object') return null;
  // La radio se da por Android: este diseño solo se usa para instalar en una. Los errores de contacto no importan aquí.
  const { clean } = validateOrder({ ...raw, radio: { type: 'android' } });
  const { car, style, features } = clean;
  return {
    v: 1,
    car: { type: car.type, color: car.color, name: car.name, greeting: car.greeting, phrase: car.phrase },
    style,
    features,
  };
}

function fallback(res) {
  res.statusCode = 302;
  res.setHeader('Location', APK_URL);
  res.setHeader('Cache-Control', 'no-store');
  res.end();
}

module.exports = async function handler(req, res) {
  if (req.method !== 'GET' && req.method !== 'HEAD') {
    res.setHeader('Allow', 'GET, HEAD');
    return res.status(405).json({ ok: false, error: 'Método no permitido' });
  }
  if (!rateLimit(req, { max: 20 })) return res.status(429).json({ ok: false, error: 'Demasiados intentos. Espera un minuto.' });

  const design = designFromParam(req.query && req.query.d);
  if (!design) return fallback(res);
  try {
    const out = injectPair(await baseApk(), DESIGN_ID, Buffer.from(JSON.stringify(design), 'utf8'));
    res.statusCode = 200;
    res.setHeader('Content-Type', 'application/vnd.android.package-archive');
    res.setHeader('Content-Disposition', 'attachment; filename="tablero.apk"');
    res.setHeader('Content-Length', String(out.length));
    res.setHeader('Cache-Control', 'no-store');
    res.end(req.method === 'HEAD' ? undefined : out);
  } catch (_) {
    fallback(res);
  }
};

module.exports.designFromParam = designFromParam;
/** Solo para las pruebas: olvida la APK guardada en memoria. */
module.exports._resetCache = () => { cache = { at: 0, buf: null }; };
