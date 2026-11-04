'use strict';
/*
 * POST /api/admin   { action, ... }   con la cabecera  x-admin-token: <ADMIN_TOKEN>
 * Panel mínimo para quien vende (no para clientes): crear códigos, confirmar pagos y administrar licencias.
 *
 *   list                      pedidos y códigos recientes
 *   create  { plan, note }    crea un pedido ya pagado y su código (venta coordinada fuera del sitio)
 *   confirm { orderId }       confirma el pago de un pedido pendiente (de /api/checkout) y crea su código
 *   revoke  { code }          anula un código
 *   release { code }          libera el código para pasarlo a otra radio (cuenta como traspaso)
 *
 * Requiere SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY y ADMIN_TOKEN. La página es /admin.html.
 */
const crypto = require('node:crypto');
const { generateCode, normalizeCode } = require('../lib/license.js');
const { allowMethod, readJson, rateLimit } = require('../lib/http.js');
const db = require('../lib/supabase.js');
const C = require('../public/shared/catalog.js');

const digest = (s) => crypto.createHash('sha256').update(String(s)).digest();
const sameToken = (a, b) => crypto.timingSafeEqual(digest(a), digest(b));
const newOrderId = () => 'TB-' + crypto.randomBytes(4).toString('hex').toUpperCase();
const ORDER_ID = /^TB-[0-9A-F]{8}$/;
const text = (v, n) => (typeof v === 'string' ? v.replace(/[\u0000-\u001f\u007f]/g, '').trim().slice(0, n) : '');

/** Crea la licencia de un pedido. Si ya tiene una, la devuelve (así confirmar dos veces no duplica nada). */
async function ensureLicense(orderId) {
  const have = await db.rest(`licenses?order_public_id=eq.${encodeURIComponent(orderId)}&select=code&limit=1`);
  if (have && have[0]) return have[0].code;
  for (let i = 0; i < 6; i++) {
    const code = generateCode();
    try {
      await db.rest('licenses', { method: 'POST', body: { code, order_public_id: orderId } });
      return code;
    } catch (err) {
      if (err.status !== 409) throw err;
      const again = await db.rest(`licenses?order_public_id=eq.${encodeURIComponent(orderId)}&select=code&limit=1`);
      if (again && again[0]) return again[0].code; // otra petición la creó justo antes
    }
  }
  throw new Error('No se pudo generar un código único');
}

const ACTIONS = {
  async list() {
    const cols = 'public_id,status,plan,total,contact_kind,contact_value,created_at,paid_at,config';
    const recent = (await db.rest(`orders?select=${cols}&order=created_at.desc&limit=300`)) || [];
    // Los pendientes se piden aparte: así un pedido antiguo sin pagar nunca queda fuera de la lista.
    const pend = (await db.rest(`orders?select=${cols}&status=eq.pending&order=created_at.desc&limit=200`)) || [];
    const seen = new Set(recent.map((o) => o.public_id));
    const orders = recent.concat(pend.filter((o) => !seen.has(o.public_id)));
    const licenses = await db.rest('licenses?select=code,order_public_id,status,device_hash,activated_at,transfers_used,created_at&order=created_at.desc&limit=300');
    const byOrder = Object.fromEntries((orders || []).map((o) => [o.public_id, o]));
    return {
      pending: (orders || []).filter((o) => o.status === 'pending').map(({ config, ...o }) => ({ ...o, hasPhoto: !!(config && config.photo && config.photo.day) })),
      licenses: (licenses || []).map((l) => ({
        code: l.code,
        orderId: l.order_public_id,
        status: l.status,
        activated: !!l.device_hash,
        activatedAt: l.activated_at,
        transfersUsed: l.transfers_used,
        createdAt: l.created_at,
        plan: (byOrder[l.order_public_id] || {}).plan || null,
        contact: (byOrder[l.order_public_id] || {}).contact_value || '',
        hasPhoto: !!(((byOrder[l.order_public_id] || {}).config || {}).photo || {}).day,
      })),
    };
  },

  async create(body) {
    const plan = C.plans.find((p) => p.id === body.plan);
    if (!plan) return { error: 'Elige un plan válido.', status: 400 };
    const orderId = newOrderId();
    await db.rest('orders', {
      method: 'POST',
      body: {
        public_id: orderId,
        status: 'paid',
        plan: plan.id,
        addons: {},
        total: plan.price,
        currency: 'USD',
        contact_kind: 'whatsapp',
        contact_value: text(body.note, 120) || 'venta manual',
        config: { manual: true },
        paid_at: new Date().toISOString(),
      },
    });
    const code = await ensureLicense(orderId);
    return { code, orderId, plan: plan.id, total: plan.price };
  },

  async confirm(body) {
    const orderId = text(body.orderId, 20).toUpperCase();
    if (!ORDER_ID.test(orderId)) return { error: 'El número de pedido no es válido (formato TB-XXXXXXXX).', status: 400 };
    const rows = await db.rest(`orders?public_id=eq.${orderId}&select=public_id,status,plan,total&limit=1`);
    const order = rows && rows[0];
    if (!order) return { error: 'No encontramos ese pedido. Si el cliente lo hizo en modo demostración, usa “Nuevo código”.', status: 404 };
    if (order.status !== 'paid') {
      await db.rest(`orders?public_id=eq.${orderId}`, { method: 'PATCH', body: { status: 'paid', paid_at: new Date().toISOString() } });
    }
    const code = await ensureLicense(orderId);
    return { code, orderId, plan: order.plan, total: order.total };
  },

  async revoke(body) {
    const code = normalizeCode(body.code);
    if (!code) return { error: 'Código no válido.', status: 400 };
    const r = await db.rest(`licenses?code=eq.${encodeURIComponent(code)}`, { method: 'PATCH', body: { status: 'revoked' } });
    if (!r || !r.length) return { error: 'No existe ese código.', status: 404 };
    return { code, status: 'revoked' };
  },

  async release(body) {
    const code = normalizeCode(body.code);
    if (!code) return { error: 'Código no válido.', status: 400 };
    const rows = await db.rest(`licenses?code=eq.${encodeURIComponent(code)}&select=code,transfers_used&limit=1`);
    if (!rows || !rows[0]) return { error: 'No existe ese código.', status: 404 };
    await db.rest(`licenses?code=eq.${encodeURIComponent(code)}`, {
      method: 'PATCH',
      body: { device_hash: null, activated_at: null, transfers_used: (rows[0].transfers_used || 0) + 1 },
    });
    return { code, released: true };
  },
};

module.exports = async function handler(req, res) {
  if (!allowMethod(req, res, 'POST')) return;
  if (!rateLimit(req, { max: 40 })) return res.status(429).json({ ok: false, error: 'Demasiados intentos. Espera un minuto.' });

  if (!process.env.ADMIN_TOKEN || !db.configured()) {
    return res.status(503).json({ ok: false, error: 'El panel aún no está configurado: faltan ADMIN_TOKEN y los datos de la base de datos en Vercel.' });
  }
  const given = (req.headers && (req.headers['x-admin-token'] || req.headers['X-Admin-Token'])) || '';
  if (!given || !sameToken(given, process.env.ADMIN_TOKEN)) return res.status(401).json({ ok: false, error: 'Clave incorrecta.' });

  const body = readJson(req) || {};
  const run = Object.prototype.hasOwnProperty.call(ACTIONS, body.action) ? ACTIONS[body.action] : null;
  if (!run) return res.status(400).json({ ok: false, error: 'Acción desconocida.' });
  try {
    const out = await run(body);
    if (out && out.error) return res.status(out.status || 400).json({ ok: false, error: out.error });
    return res.status(200).json({ ok: true, ...out });
  } catch (err) {
    console.error('admin: error', body.action, err.status, err.detail || err.message);
    return res.status(502).json({ ok: false, error: 'No pudimos completar la acción en la base de datos.' });
  }
};
