'use strict';
/*
 * Cliente mínimo de PayPal (Orders v2) sin dependencias. Solo se usa desde el servidor.
 *
 * PayPal NO acepta pesos chilenos (CLP), así que el cobro se hace en USD con un tipo de cambio fijo
 * que defines tú en PAYPAL_CLP_PER_USD. El monto en USD se guarda en el pedido al crear el cobro y
 * es el que se exige al capturar.
 */

const BASES = { sandbox: 'https://api-m.sandbox.paypal.com', live: 'https://api-m.paypal.com' };

function rate() {
  const n = Number(process.env.PAYPAL_CLP_PER_USD);
  return Number.isFinite(n) && n > 0 ? n : 0;
}

function configured() {
  return !!(process.env.PAYPAL_CLIENT_ID && process.env.PAYPAL_CLIENT_SECRET && rate());
}

function baseUrl() {
  return process.env.PAYPAL_ENV === 'live' ? BASES.live : BASES.sandbox;
}

/** CLP -> USD como texto con 2 decimales, redondeando hacia arriba al centavo. */
function clpToUsd(clp, perUsd = rate()) {
  if (!(perUsd > 0)) throw new Error('Falta PAYPAL_CLP_PER_USD');
  return (Math.ceil((clp * 100) / perUsd) / 100).toFixed(2);
}

async function call(path, { method = 'GET', body, token, headers = {}, basic } = {}) {
  const res = await fetch(baseUrl() + path, {
    method,
    headers: {
      Authorization: basic ? `Basic ${basic}` : `Bearer ${token}`,
      'Content-Type': body && typeof body === 'string' ? 'application/x-www-form-urlencoded' : 'application/json',
      ...headers,
    },
    body: body === undefined ? undefined : typeof body === 'string' ? body : JSON.stringify(body),
  });
  const text = await res.text();
  let data = null;
  try {
    data = text ? JSON.parse(text) : null;
  } catch (_) {
    data = text;
  }
  if (!res.ok) {
    const err = new Error(`PayPal ${res.status}`);
    err.status = res.status;
    err.detail = data;
    throw err;
  }
  return data;
}

async function accessToken() {
  const basic = Buffer.from(`${process.env.PAYPAL_CLIENT_ID}:${process.env.PAYPAL_CLIENT_SECRET}`).toString('base64');
  const data = await call('/v1/oauth2/token', { method: 'POST', basic, body: 'grant_type=client_credentials' });
  return data.access_token;
}

/** Crea el cobro. Devuelve { id, approveUrl }. `orderId` también sirve de clave de idempotencia. */
async function createOrder({ orderId, amount, description, returnUrl, cancelUrl, brand }) {
  const token = await accessToken();
  const data = await call('/v2/checkout/orders', {
    method: 'POST',
    token,
    headers: { 'PayPal-Request-Id': orderId },
    body: {
      intent: 'CAPTURE',
      purchase_units: [
        {
          reference_id: orderId,
          custom_id: orderId,
          invoice_id: orderId,
          description,
          amount: { currency_code: 'USD', value: amount },
        },
      ],
      payment_source: {
        paypal: {
          experience_context: {
            brand_name: brand,
            user_action: 'PAY_NOW',
            shipping_preference: 'NO_SHIPPING',
            return_url: returnUrl,
            cancel_url: cancelUrl,
          },
        },
      },
    },
  });
  const link = (data.links || []).find((l) => l.rel === 'payer-action' || l.rel === 'approve');
  if (!data.id || !link) throw new Error('PayPal no devolvió el enlace de pago');
  return { id: data.id, approveUrl: link.href };
}

async function captureOrder(id, requestId) {
  const token = await accessToken();
  return call(`/v2/checkout/orders/${encodeURIComponent(id)}/capture`, {
    method: 'POST',
    token,
    headers: { 'PayPal-Request-Id': requestId },
  });
}

async function getOrder(id) {
  const token = await accessToken();
  return call(`/v2/checkout/orders/${encodeURIComponent(id)}`, { token });
}

/** Primera captura de una orden de PayPal (o null). */
function firstCapture(order) {
  const unit = order && order.purchase_units && order.purchase_units[0];
  const caps = unit && unit.payments && unit.payments.captures;
  return (caps && caps[0]) || null;
}

module.exports = { configured, clpToUsd, createOrder, captureOrder, getOrder, firstCapture };
