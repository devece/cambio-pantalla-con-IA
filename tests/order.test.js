'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const { validateOrder, normalizeMobile } = require('../public/shared/order.js');
const { generateCode, normalizeCode, hashDeviceId, FORMAT } = require('../lib/license.js');

const base = () => ({
  radio: { type: 'android', brand: 'nwd' },
  car: { type: 'suv', color: '#1f5fa8', name: 'Daniel', greeting: 'Hola' },
  style: { theme: 'ruta', layout: 'tarjetas', scene: 'tema', season: 'ninguna', apps: ['maps', 'spotify'] },
  features: { oilOn: true, odometer: 60000, lastOil: 58000, oilEvery: 5000 },
  contact: { kind: 'whatsapp', value: '+56 9 1234 5678' },
});

test('un pedido correcto pasa y normaliza el celular', () => {
  const r = validateOrder(base());
  assert.equal(r.ok, true, r.errors.join(' | '));
  assert.equal(r.clean.contact.value, '+56912345678');
});

test('radio original no es compatible', () => {
  const o = base();
  o.radio.type = 'original';
  const r = validateOrder(o);
  assert.equal(r.ok, false);
  assert.match(r.errors.join(' '), /no son compatibles/);
});

test('todos los diseños y decoraciones se pueden pedir: no hay planes', () => {
  const o = base();
  o.style.theme = 'cordillera';
  o.style.season = 'navidad';
  const r = validateOrder(o);
  assert.equal(r.ok, true, r.errors.join(' | '));
  assert.equal(r.clean.style.theme, 'cordillera');
  assert.equal(r.clean.style.season, 'navidad');
});

test('el pedido limpio no trae plan, extras, precios ni términos', () => {
  const o = base();
  Object.assign(o, { plan: 'completo', addons: { foto: true }, total: 1, price: 1, terms: true });
  const { clean } = validateOrder(o);
  ['plan', 'addons', 'total', 'price', 'terms'].forEach((k) => assert.equal(k in clean, false, k));
});

test('"No sé" debe confirmarse antes de hacer el pedido', () => {
  const o = base();
  o.radio.type = 'nose';
  const r = validateOrder(o);
  assert.equal(r.ok, false);
  assert.match(r.errors.join(' '), /Confirma que tu radio/);
});

test('descarta campos desconocidos, valores fuera de rango y colores inválidos', () => {
  const o = base();
  o.car.color = 'rojo';
  o.features.odometer = 99999999;
  o.style.apps = ['maps', 'hackeada', 'maps', 'waze', 'spotify', 'ytmusic', 'youtube'];
  o.admin = true;
  const r = validateOrder(o);
  assert.equal(r.clean.car.color, '#b3261e');
  assert.equal(r.clean.features.odometer, 999999);
  assert.deepEqual(r.clean.style.apps, ['maps', 'waze', 'spotify', 'ytmusic']);
  assert.equal('admin' in r.clean, false);
});

test('exige un contacto válido', () => {
  const o = base();
  o.contact.value = '12345';
  const r = validateOrder(o);
  assert.equal(r.ok, false);
  assert.equal(r.errors.length, 1);
  assert.match(r.errors[0], /celular/);
});

test('correo como contacto', () => {
  const o = base();
  o.contact = { kind: 'email', value: 'Cliente@Correo.CL' };
  const r = validateOrder(o);
  assert.equal(r.ok, true);
  assert.equal(r.clean.contact.value, 'cliente@correo.cl');
});

test('normalizeMobile acepta formatos comunes de Chile', () => {
  assert.equal(normalizeMobile('+56 9 8765 4321'), '+56987654321');
  assert.equal(normalizeMobile('987654321'), '+56987654321');
  assert.equal(normalizeMobile('56987654321'), '+56987654321');
  assert.equal(normalizeMobile('22345678'), null);
});

test('códigos de licencia: formato, unicidad y normalización', () => {
  const seen = new Set();
  for (let i = 0; i < 500; i++) {
    const c = generateCode();
    assert.match(c, FORMAT);
    seen.add(c);
  }
  assert.equal(seen.size, 500);
  const c = generateCode();
  assert.equal(normalizeCode(c.toLowerCase().replace(/-/g, ' ')), c);
  assert.equal(normalizeCode('TBL-1234-5678-9ABC'), null); // contiene caracteres ambiguos
  assert.equal(normalizeCode('hola'), null);
});

test('el hash del dispositivo es estable y depende del pepper', () => {
  assert.equal(hashDeviceId('radio-123456', 'a'), hashDeviceId('radio-123456', 'a'));
  assert.notEqual(hashDeviceId('radio-123456', 'a'), hashDeviceId('radio-123456', 'b'));
});

test('la restricción vehicular se calcula con el dígito de la patente (Región Metropolitana)', () => {
  const { restrDaysFor } = require('../public/shared/order.js');
  const f = (restrType, restrDigit, extra) => Object.assign({ restrOn: true, restrType, restrDigit }, extra);
  assert.deepEqual(restrDaysFor(f('sello', '8')), ['lun']);
  assert.deepEqual(restrDaysFor(f('sello', '1')), ['mar']);
  assert.deepEqual(restrDaysFor(f('sello', '7')), ['vie']);
  // sin sello verde: 4 dígitos por día, así que cada dígito cae en dos días
  assert.deepEqual(restrDaysFor(f('sin', '8')), ['lun', 'jue']);
  assert.deepEqual(restrDaysFor(f('sin', '0')), ['mar', 'jue']);
  // sin restricción permanente
  assert.deepEqual(restrDaysFor(f('nuevo', '3')), []);
  assert.deepEqual(restrDaysFor(f('sello', '', {})), []);
  assert.deepEqual(restrDaysFor(f('sello', '3', { restrOn: false })), []);
});

test('cada dígito del 0 al 9 aparece en la tabla de restricción una vez (con sello) o dos (sin sello)', () => {
  const C = require('../public/shared/catalog.js');
  for (let d = 0; d <= 9; d++) {
    const con = C.weekdays.filter((id) => C.restriction.digits.sello[id].includes(d));
    const sin = C.weekdays.filter((id) => C.restriction.digits.sin[id].includes(d));
    assert.equal(con.length, 1, `sello ${d}`);
    assert.equal(sin.length, 2, `sin sello ${d}`);
  }
});

test('el servidor ignora los días enviados por el navegador y exige el dígito', () => {
  const o = base();
  o.features.restrOn = true;
  o.features.restrType = 'sello';
  o.features.restrDigit = '2';
  o.features.restrDays = ['lun', 'mar', 'jue'];
  const r = validateOrder(o);
  assert.equal(r.ok, true, r.errors.join('|'));
  assert.deepEqual(r.clean.features.restrDays, ['mie']);
  o.features.restrDigit = '';
  const bad = validateOrder(o);
  assert.equal(bad.ok, false);
  assert.ok(bad.errors.some((e) => /dígito/.test(e)));
});

test('la zona de restricción se detecta con el GPS (Santiago sí, otras ciudades no)', () => {
  const { inRestrictionZone } = require('../public/shared/order.js');
  const casos = [
    ['Plaza de Armas, Santiago', -33.4378, -70.6505, true],
    ['Las Condes', -33.408, -70.567, true],
    ['Maipú', -33.51, -70.76, true],
    ['Puente Alto', -33.61, -70.575, true],
    ['San Bernardo', -33.59, -70.70, true],
    ['Valparaíso', -33.0472, -71.6127, false],
    ['Rancagua', -34.1708, -70.7444, false],
    ['Temuco', -38.7359, -72.5904, false],
    ['Concepción', -36.8201, -73.0444, false],
    ['Colina (fuera de la provincia)', -33.2, -70.67, false],
  ];
  casos.forEach(([nombre, lat, lon, esperado]) => assert.equal(inRestrictionZone(lat, lon), esperado, nombre));
  assert.equal(inRestrictionZone(NaN, -70), false);
  assert.equal(inRestrictionZone('x', -70), false);
});

test('el aviso al entrar a Santiago solo sale cuando corresponde', () => {
  const { restrictionNotice } = require('../public/shared/order.js');
  const sello = { restrOn: true, restrType: 'sello', restrDigit: '8' }; // lunes
  const ctx = (weekday, month, h, m) => ({ weekday, month, minutes: h * 60 + (m || 0) });
  assert.match(restrictionNotice(sello, ctx('lun', 6, 9)).text, /Hoy tienes restricción.*terminada en 8/);
  assert.equal(restrictionNotice(sello, ctx('mar', 6, 9)), null, 'otro día: no avisa');
  assert.equal(restrictionNotice(sello, ctx('lun', 10, 9)), null, 'fuera de temporada');
  assert.equal(restrictionNotice(sello, ctx('lun', 6, 7, 29)), null, 'antes del horario');
  assert.equal(restrictionNotice(sello, ctx('lun', 6, 21)), null, 'después del horario');
  assert.equal(restrictionNotice(sello, ctx('sab', 6, 9)), null, 'fin de semana');
  assert.equal(restrictionNotice({ restrOn: false, restrType: 'sello', restrDigit: '8' }, ctx('lun', 6, 9)), null, 'apagado');
  assert.equal(restrictionNotice({ restrOn: true, restrType: 'nuevo', restrDigit: '' }, ctx('lun', 6, 9)), null, 'auto nuevo');
  const sin = { restrOn: true, restrType: 'sin', restrDigit: '8' }; // lunes y jueves
  const a = restrictionNotice(sin, ctx('lun', 6, 9));
  assert.match(a.text, /Hoy tienes restricción/);
  assert.match(a.text, /anillo Américo Vespucio/);
  const b = restrictionNotice(sin, ctx('mie', 6, 9));
  assert.doesNotMatch(b.text, /Hoy tienes restricción/, 'miércoles no le toca el dígito, pero sí el anillo');
  assert.match(b.text, /anillo Américo Vespucio/);
});

test('el saludo y la frase se recortan y el saludo vacío vuelve a "Hola"', () => {
  const o = base();
  o.car.greeting = '   Hola de nuevo, querido amigo mío   ';
  o.car.phrase = '<b>Dios</b> te bendiga' + 'x'.repeat(40);
  const r = validateOrder(o);
  assert.equal(r.ok, true, r.errors.join('|'));
  assert.ok(r.clean.car.greeting.length <= 16 && r.clean.car.greeting.startsWith('Hola de nuevo'));
  assert.ok(r.clean.car.phrase.length <= 28);
  o.car.greeting = '   ';
  assert.equal(validateOrder(o).clean.car.greeting, 'Hola');
});
