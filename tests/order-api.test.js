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
  contact: { kind: 'whatsapp', value: '+56 9 1234 5678' },
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
  const r = res();
  await require('../api/order.js')({ method: 'POST', headers: { 'x-forwarded-for': 'co-1' }, body: order() }, r);
  assert.equal(r.code, 200, JSON.stringify(r.body));
  assert.match(r.body.photoToken, /^[0-9a-f]{32}$/);
  assert.equal(saved.length, 1);
  const hash = saved[0].config.photoTokenHash;
  assert.equal(hash, crypto.createHash('sha256').update(r.body.photoToken).digest('hex'));
  assert.ok(!JSON.stringify(saved[0]).includes(r.body.photoToken), 'la llave en claro no se guarda');
});

test('el pedido queda pendiente, sin cobro, y la respuesta no habla de dinero', async () => {
  const saved = [];
  db.configured = () => true;
  db.rest = async (path, { method = 'GET', body } = {}) => { if (method === 'POST') saved.push(body); return [body]; };
  const r = res();
  await require('../api/order.js')({ method: 'POST', headers: { 'x-forwarded-for': 'co-3' }, body: order() }, r);
  assert.equal(r.code, 200);
  assert.equal(r.body.mode, 'saved');
  assert.match(r.body.orderId, /^TB-[0-9A-F]{8}$/);
  ['total', 'currency', 'paymentUrl', 'plan'].forEach((k) => assert.equal(k in r.body, false, `la respuesta no debe traer ${k}`));
  assert.equal(saved[0].status, 'pending');
  // Columnas heredadas: la base las exige, pero valen siempre lo mismo y no cobran nada.
  assert.equal(saved[0].total, 0);
  assert.equal(saved[0].plan, 'completo');
  assert.deepEqual(saved[0].addons, {});
});

test('un pedido inválido se rechaza sin guardar nada', async () => {
  let writes = 0;
  db.configured = () => true;
  db.rest = async () => { writes++; return []; };
  const r = res();
  const bad = order();
  bad.contact.value = '123';
  await require('../api/order.js')({ method: 'POST', headers: { 'x-forwarded-for': 'co-4' }, body: bad }, r);
  assert.equal(r.code, 422);
  assert.equal(writes, 0);
});

test('el modo demostración no entrega llave porque no guarda nada', async () => {
  db.configured = () => false;
  const r = res();
  await require('../api/order.js')({ method: 'POST', headers: { 'x-forwarded-for': 'co-2' }, body: order() }, r);
  assert.equal(r.code, 200);
  assert.equal(r.body.mode, 'demo');
  assert.equal(r.body.photoToken, undefined);
});
