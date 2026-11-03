'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const db = require('../lib/supabase.js');

const PNG = Buffer.concat([Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]), Buffer.alloc(400, 1)]);
const png = (b = PNG) => 'data:image/png;base64,' + b.toString('base64');
const res = () => {
  const r = { code: 200, body: null };
  r.setHeader = () => {};
  r.status = (c) => { r.code = c; return r; };
  r.json = (b) => { r.body = b; return r; };
  return r;
};
let n = 0;
const post = async (body) => {
  const r = res();
  await require('../api/order-photo.js')({ method: 'POST', headers: { 'x-forwarded-for': 'ph-' + n++ }, body }, r);
  return r;
};

function setup(status = 'pending') {
  const orders = [{ public_id: 'TB-0A1B2C3D', status, config: { car: { type: 'auto' } } }];
  const stored = {};
  db.configured = () => true;
  db.storageUpload = async (bucket, path, buf, type) => { stored[path] = { bucket, size: buf.length, type }; return path; };
  db.rest = async (path, { method = 'GET', body } = {}) => {
    if (method === 'PATCH') { Object.assign(orders[0], body); return [orders[0]]; }
    return /TB-0A1B2C3D/.test(path) ? [orders[0]] : [];
  };
  return { orders, stored };
}

test('guarda la foto de día y la de noche junto al pedido', async () => {
  const { orders, stored } = setup();
  const day = await post({ orderId: 'tb-0a1b2c3d', kind: 'day', image: png(), cut: true, lights: [{ kind: 'faro', x: 0.2, y: 0.5, size: 1 }, { kind: 'laser', x: 1, y: 1, size: 1 }] });
  assert.equal(day.code, 200, JSON.stringify(day.body));
  const night = await post({ orderId: 'TB-0A1B2C3D', kind: 'night', image: png() });
  assert.equal(night.code, 200);
  assert.deepEqual(Object.keys(stored).sort(), ['orders/TB-0A1B2C3D/day.png', 'orders/TB-0A1B2C3D/night.png']);
  assert.equal(stored['orders/TB-0A1B2C3D/day.png'].bucket, 'tablero-fotos');
  const photo = orders[0].config.photo;
  assert.equal(photo.cut, true);
  assert.equal(photo.day, 'orders/TB-0A1B2C3D/day.png');
  assert.equal(photo.night, 'orders/TB-0A1B2C3D/night.png');
  assert.deepEqual(photo.lights, [{ kind: 'faro', x: 0.2, y: 0.5, size: 1 }], 'solo luces válidas');
  assert.deepEqual(orders[0].config.car, { type: 'auto' }, 'no pierde el resto de la configuración');
});

test('rechaza pedidos inválidos, ajenos o ya pagados', async () => {
  setup();
  assert.equal((await post({ orderId: 'hola', kind: 'day', image: png() })).code, 400);
  assert.equal((await post({ orderId: 'TB-FFFFFFFF', kind: 'day', image: png() })).code, 404);
  assert.equal((await post({ orderId: 'TB-0A1B2C3D', kind: 'otra', image: png() })).code, 400);
  setup('paid');
  assert.equal((await post({ orderId: 'TB-0A1B2C3D', kind: 'day', image: png() })).code, 409);
});

test('rechaza imágenes falsas o demasiado grandes', async () => {
  const { stored } = setup();
  const fake = Buffer.concat([Buffer.from('<?php echo 1;'), Buffer.alloc(400, 65)]);
  assert.equal((await post({ orderId: 'TB-0A1B2C3D', kind: 'day', image: png(fake) })).code, 400, 'dice ser PNG pero no lo es');
  assert.equal((await post({ orderId: 'TB-0A1B2C3D', kind: 'day', image: 'https://ejemplo.com/a.png' })).code, 400);
  const big = Buffer.concat([PNG, Buffer.alloc(2_700_000, 2)]);
  assert.equal((await post({ orderId: 'TB-0A1B2C3D', kind: 'day', image: png(big) })).code, 400);
  assert.deepEqual(stored, {}, 'no se guardó nada');
});

test('sin base de datos responde 503', async () => {
  setup();
  db.configured = () => false;
  assert.equal((await post({ orderId: 'TB-0A1B2C3D', kind: 'day', image: png() })).code, 503);
});
