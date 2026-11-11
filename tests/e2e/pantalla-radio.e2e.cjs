// Prueba con navegador de la pantalla de la radio: lo que la app Android le entrega al motor (el «perfil» con sus opciones)
// debe verse en pantalla. Complementa tests/contract.test.js (que prueba los datos) comprobando lo que se dibuja.
// Uso:  npm install --no-save playwright@1.63.0 && npx playwright install chromium && node tests/e2e/pantalla-radio.e2e.cjs
'use strict';
const { chromium } = require('playwright');
const http = require('node:http');
const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');

const root = path.join(__dirname, '../../public');
const contract = JSON.parse(fs.readFileSync(path.join(root, 'shared/design-contract.json'), 'utf8'));
const TYPES = { '.html': 'text/html', '.js': 'text/javascript', '.css': 'text/css', '.json': 'application/json', '.png': 'image/png', '.svg': 'image/svg+xml' };

const server = http.createServer((req, res) => {
  const file = path.join(root, decodeURIComponent(new URL(req.url, 'http://x').pathname));
  if (!file.startsWith(root) || !fs.existsSync(file) || fs.statSync(file).isDirectory()) { res.writeHead(404).end(); return; }
  res.writeHead(200, { 'Content-Type': TYPES[path.extname(file)] || 'application/octet-stream' });
  fs.createReadStream(file).pipe(res);
});
(async () => {
await new Promise((ok) => server.listen(0, '127.0.0.1', ok));
const base = `http://127.0.0.1:${server.address().port}`;

const browser = await chromium.launch();
let failures = 0;
const check = async (name, fn) => {
  try { await fn(); console.log('ok  ', name); } catch (e) { failures++; console.log('FALLA', name, '\n     ', e.message.split('\n')[0]); }
};

/** Abre la pantalla como la abre la app, le da el perfil y el avance, y devuelve el texto de una pantalla. */
async function screenText(profile, progress, screen) {
  const page = await browser.newPage({ viewport: { width: 1024, height: 600 } });
  await page.addInitScript(() => { window.Android = { apps: () => '[]', mapInfo: () => '{"tiles":0,"bytes":0}', openApp() {}, launchPkg() {}, open() {}, media() {}, radio() {}, done() {}, saveMapArea() {}, clearMaps() {} }; });
  await page.goto(base + '/launcher.html');
  await page.waitForFunction(() => window.TBApp && window.TB && window.TBProfile);
  await page.evaluate(([p, pr]) => { window.TBApp.init(JSON.stringify({ profile: p })); window.TBApp.setProgress(JSON.stringify(pr)); }, [profile, progress]);
  if (screen) await page.evaluate((s) => window.TBApp.goTo(s), screen);
  await page.waitForTimeout(400);
  const text = await page.evaluate(() => document.getElementById('host').innerText.replace(/\s+/g, ' '));
  await page.close();
  return text;
}

const samples = Object.fromEntries(Object.entries(contract.features).map(([k, v]) => [k, v.sample]));
const progress = { odometer: 45000, lastOil: 42000, maintDone: {} };

await check('con las opciones del cliente, Mantención muestra aceite, otros mantenimientos, documentos y combustible', async () => {
  // Las muestras del contrato son valores distintos de fábrica (oilOn apagado, millas…): aquí se enciende el aceite y se usan kilómetros.
  const t = await screenText({ name: 'Ana', greeting: 'Hola', type: 'auto', color: '#b3261e', features: { ...samples, oilOn: true, miles: false } }, progress, 'mantenimiento');
  assert.match(t, /Aceite faltan 5\.000 km/, 'el intervalo de aceite elegido (8.000 km) no se aplicó: ' + t.slice(0, 160));
  assert.match(t, /Líquido de frenos y refrigerante/, 'falta el mantenimiento «frenos»');
  assert.match(t, /Filtro de aire/, 'falta el mantenimiento «filtro»');
  assert.match(t, /Revisión técnica/, 'faltan los documentos');
  assert.match(t, /Combustible/, 'falta el combustible');
});

await check('sin opciones (lo que mandaba la app antes) se ve el aceite de fábrica: el contraste prueba que el perfil con opciones sí cambia la pantalla', async () => {
  const t = await screenText({ name: 'Ana', greeting: 'Hola', type: 'auto', color: '#b3261e' }, progress, 'mantenimiento');
  assert.match(t, /Aceite faltan 2\.000 km/);
  assert.doesNotMatch(t, /Revisión técnica|Combustible/);
});

await check('una opción desactivada se respeta: sin aceite activado no se muestra el aviso de aceite', async () => {
  const t = await screenText({ name: 'Ana', features: { ...samples, oilOn: false, miles: false } }, progress, 'mantenimiento');
  assert.doesNotMatch(t, /Aceite faltan/);
  assert.match(t, /Revisión técnica/, 'lo demás sigue apareciendo');
});

await check('el inicio conserva nombre y saludo del perfil', async () => {
  const t = await screenText({ name: 'Ana', greeting: 'Wena', features: samples }, progress);
  assert.match(t, /Wena, Ana/);
});

await check('un perfil con opciones basura no rompe la pantalla', async () => {
  const t = await screenText({ name: 'Ana', features: { oilEvery: 'x', docsOn: 'si', others: [1, 'no_existe'], country: 'ZZ' } }, progress, 'mantenimiento');
  assert.match(t, /Aceite faltan 2\.000 km/);
});

await browser.close();
server.close();
if (failures) { console.log(`\n${failures} prueba(s) fallaron`); process.exit(1); }
console.log('\nTodas las pruebas de pantalla pasaron');
})().catch((e) => { console.error(e); process.exit(1); });
