'use strict';
/*
 * GET  /api/app?d=<diseño en base64url>
 * POST /api/app   { d, photo: { day, night?, cut } }   (la foto va como data URL: PNG si cut, JPEG si no)
 * Entrega la APK de Tablero con el diseño (y la foto, si viene) que el cliente armó en el sitio ya adentro (en el bloque de firmas, sin tocar la
 * firma: ver lib/apk-inject.js). Al abrirla por primera vez, la app lee su propio archivo y aplica ese diseño: sin códigos.
 *
 * El diseño pasa por aquí una sola vez y no se guarda. Si algo falla (no hay diseño válido, no se puede bajar la APK o no
 * trae bloque de firmas), se redirige a la APK normal: el cliente igual puede instalar la app y elegir todo en Ajustes.
 * La foto del auto solo viaja por POST (no cabe en una dirección) y pasa una sola vez por aquí: no se guarda. Si es muy grande o
 * no es una imagen válida, se responde 413/422 y el sitio entrega la APK sin ella (se elige en la app: Ajustes → Foto de tu auto).
 */
const crypto = require('node:crypto');
const { rateLimit } = require('../lib/http.js');
const { DESIGN_ID, PHOTO_DAY_ID, PHOTO_NIGHT_ID, injectPair } = require('../lib/apk-inject.js');
const { validateOrder } = require('../public/shared/order.js');
const stats = require('../lib/stats.js');

const APK_URL = process.env.TABLERO_APK_URL || 'https://github.com/devece/cambio-pantalla-con-IA/releases/download/app-latest/tablero.apk';
// Junto a la APK, la compilación publica version.json con su huella SHA-256: la APK base solo se usa si coincide.
const VERSION_URL = process.env.TABLERO_VERSION_URL || APK_URL.replace(/[^/]*$/, '') + 'version.json';
const MAX_PARAM = 6000;
const MAX_APK = 12 * 1024 * 1024;
const TTL_MS = 5 * 60 * 1000;
// La respuesta de una función de Vercel no puede pasar de 4,5 MB: la APK pesa ~2,2 MB, así que las fotos tienen un tope.
const MAX_PHOTO = 1_800_000;
const MAX_PHOTOS_TOTAL = 2_100_000;

let cache = { at: 0, buf: null };

/** Que la APK bajada sea exactamente la publicada: su huella SHA-256 debe coincidir con la de version.json. Lanza si no se puede comprobar. */
async function verifyBase(buf) {
  const r = await fetch(VERSION_URL, { redirect: 'follow' });
  if (!r.ok) throw new Error('No se pudo comprobar la APK (version.json ' + r.status + ')');
  let info;
  try { info = JSON.parse(await r.text()); } catch (_) { throw new Error('version.json no es válido.'); }
  const expected = String((info && info.sha256) || '').toLowerCase();
  if (!/^[0-9a-f]{64}$/.test(expected)) throw new Error('version.json no trae la huella de la APK.');
  const actual = crypto.createHash('sha256').update(buf).digest('hex');
  if (actual !== expected) throw new Error('La APK no coincide con la huella publicada.');
}

async function baseApk() {
  if (cache.buf && Date.now() - cache.at < TTL_MS) return cache.buf;
  const r = await fetch(APK_URL, { redirect: 'follow' });
  if (!r.ok) throw new Error('No se pudo bajar la APK (' + r.status + ')');
  const buf = Buffer.from(await r.arrayBuffer());
  if (buf.length < 64 || buf.length > MAX_APK) throw new Error('La APK tiene un tamaño inesperado.');
  // Si no se puede comprobar, no se usa: el cliente recibe la APK normal (sin su diseño) en vez de una que no sabemos si es la publicada.
  await verifyBase(buf);
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

/** Una imagen en data URL: PNG si `png`, JPEG si no. Devuelve los bytes, o lanza { status } si no sirve. */
function imageFromDataUrl(value, png) {
  const m = typeof value === 'string' && value.match(/^data:image\/(png|jpeg);base64,([A-Za-z0-9+/=]+)$/);
  if (!m || (m[1] === 'png') !== png) throw Object.assign(new Error('Imagen inválida'), { status: 422 });
  const bytes = Buffer.from(m[2], 'base64');
  const magic = png ? [0x89, 0x50, 0x4e, 0x47] : [0xff, 0xd8, 0xff];
  if (bytes.length < 100 || !magic.every((b, i) => bytes[i] === b)) throw Object.assign(new Error('Imagen inválida'), { status: 422 });
  if (bytes.length > MAX_PHOTO) throw Object.assign(new Error('Imagen muy grande'), { status: 413 });
  return bytes;
}

/** La foto del pedido: { day, night, cut } o null si no viene. */
function photoFromBody(p) {
  if (!p || typeof p !== 'object' || !p.day) return null;
  const cut = p.cut === true;
  const day = imageFromDataUrl(p.day, cut);
  const night = cut && p.night ? imageFromDataUrl(p.night, true) : null;
  if (day.length + (night ? night.length : 0) > MAX_PHOTOS_TOTAL) throw Object.assign(new Error('Fotos muy grandes'), { status: 413 });
  return { day, night, cut };
}

/** Redirige a la APK normal (y cuenta la descarga como «normal»: el cliente la recibe igual, sin diseño). */
async function fallback(req, res) {
  if (req.method !== 'HEAD') await stats.countDownload(req, false);
  res.statusCode = 302;
  res.setHeader('Location', APK_URL);
  res.setHeader('Cache-Control', 'no-store');
  res.end();
}

module.exports = async function handler(req, res) {
  const method = req.method;
  if (method !== 'GET' && method !== 'HEAD' && method !== 'POST') {
    res.setHeader('Allow', 'GET, HEAD, POST');
    return res.status(405).json({ ok: false, error: 'Método no permitido' });
  }
  if (!rateLimit(req, { max: method === 'POST' ? 10 : 20 })) return res.status(429).json({ ok: false, error: 'Demasiados intentos. Espera un minuto.' });

  let body = null;
  if (method === 'POST') {
    body = req.body && typeof req.body === 'object' ? req.body : null;
    if (typeof req.body === 'string') { try { body = JSON.parse(req.body); } catch (_) { body = null; } }
    if (!body) return res.status(400).json({ ok: false, error: 'Pedido inválido.' });
  }
  const design = designFromParam(method === 'POST' ? body.d : req.query && req.query.d);
  if (!design) return method === 'POST' ? res.status(400).json({ ok: false, error: 'Diseño inválido.' }) : fallback(req, res);

  let photo = null;
  if (method === 'POST') {
    try {
      photo = photoFromBody(body.photo);
    } catch (e) {
      return res.status(e.status || 422).json({ ok: false, error: e.message });
    }
  }
  try {
    if (photo) design.photo = { cut: photo.cut, night: !!photo.night };
    let out = injectPair(await baseApk(), DESIGN_ID, Buffer.from(JSON.stringify(design), 'utf8'));
    if (photo) {
      out = injectPair(out, PHOTO_DAY_ID, photo.day);
      if (photo.night) out = injectPair(out, PHOTO_NIGHT_ID, photo.night);
    }
    res.statusCode = 200;
    res.setHeader('Content-Type', 'application/vnd.android.package-archive');
    res.setHeader('Content-Disposition', 'attachment; filename="tablero.apk"');
    res.setHeader('Content-Length', String(out.length));
    res.setHeader('Cache-Control', 'no-store');
    if (method !== 'HEAD') await stats.countDownload(req, true);
    res.end(method === 'HEAD' ? undefined : out);
  } catch (_) {
    if (method === 'POST') return res.status(502).json({ ok: false, error: 'No pudimos armar la app.' });
    await fallback(req, res);
  }
};

module.exports.designFromParam = designFromParam;
module.exports.photoFromBody = photoFromBody;
/** Solo para las pruebas: olvida la APK guardada en memoria. */
module.exports._resetCache = () => { cache = { at: 0, buf: null }; };
