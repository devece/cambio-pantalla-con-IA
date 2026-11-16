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


/* ---------- movimiento: bienvenida, auto vivo y carretera (public/js/motion.js) ---------- */
/** Abre la pantalla como la app, con un nivel de movimiento, y deja la página lista para probarla. */
async function motionPage(level, opts) {
  const o = opts || {};
  const page = await browser.newPage({ viewport: { width: 1024, height: 600 } });
  if (o.reduce) await page.emulateMedia({ reducedMotion: 'reduce' });
  const errors = [];
  page.on('pageerror', (e) => errors.push(e.message));
  await page.addInitScript(() => { window.Android = { apps: () => '[]', mapInfo: () => '{"tiles":0,"bytes":0}', openApp() {}, launchPkg() {}, open() {}, media() {}, radio() {}, done() {}, saveMapArea() {}, clearMaps() {} }; });
  await page.goto(base + '/launcher.html');
  await page.waitForFunction(() => window.TBApp && window.TB && window.TB.motion);
  await page.evaluate(([lv, welcome]) => {
    // Un auto con imagen (SUV) para que el reflejo tenga dónde dibujarse.
    window.TBApp.init(JSON.stringify({ profile: { name: 'Ana', type: 'suv', color: '#1f5fd0', features: { motion: lv } } }), { welcome });
  }, [level, o.welcome !== false]);
  await page.waitForTimeout(250);
  page.motionErrors = errors;
  return page;
}

await check('movimiento «vivo»: la bienvenida corre una vez y un redibujado no la reinicia', async () => {
  const page = await motionPage('vivo');
  assert.equal(await page.evaluate(() => document.querySelector('.mot-fx').className), 'mot-fx play');
  await page.evaluate(() => { document.querySelector('.mot-fx').dataset.mark = 'mismo'; window.TBApp.setProgress(JSON.stringify({ odometer: 1234 })); });
  await page.waitForTimeout(150);
  assert.equal(await page.evaluate(() => document.querySelector('.mot-fx').dataset.mark), 'mismo', 'el redibujado reemplazó la capa de la bienvenida');
  assert.equal(await page.evaluate(() => document.querySelector('.mot-fx').className), 'mot-fx play');
  await page.waitForTimeout(3800);
  assert.equal(await page.evaluate(() => document.querySelector('.mot-fx').className), 'mot-fx', 'la bienvenida debe terminar sola');
  assert.deepEqual(page.motionErrors, []);
  await page.close();
});

await check('movimiento «vivo»: el auto vivo marca la pantalla y no hay carretera', async () => {
  const page = await motionPage('vivo', { welcome: false });
  const r = await page.evaluate(() => ({ m: document.querySelector('.scr').dataset.motion, v: document.querySelector('.scr').dataset.vivo, road: document.querySelector('.mot-road').hidden, fx: document.querySelector('.mot-fx').className }));
  assert.deepEqual(r, { m: 'vivo', v: '1', road: true, fx: 'mot-fx' });
  await page.evaluate(() => window.TBApp.setSpeed(70));
  await page.waitForTimeout(300);
  assert.equal(await page.evaluate(() => document.querySelector('.mot-road').hidden), true, 'en «vivo» la carretera no debe aparecer aunque el auto vaya en marcha');
  await page.close();
});

await check('con la bienvenida desactivada por la app, no se muestra (volver de Ajustes no la repite)', async () => {
  const page = await motionPage('vivo', { welcome: false });
  assert.equal(await page.evaluate(() => document.querySelector('.mot-fx').className), 'mot-fx');
  await page.close();
});

await check('movimiento «sin movimiento»: nada se anima y no hay reflejos', async () => {
  const page = await motionPage('off');
  const r = await page.evaluate(() => ({ m: document.querySelector('.scr').dataset.motion, v: document.querySelector('.scr').dataset.vivo, sheens: document.querySelectorAll('.m-sheen').length, fx: document.querySelector('.mot-fx').className }));
  assert.deepEqual(r, { m: 'off', v: '0', sheens: 0, fx: 'mot-fx' });
  await page.close();
});

await check('con «reducir movimiento» del sistema, todo queda quieto aunque el cliente pidiera «completo»', async () => {
  const page = await motionPage('completo', { reduce: true });
  await page.evaluate(() => window.TBApp.setSpeed(80));
  await page.waitForTimeout(400);
  const r = await page.evaluate(() => ({ m: document.querySelector('.scr').dataset.motion, road: document.querySelector('.mot-road').hidden, fx: document.querySelector('.mot-fx').className }));
  assert.deepEqual(r, { m: 'off', road: true, fx: 'mot-fx' });
  await page.close();
});

await check('movimiento «completo»: la carretera entra al ir en marcha, se descarta con un toque y se vuelve a armar al detenerse', async () => {
  const page = await motionPage('completo', { welcome: false });
  assert.equal(await page.evaluate(() => document.querySelector('.mot-road').hidden), true, 'parado no hay carretera');
  await page.evaluate(() => window.TBApp.setSpeed(60));
  await page.waitForTimeout(900);
  assert.equal(await page.evaluate(() => document.querySelector('.mot-road').hidden), false);
  assert.equal(await page.evaluate(() => document.querySelector('.scr').dataset.drive), '1');
  const f1 = await page.evaluate(() => TB.motion.stats.frames);
  await page.waitForTimeout(500);
  assert.ok((await page.evaluate(() => TB.motion.stats.frames)) > f1, 'la carretera debe animarse');
  // El dock (barra de abajo) sigue a la vista y tocable: la carretera termina donde empieza.
  const geo = await page.evaluate(() => { const r = document.querySelector('.mot-road').getBoundingClientRect(); const d = document.querySelector('.dock').getBoundingClientRect(); return [Math.round(r.bottom), Math.round(d.top)]; });
  assert.ok(geo[0] <= geo[1] + 1, `la carretera tapa la barra de abajo: ${geo}`);
  // La carretera no es un lienzo en blanco: tiene píxeles de la ruta.
  const painted = await page.evaluate(() => { const c = document.querySelector('.mot-cv'); const d = c.getContext('2d').getImageData(0, 0, c.width, c.height).data; let n = 0; for (let i = 3; i < d.length; i += 4000) if (d[i] > 0) n++; return n; });
  assert.ok(painted > 50, 'el lienzo de la carretera está vacío');
  await page.click('.mot-road');
  assert.equal(await page.evaluate(() => document.querySelector('.mot-road').hidden), true, 'un toque debe volver al inicio');
  await page.evaluate(() => window.TBApp.setSpeed(65));
  await page.waitForTimeout(300);
  assert.equal(await page.evaluate(() => document.querySelector('.mot-road').hidden), true, 'descartada: no vuelve mientras sigue en marcha');
  await page.evaluate(() => window.TBApp.setSpeed(0));
  await page.waitForTimeout(200);
  await page.evaluate(() => window.TBApp.setSpeed(60));
  await page.waitForTimeout(400);
  assert.equal(await page.evaluate(() => document.querySelector('.mot-road').hidden), false, 'al detenerse se vuelve a armar');
  assert.deepEqual(page.motionErrors, []);
  await page.close();
});

await check('la carretera sobrevive a un redibujado (no se reinicia ni parpadea)', async () => {
  const page = await motionPage('completo', { welcome: false });
  await page.evaluate(() => window.TBApp.setSpeed(60));
  await page.waitForTimeout(600);
  await page.evaluate(() => { document.querySelector('.mot-road').dataset.mark = 'mismo'; window.TBApp.setProgress(JSON.stringify({ odometer: 99 })); });
  await page.waitForTimeout(200);
  assert.equal(await page.evaluate(() => document.querySelector('.mot-road').dataset.mark), 'mismo');
  assert.equal(await page.evaluate(() => document.querySelector('.mot-road').hidden), false);
  await page.close();
});

await check('si la radio va muy lenta, la carretera se apaga sola y queda en «vivo»', async () => {
  const page = await motionPage('completo', { welcome: false });
  const cdp = await page.context().newCDPSession(page);
  await cdp.send('Emulation.setCPUThrottlingRate', { rate: 60 });
  await page.evaluate(() => window.TBApp.setSpeed(60));
  await page.waitForFunction(() => TB.motion.stats.degraded === true, null, { timeout: 40000 });
  const r = await page.evaluate(() => ({ road: document.querySelector('.mot-road').hidden, m: document.querySelector('.scr').dataset.motion }));
  assert.deepEqual(r, { road: true, m: 'vivo' });
  await page.evaluate(() => window.TBApp.setSpeed(70));
  await page.waitForTimeout(500);
  assert.equal(await page.evaluate(() => document.querySelector('.mot-road').hidden), true, 'ya degradada, no debe volver en esta sesión');
  await page.close();
});

await check('un nivel de movimiento inválido cae en «vivo»', async () => {
  const page = await motionPage('siempre', { welcome: false });
  assert.equal(await page.evaluate(() => document.querySelector('.scr').dataset.motion), 'vivo');
  await page.close();
});


/* ---------- país «Otro»: no se aplica ninguna restricción ---------- */
/** Entra a Bogotá con el GPS, con el país y la patente indicados, un lunes a media mañana. Devuelve si apareció el aviso de pico y placa. */
async function picoNoticeAfterEntering(country) {
  const page = await browser.newPage({ viewport: { width: 1024, height: 600 } });
  await page.clock.install({ time: new Date('2026-11-16T10:00:00') }); // lunes
  await page.addInitScript(() => { window.Android = { apps: () => '[]', mapInfo: () => '{"tiles":0,"bytes":0}', openApp() {}, launchPkg() {}, open() {}, media() {}, radio() {}, done() {}, saveMapArea() {}, clearMaps() {} }; });
  await page.goto(base + '/launcher.html');
  await page.waitForFunction(() => window.TBApp && window.TBPico);
  // Una placa a la que le toque pico y placa en Bogotá ese día.
  const digit = await page.evaluate(() => { const c = TBPico.cities.find((x) => x.id === 'bogota'); return ['0','1','2','3','4','5','6','7','8','9'].find((d) => TBPico.notice(c, d, new Date())) || null; });
  assert.ok(digit, 'no hay placa con pico y placa ese lunes: la prueba no puede comparar');
  await page.evaluate(([c, d]) => {
    window.TBApp.init(JSON.stringify({ profile: { name: 'Ana', features: { country: c, restrOn: true, restrDigit: d } } }), { welcome: false });
    window.TBApp.setLocation(4.65, -74.08, 0);
  }, [country, digit]);
  await page.waitForTimeout(300);
  const shown = await page.evaluate(() => { const z = document.querySelector('.zone-alert'); return !!z && !z.hidden; });
  await page.close();
  return shown;
}
await check('Colombia avisa el pico y placa al entrar a Bogotá', async () => {
  assert.equal(await picoNoticeAfterEntering('CO'), true);
});
await check('«Otro» país no avisa nada, aunque la restricción venga encendida y esté dentro de una ciudad con pico y placa', async () => {
  assert.equal(await picoNoticeAfterEntering('OTRO'), false);
});


await check('en el sitio, elegir «Otro» oculta la restricción y el pico y placa, y elegir un país la vuelve a mostrar', async () => {
  const page = await browser.newPage({ viewport: { width: 1280, height: 900 } });
  const errors = [];
  page.on('pageerror', (e) => errors.push(e.message));
  await page.goto(base + '/index.html');
  await page.waitForSelector('[data-choices="features.country"] >> text=Otro');
  const state = () => page.evaluate(() => ({
    restr: document.querySelector('fieldset[data-show-not="features.country=OTRO"]').hidden,
    note: document.querySelector('p[data-show="features.country=OTRO"]').hidden,
  }));
  assert.deepEqual(await state(), { restr: false, note: true }, 'con Chile, la restricción se ofrece');
  await page.click('[data-choices="features.country"] >> text=Otro');
  assert.deepEqual(await state(), { restr: true, note: false }, 'con «Otro», la restricción desaparece y se explica por qué');
  await page.click('[data-choices="features.country"] >> text=Colombia');
  assert.deepEqual(await state(), { restr: false, note: true });
  assert.deepEqual(errors, []);
  await page.close();
});

await browser.close();
server.close();
if (failures) { console.log(`\n${failures} prueba(s) fallaron`); process.exit(1); }
console.log('\nTodas las pruebas de pantalla pasaron');
})().catch((e) => { console.error(e); process.exit(1); });
