'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { computeVersion, latestReleased, versionJson, BASE } = require('../scripts/version.js');

test('el versionCode es 1000 más los commits y crece con cada commit', () => {
  const a = computeVersion({ count: 140, sha: 'abcdef0123456789', semver: '0.2.0' });
  const b = computeVersion({ count: 141, sha: 'fedcba9876543210', semver: '0.2.0' });
  assert.equal(a.code, 1140);
  assert.ok(b.code > a.code, 'un commit más debe dar un versionCode mayor');
  assert.equal(BASE, 1000);
});

test('supera todo lo publicado antes (el release v0.2.0 usó 200 y app-latest llegó a 138)', () => {
  const v = computeVersion({ count: 1, sha: 'a', semver: '0.0.0' });
  assert.ok(v.code > 200 && v.code > 138);
});

test('el nombre de la descarga pública lleva versión y número; el de un release es exactamente la etiqueta', () => {
  assert.equal(computeVersion({ count: 141, sha: 'abc', semver: '0.2.0' }).name, '0.2.0+141');
  assert.equal(computeVersion({ count: 141, sha: 'abc', semver: '0.2.0', release: '0.3.0' }).name, '0.3.0');
});

test('el commit se acorta a 12 caracteres y una versión de release mal escrita se rechaza', () => {
  assert.equal(computeVersion({ count: 2, sha: 'a'.repeat(40) }).commit, 'a'.repeat(12));
  assert.throws(() => computeVersion({ count: 2, sha: 'a', release: 'v1.2' }), /Versión inválida/);
  assert.throws(() => computeVersion({ count: 0, sha: 'a' }), /no es válido/);
  assert.throws(() => computeVersion({ count: 1.5, sha: 'a' }), /no es válido/);
});

test('lee la última versión publicada del CHANGELOG e ignora «Sin publicar»', () => {
  const cl = '# Cambios\n\n## [Sin publicar]\n\n### Corregido\n- algo\n\n## [0.2.0] - 2026-11-10\n\n## [0.1.0] - 2026-10-30\n';
  assert.equal(latestReleased(cl), '0.2.0');
  assert.equal(latestReleased('# vacío'), '0.0.0');
});

test('version.json lleva la huella SHA-256 y el tamaño de la APK, y conserva los campos que ya lee la app', () => {
  const apk = path.join(os.tmpdir(), 'tablero-prueba-' + process.pid + '.apk');
  fs.writeFileSync(apk, Buffer.from('contenido de prueba de una apk'));
  try {
    const j = versionJson({ code: 1141, name: '0.2.0+141', commit: 'abc123' }, apk);
    assert.equal(j.versionCode, 1141);
    assert.equal(j.versionName, '0.2.0+141');
    assert.equal(j.commit, 'abc123');
    assert.equal(j.size, 30);
    assert.match(j.sha256, /^[0-9a-f]{64}$/);
    assert.equal(j.sha256, require('node:crypto').createHash('sha256').update(fs.readFileSync(apk)).digest('hex'));
  } finally {
    fs.unlinkSync(apk);
  }
});

test('el CLI funciona en este repositorio (code, name y commit coherentes)', () => {
  const { execFileSync } = require('node:child_process');
  const run = (...a) => execFileSync('node', [path.join(__dirname, '../scripts/version.js'), ...a], { encoding: 'utf8' }).trim();
  let shallow = false;
  try { shallow = execFileSync('git', ['rev-parse', '--is-shallow-repository'], { encoding: 'utf8', cwd: path.join(__dirname, '..') }).trim() === 'true'; } catch (_) { return; }
  if (shallow) return; // sin la historia completa no se puede contar; el CLI lo rechaza (probado en CI con fetch-depth: 0)
  const code = Number(run('code'));
  assert.ok(code > 1000);
  assert.match(run('name'), /^\d+\.\d+\.\d+\+\d+$/);
  assert.match(run('commit'), /^[0-9a-f]{12}$/);
  assert.equal(run('name', '--release', '1.2.3'), '1.2.3');
});
