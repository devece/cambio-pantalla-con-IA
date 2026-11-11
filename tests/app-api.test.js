'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const { fakeApk } = require('./fake-apk.js');
const { readPair, DESIGN_ID, PHOTO_DAY_ID, PHOTO_NIGHT_ID } = require('../lib/apk-inject.js');

const crypto = require('node:crypto');
const handler = require('../api/app.js');

/** Una red falsa: la APK y su version.json (con la huella real de esa APK, o la que se indique). */
function fakeNetwork(apk, { sha, versionStatus = 200, onFetch } = {}) {
  return async (url) => {
    if (onFetch) onFetch(url);
    if (/version\.json$/.test(url)) {
      const body = JSON.stringify({ versionCode: 1155, versionName: '0.2.0+155', sha256: sha === undefined ? crypto.createHash('sha256').update(apk).digest('hex') : sha });
      return { ok: versionStatus === 200, status: versionStatus, text: async () => body };
    }
    return { ok: true, status: 200, arrayBuffer: async () => apk.buffer.slice(apk.byteOffset, apk.byteOffset + apk.length) };
  };
}
const b64 = (o) => Buffer.from(JSON.stringify(o)).toString('base64url');

function call(query, method = 'GET', body) {
  return new Promise((resolve) => {
    const res = {
      statusCode: 0, headers: {},
      setHeader(k, v) { this.headers[k] = v; },
      status(c) { this.statusCode = c; return this; },
      json(b) { resolve({ status: this.statusCode, headers: this.headers, body: b }); },
      end(b) { resolve({ status: this.statusCode, headers: this.headers, body: b }); },
    };
    handler({ method, query, body, headers: { 'x-forwarded-for': 'test-' + Math.random() } }, res);
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
  global.fetch = fakeNetwork(apk, { onFetch: (url) => { if (/\.apk$/.test(url)) pedidos++; } });
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

test('solo acepta GET, HEAD y POST', async () => {
  const r = await call({}, 'PUT');
  assert.equal(r.status, 405);
});

const png = (n = 300) => 'data:image/png;base64,' + Buffer.concat([Buffer.from([0x89, 0x50, 0x4e, 0x47]), Buffer.alloc(n, 1)]).toString('base64');
const jpg = (n = 300) => 'data:image/jpeg;base64,' + Buffer.concat([Buffer.from([0xff, 0xd8, 0xff]), Buffer.alloc(n, 2)]).toString('base64');

async function withApk(fn) {
  const { apk } = fakeApk();
  const realFetch = global.fetch;
  handler._resetCache();
  global.fetch = fakeNetwork(apk);
  try { return await fn(apk); } finally { global.fetch = realFetch; }
}

test('POST: la foto recortada de día y de noche viaja dentro de la APK', () => withApk(async (apk) => {
  const r = await call({}, 'POST', { d: b64(design), photo: { day: png(500), night: png(700), cut: true } });
  assert.equal(r.status, 200);
  const saved = JSON.parse(readPair(r.body, DESIGN_ID).toString('utf8'));
  assert.deepEqual(saved.photo, { cut: true, night: true });
  assert.equal(readPair(r.body, PHOTO_DAY_ID).length, 504);
  assert.equal(readPair(r.body, PHOTO_NIGHT_ID).length, 704);
  assert.equal(readPair(apk, PHOTO_DAY_ID), null, 'la APK original no se toca');
}));

test('POST: una foto normal (JPEG) viaja sin versión de noche', () => withApk(async () => {
  const r = await call({}, 'POST', { d: b64(design), photo: { day: jpg(400), night: jpg(400), cut: false } });
  assert.equal(r.status, 200);
  assert.deepEqual(JSON.parse(readPair(r.body, DESIGN_ID).toString('utf8')).photo, { cut: false, night: false });
  assert.equal(readPair(r.body, PHOTO_NIGHT_ID), null);
}));

test('POST sin foto entrega la APK con el diseño y sin clave de foto', () => withApk(async () => {
  const r = await call({}, 'POST', { d: b64(design) });
  assert.equal(r.status, 200);
  assert.equal(JSON.parse(readPair(r.body, DESIGN_ID).toString('utf8')).photo, undefined);
}));

test('POST: una foto que no coincide con lo que dice ser, o muy grande, se rechaza', () => withApk(async () => {
  assert.equal((await call({}, 'POST', { d: b64(design), photo: { day: jpg(), cut: true } })).status, 422, 'dice recortada pero es JPEG');
  assert.equal((await call({}, 'POST', { d: b64(design), photo: { day: 'data:text/html;base64,PGI+', cut: false } })).status, 422);
  assert.equal((await call({}, 'POST', { d: b64(design), photo: { day: png(1_900_000), cut: true } })).status, 413);
  assert.equal((await call({}, 'POST', { d: b64(design), photo: { day: png(1_500_000), night: png(1_000_000), cut: true } })).status, 413, 'entre las dos pasan el tope');
  assert.equal((await call({}, 'POST', { d: 'basura', photo: { day: png(), cut: true } })).status, 400);
  assert.equal((await call({}, 'POST', undefined)).status, 400);
}));

test('si la APK no coincide con la huella publicada, no se usa y se entrega la normal', async () => {
  const { apk } = fakeApk();
  const realFetch = global.fetch;
  handler._resetCache();
  global.fetch = fakeNetwork(apk, { sha: 'a'.repeat(64) });
  try {
    const r = await call({ d: b64(design) });
    assert.equal(r.status, 302, 'una APK que no es la publicada no debe recibir el diseño');
    const p = await call({ d: b64(design) }, 'POST', { d: b64(design) });
    assert.equal(p.status, 502);
  } finally {
    global.fetch = realFetch;
    handler._resetCache();
  }
});

test('sin version.json o sin huella en él, tampoco se usa la APK', async () => {
  const { apk } = fakeApk();
  const realFetch = global.fetch;
  try {
    handler._resetCache();
    global.fetch = fakeNetwork(apk, { versionStatus: 404 });
    assert.equal((await call({ d: b64(design) })).status, 302, 'version.json ausente');
    handler._resetCache();
    global.fetch = fakeNetwork(apk, { sha: '' });
    assert.equal((await call({ d: b64(design) })).status, 302, 'version.json sin huella');
    handler._resetCache();
    global.fetch = fakeNetwork(apk, { sha: 'no-es-hex' });
    assert.equal((await call({ d: b64(design) })).status, 302, 'huella mal formada');
  } finally {
    global.fetch = realFetch;
    handler._resetCache();
  }
});

test('con la huella correcta (aunque venga en mayúsculas) se usa la APK', async () => {
  const { apk } = fakeApk();
  const realFetch = global.fetch;
  handler._resetCache();
  global.fetch = fakeNetwork(apk, { sha: crypto.createHash('sha256').update(apk).digest('hex').toUpperCase() });
  try {
    const r = await call({ d: b64(design) });
    assert.equal(r.status, 200);
  } finally {
    global.fetch = realFetch;
    handler._resetCache();
  }
});
