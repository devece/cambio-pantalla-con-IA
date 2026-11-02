/*
 * Animaciones de temporada sobre la pantalla de la radio (nieve, fantasmas y murciélagos, fuegos artificiales, confeti).
 * Una sola capa <canvas> de 1024 x 600 que se vuelve a enganchar cada vez que la vista previa se repinta.
 * Con "reducir movimiento" activado en el sistema, dibuja un solo cuadro quieto.
 */
(function () {
  const TB = (window.TB = window.TB || {});
  const W = 1024;
  const H = 600;
  const reduced = window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  const TAU = Math.PI * 2;

  let cv = null;
  let cx = null;
  let season = 'ninguna';
  let night = false;
  let raf = 0;
  let last = 0;
  let clock = 0;
  let P = [];
  let bats = [];
  let rockets = [];
  let sparks = [];
  let nextBat = 3;
  let nextRocket = 0.6;

  const rnd = (a, b) => a + Math.random() * (b - a);

  function init() {
    P = [];
    bats = [];
    rockets = [];
    sparks = [];
    nextBat = 2.5;
    nextRocket = 0.5;
    if (season === 'navidad') {
      for (let i = 0; i < 70; i++) P.push({ x: rnd(0, W), y: rnd(0, H), r: rnd(1.5, 4), v: rnd(22, 55), sw: rnd(0.4, 1.2), ph: rnd(0, TAU), a: rnd(0.4, 0.9) });
    } else if (season === 'patrias') {
      const cols = ['#d52b1e', '#ffffff', '#2a5bd7', '#ffd23f'];
      for (let i = 0; i < 46; i++) P.push({ x: rnd(0, W), y: rnd(-H, H), w: rnd(6, 11), h: rnd(3, 6), v: rnd(40, 90), sw: rnd(0.6, 1.6), ph: rnd(0, TAU), rot: rnd(0, TAU), vr: rnd(-3, 3), c: cols[i % cols.length] });
    } else if (season === 'halloween') {
      for (let i = 0; i < 4; i++) P.push({ kind: 'ghost', x: rnd(80, W - 80), y: rnd(120, 440), s: rnd(46, 70), sp: rnd(0.25, 0.5), ph: rnd(0, TAU), dir: i % 2 ? 1 : -1 });
      for (let i = 0; i < 18; i++) P.push({ kind: 'ember', x: rnd(0, W), y: rnd(0, H), r: rnd(1.2, 2.8), v: rnd(10, 26), ph: rnd(0, TAU) });
    }
  }

  /* ---------- dibujo ---------- */
  function ghost(g, t) {
    const x = g.x + Math.sin(t * g.sp + g.ph) * 60 * g.dir;
    const y = g.y + Math.sin(t * g.sp * 1.7 + g.ph) * 18;
    const s = g.s;
    const r = s / 2;
    cx.save();
    cx.translate(x, y);
    cx.rotate(Math.sin(t * g.sp + g.ph) * 0.12);
    cx.fillStyle = night ? 'rgba(235,240,255,.34)' : 'rgba(255,255,255,.4)';
    cx.beginPath();
    cx.arc(0, 0, r, Math.PI, 0);
    cx.lineTo(r, s * 0.75);
    const bw = (2 * r) / 3;
    for (let i = 0; i < 3; i++) {
      const x1 = r - bw * i;
      const x2 = x1 - bw;
      cx.quadraticCurveTo((x1 + x2) / 2, s * (i % 2 ? 0.62 : 1), x2, s * 0.75);
    }
    cx.closePath();
    cx.fill();
    cx.fillStyle = 'rgba(32,24,48,.62)';
    [-0.34, 0.34].forEach((e) => {
      cx.beginPath();
      cx.ellipse(r * e, r * 0.02, r * 0.16, r * 0.26, 0, 0, TAU);
      cx.fill();
    });
    cx.beginPath();
    cx.ellipse(0, r * 0.55, r * 0.15, r * 0.17, 0, 0, TAU);
    cx.fill();
    cx.restore();
  }

  function bat(b, t) {
    const flap = Math.sin(t * 14 + b.ph);
    cx.save();
    cx.translate(b.x, b.y);
    cx.scale(b.dir, 1);
    cx.fillStyle = night ? 'rgba(20,16,30,.8)' : 'rgba(40,30,60,.75)';
    cx.beginPath();
    cx.ellipse(0, 0, 7, 5, 0, 0, TAU);
    cx.fill();
    [-1, 1].forEach((sd) => {
      cx.beginPath();
      cx.moveTo(sd * 5, -1);
      cx.quadraticCurveTo(sd * 22, -14 * flap - 4, sd * 32, 2 + 8 * flap);
      cx.quadraticCurveTo(sd * 22, 4, sd * 17, 7);
      cx.quadraticCurveTo(sd * 12, 2, sd * 7, 6);
      cx.closePath();
      cx.fill();
    });
    cx.restore();
  }

  function burst(x, y) {
    const hue = Math.floor(rnd(0, 360));
    const n = 38;
    for (let i = 0; i < n; i++) {
      const a = (i / n) * TAU + rnd(-0.05, 0.05);
      const v = rnd(70, 150);
      sparks.push({ x, y, vx: Math.cos(a) * v, vy: Math.sin(a) * v, life: 1, hue: hue + rnd(-14, 14) });
    }
  }

  function frame(ts) {
    if (!cv || !cv.isConnected) { raf = 0; return; }
    const dt = Math.min(0.05, last ? (ts - last) / 1000 : 0.016);
    last = ts;
    clock += dt;
    cx.clearRect(0, 0, W, H);

    if (season === 'navidad') {
      cx.fillStyle = '#fff';
      P.forEach((f) => {
        f.y += f.v * dt;
        if (f.y > H + 6) { f.y = -6; f.x = rnd(0, W); }
        cx.globalAlpha = f.a * 0.8;
        cx.beginPath();
        cx.arc(f.x + Math.sin(clock * f.sw + f.ph) * 18, f.y, f.r, 0, TAU);
        cx.fill();
      });
    } else if (season === 'patrias') {
      P.forEach((c) => {
        c.y += c.v * dt;
        c.rot += c.vr * dt;
        if (c.y > H + 10) { c.y = -10; c.x = rnd(0, W); }
        cx.save();
        cx.translate(c.x + Math.sin(clock * c.sw + c.ph) * 22, c.y);
        cx.rotate(c.rot);
        cx.globalAlpha = 0.85;
        cx.fillStyle = c.c;
        cx.fillRect(-c.w / 2, -c.h / 2, c.w, c.h);
        cx.restore();
      });
    } else if (season === 'halloween') {
      P.forEach((p) => {
        if (p.kind === 'ghost') ghost(p, clock);
        else {
          p.y -= p.v * dt;
          if (p.y < -4) { p.y = H + 4; p.x = rnd(0, W); }
          cx.globalAlpha = 0.5 + 0.4 * Math.sin(clock * 2 + p.ph);
          cx.fillStyle = '#ffb347';
          cx.beginPath();
          cx.arc(p.x + Math.sin(clock + p.ph) * 12, p.y, p.r, 0, TAU);
          cx.fill();
        }
      });
      cx.globalAlpha = 1;
      nextBat -= dt;
      if (nextBat <= 0) {
        const dir = Math.random() < 0.5 ? 1 : -1;
        bats.push({ x: dir > 0 ? -40 : W + 40, y: rnd(70, 300), dir, v: rnd(150, 230), ph: rnd(0, TAU), wob: rnd(0, TAU) });
        nextBat = rnd(5, 11);
      }
      bats = bats.filter((b) => b.x > -60 && b.x < W + 60);
      bats.forEach((b) => {
        b.x += b.dir * b.v * dt;
        b.y += Math.sin(clock * 3 + b.wob) * 40 * dt;
        bat(b, clock);
      });
    } else if (season === 'anonuevo') {
      nextRocket -= dt;
      if (nextRocket <= 0) {
        rockets.push({ x: rnd(150, W - 150), y: H, ty: rnd(110, 260), v: rnd(260, 340) });
        nextRocket = rnd(1.1, 2.2);
      }
      rockets = rockets.filter((r) => {
        r.y -= r.v * dt;
        if (r.y <= r.ty) { burst(r.x, r.y); return false; }
        cx.globalAlpha = 0.9;
        cx.fillStyle = '#ffe9a8';
        cx.fillRect(r.x - 1.2, r.y, 2.4, 10);
        return true;
      });
      cx.globalCompositeOperation = 'lighter';
      sparks = sparks.filter((s) => s.life > 0);
      sparks.forEach((s) => {
        s.x += s.vx * dt;
        s.y += s.vy * dt;
        s.vy += 70 * dt;
        s.vx *= 0.985;
        s.life -= dt * 0.7;
        cx.globalAlpha = Math.max(0, s.life);
        cx.fillStyle = `hsl(${s.hue},100%,62%)`;
        cx.beginPath();
        cx.arc(s.x, s.y, 2.4, 0, TAU);
        cx.fill();
      });
      cx.globalCompositeOperation = 'source-over';
    }
    cx.globalAlpha = 1;
    raf = reduced ? 0 : requestAnimationFrame(frame);
  }

  function start() {
    cancelAnimationFrame(raf);
    raf = 0;
    if (!cv || season === 'ninguna') { if (cx) cx.clearRect(0, 0, W, H); return; }
    last = 0;
    if (reduced) {
      // un cuadro quieto: avanzamos un rato para que no quede todo en el borde
      clock = 4;
      if (season === 'anonuevo') burst(W * 0.3, 200), burst(W * 0.72, 170);
      frame(performance.now());
    } else raf = requestAnimationFrame(frame);
  }

  TB.fx = {
    /** Engancha la capa a un <canvas> recién pintado y arranca (o detiene) la animación de la temporada. */
    attach(canvas, s, isNight) {
      cv = canvas;
      cx = canvas ? canvas.getContext('2d') : null;
      night = !!isNight;
      if (s !== season) { season = s; init(); }
      start();
    },
  };
})();
