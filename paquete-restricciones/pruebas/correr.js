'use strict';
// Ejecuta pruebas/casos.json contra la implementación de referencia en JavaScript.  Uso: node pruebas/correr.js
const path = require('path');
const R = require('../referencia/restricciones.js')(require('../data/restricciones.json'));
const casos = require('./casos.json');

const parse = (s) => { const m = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})$/.exec(s); return new Date(+m[1], +m[2] - 1, +m[3], +m[4], +m[5]); };
let ok = 0, mal = 0;
const falla = (msg) => { mal++; console.log('FALLA  ' + msg); };

for (const c of casos.reglas) {
  const ciudad = R.cityById(c.ciudad);
  if (!ciudad) { falla(`${c.nombre}: no existe la ciudad ${c.ciudad}`); continue; }
  const e = R.evaluate(ciudad, { digit: c.digito, vehicleClass: c.clase }, parse(c.fecha));
  const dif = casos.campos.filter((k) => e[k] !== c.esperado[k]);
  if (dif.length) falla(`${c.nombre}: ${dif.map((k) => `${k}=${e[k]} (esperado ${c.esperado[k]})`).join(', ')}`); else ok++;
}
for (const g of casos.geo) {
  const ciudad = R.cityAt(g.lat, g.lon, g.pais);
  const id = ciudad ? ciudad.id : null;
  if (id !== g.ciudad) falla(`zona (${g.lat}, ${g.lon}, ${g.pais}): dio ${id}, esperado ${g.ciudad}`); else ok++;
}
console.log(`JavaScript: ${ok} bien, ${mal} mal (datos ${R.version})`);
process.exit(mal ? 1 : 0);
