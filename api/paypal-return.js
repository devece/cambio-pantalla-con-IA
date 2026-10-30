'use strict';
/*
 * GET /api/paypal-return?order=TB-XXXXXXXX&token=<id de la orden en PayPal>
 * PayPal manda aquí al cliente después de aprobar el pago. Este es el único lugar que:
 *   1. captura el cobro,
 *   2. comprueba que lo cobrado coincide con lo guardado al crear el pedido,
 *   3. marca el pedido como pagado y emite la licencia.
 * Es idempotente: recargar la página o volver a abrir el enlace muestra la misma licencia.
 */
const { generateCode } = require('../lib/license.js');
const { allowMethod, rateLimit } = require('../lib/http.js');
const db = require('../lib/supabase.js');
const paypal = require('../lib/paypal.js');

const ORDER_ID = /^TB-[0-9A-F]{8}$/;

const esc = (s) => String(s).replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c]);

function page(res, status, title, html) {
  res.statusCode = status;
  res.setHeader('Content-Type', 'text/html; charset=utf-8');
  res.setHeader('Cache-Control', 'no-store');
  res.end(`<!doctype html><html lang="es"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<meta name="robots" content="noindex"><title>${esc(title)}</title>
<style>body{font:16px/1.5 system-ui,sans-serif;max-width:32rem;margin:12vh auto;padding:0 1.25rem;color:#1b1b1f}
code{display:block;font-size:1.5rem;letter-spacing:.06em;padding:.9rem 1rem;margin:1rem 0;background:#f1f1f4;border-radius:.6rem;text-align:center;user-select:all}
a{color:#1f5fa8}</style></head><body><h1>${esc(title)}</h1>${html}<p><a href="/">Volver al inicio</a></p></body></html>`);
}

async function licenseFor(orderId) {
  const rows = await db.rest(`licenses?order_public_id=eq.${encodeURIComponent(orderId)}&select=code&limit=1`);
  return rows && rows[0] ? rows[0].code : null;
}

/** Devuelve la licencia del pedido, creándola si no existe (índice único por pedido evita duplicados). */
async function ensureLicense(orderId) {
  for (let i = 0; i < 4; i++) {
    const existing = await licenseFor(orderId);
    if (existing) return existing;
    const code = generateCode();
    try {
      await db.rest('licenses', { method: 'POST', body: { code, order_public_id: orderId } });
      return code;
    } catch (err) {
      if (err.status !== 409) throw err; // 409 = otro proceso la creó, o el código chocó: reintenta
    }
  }
  throw new Error('No se pudo emitir la licencia');
}

function paidPage(res, orderId, code) {
  page(
    res,
    200,
    '¡Pago recibido!',
    `<p>Gracias. Este es tu código de activación para el pedido <b>${esc(orderId)}</b>:</p><code>${esc(code)}</code>
<p>Guárdalo: lo ingresarás en el launcher de tu radio. Se liga a una sola radio.</p>`
  );
}

module.exports = async function handler(req, res) {
  if (!allowMethod(req, res, 'GET')) return;
  if (!rateLimit(req, { max: 20 })) return page(res, 429, 'Demasiados intentos', '<p>Espera un minuto y vuelve a abrir el enlace.</p>');

  const q = req.query || {};
  const orderId = typeof q.order === 'string' ? q.order : '';
  const token = typeof q.token === 'string' ? q.token : '';
  if (!ORDER_ID.test(orderId) || !token || token.length > 64) {
    return page(res, 400, 'Enlace inválido', '<p>Este enlace de pago no es válido.</p>');
  }
  if (!db.configured() || !paypal.configured()) {
    return page(res, 503, 'Servicio no disponible', '<p>El pago aún no está configurado.</p>');
  }

  try {
    const rows = await db.rest(
      `orders?public_id=eq.${orderId}&select=public_id,status,payment_ref,charge_amount,charge_currency&limit=1`
    );
    const order = rows && rows[0];
    // Misma respuesta si no existe o el token no corresponde: no revelamos qué pedidos existen.
    if (!order || order.payment_ref !== token) {
      return page(res, 404, 'Pedido no encontrado', '<p>No encontramos ese pedido. Si pagaste, escríbenos con tu número de pedido.</p>');
    }

    if (order.status === 'paid') return paidPage(res, orderId, await ensureLicense(orderId));
    if (order.status !== 'pending') {
      return page(res, 409, 'Pedido no disponible', `<p>El pedido <b>${esc(orderId)}</b> no se puede completar. Escríbenos y lo revisamos.</p>`);
    }

    let result;
    try {
      result = await paypal.captureOrder(token, orderId);
    } catch (err) {
      const already = err.detail && Array.isArray(err.detail.details) && err.detail.details.some((d) => d.issue === 'ORDER_ALREADY_CAPTURED');
      if (!already) throw err;
      result = await paypal.getOrder(token);
    }

    const cap = paypal.firstCapture(result);
    if (cap && cap.status === 'PENDING') {
      return page(res, 202, 'Pago en revisión', `<p>PayPal aún está revisando tu pago del pedido <b>${esc(orderId)}</b>. Te avisaremos cuando se confirme.</p>`);
    }
    const matches =
      cap &&
      cap.status === 'COMPLETED' &&
      cap.amount &&
      cap.amount.currency_code === order.charge_currency &&
      Number(cap.amount.value).toFixed(2) === Number(order.charge_amount).toFixed(2);
    if (!matches) {
      console.error('paypal-return: pago no coincide', orderId, JSON.stringify(cap));
      return page(res, 402, 'No pudimos confirmar el pago', `<p>No se completó el cobro del pedido <b>${esc(orderId)}</b>. Si te cobraron, escríbenos con ese número.</p>`);
    }

    // Solo pasa de pending a paid una vez; si otra petición ganó, igual devolvemos la licencia.
    await db.rest(`orders?public_id=eq.${orderId}&status=eq.pending`, {
      method: 'PATCH',
      body: { status: 'paid', paid_at: new Date().toISOString() },
    });
    return paidPage(res, orderId, await ensureLicense(orderId));
  } catch (err) {
    console.error('paypal-return: error', orderId, err.status, err.detail || err.message);
    return page(res, 502, 'No pudimos completar el pago', `<p>Hubo un problema al confirmar el pedido <b>${esc(orderId)}</b>. Vuelve a abrir este enlace en un momento; si persiste, escríbenos.</p>`);
  }
};
