/*
 * Recorte del auto: quita el fondo de la foto y la mejora, todo dentro del navegador (la foto no se sube).
 * Usa U²-Net (versión pequeña, licencia Apache-2.0) con onnxruntime-web (licencia MIT).
 * Si el modelo no carga (sin internet, navegador antiguo), usa un recorte simple por color de fondo.
 */
(function () {
  const TB = (window.TB = window.TB || {});
  const ORT_VERSION = '1.31.1';
  const ORT_BASE = `https://cdn.jsdelivr.net/npm/onnxruntime-web@${ORT_VERSION}/dist/`;
  const MODEL_URL = 'models/u2netp.onnx';
  const SIZE = 320;
  const MAX_SIDE = 1100;

  let sessionPromise = null;

  function loadScript(src) {
    return new Promise((ok, fail) => {
      const s = document.createElement('script');
      s.src = src;
      s.onload = ok;
      s.onerror = () => fail(new Error('script'));
      document.head.appendChild(s);
    });
  }

  function getSession() {
    if (!sessionPromise) {
      sessionPromise = (async () => {
        if (!window.ort) await loadScript(TB.ortBase ? TB.ortBase + 'ort.min.js' : ORT_BASE + 'ort.min.js');
        const ort = window.ort;
        ort.env.wasm.wasmPaths = TB.ortBase || ORT_BASE;
        ort.env.wasm.numThreads = 1; // sin hilos: no exige cabeceras especiales del servidor
        ort.env.wasm.proxy = false;
        const buf = await (await fetch(MODEL_URL)).arrayBuffer();
        return ort.InferenceSession.create(buf, { executionProviders: ['wasm'] });
      })().catch((e) => {
        sessionPromise = null;
        throw e;
      });
    }
    return sessionPromise;
  }

  const smooth = (a, b, x) => {
    const t = Math.min(1, Math.max(0, (x - a) / (b - a)));
    return t * t * (3 - 2 * t);
  };

  /** Máscara 0..1 de SIZE×SIZE con el objeto principal. */
  async function modelMask(src) {
    const session = await getSession();
    const t = document.createElement('canvas');
    t.width = t.height = SIZE;
    t.getContext('2d').drawImage(src, 0, 0, SIZE, SIZE);
    const px = t.getContext('2d').getImageData(0, 0, SIZE, SIZE).data;
    let mx = 1;
    for (let i = 0; i < px.length; i += 4) mx = Math.max(mx, px[i], px[i + 1], px[i + 2]);
    const n = SIZE * SIZE;
    const data = new Float32Array(3 * n);
    const mean = [0.485, 0.456, 0.406];
    const std = [0.229, 0.224, 0.225];
    for (let i = 0; i < n; i++) {
      for (let c = 0; c < 3; c++) data[c * n + i] = (px[i * 4 + c] / mx - mean[c]) / std[c];
    }
    const out = await session.run({ [session.inputNames[0]]: new window.ort.Tensor('float32', data, [1, 3, SIZE, SIZE]) });
    const o = out[session.outputNames[0]].data;
    let lo = Infinity;
    let hi = -Infinity;
    for (let i = 0; i < n; i++) {
      if (o[i] < lo) lo = o[i];
      if (o[i] > hi) hi = o[i];
    }
    const m = new Float32Array(n);
    for (let i = 0; i < n; i++) m[i] = (o[i] - lo) / (hi - lo || 1);
    return m;
  }

  /** Deja solo la región conectada más grande (quita manchas sueltas). */
  function keepLargest(m) {
    const n = SIZE * SIZE;
    const lab = new Int32Array(n);
    const sizes = [0];
    let cur = 0;
    const stack = [];
    for (let s = 0; s < n; s++) {
      if (m[s] < 0.5 || lab[s]) continue;
      cur++;
      sizes[cur] = 0;
      stack.push(s);
      lab[s] = cur;
      while (stack.length) {
        const p = stack.pop();
        sizes[cur]++;
        const x = p % SIZE;
        const nb = [x > 0 ? p - 1 : -1, x < SIZE - 1 ? p + 1 : -1, p >= SIZE ? p - SIZE : -1, p < n - SIZE ? p + SIZE : -1];
        for (const q of nb) if (q >= 0 && !lab[q] && m[q] >= 0.5) { lab[q] = cur; stack.push(q); }
      }
    }
    let best = 0;
    for (let k = 1; k <= cur; k++) if (sizes[k] > sizes[best]) best = k;
    if (!best) return m;
    const r = new Float32Array(n);
    for (let i = 0; i < n; i++) r[i] = lab[i] === best || (m[i] > 0.15 && nearLabel(lab, i, best)) ? m[i] : 0;
    return r;
  }
  function nearLabel(lab, i, best) {
    const x = i % SIZE;
    const y = (i - x) / SIZE;
    for (let dy = -3; dy <= 3; dy++) for (let dx = -3; dx <= 3; dx++) {
      const xx = x + dx;
      const yy = y + dy;
      if (xx >= 0 && xx < SIZE && yy >= 0 && yy < SIZE && lab[yy * SIZE + xx] === best) return true;
    }
    return false;
  }

  /** Respaldo sin modelo: quita un fondo liso pelando desde los bordes. */
  function flatMask(src, w, h) {
    const c = document.createElement('canvas');
    c.width = SIZE;
    c.height = SIZE;
    c.getContext('2d').drawImage(src, 0, 0, SIZE, SIZE);
    const d = c.getContext('2d').getImageData(0, 0, SIZE, SIZE).data;
    const idx = (x, y) => (y * SIZE + x) * 4;
    const ring = [];
    for (let i = 0; i < SIZE; i++) ring.push(idx(i, 0), idx(i, SIZE - 1), idx(0, i), idx(SIZE - 1, i));
    const bg = [0, 0, 0];
    ring.forEach((a) => { for (let k = 0; k < 3; k++) bg[k] += d[a + k] / ring.length; });
    const far = (a) => Math.abs(d[a] - bg[0]) + Math.abs(d[a + 1] - bg[1]) + Math.abs(d[a + 2] - bg[2]);
    const seen = new Uint8Array(SIZE * SIZE);
    const stack = [];
    ring.forEach((a) => { const p = a / 4; if (far(a) < 60 && !seen[p]) { seen[p] = 1; stack.push(p); } });
    while (stack.length) {
      const p = stack.pop();
      const x = p % SIZE;
      for (const q of [x > 0 ? p - 1 : -1, x < SIZE - 1 ? p + 1 : -1, p >= SIZE ? p - SIZE : -1, p < SIZE * SIZE - SIZE ? p + SIZE : -1]) {
        if (q < 0 || seen[q] || far(q * 4) >= 60) continue;
        seen[q] = 1;
        stack.push(q);
      }
    }
    const m = new Float32Array(SIZE * SIZE);
    let kept = 0;
    for (let i = 0; i < m.length; i++) { m[i] = seen[i] ? 0 : 1; kept += m[i]; }
    return kept > m.length * 0.04 && kept < m.length * 0.9 ? m : null;
  }

  /** Sube la máscara al tamaño de la foto con bordes suaves. */
  function maskCanvas(m, w, h) {
    const mc = document.createElement('canvas');
    mc.width = mc.height = SIZE;
    const mx = mc.getContext('2d');
    const img = mx.createImageData(SIZE, SIZE);
    for (let i = 0; i < m.length; i++) {
      const v = Math.round(smooth(0.3, 0.7, m[i]) * 255);
      img.data[i * 4] = img.data[i * 4 + 1] = img.data[i * 4 + 2] = 255;
      img.data[i * 4 + 3] = v;
    }
    mx.putImageData(img, 0, 0);
    const out = document.createElement('canvas');
    out.width = w;
    out.height = h;
    const ox = out.getContext('2d');
    ox.imageSmoothingQuality = 'high';
    ox.filter = 'blur(1.2px)';
    ox.drawImage(mc, 0, 0, w, h);
    return out;
  }

  /**
   * src: canvas o bitmap con la foto. Devuelve { canvas, method } con el auto sin fondo y mejorado,
   * o null si no se pudo separar el auto del fondo.
   */
  async function cutout(src) {
    const k = Math.min(1, MAX_SIDE / Math.max(src.width, src.height));
    const w = Math.round(src.width * k);
    const h = Math.round(src.height * k);
    let mask = null;
    let method = 'ia';
    try {
      mask = keepLargest(await modelMask(src));
    } catch (_) {
      method = 'simple';
      mask = flatMask(src, w, h);
    }
    if (!mask) return null;
    const alpha = maskCanvas(mask, w, h);
    const car = document.createElement('canvas');
    car.width = w;
    car.height = h;
    const cx = car.getContext('2d');
    // Mejora: un poco de contraste, color y luz, solo sobre el auto.
    cx.filter = 'contrast(1.08) saturate(1.14) brightness(1.03)';
    cx.drawImage(src, 0, 0, w, h);
    cx.filter = 'none';
    cx.globalCompositeOperation = 'destination-in';
    cx.drawImage(alpha, 0, 0);
    // Recorta al contorno del auto con un pequeño margen.
    const px = cx.getImageData(0, 0, w, h).data;
    let x0 = w, y0 = h, x1 = -1, y1 = -1;
    for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) {
      if (px[(y * w + x) * 4 + 3] > 40) { if (x < x0) x0 = x; if (x > x1) x1 = x; if (y < y0) y0 = y; if (y > y1) y1 = y; }
    }
    if (x1 < 0 || (x1 - x0) * (y1 - y0) < w * h * 0.03) return null;
    const pad = Math.round(Math.max(x1 - x0, y1 - y0) * 0.02);
    x0 = Math.max(0, x0 - pad); y0 = Math.max(0, y0 - pad);
    x1 = Math.min(w - 1, x1 + pad); y1 = Math.min(h - 1, y1 + pad);
    const out = document.createElement('canvas');
    out.width = x1 - x0 + 1;
    out.height = y1 - y0 + 1;
    out.getContext('2d').drawImage(car, x0, y0, out.width, out.height, 0, 0, out.width, out.height);
    return { canvas: out, method };
  }

  TB.cutout = cutout;
})();
