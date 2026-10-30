'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const { validateOrder, quote, requiredPlan, normalizeMobile } = require('../public/shared/order.js');
const { generateCode, normalizeCode, hashDeviceId, FORMAT } = require('../lib/license.js');

const base = () => ({
  radio: { type: 'android', brand: 'nwd' },
  car: { type: 'suv', color: '#1f5fa8', name: 'Daniel', greeting: 'Hola' },
  style: { theme: 'ruta', layout: 'tarjetas', scene: 'tema', season: 'ninguna', apps: ['maps', 'spotify'] },
  features: { oilOn: true, odometer: 60000, lastOil: 58000, oilEvery: 5000 },
  plan: 'esencial',
  addons: {},
  contact: { kind: 'whatsapp', value: '+56 9 1234 5678' },
  terms: true,
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

test('un tema "plus" exige plan Completo', () => {
  const o = base();
  o.style.theme = 'cordillera';
  assert.equal(requiredPlan(o.style), 'completo');
  assert.equal(validateOrder(o).ok, false);
  o.plan = 'completo';
  assert.equal(validateOrder(o).ok, true);
});

test('las temporadas también exigen plan Completo', () => {
  const o = base();
  o.style.season = 'navidad';
  assert.equal(validateOrder(o).ok, false);
});

test('el total se calcula con el catálogo, ignorando precios del cliente', () => {
  const o = base();
  o.plan = 'completo';
  o.total = 1; // el navegador no manda en el precio
  o.price = 1;
  const q = quote(validateOrder(o).clean);
  assert.equal(q.total, 14990);
});

test('un servicio no disponible (addon "foto") ni se acepta ni se cobra', () => {
  const o = base();
  o.addons = { foto: true };
  const r = validateOrder(o);
  assert.deepEqual(r.clean.addons, {});
  assert.equal(quote(o).total, 9990); // tampoco lo cobra quote() si le llega directo
});

test('"No sé" debe confirmarse antes de pagar', () => {
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

test('exige términos y un contacto válido', () => {
  const o = base();
  o.terms = false;
  o.contact.value = '12345';
  const r = validateOrder(o);
  assert.equal(r.ok, false);
  assert.equal(r.errors.length, 2);
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
