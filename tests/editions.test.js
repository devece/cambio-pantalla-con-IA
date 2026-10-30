'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const C = require('../public/shared/catalog.js');
const { free, esencial, completo } = require('../public/shared/editions.json');

const subset = (list, all) => list.every((x) => all.includes(x));

test('la edición gratis solo usa ids que existen en el catálogo', () => {
  assert.ok(subset(free.themes, Object.keys(C.themeTiers)));
  assert.ok(subset(free.layouts, C.layouts));
  assert.ok(subset(free.scenes, C.scenes));
  assert.ok(subset(free.seasons, C.seasons));
  assert.ok(subset(free.features.speedAlerts, C.speedAlerts));
});

test('lo gratis nunca incluye lo que el catálogo reserva al plan de pago', () => {
  free.themes.forEach((t) => assert.equal(C.themeTiers[t], 'base', `el tema ${t} es de pago`));
  free.seasons.forEach((s) => assert.ok(!C.plusSeasons.includes(s), `la temporada ${s} es de pago`));
});

test('la edición gratis tiene al menos un tema, una distribución y una escena', () => {
  assert.ok(free.themes.length >= 1);
  assert.ok(free.layouts.length >= 1);
  assert.ok(free.scenes.length >= 1);
  assert.ok(free.maxApps >= 1 && free.maxApps <= 4);
});

test('las funciones de pago quedan bloqueadas y las gratis activas', () => {
  const f = free.features;
  ['othersMaintenance', 'docsOn', 'fuelOn', 'restrOn', 'voice'].forEach((k) => assert.equal(f[k], false, k));
  ['oilOn', 'speedShow'].forEach((k) => assert.equal(f[k], true, k));
});

test('Esencial y Completo coinciden con los planes del catálogo', () => {
  const base = Object.keys(C.themeTiers).filter((t) => C.themeTiers[t] === 'base');
  assert.deepEqual([...esencial.themes].sort(), [...base].sort(), 'Esencial = temas base');
  assert.deepEqual(esencial.seasons, ['ninguna'], 'Esencial no incluye temporadas');
  assert.equal(completo.themes, '*');
  assert.equal(completo.seasons, '*');
  assert.deepEqual(C.plans.map((p) => p.id).sort(), ['completo', 'esencial']);
});
