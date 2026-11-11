'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const budget = require('../lib/budget.js');
const http = require('../lib/http.js');

function fakeRes() {
  return { code: 0, body: null, headers: {}, setHeader(k, v) { this.headers[k] = v; }, status(c) { this.code = c; return this; }, json(b) { this.body = b; return this; } };
}
const withEnv = async (vars, fn) => {
  const saved = {};
  for (const k of Object.keys(vars)) saved[k] = process.env[k];
  try {
    for (const [k, v] of Object.entries(vars)) { if (v === undefined) delete process.env[k]; else process.env[k] = v; }
    await fn();
  } finally {
    for (const [k, v] of Object.entries(saved)) { if (v === undefined) delete process.env[k]; else process.env[k] = v; }
  }
};

test('sin AI_DAILY_MAX la IA queda apagada (falla cerrada)', async () => {
  await withEnv({ AI_DAILY_MAX: undefined }, () => {
    budget._reset();
    assert.deepEqual(budget.take(), { ok: false, reason: 'off' });
  });
  for (const bad of ['', '0', '-3', 'abc', '2.5']) {
    await withEnv({ AI_DAILY_MAX: bad }, () => {
      budget._reset();
      assert.equal(budget.take().reason, 'off', 'valor inválido: ' + JSON.stringify(bad));
    });
  }
});

test('el tope diario se respeta y se reinicia al día siguiente', async () => {
  await withEnv({ AI_DAILY_MAX: '2' }, () => {
    budget._reset();
    const d1 = new Date('2026-11-11T10:00:00Z');
    assert.equal(budget.take(d1).ok, true);
    assert.equal(budget.take(d1).ok, true);
    assert.deepEqual(budget.take(d1), { ok: false, reason: 'cap' });
    assert.equal(budget.take(new Date('2026-11-12T00:00:01Z')).ok, true, 'al día siguiente vuelve a contar');
  });
});

for (const [name, file] of [['analyze-radio', '../api/analyze-radio.js'], ['detect-lights', '../api/detect-lights.js']]) {
  test(`${name}: con clave pero sin tope diario responde 503 y con el tope agotado, 429 (sin llamar a la IA)`, async () => {
    const handler = require(file);
    const png = 'data:image/png;base64,' + 'A'.repeat(300);
    const post = () => ({ method: 'POST', headers: { 'x-forwarded-for': 'presupuesto-' + Math.random() }, body: { image: png } });
    await withEnv({ ANTHROPIC_API_KEY: 'sk-ant-prueba', AI_DAILY_MAX: undefined }, async () => {
      budget._reset();
      const a = fakeRes();
      await handler(post(), a);
      assert.equal(a.code, 503);
    });
    await withEnv({ ANTHROPIC_API_KEY: 'sk-ant-prueba', AI_DAILY_MAX: '1' }, async () => {
      budget._reset();
      assert.equal(budget.take().ok, true); // se gasta el único cupo del día
      const b = fakeRes();
      await handler(post(), b);
      assert.equal(b.code, 429);
    });
    await withEnv({ ANTHROPIC_API_KEY: 'sk-ant-prueba', AI_DAILY_MAX: '1' }, async () => {
      budget._reset();
      const c = fakeRes();
      await handler({ method: 'POST', headers: { 'x-forwarded-for': 'invalida-' + Math.random() }, body: { image: 'no es una imagen' } }, c);
      assert.equal(c.code, 400, 'una imagen inválida se rechaza antes de gastar el cupo');
      assert.equal(budget.take().ok, true, 'y no consumió el cupo');
    });
  });
}

test('el límite por IP no se reinicia aunque se llene el mapa de direcciones mientras sigue abusando', () => {
  const req = (ip) => ({ headers: { 'x-forwarded-for': ip } });
  const atacante = 'atacante-' + Math.random();
  for (let i = 0; i < 3; i++) assert.equal(http.rateLimit(req(atacante), { max: 3 }), true);
  assert.equal(http.rateLimit(req(atacante), { max: 3 }), false, 'se pasó del límite');
  for (let i = 0; i < 6000; i++) {
    http.rateLimit(req('relleno-' + i), { max: 1 }); // llena el mapa (tope 5000)
    if (i % 100 === 0) assert.equal(http.rateLimit(req(atacante), { max: 3 }), false, 'sigue bloqueado mientras insiste');
  }
  assert.ok(http._hits.size <= 5000 + 1, 'el mapa no crece sin límite');
  assert.equal(http.rateLimit(req(atacante), { max: 3 }), false, 'llenar el mapa no le devuelve el cupo a quien sigue activo');
});
