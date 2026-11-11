'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const read = (f) => fs.readFileSync(path.join(__dirname, '..', '.github', 'workflows', f), 'utf8');
const lines = (s) => s.split('\n');

test('android.yml: los secretos de firma solo se entregan en main', () => {
  const yml = read('android.yml');
  const uses = lines(yml).filter((l) => /secrets\.TB_/.test(l));
  assert.ok(uses.length >= 4, 'se esperan los 4 secretos de firma');
  for (const l of uses) {
    assert.match(l, /github\.ref == 'refs\/heads\/main' && secrets\.TB_/, 'secreto sin limitar a main: ' + l.trim());
  }
});

test('android.yml: compilar solo lee; escribir y publicar es otro trabajo que no recibe secretos de firma', () => {
  const yml = read('android.yml');
  const publishAt = yml.indexOf('\n  publish:');
  assert.ok(publishAt > 0, 'debe existir el trabajo «publish»');
  const build = yml.slice(0, publishAt);
  const publish = yml.slice(publishAt);
  assert.match(build, /permissions:\s*\n\s*contents: read/, 'el trabajo de compilación debe ser de solo lectura');
  assert.doesNotMatch(build, /contents: write/, 'compilar no puede escribir en el repositorio');
  assert.match(publish, /if: github\.ref == 'refs\/heads\/main'/, 'publicar solo desde main');
  assert.match(publish, /contents: write/);
  assert.doesNotMatch(publish, /secrets\.TB_/, 'el trabajo que escribe no debe ver la clave de firma');
});

test('release.yml: solo se dispara con etiquetas de versión y exige que estén en main', () => {
  const yml = read('release.yml');
  assert.match(yml, /on:\s*\n\s*push:\s*\n\s*tags:\s*\n\s*- 'v\[0-9\]\+\.\[0-9\]\+\.\[0-9\]\+'/);
  assert.doesNotMatch(yml, /branches:/, 'no debe dispararse por ramas');
  assert.match(yml, /Revisar que la etiqueta está en main/);
});
