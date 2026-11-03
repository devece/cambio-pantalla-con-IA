'use strict';
/*
 * POST /api/checkout
 * Valida el pedido, recalcula el total con el catálogo y guarda el pedido.
 *
 * Estado actual:
 *  - Con SUPABASE_URL + SUPABASE_SERVICE_ROLE_KEY: guarda el pedido en la tabla `orders` (estado 'pending').
 *  - Sin esas variables: modo demostración (no guarda nada).
 *  - Con además PAYPAL_CLIENT_ID, PAYPAL_CLIENT_SECRET y PAYPAL_CLP_PER_USD: crea el cobro en PayPal
 *    (en USD, porque PayPal no acepta CLP) y devuelve `paymentUrl`. Al volver, api/paypal-return.js captura
 *    el pago y emite la licencia en el servidor, nunca desde el navegador.
 *  - Sin PayPal configurado el pedido queda 'pending' y `paymentUrl` es null.
 */
const crypto = require('node:crypto');
const { validateOrder, quote } = require('../public/shared/order.js');
const { allowMethod, readJson, rateLimit, siteUrl } = require('../lib/http.js');
const db = require('../lib/supabase.js');
const paypal = require('../lib/paypal.js');

module.exports = async function handler(req, res) {
  if (!allowMethod(req, res, 'POST')) return;
  if (!rateLimit(req, { max: 8 })) return res.status(429).json({ ok: false, error: 'Demasiados intentos. Espera un minuto.' });

  const body = readJson(req);
  if (!body) return res.status(400).json({ ok: false, error: 'Pedido inválido.' });

  const { ok, errors, clean } = validateOrder(body);
  if (!ok) return res.status(422).json({ ok: false, errors });

  const q = quote(clean);
  const orderId = 'TB-' + crypto.randomBytes(4).toString('hex').toUpperCase();
  // Llave secreta del pedido: solo quien lo creó puede subir su foto después (se guarda únicamente su hash).
  const photoToken = crypto.randomBytes(16).toString('hex');
  const photoTokenHash = crypto.createHash('sha256').update(photoToken).digest('hex');

  if (!db.configured()) {
    return res.status(200).json({
      ok: true,
      mode: 'demo',
      orderId,
      total: q.total,
      currency: q.currency,
      paymentUrl: null,
      message: 'Modo demostración: el pedido no se guardó y no se cobró nada. Con el pago real recibirías tu código de activación (TBL-XXXX-XXXX-XXXX) para escribirlo en la app, en tu radio.',
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
        config: { radio: clean.radio, car: clean.car, style: clean.style, features: clean.features, photoTokenHash },
      },
    });
  } catch (err) {
    console.error('checkout: no se pudo guardar el pedido', err.status, err.detail);
    return res.status(502).json({ ok: false, error: 'No pudimos guardar tu pedido. Intenta de nuevo en un momento.' });
  }

  if (!paypal.configured()) {
    return res.status(200).json({
      ok: true,
      mode: 'pending-payment',
      orderId,
      photoToken,
      total: q.total,
      currency: q.currency,
      paymentUrl: null,
    });
  }

  try {
    const amount = paypal.clpToUsd(q.total);
    const site = siteUrl(req);
    const pp = await paypal.createOrder({
      orderId,
      amount,
      description: `Tablero - ${q.lines.map((l) => l.name).join(', ')}`.slice(0, 127),
      returnUrl: `${site}/api/paypal-return?order=${orderId}`,
      cancelUrl: `${site}/?pago=cancelado&pedido=${orderId}`,
      brand: 'Tablero',
    });
    await db.rest(`orders?public_id=eq.${orderId}`, {
      method: 'PATCH',
      body: { payment_ref: pp.id, charge_amount: amount, charge_currency: 'USD' },
    });
    return res.status(200).json({
      ok: true,
      mode: 'paypal',
      orderId,
      photoToken,
      total: q.total,
      currency: q.currency,
      paymentUrl: pp.approveUrl,
    });
  } catch (err) {
    console.error('checkout: no se pudo crear el cobro en PayPal', err.status, err.detail || err.message);
    return res.status(502).json({ ok: false, error: 'No pudimos iniciar el pago con PayPal. Intenta de nuevo en un momento.' });
  }
};
