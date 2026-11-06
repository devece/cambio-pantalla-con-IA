'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const { execFileSync } = require('node:child_process');
const path = require('node:path');
const P = require('../public/shared/pico.js');
const cl = require('../public/shared/restriction.json');
const datos = require('../paquete-restricciones/data/restricciones.json');

test('el paquete de restricciones pasa sus propios casos de prueba', () => {
  const out = execFileSync('node', [path.join(__dirname, '../paquete-restricciones/pruebas/correr.js')], { encoding: 'utf8' });
  assert.match(out, /0 mal/);
});

test('el paquete trae los mismos datos que usa Tablero para Colombia y Ecuador', () => {
  P.cities.forEach((c) => {
    const d = datos.cities.find((x) => x.id === c.id);
    assert.ok(d, `falta ${c.id} en el paquete`);
    assert.deepEqual(d.rule, c.rule, c.id);
    assert.deepEqual(d.hours, c.hours, c.id);
    assert.deepEqual(d.zone, c.zone, c.id);
    assert.equal(d.validTo, c.validTo, c.id);
    assert.equal(d.confidence, c.confidence, c.id);
  });
});

test('el paquete trae la misma tabla de Chile que usa Tablero', () => {
  const d = datos.cities.find((x) => x.id === 'santiago');
  assert.deepEqual(d.months, cl.season.months);
  assert.deepEqual(d.zone, cl.zone.polygon);
  assert.deepEqual(d.rule.classes.find((c) => c.id === 'sello').days, cl.digits.sello);
  assert.deepEqual(d.rule.classes.find((c) => c.id === 'sin').days, cl.digits.sin);
});

test('cada ciudad del paquete tiene los campos que exige el esquema', () => {
  const req = ['id', 'country', 'name', 'label', 'confidence', 'sources', 'checkedAt', 'weekdays', 'hours', 'rule', 'zone'];
  datos.cities.forEach((c) => {
    req.forEach((k) => assert.ok(c[k] !== undefined, `${c.id}: falta ${k}`));
    assert.ok(datos.countries.some((p) => p.id === c.country), `${c.id}: país desconocido`);
    assert.ok(c.sources.length >= 1);
  });
});
