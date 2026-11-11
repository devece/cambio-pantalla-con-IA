'use strict';
// Las reglas de restricción contrastadas con la fuente oficial. Estas pruebas copian a mano lo que dicen las fuentes:
// si alguien cambia una tabla sin una fuente nueva, fallan a propósito. Al llegar la resolución de un año nuevo hay que actualizar
// aquí la transcripción junto con public/shared/catalog.js (docs/RESTRICCION.md).
const test = require('node:test');
const assert = require('node:assert/strict');
const C = require('../public/shared/catalog.js');
const O = require('../public/shared/order.js');
const Pico = require('../public/shared/pico.js');

// Resolución Exenta N.º 1698/2026 de la SEREMI de Transportes y Telecomunicaciones RM, resuelvo 1, letras b) y e).
// https://mtt.gob.cl/wp-content/uploads/2026/04/Res-2026-Restriccion-vehicular-permanente-ano-2026_firmado_GCB.pdf (leída el 2026-11-11)
const OFICIAL_2026 = {
  sinSello: { lun: [6, 7, 8, 9], mar: [0, 1, 2, 3], mie: [4, 5, 6, 7], jue: [8, 9, 0, 1], vie: [2, 3, 4, 5] },
  conSello: { lun: [8, 9], mar: [0, 1], mie: [2, 3], jue: [4, 5], vie: [6, 7] },
};

test('Chile 2026: las tablas de dígitos son las de la resolución oficial N.º 1698/2026', () => {
  assert.deepEqual(C.restriction.digits.sin, OFICIAL_2026.sinSello, 'sin sello verde (letra b)');
  assert.deepEqual(C.restriction.digits.sello, OFICIAL_2026.conSello, 'con sello verde inscrito antes del 1-sep-2011 (letra e)');
});

test('Chile 2026: temporada, horario y año coinciden con la resolución', () => {
  const r = C.restriction;
  assert.equal(r.year, 2026);
  assert.deepEqual(r.season.months, [5, 6, 7, 8], 'del 1 de mayo al 31 de agosto');
  assert.equal(r.season.from, '1 de mayo');
  assert.equal(r.season.to, '31 de agosto');
  assert.equal(r.season.hours, '7:30 a 21:00');
  assert.match(r.season.days, /lunes a viernes, excepto festivos/);
  assert.ok(r.sources.some((s) => /Res-2026-Restriccion-vehicular-permanente-ano-2026/.test(s)), 'cita la resolución oficial');
});

test('Chile: dentro de su año el aviso afirma la tabla; en otro año no afirma nada y manda a revisar la regla vigente', () => {
  const sello = { restrOn: true, restrType: 'sello', restrDigit: '8' }; // lunes
  const ctx = (year, weekday = 'lun', month = 6) => ({ weekday, month, minutes: 9 * 60, year });
  const dentro = O.restrictionNotice(sello, ctx(2026));
  assert.equal(dentro.warn, true);
  assert.match(dentro.text, /Hoy tienes restricción/);
  const fuera = O.restrictionNotice(sello, ctx(2027));
  assert.equal(fuera.warn, false);
  assert.equal(fuera.stale, true);
  assert.match(fuera.text, /2026/);
  assert.match(fuera.text, /2027/);
  assert.doesNotMatch(fuera.text, /Hoy tienes restricción/, 'no afirma que le toque con una tabla de otro año');
  // También para un día que «no le toca» con la tabla vieja: no se afirma lo contrario.
  assert.equal(O.restrictionNotice(sello, ctx(2027, 'mar')).stale, true);
  // Las demás guardas siguen funcionando igual.
  assert.equal(O.restrictionNotice(sello, ctx(2027, 'lun', 10)), null, 'fuera de temporada no avisa nada');
  assert.equal(O.restrictionNotice(sello, ctx(2027, 'sab')), null, 'fin de semana');
  assert.equal(O.restrictionNotice({ restrOn: true, restrType: 'nuevo', restrDigit: '' }, ctx(2027)), null, 'auto nuevo: sin restricción permanente');
  assert.equal(O.restrictionNotice({ restrOn: false, restrType: 'sello', restrDigit: '8' }, ctx(2027)), null, 'apagado');
  // Sin año (la vista previa del sitio) no se comprueba: sigue como siempre.
  assert.match(O.restrictionNotice(sello, { weekday: 'lun', month: 6, minutes: 540 }).text, /Hoy tienes restricción/);
});

test('Bogotá: la regla par/impar coincide con la Secretaría Distrital de Movilidad (semana del 21 al 27 de septiembre de 2026)', () => {
  // Fuente: https://bogota.gov.co/mi-ciudad/movilidad/pico-y-placa-en-bogota-para-la-semana-del-21-al-27-de-septiembre-2026
  // «En días impares pueden circular las placas terminadas en 1, 2, 3, 4 y 5; en días pares, 6, 7, 8, 9 y 0», de 6:00 a. m. a 9:00 p. m., lunes a viernes.
  const bogota = Pico.cities.find((c) => c.id === 'bogota');
  const dia = (d) => new Date(2026, 8, d, 10, 0); // septiembre = mes 8
  const casos = [[21, [1, 2, 3, 4, 5]], [22, [6, 7, 8, 9, 0]], [23, [1, 2, 3, 4, 5]], [24, [6, 7, 8, 9, 0]]]; // lunes a jueves; pueden circular...
  for (const [d, circulan] of casos) {
    for (let digit = 0; digit <= 9; digit++) {
      assert.equal(Pico.isRestricted(bogota, digit, dia(d)), !circulan.includes(digit), `${d} de septiembre, placa ${digit}`);
    }
  }
  assert.deepEqual(bogota.hours, [[360, 1260]], '6:00 a 21:00');
  assert.equal(Pico.isRestricted(bogota, 7, new Date(2026, 8, 26, 10, 0)), false, 'sábado: no rige');
  assert.equal(bogota.confidence, 'oficial');
});
