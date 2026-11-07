'use strict';
/*
 * POST /api/order
 * Valida el pedido y lo guarda. Tablero es gratis: no hay cobro. Quien arma la pantalla recibe el pedido
 * (el cliente se lo envía por el contacto del sitio) y le entrega su código de activación desde el panel de administración.
 *
 *  - Con SUPABASE_URL + SUPABASE_SERVICE_ROLE_KEY: guarda el pedido en la tabla `orders` (estado 'pending').
 *  - Sin esas variables: modo demostración (no guarda nada).
 */
const crypto = require('node:crypto');
const { validateOrder } = require('../public/shared/order.js');
const { allowMethod, readJson, rateLimit } = require('../lib/http.js');
const db = require('../lib/supabase.js');
const { LEGACY_ORDER_COLUMNS } = require('../lib/legacy.js');

module.exports = async function handler(req, res) {
  if (!allowMethod(req, res, 'POST')) return;
  if (!rateLimit(req, { max: 8 })) return res.status(429).json({ ok: false, error: 'Demasiados intentos. Espera un minuto.' });

  const body = readJson(req);
  if (!body) return res.status(400).json({ ok: false, error: 'Pedido inválido.' });

  const { ok, errors, clean } = validateOrder(body);
  if (!ok) return res.status(422).json({ ok: false, errors });

  const orderId = 'TB-' + crypto.randomBytes(4).toString('hex').toUpperCase();
  // Llave secreta del pedido: solo quien lo creó puede subir su foto después (se guarda únicamente su hash).
  const photoToken = crypto.randomBytes(16).toString('hex');
  const photoTokenHash = crypto.createHash('sha256').update(photoToken).digest('hex');

  if (!db.configured()) {
    return res.status(200).json({
      ok: true,
      mode: 'demo',
      orderId,
      message: 'Modo demostración: el pedido no se guardó. Con el sitio en funcionamiento recibirías tu código de activación (TBL-XXXX-XXXX-XXXX) para escribirlo en la app, en tu radio.',
    });
  }

  try {
    await db.rest('orders', {
      method: 'POST',
      body: {
        public_id: orderId,
        status: 'pending',
        ...LEGACY_ORDER_COLUMNS,
        contact_kind: clean.contact.kind,
        contact_value: clean.contact.value,
        config: { radio: clean.radio, car: clean.car, style: clean.style, features: clean.features, photoTokenHash },
      },
    });
  } catch (err) {
    console.error('order: no se pudo guardar el pedido', err.status, err.detail);
    return res.status(502).json({ ok: false, error: 'No pudimos guardar tu pedido. Intenta de nuevo en un momento.' });
  }

  return res.status(200).json({ ok: true, mode: 'saved', orderId, photoToken });
};
