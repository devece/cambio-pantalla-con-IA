// Captura, desde la vista previa real del sitio, lo que usa el video: las pantallas verticales (JPG) y el clip de la
// pantalla viva (bienvenida, auto vivo y carretera, MP4 de 10 s). Salida en video/proyecto/assets/.
// Uso (desde la raíz del repositorio):
//   python3 -m http.server 8765 --directory public &
//   npm install --no-save playwright@1.63.0 && npx playwright install chromium
//   node video/capturas/capturar.cjs
// Necesita ffmpeg. Con OUT=/otra/carpeta se escribe en otro lugar (para probar sin pisar los archivos del video).
'use strict';
const { chromium } = require('playwright');
const { execFileSync } = require('node:child_process');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');

const BASE = process.env.BASE || 'http://localhost:8765/index.html';
const OUT = process.env.OUT || path.join(__dirname, '../proyecto/assets');
const OCULTAR = '.scr .alert{display:none!important}'; // el aviso «Baja la velocidad» de la demo estorba en el video

const click = (page, sel) => page.evaluate((s) => { const e = document.querySelector(s); if (e) e.click(); return !!e; }, sel);

async function verticales(browser) {
  const tomas = [
    ['v-ruta-estelar-noche', 'ruta', 'estelar', true],
    ['v-cristal-consola-dia', 'cristal', 'consola', false],
    ['v-patagonia-manejo-noche', 'patagonia', 'manejo', true],
  ];
  for (const [nombre, tema, distribucion, noche] of tomas) {
    const page = await browser.newPage({ viewport: { width: 1500, height: 1100 }, deviceScaleFactor: 2 });
    await page.goto(BASE);
    await page.waitForTimeout(900);
    await page.addStyleTag({ content: OCULTAR });
    await click(page, `[data-choices="style.theme"] [data-id="${tema}"]`);
    await click(page, `[data-choices="style.layout"] [data-id="${distribucion}"]`);
    await click(page, noche ? '#mode-night' : '#mode-day');
    await click(page, '#orient-v');
    await page.waitForTimeout(900);
    const png = path.join(os.tmpdir(), nombre + '.png');
    const el = await page.$('#device .scr');
    await el.scrollIntoViewIfNeeded();
    await page.waitForTimeout(400);
    await el.screenshot({ path: png });
    execFileSync('ffmpeg', ['-y', '-loglevel', 'error', '-i', png, '-vf', 'scale=720:-2', '-q:v', '4', path.join(OUT, nombre + '.jpg')]);
    await page.close();
  }
}

async function clipMovimiento(browser) {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'mov-'));
  const ctx = await browser.newContext({ viewport: { width: 1400, height: 822 }, deviceScaleFactor: 1, recordVideo: { dir, size: { width: 1400, height: 822 } } });
  const page = await ctx.newPage();
  await page.goto(BASE);
  await page.waitForTimeout(800);
  // La vista previa llena todo el cuadro y se oculta la barra del sitio.
  await page.addStyleTag({ content: OCULTAR + '#device{position:fixed!important;left:0!important;top:0!important;width:1400px!important;height:822px!important;margin:0!important;border-radius:0!important;z-index:99999!important;background:#000!important}header,nav,.topbar,.strip,.stats,#stats{display:none!important}' });
  await click(page, '[data-choices="style.theme"] [data-id="ruta"]');
  await click(page, '[data-choices="style.layout"] [data-id="estelar"]');
  await click(page, '#mode-night');
  await click(page, '[data-choices="features.motion"] [data-id="vivo"]');
  await page.waitForTimeout(1500);
  const t0 = Date.now();
  await page.evaluate(() => { TB.motion.welcome(); });                           // 1) bienvenida
  await page.waitForTimeout(10000);                                              // 2) auto vivo (el reflejo cruza hacia los 7 s)
  await click(page, '[data-choices="features.motion"] [data-id="completo"]');    // 3) carretera
  await page.waitForTimeout(6500);
  const duracion = (Date.now() - t0) / 1000;
  await ctx.close();
  const webm = fs.readdirSync(dir).map((f) => path.join(dir, f))[0];
  const total = parseFloat(execFileSync('ffprobe', ['-v', 'error', '-show_entries', 'format=duration', '-of', 'csv=p=0', webm]).toString());
  const inicio = (total - duracion - 0.05).toFixed(2);           // lo que se grabó antes de la bienvenida
  const crudo = path.join(dir, 'crudo.mp4');
  execFileSync('ffmpeg', ['-y', '-loglevel', 'error', '-ss', inicio, '-i', webm, '-vf', 'fps=30,format=yuv420p', '-c:v', 'libx264', '-crf', '17', '-an', crudo]);
  // Se arma un clip de 10 s con lo mejor de cada parte: bienvenida (3,4 s), el reflejo del auto vivo (2,7 s) y la carretera (3,9 s).
  const filtro = '[0:v]trim=0:3.4,setpts=PTS-STARTPTS[a];[0:v]trim=6.3:9.0,setpts=PTS-STARTPTS[b];[0:v]trim=11.3:15.2,setpts=PTS-STARTPTS[c];[a][b][c]concat=n=3:v=1:a=0,fps=30,format=yuv420p[v]';
  execFileSync('ffmpeg', ['-y', '-loglevel', 'error', '-i', crudo, '-filter_complex', filtro, '-map', '[v]', '-c:v', 'libx264', '-crf', '18', '-preset', 'medium', '-g', '30', '-movflags', '+faststart', '-an', path.join(OUT, 'movimiento.mp4')]);
}

(async () => {
  const browser = await chromium.launch();
  await verticales(browser);
  await clipMovimiento(browser);
  await browser.close();
  console.log('Listo: pantallas verticales y movimiento.mp4 en', OUT);
})().catch((e) => { console.error(e); process.exit(1); });
