/*
 * Movimiento de la pantalla de inicio (estilo Tesla). Tres cosas, que se eligen con una sola opción (`features.motion`):
 *   off       sin movimiento nuevo.
 *   vivo      bienvenida al encender (una vez) + auto vivo (respira, un reflejo le cruza la carrocería, los faros pulsan).
 *   completo  lo anterior + modo conducción: al ir en marcha, una carretera vista desde atrás con tu auto, las líneas del
 *             camino corren según la velocidad del GPS. Se quita solo al detenerse, o al tocar la pantalla.
 *
 * Reglas de la casa para radios débiles (Android 5 a 10, WebView viejo, GPU floja):
 *  - Solo se animan `transform` y `opacity` (el modo conducción dibuja en un canvas, a 30 fps como máximo).
 *  - Nada de `inset`, `aspect-ratio`, `cqw`, `color-mix` ni `replaceChildren`: no existen en un WebView viejo.
 *  - Si el modo conducción anda lento (menos de 15 fps seguidos), se apaga solo para esta sesión y queda en «vivo».
 *  - Con `prefers-reduced-motion` todo queda quieto.
 * La capa de bienvenida y la de la carretera NO se borran cuando la pantalla se redibuja (render() cambia todo su HTML):
 * por eso viven aparte y `setHtml` las conserva.
 */
(function () {
  'use strict';
  const TB = (window.TB = window.TB || {});
  const LEVELS = ['off', 'vivo', 'completo'];
  const rank = (l) => Math.max(0, LEVELS.indexOf(l));
  const mq = window.matchMedia ? window.matchMedia('(prefers-reduced-motion: reduce)') : null;

  // Velocidades (km/h) con las que entra y sale la carretera. La salida espera unos segundos para no parpadear en un semáforo.
  const DRIVE_ON = 8;
  const DRIVE_OFF = 3;
  const DRIVE_HOLD_MS = 8000;
  const WELCOME_MS = 3600;
  const FRAME_MS = 1000 / 30;
  const SLOW_FPS = 15;

  const M = {
    level: 'vivo', // lo que pidió el cliente
    cap: 2, // tope por rendimiento (baja si la radio va lenta)
    scr: null, fx: null, road: null, cv: null, g: null,
    state: null, ui: null,
    welcomed: false, welcomeTimer: 0,
    kmh: 0, hud: { speedText: '0', unit: 'km/h', limitText: '' },
    driving: false, dismissed: false, slowSince: 0,
    dist: 0, t: 0, last: 0, acc: 0, raf: 0, smooth: 0,
    cw: 0, ch: 0, k: 1,
    slowFrames: 0, fpsAvg: 60,
  };
  const stats = { level: 'vivo', driving: false, fps: 0, degraded: false, welcomed: false, frames: 0 };

  const reduced = () => !!(mq && mq.matches);
  const effective = () => (reduced() ? 0 : Math.min(rank(M.level), M.cap));

  /* ---------- capas que sobreviven al redibujado ---------- */
  function ensureLayers(scr) {
    if (M.scr !== scr) {
      M.scr = scr;
      M.fx = M.road = M.cv = null;
    }
    if (!M.road) {
      const road = document.createElement('div');
      road.className = 'mot-road';
      road.hidden = true;
      road.setAttribute('aria-hidden', 'true');
      road.innerHTML =
        '<canvas class="mot-cv"></canvas>' +
        '<div class="mot-hud"><b data-mspeed>0</b><span data-munit>km/h</span></div>' +
        '<div class="mot-limit" hidden><small>LÍMITE</small><b data-mlimit></b></div>' +
        '<div class="mot-hint">Toca para ver el inicio</div>';
      road.addEventListener('click', () => { M.dismissed = true; stopDrive(); });
      M.road = road;
      M.cv = road.querySelector('canvas');
      try { M.g = M.cv.getContext('2d'); } catch (e) { M.g = null; }
    }
    if (!M.fx) {
      const fx = document.createElement('div');
      fx.className = 'mot-fx';
      fx.setAttribute('aria-hidden', 'true');
      fx.innerHTML = '<div class="mot-veil"></div><div class="mot-bloom"></div><div class="mot-sweep"></div>';
      M.fx = fx;
      // Tocar la pantalla corta la bienvenida: en una radio nadie debe esperar.
      const skip = () => { if (M.fx && M.fx.className.indexOf('play') >= 0) endWelcome(); };
      ['pointerdown', 'mousedown', 'touchstart'].forEach((ev) => scr.addEventListener(ev, skip, true));
    }
    if (M.road.parentNode !== scr) scr.appendChild(M.road);
    if (M.fx.parentNode !== scr) scr.appendChild(M.fx);
  }

  /** Reemplaza el HTML de la pantalla sin tocar la carretera ni la bienvenida (siguen en pie y no se reinician). */
  function setHtml(scr, html) {
    ensureLayers(scr);
    Array.prototype.slice.call(scr.children).forEach((c) => { if (c !== M.road && c !== M.fx) scr.removeChild(c); });
    scr.insertAdjacentHTML('afterbegin', html);
  }

  /* ---------- auto vivo: reflejo de luz sobre la silueta del auto ---------- */
  function addSheens(scr) {
    scr.querySelectorAll('.m-sheen').forEach((n) => n.parentNode && n.parentNode.removeChild(n));
    if (effective() < 1) return;
    const make = (parent, src, flip, css) => {
      const s = document.createElement('div');
      s.className = 'm-sheen' + (flip ? ' flip' : '') + (css ? ' photo' : '');
      s.setAttribute('aria-hidden', 'true');
      const m = 'url("' + String(src).replace(/"/g, '%22') + '")';
      s.style.webkitMaskImage = m;
      s.style.maskImage = m;
      if (css) s.style.cssText += css;
      s.appendChild(document.createElement('i'));
      parent.appendChild(s);
    };
    // Autos con imagen (los de fábrica, pintados al color del cliente): la capa llena el contenedor, sin contar la sombra de abajo.
    scr.querySelectorAll('.pickup').forEach((p) => {
      const img = p.querySelector('img.pickup-img');
      if (img) make(p, img.currentSrc || img.src, p.classList.contains('flip'), '');
    });
    // Foto recortada del cliente: la capa copia el cuadro de la imagen (el recorte ya viene sin fondo, así el reflejo no sale de la silueta).
    scr.querySelectorAll('img.car-photo.cut').forEach((img) => {
      const p = img.parentNode;
      if (!p) return;
      try { if (window.getComputedStyle(p).position === 'static') p.style.position = 'relative'; } catch (e) { /* ignorar */ }
      make(p, img.currentSrc || img.src, false,
        'left:' + img.offsetLeft + 'px;top:' + img.offsetTop + 'px;width:' + img.offsetWidth + 'px;height:' + img.offsetHeight + 'px;');
    });
  }

  /* ---------- bienvenida ---------- */
  function welcome() {
    if (M.welcomed || effective() < 1 || !M.fx) return;
    M.welcomed = true;
    stats.welcomed = true;
    M.fx.className = 'mot-fx play';
    if (M.scr) M.scr.setAttribute('data-welcome', '1');
    clearTimeout(M.welcomeTimer);
    M.welcomeTimer = setTimeout(endWelcome, WELCOME_MS);
  }
  function endWelcome() {
    clearTimeout(M.welcomeTimer);
    if (M.fx) M.fx.className = 'mot-fx';
    if (M.scr) M.scr.removeAttribute('data-welcome');
  }

  /* ---------- modo conducción ---------- */
  const shade = (hex, f) => {
    const m = /^#?([0-9a-f]{6})$/i.exec(hex || '');
    const n = parseInt(m ? m[1] : 'b3261e', 16);
    const c = (v) => Math.max(0, Math.min(255, Math.round(v * f)));
    return 'rgb(' + c((n >> 16) & 255) + ',' + c((n >> 8) & 255) + ',' + c(n & 255) + ')';
  };
  const PAL = {
    night: { sky0: '#04070b', sky1: '#0e1824', side: '#0a1119', roadFar: '#0e151d', road: '#19222d', edge: '#34465a', dash: '#dfe8f1', yellow: '#e7bf3d', fog: '10,17,26', glass: '#0a121b' },
    day: { sky0: '#cfd6dd', sky1: '#eceff2', side: '#dfe2e5', roadFar: '#e4e6e9', road: '#c4c8ce', edge: '#9ba4af', dash: '#ffffff', yellow: '#d9ac17', fog: '233,235,238', glass: '#27303b' },
  };
  // Medidas de cada tipo de auto visto desde atrás (metros): ancho, alto.
  const REAR = { auto: [1.8, 1.4], suv: [1.85, 1.7], camioneta: [1.95, 1.8], '4x4': [1.95, 1.95] };

  function resize() {
    const scr = M.scr;
    if (!scr || !M.cv) return;
    const dock = scr.querySelector('.dock');
    const dockH = dock ? dock.offsetHeight : 92;
    const w = scr.clientWidth || 1024;
    const h = Math.max(200, (scr.clientHeight || 600) - dockH);
    // Resolución del canvas: la de la pantalla física (la pantalla se reduce o se agranda con `scale`), hasta 2×.
    let k = 1;
    try { const r = scr.getBoundingClientRect(); if (r.width && scr.offsetWidth) k = Math.min(2, Math.max(1, Math.round(r.width / scr.offsetWidth))); } catch (e) { k = 1; }
    if (M.cw === w && M.ch === h && M.k === k) return;
    M.cw = w; M.ch = h; M.k = k;
    M.road.style.bottom = dockH + 'px';
    M.cv.width = w * k;
    M.cv.height = h * k;
    M.cv.style.width = w + 'px';
    M.cv.style.height = h + 'px';
  }

  function drawRoad() {
    const g = M.g;
    if (!g) return;
    const st = M.state || {};
    const night = !!(M.ui && M.ui.night);
    const P = night ? PAL.night : PAL.day;
    const W = M.cw, H = M.ch;
    const HOR = H * 0.3, F = W * 0.46, CAMH = 3, CAMX = 3.6, ZMIN = 1.25, ZMAX = 130;
    const bend = 0.0032 * Math.sin(M.t * 0.12) + 0.0012 * Math.sin(M.t * 0.31);
    g.setTransform(M.k, 0, 0, M.k, 0, 0);
    const px = (x, z) => { const s = F / z; return [W / 2 + (x - CAMX + bend * z * z) * s, HOR + CAMH * s]; };
    const ribbon = (x0, x1, z0, z1, fill) => {
      const n = Math.max(2, Math.ceil((z1 - z0) / 3));
      const a = [], b = [];
      for (let i = 0; i <= n; i++) { const z = z0 + ((z1 - z0) * i) / n; a.push(px(x0, z)); b.push(px(x1, z)); }
      g.beginPath(); g.moveTo(a[0][0], a[0][1]);
      for (let i = 1; i < a.length; i++) g.lineTo(a[i][0], a[i][1]);
      for (let i = b.length - 1; i >= 0; i--) g.lineTo(b[i][0], b[i][1]);
      g.closePath(); g.fillStyle = fill; g.fill();
    };
    let grad = g.createLinearGradient(0, 0, 0, HOR + 10);
    grad.addColorStop(0, P.sky0); grad.addColorStop(1, P.sky1);
    g.fillStyle = grad; g.fillRect(0, 0, W, HOR + 10);
    g.fillStyle = P.side; g.fillRect(0, HOR, W, H - HOR);
    grad = g.createLinearGradient(0, HOR, 0, H);
    grad.addColorStop(0, P.roadFar); grad.addColorStop(1, P.road);
    ribbon(-9.4, 9.4, ZMIN, ZMAX, grad);
    ribbon(-9.55, -9.3, ZMIN, ZMAX, P.edge); ribbon(9.3, 9.55, ZMIN, ZMAX, P.edge);
    ribbon(-1.98, -1.86, ZMIN, ZMAX, P.yellow); ribbon(-1.74, -1.62, ZMIN, ZMAX, P.yellow);
    const off = M.dist % 9;
    for (let k = 0; k < 16; k++) {
      let z0 = k * 9 - off;
      const z1 = z0 + 3;
      if (z1 < ZMIN || z0 > ZMAX) continue;
      z0 = Math.max(z0, ZMIN);
      [1.8, 5.4, -5.4].forEach((lx) => ribbon(lx - 0.07, lx + 0.07, z0, z1, P.dash));
    }
    grad = g.createLinearGradient(0, HOR - 6, 0, HOR + H * 0.26);
    grad.addColorStop(0, 'rgba(' + P.fog + ',1)'); grad.addColorStop(1, 'rgba(' + P.fog + ',0)');
    g.fillStyle = grad; g.fillRect(0, HOR - 6, W, H * 0.26 + 6);
    // Tu auto, visto desde atrás, en su carril (a la derecha de la doble línea amarilla), con su tipo y su color.
    const type = REAR[st.car && st.car.type] ? st.car.type : 'auto';
    const sway = M.smooth > 1 ? Math.sin(M.t * 1.4) * 0.05 : 0;
    const zEgo = (CAMH * F) / (H * 0.82 - HOR); // a esta distancia, las ruedas quedan al 82 % del alto del cuadro
    drawRear(g, type, px(3.6 + sway, zEgo), F / zEgo, (st.car && st.car.color) || '#b3261e', P, night);
  }

  function rr(g, x, y, w, h, r, fill) {
    g.beginPath(); g.moveTo(x + r, y); g.arcTo(x + w, y, x + w, y + h, r); g.arcTo(x + w, y + h, x, y + h, r);
    g.arcTo(x, y + h, x, y, r); g.arcTo(x, y, x + w, y, r); g.closePath(); g.fillStyle = fill; g.fill();
  }

  /** Dibuja un auto visto desde atrás, parado en (p[0], p[1]) con `s` píxeles por metro. */
  function drawRear(g, type, p, s, color, P, night) {
    const dim = REAR[type];
    const bw = dim[0] * s, bh = dim[1] * s, x0 = p[0] - bw / 2, yb = p[1];
    const mid = shade(color, 0.82), dark = shade(color, 0.6), glass = P.glass;
    g.fillStyle = 'rgba(0,0,0,.45)'; g.beginPath(); g.ellipse(p[0], yb, bw * 0.6, bh * 0.08, 0, 0, 6.283); g.fill();
    if (type === 'auto') {
      rr(g, x0, yb - bh * 0.64, bw, bh * 0.6, bw * 0.1, color);
      rr(g, x0 + bw * 0.13, yb - bh, bw * 0.74, bh * 0.52, bw * 0.14, mid);
      rr(g, x0 + bw * 0.2, yb - bh * 0.93, bw * 0.6, bh * 0.34, bw * 0.08, glass);
      rr(g, x0 + bw * 0.04, yb - bh * 0.2, bw * 0.92, bh * 0.16, bw * 0.04, '#10151b');
      rr(g, x0 + bw * 0.03, yb - bh * 0.58, bw * 0.22, bh * 0.12, bw * 0.02, '#ff4256');
      rr(g, x0 + bw * 0.75, yb - bh * 0.58, bw * 0.22, bh * 0.12, bw * 0.02, '#ff4256');
    } else if (type === 'camioneta') {
      rr(g, x0, yb - bh * 0.6, bw, bh * 0.56, bw * 0.05, color);
      rr(g, x0 + bw * 0.14, yb - bh, bw * 0.72, bh * 0.44, bw * 0.07, mid);
      rr(g, x0 + bw * 0.24, yb - bh * 0.93, bw * 0.52, bh * 0.28, bw * 0.05, glass);
      rr(g, x0 + bw * 0.02, yb - bh * 0.2, bw * 0.96, bh * 0.16, bw * 0.03, '#10151b');
      rr(g, x0 + bw * 0.01, yb - bh * 0.56, bw * 0.1, bh * 0.2, bw * 0.02, '#ff4256');
      rr(g, x0 + bw * 0.89, yb - bh * 0.56, bw * 0.1, bh * 0.2, bw * 0.02, '#ff4256');
    } else {
      // SUV y 4x4: techo plano, luneta casi vertical, alerón y luces altas.
      rr(g, x0, yb - bh * 0.55, bw, bh * 0.5, bw * 0.07, color);
      rr(g, x0 + bw * 0.03, yb - bh * 0.2, bw * 0.94, bh * 0.17, bw * 0.05, '#10151b');
      g.beginPath(); g.moveTo(x0 + bw * 0.03, yb - bh * 0.5); g.lineTo(x0 + bw * 0.12, yb - bh * 0.93);
      g.quadraticCurveTo(x0 + bw * 0.14, yb - bh * 0.98, x0 + bw * 0.2, yb - bh * 0.98); g.lineTo(x0 + bw * 0.8, yb - bh * 0.98);
      g.quadraticCurveTo(x0 + bw * 0.86, yb - bh * 0.98, x0 + bw * 0.88, yb - bh * 0.93); g.lineTo(x0 + bw * 0.97, yb - bh * 0.5);
      g.closePath(); g.fillStyle = mid; g.fill();
      g.beginPath(); g.moveTo(x0 + bw * 0.14, yb - bh * 0.55); g.lineTo(x0 + bw * 0.19, yb - bh * 0.88); g.lineTo(x0 + bw * 0.81, yb - bh * 0.88);
      g.lineTo(x0 + bw * 0.86, yb - bh * 0.55); g.closePath(); g.fillStyle = glass; g.fill();
      rr(g, x0 + bw * 0.1, yb - bh * 1.04, bw * 0.8, bh * 0.07, bw * 0.03, dark);
      rr(g, x0 - bw * 0.005, yb - bh * 0.66, bw * 0.15, bh * 0.3, bw * 0.03, '#ff4256');
      rr(g, x0 + bw * 0.855, yb - bh * 0.66, bw * 0.15, bh * 0.3, bw * 0.03, '#ff4256');
    }
    rr(g, x0 + bw * 0.4, yb - bh * 0.36, bw * 0.2, bh * 0.07, bw * 0.01, '#e8edf2');
    const gl = g.createRadialGradient(p[0], yb - bh * 0.5, 2, p[0], yb - bh * 0.5, bw);
    gl.addColorStop(0, night ? 'rgba(255,50,70,.26)' : 'rgba(255,50,70,.1)'); gl.addColorStop(1, 'rgba(255,50,70,0)');
    g.fillStyle = gl; g.fillRect(p[0] - bw, yb - bh * 0.5 - bw, bw * 2, bw * 2);
  }

  function frame(ts) {
    M.raf = 0;
    if (!M.driving) return;
    if (document.hidden) { M.raf = requestAnimationFrame(frame); return; }
    const dtRaw = M.last ? ts - M.last : FRAME_MS;
    M.last = ts;
    M.acc += dtRaw;
    // Medida de rendimiento: promedio móvil de los cuadros reales (antes de saltar cuadros).
    if (dtRaw > 0 && dtRaw < 500) {
      M.fpsAvg = M.fpsAvg * 0.92 + (1000 / dtRaw) * 0.08;
      stats.fps = Math.round(M.fpsAvg);
      if (M.fpsAvg < SLOW_FPS) M.slowFrames++; else M.slowFrames = 0;
      if (M.slowFrames > 90) { degrade(); return; }
    }
    if (M.acc >= FRAME_MS) {
      const dt = Math.min(0.1, M.acc / 1000);
      M.acc = 0;
      M.smooth += (M.kmh - M.smooth) * Math.min(1, dt * 2.2); // la velocidad del GPS llega a saltos de 1 s: se suaviza
      M.dist += (M.smooth / 3.6) * dt;
      M.t += dt;
      stats.frames++;
      try { drawRoad(); } catch (e) { degrade(); return; }
    }
    M.raf = requestAnimationFrame(frame);
  }

  function startDrive() {
    if (M.driving || !M.road || !M.g) return;
    M.driving = true;
    stats.driving = true;
    resize();
    M.road.hidden = false;
    if (M.scr) M.scr.setAttribute('data-drive', '1');
    M.last = 0; M.acc = FRAME_MS; M.slowFrames = 0; M.fpsAvg = 60;
    if (!M.raf) M.raf = requestAnimationFrame(frame);
  }
  function stopDrive() {
    M.driving = false;
    stats.driving = false;
    M.slowSince = 0;
    if (M.raf) { cancelAnimationFrame(M.raf); M.raf = 0; }
    if (M.road) M.road.hidden = true;
    if (M.scr) M.scr.removeAttribute('data-drive');
  }
  /** La radio no da el ancho: se queda en «vivo» el resto de la sesión. */
  function degrade() {
    M.cap = 1;
    stats.degraded = true;
    stopDrive();
    refresh();
  }

  /** Decide si la carretera entra o sale, según la velocidad, la pantalla en que está el cliente y el nivel elegido. */
  function evaluate() {
    const want = effective() >= 2 && M.ui && M.ui.screen === 'inicio' && !M.dismissed && !(M.fx && M.fx.className.indexOf('play') >= 0);
    if (M.kmh < DRIVE_OFF) M.dismissed = false; // al detenerse, vuelve a armarse
    if (!M.driving) {
      if (want && M.kmh >= DRIVE_ON) startDrive();
      return;
    }
    if (!want) { stopDrive(); return; }
    if (M.kmh < DRIVE_OFF) {
      const now = Date.now();
      if (!M.slowSince) M.slowSince = now;
      else if (now - M.slowSince > DRIVE_HOLD_MS) stopDrive();
    } else {
      M.slowSince = 0;
    }
  }

  function refresh() {
    if (!M.scr) return;
    const eff = effective();
    stats.level = LEVELS[eff];
    M.scr.setAttribute('data-motion', LEVELS[eff]);
    M.scr.setAttribute('data-vivo', eff >= 1 ? '1' : '0');
    if (eff < 2 && M.driving) stopDrive();
    evaluate();
  }

  /* ---------- lo que usa el resto de la pantalla ---------- */
  TB.motion = {
    LEVELS,
    stats,
    setHtml,
    /** Se llama después de cada render. */
    attach(scr, state, ui) {
      ensureLayers(scr);
      M.state = state; M.ui = ui;
      const lv = state && state.features && state.features.motion;
      M.level = LEVELS.indexOf(lv) >= 0 ? lv : 'vivo';
      refresh();
      try { addSheens(scr); } catch (e) { /* un reflejo que no se dibuja no debe romper la pantalla */ }
      if (M.driving) { M.cw = 0; resize(); }
    },
    /** La app (o la demo) avisa la velocidad en km/h. */
    setSpeed(kmh) {
      const v = Number(kmh);
      M.kmh = isFinite(v) && v > 0 ? v : 0;
      if (M.scr) evaluate();
    },
    /** Textos que se ven en la carretera (velocidad, unidad y límite, ya convertidos a km o millas). */
    hud(h) {
      if (!h) return;
      M.hud = h;
      if (typeof h.kmh === 'number') M.kmh = Math.max(0, h.kmh);
      if (M.road) {
        const set = (sel, v) => { const el = M.road.querySelector(sel); if (el && el.textContent !== v) el.textContent = v; };
        set('[data-mspeed]', String(h.speedText == null ? '' : h.speedText));
        set('[data-munit]', String(h.unit || 'km/h'));
        const lim = M.road.querySelector('.mot-limit');
        if (lim) { lim.hidden = !h.limitText; set('[data-mlimit]', String(h.limitText || '')); }
      }
      if (M.scr) evaluate();
    },
    welcome,
    endWelcome,
  };
})();
