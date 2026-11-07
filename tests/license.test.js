'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const db = require('../lib/supabase.js');
const { buildDesign } = require('../lib/design.js');

const res = () => {
  const r = { code: 200, body: null };
  r.setHeader = () => {};
  r.status = (c) => { r.code = c; return r; };
  r.json = (b) => { r.body = b; return r; };
  return r;
};
let n = 0;
const call = async (body) => {
  const r = res();
  await require('../api/license.js')({ method: 'POST', headers: { 'x-forwarded-for': 'lic-' + n++ }, body }, r);
  return r;
};

function setup(orderConfig, signFails = false) {
  process.env.LICENSE_PEPPER = 'pimienta-de-prueba';
  const lic = { code: 'TBL-AAAA-BBBB-CCCC', status: 'active', device_hash: null, order_public_id: 'TB-0A1B2C3D' };
  const signed = [];
  db.configured = () => true;
  db.storageSignedUrl = async (bucket, path) => {
    if (signFails) throw new Error('sin firma');
    signed.push(path);
    return `https://storage.test/${bucket}/${path}?token=x`;
  };
  db.rest = async (path, { method = 'GET', body } = {}) => {
    if (path.startsWith('licenses') && method === 'PATCH') { Object.assign(lic, body); return [lic]; }
    if (path.startsWith('licenses')) return [lic];
    if (path.startsWith('orders')) return [{ plan: 'esencial', config: orderConfig }]; // un pedido viejo, de cuando había planes
    return [];
  };
  return { lic, signed };
}

const config = {
  radio: { type: 'android' },
  car: { type: 'auto', name: 'Daniel', color: '#b3261e' },
  style: { theme: 'cristal', layout: 'centro' },
  features: { oilOn: true },
  photo: { day: 'orders/TB-0A1B2C3D/day.png', night: 'orders/TB-0A1B2C3D/night.png', cut: true, lights: [{ kind: 'faro', x: 0.2, y: 0.5, size: 1 }] },
  contact: 'no debe salir',
};

test('al activar, entrega el diseño del cliente con enlaces temporales a su foto', async () => {
  const { signed } = setup(config);
  const r = await call({ code: 'tbl-aaaa-bbbb-cccc', deviceId: 'radio-de-prueba-001' });
  assert.equal(r.code, 200, JSON.stringify(r.body));
  assert.equal(r.body.plan, 'completo', 'el plan guardado en un pedido viejo se ignora: hay un solo nivel');
  const d = r.body.design;
  assert.equal(d.config.style.theme, 'cristal');
  assert.equal(d.config.car.name, 'Daniel');
  assert.equal(d.photo.cut, true);
  assert.match(d.photo.day, /^https:\/\/storage\.test\/tablero-fotos\/orders\/TB-0A1B2C3D\/day\.png\?token=/);
  assert.match(d.photo.night, /night\.png/);
  assert.deepEqual(d.photo.lights, [{ kind: 'faro', x: 0.2, y: 0.5, size: 1 }]);
  assert.ok(!JSON.stringify(r.body).includes('no debe salir'), 'no se filtra nada que no sea del diseño');
  assert.equal(signed.length, 2);
});

test('un código creado a mano no trae diseño, y sin firma el diseño sirve sin foto', async () => {
  setup({ manual: true });
  assert.equal((await call({ code: 'TBL-AAAA-BBBB-CCCC', deviceId: 'radio-de-prueba-001' })).body.design, null);
  setup(config, true);
  const r = await call({ code: 'TBL-AAAA-BBBB-CCCC', deviceId: 'radio-de-prueba-002' });
  assert.equal(r.code, 200);
  assert.equal(r.body.design.photo, null);
  assert.equal(r.body.design.config.style.layout, 'centro');
});

test('buildDesign tolera datos raros', async () => {
  assert.equal(await buildDesign(null), null);
  assert.equal(await buildDesign('x'), null);
  assert.equal(await buildDesign({}), null);
});

test('la misma radio puede volver a pedir su diseño; otra radio no', async () => {
  setup(config);
  const a = await call({ code: 'TBL-AAAA-BBBB-CCCC', deviceId: 'radio-de-prueba-001' });
  const again = await call({ code: 'TBL-AAAA-BBBB-CCCC', deviceId: 'radio-de-prueba-001' });
  const other = await call({ code: 'TBL-AAAA-BBBB-CCCC', deviceId: 'otra-radio-00002' });
  assert.equal(a.code, 200);
  assert.equal(again.code, 200);
  assert.ok(again.body.design);
  assert.equal(other.code, 409);
});
