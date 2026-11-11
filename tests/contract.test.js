'use strict';
// Contrato entre el configurador y la pantalla de la radio: ninguna opción del cliente puede perderse en el camino.
// El mismo design-contract.json lo lee la prueba de Kotlin (core/.../ContractTest.kt), que cubre el lado de la app.
const test = require('node:test');
const assert = require('node:assert/strict');
const path = require('node:path');
const fs = require('node:fs');

const contract = JSON.parse(fs.readFileSync(path.join(__dirname, '../public/shared/design-contract.json'), 'utf8'));
const Order = require('../public/shared/order.js');
const Profile = require('../public/shared/profile.js');
const C = require('../public/shared/catalog.js');

const KEYS = Object.keys(contract.features);
const clone = (v) => JSON.parse(JSON.stringify(v));

// El estado de fábrica de la pantalla de la radio (el mismo que arma launcher.js).
const freshState = () => ({
  car: { type: 'auto', color: '#b3261e', name: '', greeting: 'Hola', phrase: '', plate: '', photo: null, photoCut: false, photoNight: null },
  style: { theme: 'cristal', layout: 'estelar', scene: 'tema', sceneTime: 'auto', bg: null, bgMode: 'photo', season: 'ninguna', accent: null, apps: ['maps', 'spotify', 'waze'] },
  features: {
    oilOn: true, odometer: 0, lastOil: 0, oilEvery: 5000, others: [], maintDone: {}, docsOn: false, rtMonth: 1, fuelOn: false, tankL: 45, kmPerL: 12,
    speedShow: true, speedAlert: 'auto', manualLimit: 60, country: 'CL', restrOn: false, restrType: 'sello', restrDigit: '', restrDays: [], voice: false, miles: false, gallons: false, clock12: false,
  },
});

const cleanOf = () => Order.validateOrder({ radio: { type: 'android' }, car: {}, style: {}, features: {}, contact: { kind: 'email', value: 'a@b.cl' } }).clean;

test('el contrato declara exactamente las claves que valida el configurador', () => {
  const clean = cleanOf();
  assert.deepEqual([...KEYS].sort(), Object.keys(clean.features).sort(), 'features: el contrato y order.js deben coincidir');
  assert.deepEqual([...contract.car].sort(), Object.keys(clean.car).sort(), 'car');
  assert.deepEqual([...contract.style].sort(), Object.keys(clean.style).sort(), 'style');
});

test('cada opción del contrato tiene un valor de muestra válido y distinto del de fábrica', () => {
  const orig = freshState().features;
  for (const k of KEYS) {
    const spec = contract.features[k];
    assert.notDeepEqual(spec.sample, spec.default, `${k}: la muestra debe distinguirse del valor de fábrica`);
    assert.ok(Array.isArray(spec.bad) && spec.bad.length > 0, `${k}: faltan valores inválidos de prueba`);
    // El valor de fábrica del contrato es el que usa la radio cuando el cliente no eligió nada (salvo `voice`, que en el sitio nace activado).
    if (k !== 'voice') assert.deepEqual(orig[k], spec.default, `${k}: el valor de fábrica de la radio no coincide con el del contrato`);
  }
});

test('país, patente, restricción, combustible, mantenimientos, intervalos, documentos y unidades llegan a la radio', () => {
  for (const k of KEYS) {
    const state = freshState();
    Profile.apply(state, { features: { [k]: clone(contract.features[k].sample) } });
    assert.deepEqual(state.features[k], contract.features[k].sample, `la opción «${k}» no llegó a la pantalla de la radio`);
  }
});

test('todas las opciones juntas llegan, sin pisarse entre sí', () => {
  const state = freshState();
  const all = Object.fromEntries(KEYS.map((k) => [k, clone(contract.features[k].sample)]));
  Profile.apply(state, { features: all });
  for (const k of KEYS) assert.deepEqual(state.features[k], contract.features[k].sample, k);
});

test('las opciones desactivadas se respetan (no vuelven al valor de fábrica)', () => {
  const state = freshState();
  Profile.apply(state, { features: { oilOn: false, speedShow: false } }); // ambas nacen activadas
  assert.equal(state.features.oilOn, false);
  assert.equal(state.features.speedShow, false);
  const on = freshState();
  Profile.apply(on, { features: { docsOn: true, fuelOn: true, restrOn: true } });
  Profile.apply(on, { features: { docsOn: false, fuelOn: false, restrOn: false } });
  assert.deepEqual([on.features.docsOn, on.features.fuelOn, on.features.restrOn], [false, false, false]);
});

test('los valores inválidos se ignoran y queda el valor anterior', () => {
  for (const k of KEYS) {
    for (const bad of contract.features[k].bad) {
      const state = freshState();
      const before = clone(state.features[k]);
      Profile.apply(state, { features: { [k]: clone(bad) } });
      assert.deepEqual(state.features[k], before, `«${k}» aceptó un valor inválido: ${JSON.stringify(bad)}`);
    }
  }
});

test('un perfil sin opciones no toca las de fábrica, y uno basura no rompe nada', () => {
  const state = freshState();
  const before = clone(state.features);
  Profile.apply(state, { name: 'Ana' });
  assert.deepEqual(state.features, before);
  for (const junk of [null, undefined, 5, 'x', [], { features: 'x' }, { features: [1] }, { features: null }]) {
    const s = freshState();
    assert.doesNotThrow(() => Profile.apply(s, junk));
    assert.deepEqual(s.features, before);
  }
});

test('la patente, el nombre y la apariencia siguen llegando como antes', () => {
  const state = freshState();
  Profile.apply(state, { name: 'Ana', greeting: 'Wena', phrase: 'Buen viaje', type: 'suv', color: '#1F5FA8', plate: 'abcd12', style: { theme: C.themes[1], layout: C.layouts[1], accent: '#4cc9f0' } });
  assert.deepEqual([state.car.name, state.car.greeting, state.car.phrase, state.car.type, state.car.color, state.car.plate], ['Ana', 'Wena', 'Buen viaje', 'suv', '#1F5FA8', 'ABCD12']);
  assert.equal(state.style.theme, C.themes[1]);
  assert.equal(state.style.layout, C.layouts[1]);
  assert.equal(state.style.accent, '#4cc9f0');
});

test('lo que viaja con la foto no se pierde: el perfil conserva clock12 suelto de las versiones anteriores', () => {
  const state = freshState();
  Profile.apply(state, { clock12: true });
  assert.equal(state.features.clock12, true);
});

test('cada archivo de la pantalla que usa el motor de perfil viaja a la radio', () => {
  const gradle = fs.readFileSync(path.join(__dirname, '../android/app/build.gradle.kts'), 'utf8');
  assert.ok(gradle.includes('"shared/profile.js"'), 'copyWeb debe incluir shared/profile.js');
  const html = fs.readFileSync(path.join(__dirname, '../public/launcher.html'), 'utf8');
  assert.ok(html.includes('shared/profile.js'), 'launcher.html debe cargar shared/profile.js');
  assert.ok(html.indexOf('shared/catalog.js') < html.indexOf('shared/profile.js'), 'profile.js necesita catalog.js ya cargado');
  assert.ok(html.indexOf('shared/profile.js') < html.indexOf('js/launcher.js'), 'launcher.js necesita profile.js ya cargado');
});
