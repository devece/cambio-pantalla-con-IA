'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');

/* Simula Supabase (en memoria) y PayPal con un fetch falso, y ejecuta el handler real. */
function setup({ captureStatus = 'COMPLETED', captureValue = '10.52' } = {}) {
  const db = {
    orders: [{ public_id: 'TB-AAAA1111', status: 'pending', payment_ref: 'PP-1', charge_amount: 10.52, charge_currency: 'USD' }],
    licenses: [],
  };
  const captures = { n: 0 };
  Object.assign(process.env, {
    SUPABASE_URL: 'https://db.test',
    SUPABASE_SERVICE_ROLE_KEY: 'k',
    PAYPAL_CLIENT_ID: 'id',
    PAYPAL_CLIENT_SECRET: 's',
    PAYPAL_CLP_PER_USD: '950',
  });
  const reply = (status, body) => ({ ok: status < 400, status, text: async () => JSON.stringify(body) });

  global.fetch = async (url, init = {}) => {
    const u = new URL(url);
    if (u.host === 'db.test') {
      const table = u.pathname.split('/').pop();
      const rows = db[table];
      const q = u.searchParams;
      const match = (r) => [...q.entries()].every(([k, v]) => !v.startsWith('eq.') || String(r[k]) === v.slice(3));
      if (init.method === 'POST') {
        const row = JSON.parse(init.body);
        if (rows.some((r) => r.order_public_id === row.order_public_id || r.code === row.code)) return reply(409, { code: '23505' });
        rows.push(row);
        return reply(201, [row]);
      }
      if (init.method === 'PATCH') {
        const hit = rows.filter(match);
        hit.forEach((r) => Object.assign(r, JSON.parse(init.body)));
        return reply(200, hit);
      }
      return reply(200, rows.filter(match));
    }
    if (u.pathname.endsWith('/oauth2/token')) return reply(200, { access_token: 't' });
    if (u.pathname.endsWith('/capture')) {
      captures.n++;
      return reply(201, {
        purchase_units: [{ payments: { captures: [{ status: captureStatus, amount: { currency_code: 'USD', value: captureValue } }] } }],
      });
    }
    throw new Error('fetch inesperado: ' + url);
  };
  return { db, captures };
}

function run(query) {
  const handler = require('../api/paypal-return.js');
  const res = { headers: {}, setHeader(k, v) { this.headers[k] = v; }, end(b) { this.body = b; }, status(c) { this.statusCode = c; return this; }, json(o) { this.body = JSON.stringify(o); } };
  return handler({ method: 'GET', headers: {}, query }, res).then(() => res);
}

test('pago correcto: marca pagado, emite una licencia y recargar no duplica nada', async () => {
  const realFetch = global.fetch;
  const keep = { ...process.env };
  try {
    const { db, captures } = setup();
    const first = await run({ order: 'TB-AAAA1111', token: 'PP-1' });
    assert.equal(first.statusCode, 200);
    assert.equal(db.orders[0].status, 'paid');
    assert.equal(db.licenses.length, 1);
    assert.match(first.body, new RegExp(db.licenses[0].code));

    const again = await run({ order: 'TB-AAAA1111', token: 'PP-1' });
    assert.equal(again.statusCode, 200);
    assert.equal(db.licenses.length, 1, 'no se emite otra licencia');
    assert.equal(captures.n, 1, 'no se vuelve a capturar');
    assert.match(again.body, new RegExp(db.licenses[0].code));
  } finally {
    global.fetch = realFetch;
    process.env = keep;
  }
});

test('monto cobrado distinto al pedido: no se paga ni se emite licencia', async () => {
  const realFetch = global.fetch;
  const keep = { ...process.env };
  try {
    const { db } = setup({ captureValue: '1.00' });
    const r = await run({ order: 'TB-AAAA1111', token: 'PP-1' });
    assert.equal(r.statusCode, 402);
    assert.equal(db.orders[0].status, 'pending');
    assert.equal(db.licenses.length, 0);
  } finally {
    global.fetch = realFetch;
    process.env = keep;
  }
});

test('token de otra orden de PayPal: se rechaza sin capturar', async () => {
  const realFetch = global.fetch;
  const keep = { ...process.env };
  try {
    const { db, captures } = setup();
    const r = await run({ order: 'TB-AAAA1111', token: 'OTRO' });
    assert.equal(r.statusCode, 404);
    assert.equal(captures.n, 0);
    assert.equal(db.licenses.length, 0);
  } finally {
    global.fetch = realFetch;
    process.env = keep;
  }
});

test('pago pendiente de revisión en PayPal: no emite licencia todavía', async () => {
  const realFetch = global.fetch;
  const keep = { ...process.env };
  try {
    const { db } = setup({ captureStatus: 'PENDING' });
    const r = await run({ order: 'TB-AAAA1111', token: 'PP-1' });
    assert.equal(r.statusCode, 202);
    assert.equal(db.licenses.length, 0);
  } finally {
    global.fetch = realFetch;
    process.env = keep;
  }
});
