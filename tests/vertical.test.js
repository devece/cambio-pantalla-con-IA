'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const C = require('../public/shared/catalog.js');

// El diseño para radios verticales vive en preview.css bajo [data-orient='v'] y lo activa preview.js.
const css = fs.readFileSync(path.join(__dirname, '../public/css/preview.css'), 'utf8');
const js = fs.readFileSync(path.join(__dirname, '../public/js/preview.js'), 'utf8');

test('cada distribución tiene su versión vertical', () => {
  // Minimalista y Todoterreno ya eran una sola columna: solo ajustan tamaños.
  const porClase = { minimal: '.bigclock', todoterreno: '.tt-car' };
  C.layouts.forEach((id) => {
    const sel = porClase[id] || '.l-' + id;
    assert.ok(css.includes("[data-orient='v'] " + sel) || css.includes("[data-orient='v'] .home" + sel), 'falta el diseño vertical de ' + id);
  });
});

test('las reglas verticales no tocan la pantalla horizontal', () => {
  const desde = css.indexOf('Radios verticales');
  assert.ok(desde > 0, 'no encuentro el bloque de radios verticales');
  const reglas = css.slice(desde).split('\n').filter((l) => /^\.scr/.test(l));
  assert.ok(reglas.length > 20);
  reglas.forEach((l) => assert.ok(l.split('{')[0].split(',').every((s) => s.includes("[data-orient='v']")), 'regla sin [data-orient]: ' + l.slice(0, 80)));
});

test('el motor marca la orientación y ofrece la vista vertical al sitio', () => {
  assert.match(js, /scr\.dataset\.orient = vert \? 'v' : 'h'/);
  assert.match(js, /setVertical/);
});
