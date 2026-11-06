'use strict';
/*
 * Lee CHANGELOG.md para publicar una versión (workflow "Release", ver docs/RELEASE.md).
 *   node scripts/changelog.js notes 1.2.3   -> imprime las notas de esa versión (falla si no existe o está vacía)
 *   node scripts/changelog.js code 1.2.3    -> imprime el versionCode de Android (1.2.3 -> 10203)
 */
const fs = require('node:fs');
const path = require('node:path');

const SEMVER = /^(\d+)\.(\d+)\.(\d+)$/;

/** ¿Es una versión X.Y.Z (sin "v" ni sufijos)? */
function isVersion(v) {
  return typeof v === 'string' && SEMVER.test(v);
}

/** versionCode de Android: mayor*10000 + menor*100 + parche. Falla con una versión mal escrita o con menor/parche de 100 o más. */
function versionCode(version) {
  const m = SEMVER.exec(version || '');
  if (!m) throw new Error(`Versión inválida: "${version}". Usa el formato X.Y.Z, por ejemplo 1.2.3.`);
  const [major, minor, patch] = [Number(m[1]), Number(m[2]), Number(m[3])];
  if (minor > 99 || patch > 99) throw new Error(`La versión ${version} no cabe en el versionCode: menor y parche deben ser de 0 a 99.`);
  return major * 10000 + minor * 100 + patch;
}

/** Texto de la sección "## [version]" (sin el título), o null si no existe. Termina en la siguiente sección "## ". */
function extract(changelog, version) {
  const lines = String(changelog).replace(/\r\n/g, '\n').split('\n');
  const head = new RegExp(`^## \\[${version.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}\\]`);
  const start = lines.findIndex((l) => head.test(l));
  if (start === -1) return null;
  let end = lines.findIndex((l, i) => i > start && /^## /.test(l));
  if (end === -1) end = lines.length;
  // Los enlaces de referencia del final ("[1.0.0]: https://...") no son parte de las notas.
  const body = lines.slice(start + 1, end).filter((l) => !/^\[[^\]]+\]:\s/.test(l));
  return body.join('\n').trim();
}

/** Notas listas para publicar, o lanza un error que dice qué falta. */
function notesFor(changelog, version) {
  if (!isVersion(version)) throw new Error(`Versión inválida: "${version}". Usa el formato X.Y.Z, por ejemplo 1.2.3.`);
  const notes = extract(changelog, version);
  if (notes === null) {
    throw new Error(`CHANGELOG.md no tiene la sección "## [${version}] - AAAA-MM-DD". Mueve ahí lo de "Sin publicar" antes de crear la etiqueta.`);
  }
  if (!/^\s*[-*] /m.test(notes)) throw new Error(`La sección [${version}] de CHANGELOG.md no tiene cambios (líneas que empiecen con "- ").`);
  return notes;
}

module.exports = { isVersion, versionCode, extract, notesFor };

if (require.main === module) {
  const [cmd, version] = process.argv.slice(2);
  try {
    if (cmd === 'notes') {
      const file = path.join(__dirname, '..', 'CHANGELOG.md');
      process.stdout.write(notesFor(fs.readFileSync(file, 'utf8'), version) + '\n');
    } else if (cmd === 'code') {
      process.stdout.write(String(versionCode(version)) + '\n');
    } else {
      throw new Error('Uso: node scripts/changelog.js notes|code X.Y.Z');
    }
  } catch (e) {
    process.stderr.write(e.message + '\n');
    process.exit(1);
  }
}
