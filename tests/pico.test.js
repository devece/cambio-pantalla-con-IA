'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const P = require('../public/shared/pico.js');
const { validateOrder, normalizeMobile } = require('../public/shared/order.js');

const city = (id) => P.cities.find((c) => c.id === id);
// Fechas locales: lunes 21 de septiembre de 2026 (día impar) y martes 22 (día par).
const at = (y, m, d, h = 10, min = 0) => new Date(y, m - 1, d, h, min);

test('Bogotá se rige por día par o impar del mes, de lunes a viernes', () => {
  const b = city('bogota');
  assert.deepEqual(P.restrictedDigits(b, at(2026, 9, 21)), [6, 7, 8, 9, 0], 'día impar: restringen 6-0');
  assert.deepEqual(P.restrictedDigits(b, at(2026, 9, 22)), [1, 2, 3, 4, 5], 'día par: restringen 1-5');
  assert.equal(P.restrictedDigits(b, at(2026, 9, 26)), null, 'sábado no rige');
  assert.equal(P.isRestricted(b, 7, at(2026, 9, 21)), true);
  assert.equal(P.isRestricted(b, 3, at(2026, 9, 21)), false);
});

test('cada dígito 0-9 tiene exactamente un día de restricción a la semana en las ciudades por día de semana', () => {
  P.cities.filter((c) => c.rule.kind === 'weekday').forEach((c) => {
    const all = c.weekdays.flatMap((w) => c.rule.days[w]).sort((a, b) => a - b);
    assert.deepEqual(all, [0, 1, 2, 3, 4, 5, 6, 7, 8, 9], c.id);
  });
  const b = city('bogota');
  assert.deepEqual([...b.rule.odd, ...b.rule.even].sort((a, c) => a - c), [0, 1, 2, 3, 4, 5, 6, 7, 8, 9]);
});

test('Quito tiene dos franjas horarias y avisa solo cuando corresponde', () => {
  const q = city('quito');
  assert.equal(P.isRestricted(q, 3, at(2026, 6, 23)), true, 'martes: 3 y 4');
  const manana = P.notice(q, 3, at(2026, 6, 23, 7, 0));
  assert.equal(manana.warn, true);
  assert.match(manana.text, /Quito/);
  const medio = P.notice(q, 3, at(2026, 6, 23, 11, 0));
  assert.equal(medio.warn, false, 'entre franjas todavía le toca hoy, pero no rige ahora');
  assert.equal(P.notice(q, 3, at(2026, 6, 23, 21, 0)), null, 'ya pasó el horario de hoy');
  assert.equal(P.notice(q, 5, at(2026, 6, 23, 7, 0)), null, 'ese día no le toca');
});

test('una regla vencida no afirma nada y manda a revisar la fuente oficial', () => {
  const m = city('medellin');
  const n = P.notice(m, 5, at(2027, 1, 4));
  assert.equal(n.warn, false);
  assert.match(n.text, /valía hasta el 2026-12-31/);
  assert.equal(P.sampleDate(m, 5, at(2027, 1, 4)), null, 'no inventa un día de ejemplo con una regla vencida');
});

test('Hoy No Circula de la Ciudad de México: un día por dígito, de 5:00 a 22:00 y aviso de exenciones', () => {
  const m = city('cdmx');
  assert.equal(P.isRestricted(m, 5, at(2026, 10, 19)), true, 'lunes: 5 y 6');
  assert.equal(P.isRestricted(m, 0, at(2026, 10, 23)), true, 'viernes: 9 y 0');
  assert.equal(P.restrictedDigits(m, at(2026, 10, 24)), null, 'sábado no se incluye');
  const n = P.notice(m, 5, at(2026, 10, 19, 8, 0));
  assert.equal(n.warn, true);
  assert.match(n.text, /holograma 0 o 00/);
  assert.equal(P.notice(m, 5, at(2026, 10, 19, 23, 0)), null, 'ya pasó el horario');
  assert.equal(P.cityAt(19.43, -99.13, 'MX').id, 'cdmx');
  assert.equal(P.cityAt(19.43, -99.13, 'CO'), null);
  assert.match(P.describe(m, 5), /te toca el lunes/);
});

test('la ciudad se detecta por GPS y solo dentro del país elegido', () => {
  assert.equal(P.cityAt(4.65, -74.08, 'CO').id, 'bogota');
  assert.equal(P.cityAt(6.25, -75.57, 'CO').id, 'medellin');
  assert.equal(P.cityAt(3.45, -76.53, 'CO').id, 'cali');
  assert.equal(P.cityAt(-0.18, -78.47, 'EC').id, 'quito');
  assert.equal(P.cityAt(4.65, -74.08, 'EC'), null, 'Bogotá no es de Ecuador');
  assert.equal(P.cityAt(-33.45, -70.66, 'CO'), null, 'Santiago no es de Colombia');
  assert.equal(P.cityAt('x', null, 'CO'), null);
});

test('cada ciudad cita su fuente, cuándo se revisó y qué tan confiable es', () => {
  P.cities.forEach((c) => {
    assert.match(c.source, /^https:\/\//, c.id);
    assert.match(c.checkedAt, /^\d{4}-\d{2}-\d{2}$/, c.id);
    assert.ok(['oficial', 'prensa'].includes(c.confidence), c.id);
    assert.ok(c.zone.length >= 4, c.id);
    c.hours.forEach(([a, b]) => assert.ok(a >= 0 && b <= 1440 && a < b, c.id));
  });
});

test('describe explica el día de la placa y la fuente', () => {
  assert.match(P.describe(city('medellin'), 7), /te toca el viernes/);
  assert.match(P.describe(city('bogota'), 7), /días impares/);
  assert.match(P.describe(city('bogota'), 2), /días pares/);
  assert.match(P.describe(city('quito'), 9), /Fuente: prensa/);
  assert.match(P.describe(city('bogota'), 7), /Fuente oficial/);
});

test('el pedido acepta país y valida el celular de ese país', () => {
  const base = (country, phone) => ({
    radio: { type: 'android' }, car: { type: 'auto' }, style: { theme: 'ruta', layout: 'consola', apps: ['maps'] },
    features: { country, restrOn: true, restrDigit: '7' }, plan: 'esencial', contact: { kind: 'whatsapp', value: phone }, terms: true,
  });
  const co = validateOrder(base('CO', '300 123 4567'));
  assert.equal(co.ok, true, co.errors.join(' | '));
  assert.equal(co.clean.contact.value, '+573001234567');
  assert.equal(co.clean.features.country, 'CO');
  assert.deepEqual(co.clean.features.restrDays, [], 'los días de Chile no aplican fuera de Chile');
  const ec = validateOrder(base('EC', '0991234567'));
  assert.equal(ec.ok, true, ec.errors.join(' | '));
  assert.equal(ec.clean.contact.value, '+593991234567');
  assert.equal(validateOrder(base('CO', '+56 9 1234 5678')).ok, false, 'un celular chileno no sirve en Colombia');
  assert.equal(validateOrder(base('XX', '+56 9 1234 5678')).clean.features.country, 'CL', 'país desconocido vuelve a Chile');
  const sinDigito = base('CO', '3001234567');
  sinDigito.features.restrDigit = '';
  assert.match(validateOrder(sinDigito).errors.join(' '), /pico y placa/);
});

test('normalizeMobile sigue funcionando como antes para Chile', () => {
  assert.equal(normalizeMobile('+56 9 1234 5678'), '+56912345678');
  assert.equal(normalizeMobile('912345678'), '+56912345678');
  assert.equal(normalizeMobile('+57 300 123 4567', 'CO'), '+573001234567');
  assert.equal(normalizeMobile('hola', 'CO'), null);
});
