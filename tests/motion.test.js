'use strict';
// El movimiento de la pantalla (public/js/motion.js + el bloque «movimiento» de preview.css) corre en radios con Android 5 a 10,
// WebView viejo y GPU floja. Estas pruebas cuidan las reglas de la casa que ahí importan; lo que se ve lo prueba tests/e2e.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const C = require('../public/shared/catalog.js');
const Order = require('../public/shared/order.js');

const root = path.join(__dirname, '..');
const read = (f) => fs.readFileSync(path.join(root, f), 'utf8');
const stripComments = (t) => t.replace(/\/\*[\s\S]*?\*\//g, '').replace(/^\s*\/\/.*$/gm, '');
const js = read('public/js/motion.js');
const css = read('public/css/preview.css');
const preview = read('public/js/preview.js');
const launcher = read('public/js/launcher.js');

// Solo el bloque de movimiento del CSS (empieza en su comentario y termina antes del bloque de radios verticales).
const block = css.slice(css.indexOf('/* ---------- movimiento'), css.indexOf('Radios verticales'));

test('el catálogo declara los tres niveles y el sitio los valida (inválido cae en «vivo»)', () => {
  assert.deepEqual(C.motions, ['off', 'vivo', 'completo']);
  const mk = (motion) => Order.validateOrder({ radio: { type: 'android' }, car: {}, style: {}, features: { motion }, contact: { kind: 'email', value: 'a@b.cl' } }).clean.features.motion;
  assert.equal(mk('completo'), 'completo');
  assert.equal(mk('off'), 'off');
  for (const bad of ['siempre', 1, null, undefined, true, ['off']]) assert.equal(mk(bad), 'vivo', 'valor inválido: ' + JSON.stringify(bad));
});

test('motion.js lo cargan la radio y el sitio, antes de preview.js', () => {
  for (const f of ['public/launcher.html', 'public/index.html']) {
    const html = read(f);
    const m = html.indexOf('js/motion.js');
    assert.ok(m > 0, f + ' no carga motion.js');
    assert.ok(m < html.indexOf('js/preview.js'), f + ': motion.js debe cargarse antes de preview.js');
  }
});

test('el redibujado de la pantalla conserva las capas de movimiento', () => {
  assert.match(preview, /TB\.motion\.setHtml\(scr, html\)/, 'render() debe pasar por motion.setHtml (innerHTML borraría la bienvenida y la carretera)');
  assert.match(preview, /TB\.motion\.attach\(scr, state, ui\)/);
  assert.match(preview, /TB\.motion\.hud\(/, 'tick() debe pasarle la velocidad a la carretera');
  assert.match(launcher, /TB\.motion\.setSpeed/);
  assert.match(launcher, /opts\.welcome === false/, 'la app debe poder pedir que no se repita la bienvenida');
});

test('solo se animan transform y opacity (nada que repinte o reacomode cada cuadro)', () => {
  const kf = [...block.matchAll(/@keyframes (m[A-Za-z]+) \{([\s\S]*?)\n?\}\s*(?=\n|$)/g)];
  assert.ok(kf.length >= 6, 'deberían estar las animaciones de movimiento');
  for (const [, name, body] of kf) {
    const props = [...body.matchAll(/([a-z-]+)\s*:/g)].map((m) => m[1]).filter((p) => !['from', 'to'].includes(p));
    for (const p of props) assert.ok(['transform', 'opacity'].includes(p), `@keyframes ${name} anima «${p}»`);
  }
});

test('nada que un WebView de Android 5 a 10 no entienda', () => {
  const forbidden = [/\binset\s*:/, /aspect-ratio/, /\bcqw\b/, /color-mix/, /mix-blend-mode/, /container-type/, /:has\(/];
  for (const re of forbidden) {
    assert.doesNotMatch(stripComments(block), re, 'CSS de movimiento usa ' + re);
    assert.doesNotMatch(stripComments(js), re, 'motion.js usa ' + re);
  }
  for (const api of ['replaceChildren', 'structuredClone', 'Object.fromEntries', '.at(', 'ResizeObserver', 'IntersectionObserver', 'queueMicrotask']) {
    assert.ok(!stripComments(js).includes(api), 'motion.js usa ' + api + ', que no existe en un WebView viejo');
  }
  assert.ok(!/\?\.|\?\?/.test(js.replace(/\/\*[\s\S]*?\*\//g, '').replace(/\/\/.*$/gm, '')), 'motion.js usa ?. o ??, que un WebView viejo no entiende');
});

test('cada animación de movimiento se apaga con «reducir movimiento»', () => {
  const reduce = block.slice(block.indexOf('@media (prefers-reduced-motion: reduce)'));
  assert.ok(reduce.includes('animation: none'));
  const animated = new Set([...block.matchAll(/([^{}\n][^{}]*)\{[^}]*\banimation:/g)].flatMap((m) => m[1].split(',').map((s) => s.trim())).filter((s) => s.startsWith('.scr')));
  assert.ok(animated.size >= 5);
  for (const sel of animated) assert.ok(reduce.includes(sel), `«${sel}» se anima y no está en el bloque de reducir movimiento`);
  assert.match(js, /prefers-reduced-motion/, 'motion.js también debe respetarlo');
});

test('la carretera deja la barra de abajo a la vista y se apaga sola si va lenta', () => {
  assert.match(block, /\.mot-road \{[^}]*z-index: 4/, 'la carretera debe quedar bajo la barra de abajo (z-index 5) y los avisos');
  assert.match(js, /querySelector\('\.dock'\)/);
  assert.match(js, /SLOW_FPS/);
  assert.match(js, /FRAME_MS = 1000 \/ 30/, 'tope de 30 cuadros por segundo');
});
