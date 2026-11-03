'use strict';
/*
 * POST /api/order-photo   { orderId, kind: 'day' | 'night', image: "data:image/png;base64,...", cut?: boolean, lights?: [...] }
 * Guarda la foto del auto del cliente (ya sin fondo, hecha en su navegador) junto a su pedido, para que la app de la radio
 * la descargue al activar el código. Solo se acepta mientras el pedido está pendiente de pago.
 *
 * Requiere SUPABASE_URL y SUPABASE_SERVICE_ROLE_KEY y el bucket privado `tablero-fotos`.
 */
const { allowMethod, readJson, rateLimit } = require('../lib/http.js');
const { parseImage } = require('../lib/radio-analysis.js');
const { cleanLights } = require('../lib/lights.js');
const db = require('../lib/supabase.js');

const ORDER_ID = /^TB-[0-9A-F]{8}$/;
const MAX_BYTES = 2_600_000;
const EXT = { 'image/png': 'png', 'image/jpeg': 'jpg', 'image/webp': 'webp' };

/** Comprueba que los primeros bytes correspondan al tipo declarado (no basta con confiar en el texto). */
function magicOk(buf, mediaType) {
  if (mediaType === 'image/png') return buf.length > 8 && buf.slice(0, 4).equals(Buffer.from([0x89, 0x50, 0x4e, 0x47]));
  if (mediaType === 'image/jpeg') return buf.length > 3 && buf[0] === 0xff && buf[1] === 0xd8 && buf[2] === 0xff;
  if (mediaType === 'image/webp') return buf.length > 12 && buf.slice(0, 4).toString() === 'RIFF' && buf.slice(8, 12).toString() === 'WEBP';
  return false;
}

module.exports = async function handler(req, res) {
  if (!allowMethod(req, res, 'POST')) return;
  if (!rateLimit(req, { max: 12 })) return res.status(429).json({ ok: false, error: 'Demasiados intentos. Espera un minuto.' });
  if (!db.configured()) return res.status(503).json({ ok: false, error: 'El guardado de fotos aún no está disponible.' });

  const body = readJson(req) || {};
  const orderId = String(body.orderId || '').toUpperCase();
  if (!ORDER_ID.test(orderId)) return res.status(400).json({ ok: false, error: 'Número de pedido no válido.' });
  if (body.kind !== 'day' && body.kind !== 'night') return res.status(400).json({ ok: false, error: 'Tipo de foto no válido.' });
  const img = parseImage(body.image);
  if (!img) return res.status(400).json({ ok: false, error: 'La imagen no es válida o pesa demasiado.' });
  const buf = Buffer.from(img.data, 'base64');
  if (buf.length > MAX_BYTES || !magicOk(buf, img.mediaType)) return res.status(400).json({ ok: false, error: 'La imagen no es válida o pesa demasiado.' });

  try {
    const rows = await db.rest(`orders?public_id=eq.${orderId}&select=status,config&limit=1`);
    const order = rows && rows[0];
    if (!order) return res.status(404).json({ ok: false, error: 'No encontramos ese pedido.' });
    if (order.status !== 'pending') return res.status(409).json({ ok: false, error: 'Este pedido ya no admite cambios de foto.' });

    const ext = EXT[img.mediaType];
    const path = `orders/${orderId}/${body.kind}.${ext}`;
    await db.storageUpload(db.PHOTO_BUCKET, path, buf, img.mediaType);

    const config = order.config && typeof order.config === 'object' ? order.config : {};
    const photo = Object.assign({}, config.photo || {});
    if (body.kind === 'day') {
      photo.day = path;
      photo.cut = body.cut === true;
      photo.lights = cleanLights({ lights: body.lights }).lights;
    } else {
      photo.night = path;
    }
    await db.rest(`orders?public_id=eq.${orderId}`, { method: 'PATCH', body: { config: Object.assign({}, config, { photo }) } });
    return res.status(200).json({ ok: true, kind: body.kind });
  } catch (err) {
    console.error('order-photo: error', err.status, err.detail || err.message);
    return res.status(502).json({ ok: false, error: 'No pudimos guardar tu foto. Inténtalo de nuevo.' });
  }
};
