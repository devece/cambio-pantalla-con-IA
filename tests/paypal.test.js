'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const paypal = require('../lib/paypal.js');

test('clpToUsd redondea hacia arriba al centavo', () => {
  assert.equal(paypal.clpToUsd(9990, 950), '10.52');
  assert.equal(paypal.clpToUsd(9500, 950), '10.00');
  assert.equal(paypal.clpToUsd(14990, 1000), '14.99');
});

test('clpToUsd exige un tipo de cambio válido', () => {
  assert.throws(() => paypal.clpToUsd(9990, 0));
});

test('configured necesita credenciales y tipo de cambio', () => {
  const keep = { ...process.env };
  try {
    delete process.env.PAYPAL_CLIENT_ID;
    delete process.env.PAYPAL_CLIENT_SECRET;
    delete process.env.PAYPAL_CLP_PER_USD;
    assert.equal(paypal.configured(), false);
    process.env.PAYPAL_CLIENT_ID = 'id';
    process.env.PAYPAL_CLIENT_SECRET = 'secret';
    assert.equal(paypal.configured(), false, 'sin tipo de cambio no está listo');
    process.env.PAYPAL_CLP_PER_USD = '950';
    assert.equal(paypal.configured(), true);
  } finally {
    process.env = keep;
  }
});

test('firstCapture lee la primera captura o devuelve null', () => {
  const order = { purchase_units: [{ payments: { captures: [{ id: 'C1', status: 'COMPLETED' }] } }] };
  assert.equal(paypal.firstCapture(order).id, 'C1');
  assert.equal(paypal.firstCapture({ purchase_units: [{}] }), null);
  assert.equal(paypal.firstCapture(null), null);
});

test('createOrder pide USD, usa el pedido como idempotencia y devuelve el enlace de pago', async () => {
  const keep = { ...process.env };
  const realFetch = global.fetch;
  const calls = [];
  process.env.PAYPAL_CLIENT_ID = 'id';
  process.env.PAYPAL_CLIENT_SECRET = 'secret';
  global.fetch = async (url, init) => {
    calls.push({ url, init });
    const body = url.endsWith('/v1/oauth2/token')
      ? { access_token: 'tok' }
      : { id: 'PP-1', links: [{ rel: 'payer-action', href: 'https://paypal.test/pay' }] };
    return { ok: true, status: 200, text: async () => JSON.stringify(body) };
  };
  try {
    const r = await paypal.createOrder({
      orderId: 'TB-AAAA1111',
      amount: '10.52',
      description: 'x',
      returnUrl: 'https://s/r',
      cancelUrl: 'https://s/c',
      brand: 'Tablero',
    });
    assert.deepEqual(r, { id: 'PP-1', approveUrl: 'https://paypal.test/pay' });
    const create = calls[1];
    assert.equal(create.init.headers['PayPal-Request-Id'], 'TB-AAAA1111');
    const sent = JSON.parse(create.init.body);
    assert.equal(sent.purchase_units[0].amount.currency_code, 'USD');
    assert.equal(sent.purchase_units[0].amount.value, '10.52');
    assert.equal(sent.payment_source.paypal.experience_context.return_url, 'https://s/r');
  } finally {
    global.fetch = realFetch;
    process.env = keep;
  }
});
