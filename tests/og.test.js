'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

// La tarjeta que se ve al compartir el enlace (Facebook, WhatsApp…): título, descripción e imagen deben existir y ser absolutas.
const html = fs.readFileSync(path.join(__dirname, '../public/index.html'), 'utf8');
const meta = (prop) => (html.match(new RegExp('<meta (?:property|name)="' + prop + '" content="([^"]*)"')) || [])[1];

test('la portada trae las etiquetas Open Graph para compartir el enlace', () => {
  for (const p of ['og:type', 'og:url', 'og:title', 'og:description', 'og:image', 'twitter:card']) assert.ok(meta(p), 'falta ' + p);
  assert.match(meta('og:url'), /^https:\/\//);
  assert.match(meta('og:image'), /^https:\/\//, 'la imagen debe ser una dirección absoluta');
  assert.ok(meta('og:description').length <= 200, 'descripción demasiado larga para la tarjeta');
});

test('la imagen de la tarjeta existe y pesa poco', () => {
  const rel = meta('og:image').replace(/^https:\/\/[^/]+\//, '');
  const file = path.join(__dirname, '../public', rel);
  assert.ok(fs.existsSync(file), 'no existe ' + rel);
  assert.ok(fs.statSync(file).size < 300 * 1024, 'la imagen pesa más de 300 KB');
  // JPEG de 1200 × 630: lee el tamaño del marcador SOF.
  const b = fs.readFileSync(file);
  let i = 2; let dims = null;
  while (i < b.length) {
    if (b[i] !== 0xff) { i++; continue; }
    const m = b[i + 1];
    if (m >= 0xc0 && m <= 0xcf && m !== 0xc4 && m !== 0xc8 && m !== 0xcc) { dims = [b.readUInt16BE(i + 7), b.readUInt16BE(i + 5)]; break; }
    i += 2 + b.readUInt16BE(i + 2);
  }
  assert.deepEqual(dims, [1200, 630]);
});
