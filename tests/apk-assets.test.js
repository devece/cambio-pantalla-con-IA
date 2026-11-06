'use strict';
// La pantalla de la radio (launcher.html) corre dentro de la app con los archivos que copia `copyWeb` en android/app/build.gradle.kts.
// Si launcher.html carga un archivo que esa lista no trae, en la radio falla en silencio (en el navegador sí se ve bien).
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.join(__dirname, '..');
const html = fs.readFileSync(path.join(root, 'public/launcher.html'), 'utf8');
const gradle = fs.readFileSync(path.join(root, 'android/app/build.gradle.kts'), 'utf8');

test('todo script y hoja de estilos de launcher.html viaja dentro del APK', () => {
  const block = gradle.slice(gradle.indexOf('tasks.register<Copy>("copyWeb")'));
  const included = new Set([...block.slice(0, block.indexOf('into(')).matchAll(/"([^"]+\.[a-z]+)"/g)].map((m) => m[1]));
  const needed = [
    ...[...html.matchAll(/<script[^>]+src="([^"]+)"/g)].map((m) => m[1]),
    ...[...html.matchAll(/<link[^>]+href="([^"]+\.css)"/g)].map((m) => m[1]),
  ].filter((f) => !/^https?:/.test(f));
  assert.ok(needed.length >= 6, 'launcher.html debería cargar varios archivos');
  const missing = needed.filter((f) => !included.has(f));
  assert.deepEqual(missing, [], `Faltan en copyWeb (no llegan a la radio): ${missing.join(', ')}`);
});

test('el workflow Release revisa los mismos archivos dentro del APK', () => {
  const wf = fs.readFileSync(path.join(root, '.github/workflows/release.yml'), 'utf8');
  const scripts = [...html.matchAll(/<script[^>]+src="([^"]+)"/g)].map((m) => m[1]);
  for (const f of scripts) assert.ok(wf.includes(f), `release.yml no revisa ${f}`);
});
