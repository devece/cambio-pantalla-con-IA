'use strict';
/*
 * POST /api/license   { code, deviceId }
 * El launcher en la radio llama aquí para activar su código.
 * Regla: un código se liga a UNA radio. Si ya está ligado a otra, se rechaza.
 * Solo se guarda un hash (HMAC) del identificador de la radio.
 * Respuesta correcta: { ok: true, status: 'active', plan: 'completo', design }  (`plan` es un campo heredado).
 * `design` es lo que el cliente armó en el sitio (configuración + enlaces temporales a su foto) o null si el código se creó a mano.
 *
 * Requiere SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY y LICENSE_PEPPER.
 */
const { normalizeCode, hashDeviceId } = require('../lib/license.js');
const { allowMethod, readJson, rateLimit } = require('../lib/http.js');
const db = require('../lib/supabase.js');
const { buildDesign } = require('../lib/design.js');
const { LICENSE_PLAN } = require('../lib/legacy.js');

module.exports = async function handler(req, res) {
  if (!allowMethod(req, res, 'POST')) return;
  if (!rateLimit(req, { max: 12 })) return res.status(429).json({ ok: false, error: 'Demasiados intentos. Espera un minuto.' });

  if (!db.configured() || !process.env.LICENSE_PEPPER) {
    return res.status(503).json({ ok: false, error: 'El servicio de licencias aún no está configurado.' });
  }

  const body = readJson(req) || {};
  const code = normalizeCode(body.code);
  const deviceId = typeof body.deviceId === 'string' ? body.deviceId.trim() : '';
  if (!code || deviceId.length < 8 || deviceId.length > 128) {
    return res.status(400).json({ ok: false, error: 'Código o radio inválidos.' });
  }
  const deviceHash = hashDeviceId(deviceId, process.env.LICENSE_PEPPER);

  try {
    const rows = await db.rest(`licenses?code=eq.${encodeURIComponent(code)}&select=code,status,device_hash,order_public_id&limit=1`);
    const lic = rows && rows[0];
    // Misma respuesta para "no existe" y "revocada": no revelamos qué códigos existen.
    if (!lic || lic.status !== 'active') return res.status(404).json({ ok: false, error: 'Código no válido.' });

    if (lic.device_hash && lic.device_hash !== deviceHash) {
      return res.status(409).json({ ok: false, error: 'Este código ya está activo en otra radio. Escríbenos para pasarlo a tu radio nueva.' });
    }
    if (!lic.device_hash) {
      // Condición `device_hash=is.null` evita que dos activaciones simultáneas ganen las dos.
      const updated = await db.rest(`licenses?code=eq.${encodeURIComponent(code)}&device_hash=is.null`, {
        method: 'PATCH',
        body: { device_hash: deviceHash, activated_at: new Date().toISOString() },
      });
      if (!updated || !updated.length) return res.status(409).json({ ok: false, error: 'Este código ya fue activado.' });
    }
    const orders = await db.rest(`orders?public_id=eq.${encodeURIComponent(lic.order_public_id)}&select=config&limit=1`);
    if (!orders || !orders[0]) throw new Error('Licencia sin pedido asociado');
    const design = await buildDesign(orders[0].config);
    // `plan` es un campo heredado que la app todavía lee. Hoy es siempre el mismo (lo incluye todo), también para pedidos viejos.
    return res.status(200).json({ ok: true, status: 'active', plan: LICENSE_PLAN, design });
  } catch (err) {
    console.error('license: error', err.status, err.detail);
    return res.status(502).json({ ok: false, error: 'No pudimos verificar el código. Intenta de nuevo.' });
  }
};
