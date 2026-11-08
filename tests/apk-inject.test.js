'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const { DESIGN_ID, readPair, injectPair, locate, pairs } = require('../lib/apk-inject.js');

const { fakeApk, u16, u32 } = require('./fake-apk.js');

test('agrega el diseño y se puede leer de vuelta', () => {
  const { apk } = fakeApk();
  assert.equal(readPair(apk), null);
  const design = Buffer.from(JSON.stringify({ v: 1, style: { theme: 'costa' } }));
  const out = injectPair(apk, DESIGN_ID, design);
  assert.deepEqual(readPair(out), design);
});

test('no toca las entradas del zip ni el directorio central, y conserva los pares de la firma', () => {
  const { apk, local, cd } = fakeApk();
  const out = injectPair(apk, DESIGN_ID, Buffer.from('{"v":1}'));
  assert.ok(out.subarray(0, local.length).equals(local), 'las entradas del zip no cambian');
  assert.ok(out.includes(cd), 'el directorio central sigue igual');
  const ids = pairs(out, locate(out)).map((p) => p.id);
  assert.deepEqual(ids, [0x7109871a, DESIGN_ID]);
  assert.deepEqual(pairs(out, locate(out))[0].value, Buffer.from('firma-falsa'));
});

test('la posición del directorio central en el final del zip queda apuntando bien', () => {
  const { apk } = fakeApk();
  const out = injectPair(apk, DESIGN_ID, Buffer.from('x'.repeat(300)));
  const loc = locate(out);
  assert.equal(out.readUInt32LE(loc.cdOffset), 0x02014b50, 'ahí empieza el directorio central');
  assert.equal(out.length - apk.length, 12 + 300);
});

test('inyectar dos veces reemplaza el diseño anterior', () => {
  const { apk } = fakeApk();
  const once = injectPair(apk, DESIGN_ID, Buffer.from('uno'));
  const twice = injectPair(once, DESIGN_ID, Buffer.from('dos'));
  assert.deepEqual(readPair(twice), Buffer.from('dos'));
  assert.equal(pairs(twice, locate(twice)).filter((p) => p.id === DESIGN_ID).length, 1);
});

test('una APK sin bloque de firmas no se modifica en silencio: da error', () => {
  const sin = Buffer.concat([Buffer.alloc(100), Buffer.concat([u32(0x06054b50), u16(0), u16(0), u16(0), u16(0), u32(0), u32(100), u16(0)])]);
  assert.throws(() => injectPair(sin, DESIGN_ID, Buffer.from('x')), /bloque de firmas/);
  assert.throws(() => injectPair(Buffer.from('nada'), DESIGN_ID, Buffer.from('x')), /APK/);
});
