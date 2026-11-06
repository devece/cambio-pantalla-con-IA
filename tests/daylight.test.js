'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const D = require('../public/shared/daylight.js');

// Santiago de Chile.
const LAT = -33.45;
const LON = -70.66;
const hhmm = (min) => `${String(Math.floor(min / 60)).padStart(2, '0')}:${String(Math.round(min % 60)).padStart(2, '0')}`;
const near = (min, h, m, tol = 12) => assert.ok(Math.abs(min - (h * 60 + m)) <= tol, `${hhmm(min)} no está a ${tol} min de ${h}:${m}`);
const at = (y, mo, d, h, mi) => new Date(y, mo - 1, d, h, mi);

test('Santiago en pleno invierno (21 de junio, UTC-4): sale ~07:45 y se pone ~17:50', () => {
  const s = D.sunTimes(at(2026, 6, 21, 12, 0), LAT, LON, -240);
  near(s.sunrise, 7, 45);
  near(s.sunset, 17, 50);
});

test('Santiago en pleno verano (21 de diciembre, UTC-3): sale ~06:30 y se pone ~20:52', () => {
  const s = D.sunTimes(at(2026, 12, 21, 12, 0), LAT, LON, -180);
  near(s.sunrise, 6, 30);
  near(s.sunset, 20, 52);
});

test('el verano alarga el día y el invierno lo acorta', () => {
  const len = (m, d, tz) => { const s = D.sunTimes(at(2026, m, d, 12, 0), LAT, LON, tz); return s.sunset - s.sunrise; };
  assert.ok(len(12, 21, -180) > len(6, 21, -240) + 180);
});

test('el cambio día/noche sigue al sol, no una hora fija', () => {
  // 18:30 en invierno ya es de noche (la hora fija de antes decía que no hasta las 19).
  assert.equal(D.isNight(at(2026, 6, 21, 18, 30), LAT, LON, -240), true);
  // 19:30 en verano aún es de día (la hora fija decía noche).
  assert.equal(D.isNight(at(2026, 12, 21, 19, 30), LAT, LON, -180), false);
  assert.equal(D.isNight(at(2026, 12, 21, 3, 0), LAT, LON, -180), true);
  assert.equal(D.isNight(at(2026, 12, 21, 12, 0), LAT, LON, -180), false);
});

test('fases del día para el fondo', () => {
  const p = (h, m) => D.phase(at(2026, 12, 21, h, m), LAT, LON, -180);
  assert.equal(p(2, 0), 'noche');
  assert.equal(p(6, 0), 'amanecer');
  assert.equal(p(12, 0), 'dia');
  assert.equal(p(20, 0), 'atardecer');
  assert.equal(p(23, 0), 'noche');
});

test('sin sol en el día (polo) no revienta y responde según corresponda', () => {
  const verano = D.sunTimes(at(2026, 12, 21, 12, 0), -80, 0, 0);
  assert.equal(verano.polar, 'dia');
  assert.equal(D.isNight(at(2026, 12, 21, 2, 0), -80, 0, 0), false);
  assert.equal(D.phase(at(2026, 6, 21, 12, 0), -80, 0, 0), 'noche');
});

test('día del año', () => {
  assert.equal(D.dayOfYear(at(2026, 1, 1, 0, 0)), 1);
  assert.equal(D.dayOfYear(at(2026, 12, 31, 23, 0)), 365);
  assert.equal(D.dayOfYear(at(2028, 12, 31, 23, 0)), 366);
});
