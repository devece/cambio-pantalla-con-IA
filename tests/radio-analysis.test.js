'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const { parseImage, cleanAnalysis, SCHEMA } = require('../lib/radio-analysis.js');
const { validateOrder } = require('../public/shared/order.js');

const b64 = 'A'.repeat(400);

test('parseImage acepta solo imágenes base64 válidas', () => {
  assert.deepEqual(parseImage(`data:image/jpeg;base64,${b64}`), { mediaType: 'image/jpeg', data: b64 });
  assert.equal(parseImage('data:image/gif;base64,' + b64), null);
  assert.equal(parseImage('data:image/png;base64,corto'), null);
  assert.equal(parseImage('https://ejemplo.com/a.png'), null);
  assert.equal(parseImage(`data:image/png;base64,${'A'.repeat(4_000_000)}`), null);
  assert.equal(parseImage(null), null);
});

test('cleanAnalysis descarta valores fuera de catálogo y recorta textos', () => {
  const r = cleanAnalysis({
    shows_head_unit: true,
    android_detected: 'sí',
    compatible: 'tal vez',
    reason: '<b>Hola</b>\n' + 'x'.repeat(500),
    android_version: '10',
    visible_apps: ['Maps', '', 'a'.repeat(80), ...Array(20).fill('Waze')],
    recommended: { layout: 'inventado', theme: 'cristal', dock_apps: ['maps', 'zzz', 'maps', 'waze', 'spotify', 'youtube', 'whatsapp'] },
    notes: ['uno', 'dos', 'tres', 'cuatro', 'cinco'],
  });
  assert.equal(r.android_detected, false);
  assert.equal(r.compatible, 'quizas');
  assert.ok(r.reason.length <= 280 && !/[<>]/.test(r.reason));
  assert.equal(r.recommended.layout, 'tarjetas');
  assert.equal(r.recommended.theme, 'cristal');
  assert.deepEqual(r.recommended.dock_apps, ['maps', 'waze', 'spotify', 'youtube']);
  assert.equal(r.visible_apps.length, 8);
  assert.equal(r.notes.length, 4);
});

test('cleanAnalysis tolera basura', () => {
  const r = cleanAnalysis('nada');
  assert.equal(r.compatible, 'quizas');
  assert.deepEqual(r.recommended.dock_apps, ['maps', 'spotify', 'waze']);
});

test('el esquema exige todos los campos que devuelve cleanAnalysis', () => {
  const keys = Object.keys(cleanAnalysis({}));
  keys.forEach((k) => assert.ok(SCHEMA.required.includes(k) || k === 'shows_head_unit', k));
});

test('el pedido solo guarda las medidas de la captura, nunca la imagen', () => {
  const base = () => ({
    radio: { type: 'android', screen: { w: 1280, h: 720 }, image: 'data:image/png;base64,AAAA' },
    car: { type: 'suv', color: '#1f5fa8', name: 'Daniel', greeting: 'Hola' },
    style: { theme: 'ruta', layout: 'tarjetas', scene: 'tema', season: 'ninguna', apps: ['maps'] },
    features: { oilOn: true, odometer: 60000, lastOil: 58000, oilEvery: 5000 },
    plan: 'esencial', addons: {}, contact: { kind: 'whatsapp', value: '+56 9 1234 5678' }, terms: true,
  });
  const r = validateOrder(base());
  assert.equal(r.ok, true, r.errors.join('|'));
  assert.deepEqual(r.clean.radio.screen, { w: 1280, h: 720 });
  assert.equal(JSON.stringify(r.clean).includes('base64'), false);
  const bad = base();
  bad.radio.screen = { w: 5, h: 'x' };
  assert.equal(validateOrder(bad).clean.radio.screen, null);
});
