'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { isVersion, versionCode, extract, notesFor } = require('../scripts/changelog.js');

const SAMPLE = `# Cambios

## [Sin publicar]

### Agregado
- Algo que aún no sale.

## [1.1.0] - 2026-12-01

### Agregado
- Botones del volante.

### Arreglado
- El mapa.

## [1.0.0] - 2026-11-20

- Primera versión.

[1.1.0]: https://example.com/compare/v1.0.0...v1.1.0
[1.0.0]: https://example.com/releases/v1.0.0
`;

test('reconoce solo versiones X.Y.Z', () => {
  assert.equal(isVersion('1.2.3'), true);
  for (const bad of ['v1.2.3', '1.2', '1.2.3-rc.1', '', 'a.b.c', null, undefined]) assert.equal(isVersion(bad), false, String(bad));
});

test('versionCode crece con la versión y respeta el orden', () => {
  assert.equal(versionCode('0.1.0'), 100);
  assert.equal(versionCode('1.0.0'), 10000);
  assert.equal(versionCode('1.4.90'), 10490);
  assert.ok(versionCode('1.10.0') > versionCode('1.9.99'));
  assert.ok(versionCode('2.0.0') > versionCode('1.99.99'));
});

test('versionCode rechaza lo que no cabe o está mal escrito', () => {
  assert.throws(() => versionCode('1.100.0'), /no cabe/);
  assert.throws(() => versionCode('1.0.100'), /no cabe/);
  assert.throws(() => versionCode('v1.0.0'), /inválida/);
  assert.throws(() => versionCode(undefined), /inválida/);
});

test('extrae la sección de una versión sin tocar las vecinas ni los enlaces', () => {
  const n = extract(SAMPLE, '1.1.0');
  assert.match(n, /### Agregado\n- Botones del volante\./);
  assert.match(n, /### Arreglado\n- El mapa\./);
  assert.doesNotMatch(n, /Primera versión|Algo que aún no sale|example\.com/);
  assert.equal(extract(SAMPLE, '1.0.0'), '- Primera versión.');
  assert.equal(extract(SAMPLE, '9.9.9'), null);
});

test('el punto de la versión no se toma como comodín', () => {
  assert.equal(extract('## [1x1x0] - 2026-01-01\n- no\n', '1.1.0'), null);
});

test('notesFor falla con mensajes que dicen qué hacer', () => {
  assert.throws(() => notesFor(SAMPLE, '2.0.0'), /no tiene la sección "## \[2\.0\.0\]/);
  assert.throws(() => notesFor('## [1.0.0] - 2026-01-01\n\nsin cambios\n', '1.0.0'), /no tiene cambios/);
  assert.throws(() => notesFor(SAMPLE, 'v1.1.0'), /inválida/);
  assert.match(notesFor(SAMPLE, '1.1.0'), /Botones del volante/);
});

test('acepta saltos de línea de Windows', () => {
  assert.equal(extract(SAMPLE.replace(/\n/g, '\r\n'), '1.0.0'), '- Primera versión.');
});

test('el CHANGELOG.md real: tiene "Sin publicar" y cada versión publicada trae cambios y fecha', () => {
  const text = fs.readFileSync(path.join(__dirname, '..', 'CHANGELOG.md'), 'utf8');
  assert.match(text, /^## \[Sin publicar\]/m);
  const versions = [...text.matchAll(/^## \[(\d+\.\d+\.\d+)\] - (\S+)/gm)];
  for (const [, v, date] of versions) {
    assert.match(date, /^\d{4}-\d{2}-\d{2}$/, `fecha de ${v}`);
    assert.doesNotThrow(() => notesFor(text, v), `notas de ${v}`);
  }
  // Ninguna versión repetida.
  assert.equal(new Set(versions.map((m) => m[1])).size, versions.length);
});
