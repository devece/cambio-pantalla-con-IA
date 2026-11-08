'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const C = require('../public/shared/catalog.js');

// Los ids de la apariencia que se elige en la app (Look.kt) tienen que existir en el catálogo del sitio: si no, la pantalla de la radio los descarta.
const src = fs.readFileSync(path.join(__dirname, '../android/core/src/main/kotlin/cl/tablero/core/Look.kt'), 'utf8');

function mapKeys(name) {
  const m = src.match(new RegExp('val ' + name + ': Map<String, String> = linkedMapOf\\(([\\s\\S]*?)\\n        \\)'));
  assert.ok(m, 'no encuentro ' + name + ' en Look.kt');
  return [...m[1].matchAll(/"([^"]+)" to "/g)].map((x) => x[1]);
}

test('los temas, distribuciones, fondos, horas y decoraciones de la app existen en el catálogo del sitio', () => {
  assert.deepEqual(mapKeys('THEMES'), C.themes);
  mapKeys('LAYOUTS').forEach((id) => assert.ok(C.layouts.includes(id), 'distribución ' + id));
  mapKeys('SCENES').forEach((id) => assert.ok(C.scenes.includes(id), 'fondo ' + id));
  assert.deepEqual(mapKeys('SCENE_TIMES'), C.sceneTimes);
  assert.deepEqual(mapKeys('SEASONS'), C.seasons);
});

test('lo único que la app no ofrece todavía es lo que el sitio reserva a la camioneta 4x4', () => {
  const missingLayouts = C.layouts.filter((id) => !mapKeys('LAYOUTS').includes(id));
  const missingScenes = C.scenes.filter((id) => !mapKeys('SCENES').includes(id));
  assert.deepEqual(missingLayouts, ['todoterreno']);
  assert.deepEqual(missingScenes, ['nieve', 'rocas', 'barro']);
});

test('los colores de acento de la app son los del sitio', () => {
  const themesJs = fs.readFileSync(path.join(__dirname, '../public/js/themes.js'), 'utf8');
  const site = [...themesJs.match(/TB\.accents = \[([^\]]+)\]/)[1].matchAll(/'(#[0-9a-f]{6})'/gi)].map((x) => x[1]);
  const a = src.match(/val ACCENTS: List<String> = listOf\(([^)]+)\)/)[1];
  const app = [...a.matchAll(/"(#[0-9a-f]{6})"/gi)].map((x) => x[1]);
  assert.deepEqual(app, site);
});
