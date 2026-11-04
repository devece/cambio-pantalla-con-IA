'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const crypto = require('node:crypto');
const db = require('../lib/supabase.js');

const order = () => ({
  radio: { type: 'android' },
  car: { type: 'suv', color: '#1f5fa8', name: 'Daniel', greeting: 'Hola' },
  style: { theme: 'ruta', layout: 'estelar', scene: 'tema', season: 'ninguna', apps: ['maps'] },
  features: { oilOn: true, odometer: 60000, lastOil: 58000, oilEvery: 5000 },
  plan: 'esencial',
  addons: {},
  contact: { kind: 'whatsapp', value: '+56 9 1234 5678' },
  terms: true,
});
const res = () => {
  const r = { code: 200, body: null };
  r.setHeader = () => {};
  r.status = (c) => { r.code = c; return r; };
  r.json = (b) => { r.body = b; return r; };
  return r;
};

test('el pedido guarda solo el hash de la llave de fotos y entrega la llave una vez', async () => {
  const saved = [];
  db.configured = () => true;
  db.rest = async (path, { method = 'GET', body } = {}) => { if (method === 'POST') saved.push(body); return [body]; };
  delete process.env.PAYPAL_CLIENT_ID;
  const r = res();
  await require('../api/checkout.js')({ method: 'POST', headers: { 'x-forwarded-for': 'co-1' }, body: order() }, r);
  assert.equal(r.code, 200, JSON.stringify(r.body));
  assert.match(r.body.photoToken, /^[0-9a-f]{32}$/);
  assert.equal(saved.length, 1);
  const hash = saved[0].config.photoTokenHash;
  assert.equal(hash, crypto.createHash('sha256').update(r.body.photoToken).digest('hex'));
  assert.ok(!JSON.stringify(saved[0]).includes(r.body.photoToken), 'la llave en claro no se guarda');
});

test('el modo demostración no entrega llave porque no guarda nada', async () => {
  db.configured = () => false;
  const r = res();
  await require('../api/checkout.js')({ method: 'POST', headers: { 'x-forwarded-for': 'co-2' }, body: order() }, r);
  assert.equal(r.code, 200);
  assert.equal(r.body.mode, 'demo');
  assert.equal(r.body.photoToken, undefined);
});
