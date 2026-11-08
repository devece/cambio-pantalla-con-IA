'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const { fakeApk } = require('./fake-apk.js');
const { readPair, DESIGN_ID } = require('../lib/apk-inject.js');

const ENV = { STATS_SUPABASE_URL: 'https://demo.supabase.co', STATS_SUPABASE_ANON_KEY: 'anon-demo', STATS_HIT_KEY: 'clave-privada' };
const BROWSER = 'Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36';
const realFetch = global.fetch;
const realEnv = {};

function withEnv(on = true) {
  Object.keys(ENV).forEach((k) => { realEnv[k] = process.env[k]; if (on) process.env[k] = ENV[k]; else delete process.env[k]; });
}
function restore() {
  Object.keys(ENV).forEach((k) => { if (realEnv[k] === undefined) delete process.env[k]; else process.env[k] = realEnv[k]; });
  global.fetch = realFetch;
}
const json = (body) => ({ ok: true, status: 200, text: async () => JSON.stringify(body) });
const okEmpty = { ok: true, status: 204, text: async () => '' };

function call(handler, req) {
  return new Promise((resolve) => {
    const res = {
      statusCode: 0, headers: {},
      setHeader(k, v) { this.headers[k] = v; },
      status(c) { this.statusCode = c; return this; },
      json(b) { resolve({ status: this.statusCode, headers: this.headers, body: b }); },
      end(b) { resolve({ status: this.statusCode, headers: this.headers, body: b }); },
    };
    handler({ headers: { 'x-forwarded-for': 'st-' + Math.random(), 'user-agent': BROWSER }, ...req }, res);
  });
}

test('sumar manda la clave privada a la función de la base y nada más', async () => {
  withEnv();
  const llamadas = [];
  global.fetch = async (url, opts) => { llamadas.push({ url, opts }); return okEmpty; };
  try {
    const stats = require('../lib/stats.js');
    assert.equal(await stats.hit('visits'), true);
    assert.equal(llamadas.length, 1);
    assert.equal(llamadas[0].url, 'https://demo.supabase.co/rest/v1/rpc/tablero_hit');
    assert.deepEqual(JSON.parse(llamadas[0].opts.body), { p_kind: 'visits', p_secret: 'clave-privada' });
    assert.equal(llamadas[0].opts.headers.apikey, 'anon-demo');
    assert.equal(await stats.hit('otra-cosa'), false, 'solo visitas y descargas');
    assert.equal(llamadas.length, 1, 'un tipo inválido ni siquiera llega a la base');
  } finally { restore(); }
});

test('sin configuración o con la base caída, no hace nada y no lanza error', async () => {
  const stats = require('../lib/stats.js');
  withEnv(false);
  let llamado = false;
  global.fetch = async () => { llamado = true; return okEmpty; };
  assert.equal(await stats.hit('visits'), false);
  assert.equal(await stats.totals(), null);
  assert.equal(llamado, false);
  restore();
  withEnv();
  global.fetch = async () => { throw new Error('sin red'); };
  try {
    assert.equal(await stats.hit('downloads'), false);
    assert.equal(await stats.totals(), null);
    global.fetch = async () => ({ ok: false, status: 500, text: async () => 'error' });
    assert.equal(await stats.hit('downloads'), false);
  } finally { restore(); }
});

test('los totales se leen de la base', async () => {
  withEnv();
  global.fetch = async () => json([{ kind: 'visits', total: 120 }, { kind: 'downloads', total: '7' }]);
  try {
    assert.deepEqual(await require('../lib/stats.js').totals(), { visits: 120, downloads: 7, downloadsDesign: 0, downloadsPlain: 0 });
  } finally { restore(); }
});

test('los robots no cuentan, ni los que no se identifican', () => {
  const { isBot } = require('../lib/stats.js');
  assert.equal(isBot({ headers: { 'user-agent': BROWSER } }), false);
  assert.equal(isBot({ headers: { 'user-agent': 'facebookexternalhit/1.1' } }), true);
  assert.equal(isBot({ headers: { 'user-agent': 'Googlebot/2.1' } }), true);
  assert.equal(isBot({ headers: { 'user-agent': 'curl/8.0' } }), true);
  assert.equal(isBot({ headers: {} }), true);
});

test('GET /api/stats entrega los totales y se guarda un minuto en la red', async () => {
  withEnv();
  global.fetch = async () => json([{ kind: 'visits', total: 5 }, { kind: 'downloads', total: 2 }]);
  try {
    const r = await call(require('../api/stats.js'), { method: 'GET' });
    assert.equal(r.status, 200);
    assert.deepEqual(r.body, { ok: true, visits: 5, downloads: 2, downloadsDesign: 0, downloadsPlain: 0 });
    assert.match(r.headers['Cache-Control'], /s-maxage=60/);
    global.fetch = async () => { throw new Error('caída'); };
    assert.equal((await call(require('../api/stats.js'), { method: 'GET' })).status, 503);
  } finally { restore(); }
});

test('POST /api/stats suma una visita, salvo robots y tipos inválidos', async () => {
  withEnv();
  const tipos = [];
  global.fetch = async (url, opts) => { tipos.push(JSON.parse(opts.body).p_kind); return okEmpty; };
  const handler = require('../api/stats.js');
  try {
    assert.equal((await call(handler, { method: 'POST', body: { kind: 'visit' } })).status, 204);
    assert.deepEqual(tipos, ['visits']);
    assert.equal((await call(handler, { method: 'POST', body: { kind: 'visit' }, headers: { 'user-agent': 'Googlebot/2.1', 'x-forwarded-for': 'bot' } })).status, 204);
    assert.deepEqual(tipos, ['visits'], 'el robot no suma');
    assert.equal((await call(handler, { method: 'POST', body: { kind: 'download' } })).status, 400, 'las descargas no se suman desde el navegador');
    assert.equal((await call(handler, { method: 'POST', body: { kind: 'visits' } })).status, 400);
    assert.equal((await call(handler, { method: 'DELETE' })).status, 405);
    assert.deepEqual(tipos, ['visits']);
  } finally { restore(); }
});

test('cada descarga de la APK suma una, sin importar si lleva diseño', async () => {
  withEnv();
  const { apk } = fakeApk();
  const tipos = [];
  global.fetch = async (url, opts) => {
    if (String(url).includes('/rpc/')) { tipos.push(JSON.parse(opts.body).p_kind); return okEmpty; }
    return { ok: true, status: 200, arrayBuffer: async () => apk.buffer.slice(apk.byteOffset, apk.byteOffset + apk.length) };
  };
  const app = require('../api/app.js');
  app._resetCache();
  const d = Buffer.from(JSON.stringify({ car: { name: 'Dani' }, style: { theme: 'costa' } })).toString('base64url');
  try {
    const r = await call(app, { method: 'GET', query: { d } });
    assert.equal(r.status, 200);
    assert.ok(readPair(r.body, DESIGN_ID));
    assert.deepEqual(tipos, ['downloads_design'], 'con diseño adentro');
    const normal = await call(app, { method: 'GET', query: {} });
    assert.equal(normal.status, 302, 'la descarga normal (/app) también pasa por aquí');
    assert.deepEqual(tipos, ['downloads_design', 'downloads_plain'], 'la normal cuenta aparte');
    await call(app, { method: 'HEAD', query: { d } });
    assert.equal(tipos.length, 2, 'un HEAD no es una descarga');
    await call(app, { method: 'GET', query: { d }, headers: { 'user-agent': 'facebookexternalhit/1.1', 'x-forwarded-for': 'fb' } });
    assert.equal(tipos.length, 2, 'el robot de Facebook no cuenta');
    const rechazada = await call(app, { method: 'POST', body: { d, photo: { day: 'data:image/png;base64,AAAA', cut: true } } });
    assert.equal(rechazada.status, 422);
    assert.equal(tipos.length, 2, 'una foto rechazada no entrega APK, así que no cuenta');
  } finally { restore(); }
});

test('si el contador falla, la descarga sale igual', async () => {
  withEnv();
  const { apk } = fakeApk();
  global.fetch = async (url) => {
    if (String(url).includes('/rpc/')) throw new Error('base caída');
    return { ok: true, status: 200, arrayBuffer: async () => apk.buffer.slice(apk.byteOffset, apk.byteOffset + apk.length) };
  };
  const app = require('../api/app.js');
  app._resetCache();
  const d = Buffer.from(JSON.stringify({ car: { name: 'Dani' } })).toString('base64url');
  try {
    assert.equal((await call(app, { method: 'GET', query: { d } })).status, 200);
    assert.equal((await call(app, { method: 'GET', query: {} })).status, 302);
  } finally { restore(); }
});

test('los totales separan descargas con diseño y normales, y el total las suma', async () => {
  withEnv();
  global.fetch = async () => json([
    { kind: 'visits', total: 90 }, { kind: 'downloads', total: 40 }, { kind: 'downloads_design', total: 6 }, { kind: 'downloads_plain', total: 2 },
  ]);
  try {
    assert.deepEqual(await require('../lib/stats.js').totals(), { visits: 90, downloads: 40, downloadsDesign: 6, downloadsPlain: 2 });
    const r = await call(require('../api/stats.js'), { method: 'GET' });
    assert.equal(r.body.downloadsDesign, 6);
    assert.equal(r.body.downloadsPlain, 2);
  } finally { restore(); }
});

test('un fallo al armar el diseño entrega la APK normal y cuenta como normal', async () => {
  withEnv();
  const tipos = [];
  global.fetch = async (url, opts) => {
    if (String(url).includes('/rpc/')) { tipos.push(JSON.parse(opts.body).p_kind); return okEmpty; }
    throw new Error('GitHub caído');
  };
  const app = require('../api/app.js');
  app._resetCache();
  const d = Buffer.from(JSON.stringify({ car: { name: 'Dani' } })).toString('base64url');
  try {
    const r = await call(app, { method: 'GET', query: { d } });
    assert.equal(r.status, 302);
    assert.deepEqual(tipos, ['downloads_plain'], 'recibió la APK sin diseño');
  } finally { restore(); }
});
