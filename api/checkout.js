'use strict';
/*
 * POST /api/checkout
 * Valida el pedido, recalcula el total con el catálogo y guarda el pedido.
 *
 * Estado actual:
 *  - Con SUPABASE_URL + SUPABASE_SERVICE_ROLE_KEY: guarda el pedido en la tabla `orders` (estado 'pending').
 *  - Sin esas variables: modo demostración (no guarda nada).
 *  - El cobro real (Flow, Mercado Pago, Webpay...) NO está implementado: ver docs/ROADMAP.md.
 *    Cuando exista, esta función devolverá `paymentUrl` y la licencia se emitirá desde el webhook del proveedor,
 *    nunca desde el navegador.
 */
const crypto = require('node:crypto');
const { validateOrder, quote } = require('../public/shared/order.js');
const { allowMethod, readJson, rateLimit } = require('../lib/http.js');
const db = require('../lib/supabase.js');

module.exports = async function handler(req, res) {
  if (!allowMethod(req, res, 'POST')) return;
  if (!rateLimit(req, { max: 8 })) return res.status(429).json({ ok: false, error: 'Demasiados intentos. Espera un minuto.' });

  const body = readJson(req);
  if (!body) return res.status(400).json({ ok: false, error: 'Pedido inválido.' });

  const { ok, errors, clean } = validateOrder(body);
  if (!ok) return res.status(422).json({ ok: false, errors });

  const q = quote(clean);
  const orderId = 'TB-' + crypto.randomBytes(4).toString('hex').toUpperCase();

  if (!db.configured()) {
    return res.status(200).json({
      ok: true,
      mode: 'demo',
      orderId,
      total: q.total,
      currency: q.currency,
      paymentUrl: null,
      message: 'Modo demostración: el pedido no se guardó y no se cobró nada.',
    });
  }

  try {
    await db.rest('orders', {
      method: 'POST',
      body: {
        public_id: orderId,
        status: 'pending',
        plan: clean.plan,
        addons: clean.addons,
        total: q.total,
        currency: q.currency,
        contact_kind: clean.contact.kind,
        contact_value: clean.contact.value,
        config: { radio: clean.radio, car: clean.car, style: clean.style, features: clean.features },
      },
    });
  } catch (err) {
    console.error('checkout: no se pudo guardar el pedido', err.status, err.detail);
    return res.status(502).json({ ok: false, error: 'No pudimos guardar tu pedido. Intenta de nuevo en un momento.' });
  }

  return res.status(200).json({
    ok: true,
    mode: 'pending-payment',
    orderId,
    total: q.total,
    currency: q.currency,
    paymentUrl: null, // TODO: crear el cobro con el proveedor elegido y devolver su URL
  });
};
