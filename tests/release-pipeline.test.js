'use strict';
// Protege la cadena de distribución: numeración única, firma con la clave de Tablero y variante de Play Store.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.join(__dirname, '..');
const read = (f) => fs.readFileSync(path.join(root, f), 'utf8');
const android = read('.github/workflows/android.yml');
const release = read('.github/workflows/release.yml');

test('la descarga pública y los releases usan la misma numeración (scripts/version.js), no un contador de la CI', () => {
  assert.match(android, /node scripts\/version\.js github-output/);
  assert.doesNotMatch(android, /github\.run_number/, 'el versionCode no puede depender del número de ejecución (es distinto en cada workflow)');
  assert.match(android, /-PtableroVersionCode=\$\{\{ steps\.ver\.outputs\.code \}\}/);
  assert.match(android, /-PtableroCommit=\$\{\{ steps\.ver\.outputs\.commit \}\}/);
  assert.match(release, /node scripts\/version\.js code/);
  assert.doesNotMatch(release, /changelog\.js code/, 'el release ya no calcula el código desde la versión de la etiqueta');
  assert.match(android, /fetch-depth: 0/, 'contar commits necesita la historia completa');
});

test('en main la APK pública debe salir firmada con la clave fija y no se publica una de prueba', () => {
  assert.match(android, /Exigir la clave de firma en main/);
  assert.match(android, /if: github\.ref == 'refs\/heads\/main' && steps\.sign\.outputs\.signed != 'true'/);
});

test('la CI compara la firma del APK con la huella pública de la clave de Tablero', () => {
  const fp = read('android/firma-sha256.txt').trim();
  assert.match(fp, /^[0-9a-f]{64}$/, 'android/firma-sha256.txt debe ser una huella SHA-256 en hexadecimal');
  for (const [name, yml] of [['android.yml', android], ['release.yml', release]]) {
    assert.match(yml, /Signer #1 certificate SHA-256 digest/, name + ' debe leer la huella del certificado');
    assert.match(yml, /android\/firma-sha256\.txt/, name + ' debe comparar con la huella publicada');
  }
});

test('la compilación publica version.json con huella y commit, y la huella .sha256 de la APK', () => {
  assert.match(android, /node scripts\/version\.js json tablero\.apk > version\.json/);
  assert.match(android, /sha256sum tablero\.apk > tablero\.apk\.sha256/);
  assert.match(android, /tablero\.apk\.sha256/);
});

test('existe la variante de Play Store sin actualizador ni permiso de instalar apps, y la CI comprueba que compila', () => {
  const gradle = read('android/app/build.gradle.kts');
  assert.match(gradle, /create\("play"\)/);
  assert.match(gradle, /buildConfigField\("boolean", "IN_APP_UPDATE", "false"\)/);
  assert.match(gradle, /buildConfigField\("boolean", "IN_APP_UPDATE", "true"\)/);
  assert.match(gradle, /GIT_COMMIT/);
  const manifest = read('android/app/src/play/AndroidManifest.xml');
  assert.match(manifest, /REQUEST_INSTALL_PACKAGES" tools:node="remove"/);
  assert.match(android, /:app:assemblePlay/);
  // La variante de descarga directa sigue pidiendo el permiso (lo necesita el actualizador).
  assert.match(read('android/app/src/main/AndroidManifest.xml'), /REQUEST_INSTALL_PACKAGES/);
});

test('el actualizador no instala nada sin verificar huella y firma, y no corre en la variante de Play', () => {
  const up = read('android/app/src/main/kotlin/cl/tablero/launcher/Updater.kt');
  assert.match(up, /if \(!BuildConfig\.IN_APP_UPDATE\) return/);
  assert.match(up, /AppUpdate\.checkDownload/);
  assert.match(up, /sameSigner/);
  const verify = up.indexOf('verify(activity, file, latest)');
  const install = up.indexOf('install(activity, file, latest)');
  assert.ok(verify > 0 && install > verify, 'la verificación debe ir antes de entregar el APK al instalador');
});
