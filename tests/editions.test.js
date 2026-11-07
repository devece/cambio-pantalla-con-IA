'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const C = require('../public/shared/catalog.js');
const { free, esencial, completo } = require('../public/shared/editions.json');

const subset = (list, all) => list === '*' || list.every((x) => all.includes(x));

test('la edición gratis solo usa ids que existen en el catálogo', () => {
  assert.ok(subset(free.themes, Object.keys(C.themeTiers)));
  assert.ok(subset(free.layouts, C.layouts));
  assert.ok(subset(free.scenes, C.scenes));
  assert.ok(subset(free.seasons, C.seasons));
  assert.ok(subset(free.features.speedAlerts, C.speedAlerts));
});

test('por ahora no hay versión de pago: la edición gratis incluye todo', () => {
  // Si algún día vuelve a haber planes, este es el lugar para decidirlo a propósito.
  assert.deepEqual(free, completo, "'free' debe ser igual a 'completo' mientras no haya versión de pago");
  ['themes', 'layouts', 'scenes', 'seasons'].forEach((k) => assert.equal(free[k], '*', k));
  assert.equal(free.features.speedAlerts, '*');
  assert.equal(free.maxApps, 4);
  ['oilOn', 'speedShow', 'othersMaintenance', 'docsOn', 'fuelOn', 'restrOn', 'voice'].forEach((k) => assert.equal(free.features[k], true, k));
});

test('la edición gratis permite el límite de velocidad manual (el único que hoy funciona)', () => {
  assert.equal(free.features.speedAlerts, '*');
  assert.ok(C.speedAlerts.includes('manual'));
});

test('Esencial y Completo coinciden con los planes del catálogo', () => {
  const base = Object.keys(C.themeTiers).filter((t) => C.themeTiers[t] === 'base');
  assert.deepEqual([...esencial.themes].sort(), [...base].sort(), 'Esencial = temas base');
  assert.deepEqual(esencial.seasons, ['ninguna'], 'Esencial no incluye temporadas');
  assert.equal(completo.themes, '*');
  assert.equal(completo.seasons, '*');
  assert.deepEqual(C.plans.map((p) => p.id).sort(), ['completo', 'esencial']);
});

test('maintenance.json (usado por el launcher) coincide con el catálogo del sitio', () => {
  const maintenance = require('../public/shared/maintenance.json');
  assert.deepEqual(maintenance, C.maintenance);
});

test('restriction.json (usado por el launcher) coincide con el catálogo del sitio', () => {
  const json = require('../public/shared/restriction.json');
  const r = C.restriction;
  assert.deepEqual(json, { year: r.year, verifiedAt: r.verifiedAt, season: r.season, zone: r.zone, types: r.types, digits: r.digits, sources: r.sources });
});
