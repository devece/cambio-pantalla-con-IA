'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { tiles, MAX } = require('../public/js/mosaic.js');

test('el mosaico reparte la pantalla de 1024×600 sin salirse ni encimar tomas, de 1 a 6', () => {
  for (let n = 1; n <= MAX; n++) {
    const t = tiles(n);
    assert.equal(t.length, n);
    let area = 0;
    t.forEach((a, i) => {
      assert.ok(a.w > 0 && a.h > 0, `toma ${i} de ${n} con tamaño`);
      assert.ok(a.x >= 0 && a.y >= 0 && a.x + a.w <= 1024 && a.y + a.h <= 600, `toma ${i} de ${n} dentro de la pantalla`);
      area += a.w * a.h;
      t.slice(i + 1).forEach((b) => {
        const overlap = a.x < b.x + b.w && b.x < a.x + a.w && a.y < b.y + b.h && b.y < a.y + a.h;
        assert.ok(!overlap, `tomas encimadas con ${n}`);
      });
    });
    assert.ok(area > 1024 * 600 * 0.95, `con ${n} tomas se cubre casi toda la pantalla`);
  }
});

test('el mosaico limita a 6 tomas y se carga en el sitio', () => {
  assert.equal(tiles(0).length, 1);
  assert.equal(tiles(99).length, 6);
  const html = fs.readFileSync(path.join(__dirname, '../public/index.html'), 'utf8');
  assert.ok(html.includes('js/mosaic.js'), 'index.html debe cargar mosaic.js');
  assert.ok(html.indexOf('js/mosaic.js') < html.indexOf('js/app.js'), 'app.js necesita mosaic.js ya cargado');
});
