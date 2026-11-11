'use strict';
/*
 * Numeración única de la app (versionCode, versionName y commit): la usan la descarga pública (`app-latest`) y los releases.
 *
 *   versionCode = 1000 + número de commits de la historia (git rev-list --count HEAD)
 *
 * Crece con cada commit de main sin depender de ningún contador de la CI (que es distinto en cada workflow), no puede repetirse
 * y se puede reconstruir desde cualquier commit. Los 1000 de base la dejan por encima de todo lo publicado antes (200 y 138).
 * Hace falta la historia completa (actions/checkout con fetch-depth: 0).
 *
 *   node scripts/version.js code                 -> 1141
 *   node scripts/version.js name [--release X.Y.Z] -> 0.2.0+141  (con --release, solo X.Y.Z: el nombre exacto de la etiqueta)
 *   node scripts/version.js commit               -> sha corto (12)
 *   node scripts/version.js github-output [--release X.Y.Z]   -> líneas code=, name= y commit= para $GITHUB_OUTPUT
 *   node scripts/version.js json <apk> [--release X.Y.Z]      -> el contenido de version.json (con la huella SHA-256 de la APK)
 */
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const { execFileSync } = require('node:child_process');

const BASE = 1000;
const SEMVER = /^\d+\.\d+\.\d+$/;

/** La última versión publicada en CHANGELOG.md («## [0.2.0] - fecha»), o '0.0.0' si no hay. */
function latestReleased(changelog) {
  const m = /^## \[(\d+\.\d+\.\d+)\]/m.exec(String(changelog));
  return m ? m[1] : '0.0.0';
}

/** { code, name, commit } a partir del conteo de commits. `release` (X.Y.Z) fija el nombre exacto, para las etiquetas. */
function computeVersion({ count, sha, semver, release }) {
  if (!Number.isInteger(count) || count < 1) throw new Error('El número de commits no es válido: ' + count);
  if (release !== undefined && !SEMVER.test(release)) throw new Error(`Versión inválida: "${release}". Usa el formato X.Y.Z.`);
  const base = release !== undefined ? release : `${semver || '0.0.0'}+${count}`;
  return { code: BASE + count, name: base, commit: String(sha || 'desconocido').slice(0, 12) };
}

const git = (...args) => execFileSync('git', args, { encoding: 'utf8', cwd: path.join(__dirname, '..') }).trim();

function fromGit(release) {
  if (git('rev-parse', '--is-shallow-repository') === 'true') {
    throw new Error('El repositorio está recortado: usa actions/checkout con fetch-depth: 0 para contar los commits.');
  }
  const changelog = fs.readFileSync(path.join(__dirname, '..', 'CHANGELOG.md'), 'utf8');
  return computeVersion({ count: Number(git('rev-list', '--count', 'HEAD')), sha: git('rev-parse', 'HEAD'), semver: latestReleased(changelog), release });
}

/** El version.json que lee la app para avisar de una actualización, con la huella de la APK que acompaña. */
function versionJson(v, apkPath) {
  const bytes = fs.readFileSync(apkPath);
  return {
    versionCode: v.code,
    versionName: v.name,
    commit: v.commit,
    sha256: crypto.createHash('sha256').update(bytes).digest('hex'),
    size: bytes.length,
  };
}

module.exports = { BASE, computeVersion, latestReleased, versionJson };

if (require.main === module) {
  try {
    const args = process.argv.slice(2);
    const cmd = args[0];
    const at = args.indexOf('--release');
    const release = at >= 0 ? args[at + 1] : undefined;
    const v = fromGit(release);
    if (cmd === 'code') console.log(v.code);
    else if (cmd === 'name') console.log(v.name);
    else if (cmd === 'commit') console.log(v.commit);
    else if (cmd === 'github-output') console.log(`code=${v.code}\nname=${v.name}\ncommit=${v.commit}`);
    else if (cmd === 'json') console.log(JSON.stringify(versionJson(v, args[1])));
    else throw new Error('Uso: node scripts/version.js code|name|commit|github-output|json <apk> [--release X.Y.Z]');
  } catch (e) {
    console.error(e.message);
    process.exit(1);
  }
}
