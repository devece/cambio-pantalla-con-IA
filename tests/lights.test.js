'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const { cleanLights, SCHEMA } = require('../lib/lights.js');

const fakeRes = () => {
  const r = { headers: {}, code: 200, body: null };
  r.setHeader = (k, v) => { r.headers[k] = v; };
  r.status = (c) => { r.code = c; return r; };
  r.json = (b) => { r.body = b; return r; };
  return r;
};

test('cleanLights deja solo luces válidas, dentro de la imagen y con tamaño razonable', () => {
  const r = cleanLights({
    facing: 'derecha',
    lights: [
      { kind: 'faro', x: 0.31234, y: 0.5, size: 1 },
      { kind: 'faro', x: 3, y: -2, size: 99 },
      { kind: 'laser', x: 0.5, y: 0.5, size: 1 },
      { kind: 'neblinero', x: 'x', y: 0.5, size: 1 },
      { kind: 'luz_trasera', x: 0.9, y: 0.4, size: 'grande' },
      ...Array(10).fill({ kind: 'faro', x: 0.2, y: 0.2, size: 1 }),
    ],
  });
  assert.equal(r.facing, 'derecha');
  assert.equal(r.lights.length, 6);
  assert.deepEqual(r.lights[0], { kind: 'faro', x: 0.312, y: 0.5, size: 1 });
  assert.deepEqual(r.lights[1], { kind: 'faro', x: 1, y: 0, size: 2 });
  assert.equal(r.lights[2].kind, 'luz_trasera');
  assert.equal(r.lights[2].size, 1);
});

test('cleanLights tolera basura', () => {
  assert.deepEqual(cleanLights(null), { facing: 'no_se_ve', lights: [] });
  assert.deepEqual(cleanLights({ facing: 'arriba', lights: 'x' }), { facing: 'no_se_ve', lights: [] });
  assert.ok(SCHEMA.required.includes('lights'));
});

test('el endpoint de detección responde 503 sin clave y 400 con una imagen inválida', async () => {
  const saved = process.env.ANTHROPIC_API_KEY;
  const handler = require('../api/detect-lights.js');
  const post = (body) => ({ method: 'POST', headers: { 'x-forwarded-for': 'test-' + Math.random() }, body });
  try {
    delete process.env.ANTHROPIC_API_KEY;
    const a = fakeRes();
    await handler(post({ image: 'data:image/png;base64,' + 'A'.repeat(300) }), a);
    assert.equal(a.code, 503);
    process.env.ANTHROPIC_API_KEY = 'sk-ant-prueba';
    const b = fakeRes();
    await handler(post({ image: 'no es una imagen' }), b);
    assert.equal(b.code, 400);
    const c = fakeRes();
    await handler({ method: 'GET', headers: {} }, c);
    assert.equal(c.code, 405);
  } finally {
    if (saved === undefined) delete process.env.ANTHROPIC_API_KEY;
    else process.env.ANTHROPIC_API_KEY = saved;
  }
});
