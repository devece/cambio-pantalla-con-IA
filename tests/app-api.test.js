'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const { fakeApk } = require('./fake-apk.js');
const { readPair, DESIGN_ID } = require('../lib/apk-inject.js');

const handler = require('../api/app.js');
const b64 = (o) => Buffer.from(JSON.stringify(o)).toString('base64url');

function call(query, method = 'GET') {
  return new Promise((resolve) => {
    const res = {
      statusCode: 0, headers: {},
      setHeader(k, v) { this.headers[k] = v; },
      status(c) { this.statusCode = c; return this; },
      json(b) { resolve({ status: this.statusCode, headers: this.headers, body: b }); },
      end(b) { resolve({ status: this.statusCode, headers: this.headers, body: b }); },
    };
    handler({ method, query, headers: { 'x-forwarded-for': 'test-' + Math.random() } }, res);
  });
}

const design = {
  car: { type: 'suv', color: '#1f5fa8', name: 'Dani', greeting: 'Wena', phrase: 'Buen viaje', plate: 'ABCD12' },
  style: { theme: 'costa', layout: 'minimal', scene: 'costa', sceneTime: 'noche', season: 'navidad', accent: '#4cc9f0', apps: ['maps', 'waze', 'inexistente'] },
  features: { oilOn: true, odometer: 52000, lastOil: 50000, oilEvery: 5000, restrOn: true, restrDigit: '7', country: 'CL' },
  contact: { kind: 'email', value: 'no-debe-viajar@correo.cl' },
};

test('entrega la APK con el diseño limpio adentro', async () => {
  const { apk } = fakeApk();
  const realFetch = global.fetch;
  let pedidos = 0;
  global.fetch = async () => { pedidos++; return { ok: true, status: 200, arrayBuffer: async () => apk.buffer.slice(apk.byteOffset, apk.byteOffset + apk.length) }; };
  try {
    const r = await call({ d: b64(design) });
    assert.equal(r.status, 200);
    assert.equal(r.headers['Content-Type'], 'application/vnd.android.package-archive');
    assert.match(r.headers['Content-Disposition'], /attachment/);
    assert.equal(Number(r.headers['Content-Length']), r.body.length);
    const saved = JSON.parse(readPair(r.body, DESIGN_ID).toString('utf8'));
    assert.equal(saved.car.name, 'Dani');
    assert.equal(saved.style.theme, 'costa');
    // La APK original no se modificó: sigue sin diseño.
    assert.equal(readPair(apk, DESIGN_ID), null);
    // Segunda descarga: sale de la memoria, sin volver a bajar la APK.
    await call({ d: b64(design) });
    assert.equal(pedidos, 1);
  } finally {
    global.fetch = realFetch;
  }
});

test('si no se puede bajar la APK, redirige a la normal', async () => {
  const realFetch = global.fetch;
  handler._resetCache();
  global.fetch = async () => { throw new Error('sin red'); };
  try {
    const r = await call({ d: b64(design) });
    assert.equal(r.status, 302);
    assert.match(r.headers.Location, /tablero\.apk$/);
  } finally {
    global.fetch = realFetch;
  }
});

test('el diseño que viaja solo tiene campos conocidos y no lleva contacto ni placa', () => {
  const d = handler.designFromParam(b64(design));
  assert.equal(d.v, 1);
  assert.deepEqual(Object.keys(d).sort(), ['car', 'features', 'style', 'v']);
  assert.equal(d.car.name, 'Dani');
  assert.equal(d.car.plate, undefined);
  assert.equal(d.style.theme, 'costa');
  assert.deepEqual(d.style.apps, ['maps', 'waze'], 'una app que no existe se descarta');
  assert.equal(JSON.stringify(d).includes('no-debe-viajar'), false);
  assert.equal(d.features.restrDigit, '7');
  assert.ok(d.features.restrDays.length > 0, 'los días de restricción los calcula el servidor');
});

test('un parámetro inválido no sirve', () => {
  assert.equal(handler.designFromParam(undefined), null);
  assert.equal(handler.designFromParam('%%%'), null);
  assert.equal(handler.designFromParam('x'.repeat(7000)), null);
  assert.equal(handler.designFromParam(Buffer.from('no es json').toString('base64url')), null);
});

test('sin diseño válido redirige a la APK normal', async () => {
  const r = await call({ d: 'basura!!' });
  assert.equal(r.status, 302);
  assert.match(r.headers.Location, /tablero\.apk$/);
});

test('solo acepta GET', async () => {
  const r = await call({}, 'POST');
  assert.equal(r.status, 405);
});
