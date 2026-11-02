/*
 * Vista previa en vivo de la radio (1024 x 600).
 * Recibe el estado del configurador y pinta la pantalla. Los datos (clima, canción, emisora) son de ejemplo.
 */
(function () {
  const TB = (window.TB = window.TB || {});
  const fmt = (n) => new Intl.NumberFormat('es-CL').format(Math.round(n));
  const esc = (s) => String(s).replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
  const clamp = (n, a, b) => Math.min(b, Math.max(a, n));
  const icon = (id, size) =>
    `<svg viewBox="0 0 24 24" width="${size || 28}" height="${size || 28}" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${TB.icons[id] || ''}</svg>`;

  TB.icons.casa = '<path d="M4 11l8-7 8 7M6.500 10v10h11V10"/>';

  const ZONES = [
    { limit: 50, name: 'Urbana' },
    { limit: 100, name: 'Ruta' },
    { limit: 120, name: 'Autopista' },
  ];
  const PRESETS = [88.9, 92.5, 96.1, 99.3, 101.7, 105.3];
  const reduced = window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  const live = { speed: 58, zone: 0, n: 0, maint: 0, freq: 96.1 };
  let scr = null;
  let host = null;
  let current = null;
  let uid = 0;

  /* ---------- helpers de estado ---------- */
  const themeOf = (s) => TB.themes.find((t) => t.id === s.style.theme) || TB.themes[0];
  const seasonOf = (s) => (s.style.season === 'auto' ? TB.seasonFor(new Date()) : s.style.season);
  function lum(hex) {
    const n = parseInt(hex.slice(1), 16);
    const ch = [(n >> 16) & 255, (n >> 8) & 255, n & 255].map((v) => {
      v /= 255;
      return v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
    });
    return 0.2126 * ch[0] + 0.7152 * ch[1] + 0.0722 * ch[2];
  }
  function tokens(state, night) {
    const th = themeOf(state);
    const t = Object.assign({}, night ? th.night : th.day);
    if (state.style.accent) {
      t.accent = state.style.accent;
      t.accentInk = lum(state.style.accent) > 0.45 ? '#141414' : '#ffffff';
    }
    return { th, t };
  }
  function limitFor(state) {
    const f = state.features;
    return f.speedAlert === 'manual' ? f.manualLimit : ZONES[live.zone].limit;
  }

  /** Lista de avisos de mantenimiento y documentos, según lo que el cliente activó. */
  function maintItems(state) {
    const f = state.features;
    const out = [];
    if (f.oilOn) {
      const left = f.lastOil + f.oilEvery - f.odometer;
      out.push({
        key: 'Aceite',
        text: left >= 0 ? `faltan ${fmt(left)} km` : `pasado por ${fmt(-left)} km`,
        pct: clamp(1 - left / f.oilEvery, 0, 1),
        warn: left <= 500,
      });
    }
    TBCatalog.maintenance.forEach((m) => {
      if (f.others.includes(m.id)) out.push({ key: m.name, text: `faltan ${fmt(m.every)} km`, pct: 0.02, warn: false });
    });
    if (f.docsOn) {
      const now = new Date().getMonth() + 1;
      const diff = (f.rtMonth - now + 12) % 12;
      out.push({ key: 'Revisión técnica', text: diff === 0 ? 'vence este mes' : `vence en ${TB.months[f.rtMonth - 1]}`, pct: clamp(1 - diff / 12, 0, 1), warn: diff <= 1 });
      const nextPermiso = now <= 3 ? 'marzo' : now <= 8 ? 'agosto' : 'marzo';
      out.push({ key: 'Permiso de circulación', text: `próximo pago en ${nextPermiso}`, pct: 0.3, warn: false });
      out.push({ key: 'Seguro obligatorio (SOAP)', text: 'vence el 31 de marzo', pct: 0.3, warn: false });
    }
    if (f.fuelOn) out.push({ key: 'Combustible', text: `~${fmt(f.tankL * f.kmPerL)} km con el tanque lleno`, pct: 1, warn: false });
    if (f.restrOn && f.restrDays.length) {
      const names = TB.weekdays.filter((d) => f.restrDays.includes(d.id)).map((d) => d.name.toLowerCase());
      out.push({ key: 'Restricción vehicular', text: names.join(', '), pct: 0, warn: false, noBar: true });
    }
    return out;
  }

  /* ---------- piezas de la pantalla de inicio ---------- */
  function pieces(state, ui) {
    const f = state.features;
    const car = state.car;
    const night = ui.night;
    const h = new Date().getHours();
    const tod = h < 12 ? 'Buenos días' : h < 20 ? 'Buenas tardes' : 'Buenas noches';
    const now = new Date();
    const time = now.toLocaleTimeString('es-CL', { hour: '2-digit', minute: '2-digit', hour12: false });
    const rawDate = now.toLocaleDateString('es-CL', { weekday: 'long', day: 'numeric', month: 'long' });
    const date = rawDate.charAt(0).toUpperCase() + rawDate.slice(1);
    const rawShort = now.toLocaleDateString('es-CL', { weekday: 'short', day: 'numeric', month: 'short' }).replace(/\./g, '');
    const dateShort = rawShort.charAt(0).toUpperCase() + rawShort.slice(1);
    const season = seasonOf(state);
    const carHtml = car.photo
      ? `<img class="car-photo" src="${car.photo}" alt="Tu auto">`
      : TB.carSVG(car.type, car.color, night, ++uid);
    const limit = limitFor(state);
    const items = maintItems(state);
    return {
      hello: `${esc(car.greeting)}, ${car.name ? esc(car.name) : 'amigo'}`,
      sub: (TB.seasonLabel[season] || tod) + (f.odometer ? ` · ${fmt(f.odometer)} km` : ''),
      car: carHtml,
      time,
      date,
      dateShort,
      speed: f.speedShow ? `<div class="spd"><b data-speed>${Math.round(live.speed)}</b><span>km/h</span></div>` : '',
      weather: `<div class="weather"><span class="wx">${icon('lluvia', 44)}</span><b>14°</b><span class="wd">Lluvioso<small>Sensación 13° · 82%</small></span></div>`,
      radio: `<div class="radio-mini"><button class="rbtn" data-go="radio" aria-label="Anterior">${icon('prev', 22)}</button><div><small>FM</small><b>${live.freq.toFixed(1)}</b><i>MHz</i></div><button class="rbtn" data-go="radio" aria-label="Siguiente">${icon('next', 22)}</button></div>`,
      music: `<div class="music"><span class="src">Bluetooth</span><div class="disc"></div><b>Camino a casa</b><span>Artista de ejemplo</span><div class="ctl"><button class="rbtn" aria-label="Anterior">${icon('prev', 24)}</button><button class="rbtn play" aria-label="Reproducir">${icon('play', 26)}</button><button class="rbtn" aria-label="Siguiente">${icon('next', 24)}</button></div></div>`,
      limitSign: `<div class="limit"><div class="sign"><b data-limit>${limit}</b></div><small>${f.speedAlert === 'manual' ? 'Límite fijo' : 'Límite de la vía'}</small></div>`,
      maint: items.length ? `<div class="maint${items[0].warn ? ' warn' : ''}" data-maint><b>${esc(items[0].key)}</b><span>${esc(items[0].text)}</span></div>` : '',
      hasMaint: items.length > 0,
    };
  }

  const LAYOUTS = {
    tarjetas: (p) => `
      <section class="card greet"><div class="greet-txt"><h2>${p.hello}</h2><p>${esc(p.sub)}</p></div><div class="car">${p.car}</div>${p.weather}</section>
      <section class="card clock"><div class="clk"><b data-clock>${p.time}</b><span>${esc(p.dateShort)}</span></div>${p.speed}</section>
      <section class="card radio">${p.radio}</section>
      <section class="card music-card">${p.music}</section>`,
    centro: (p) => `
      <section class="card clock"><div class="clk"><b data-clock>${p.time}</b><span>${esc(p.dateShort)}</span></div>${p.speed}</section>
      <section class="hero"><div class="greet-txt"><h2>${p.hello}</h2><p>${esc(p.sub)}</p></div><div class="car">${p.car}</div></section>
      <section class="card radio">${p.radio}</section>
      <section class="card wx-card">${p.weather}</section>
      <section class="card music-card">${p.music}</section>`,
    manejo: (p) => `
      <section class="speedbig"><b data-speed>${Math.round(live.speed)}</b><span>km/h</span><div class="car mini-car">${p.car}</div></section>
      ${p.limitSign}
      <section class="card nav"><span class="turn">${icon('giro', 46)}</span><div><b>En 300 m, gira a la derecha</b><small>Av. Costanera · ejemplo de indicación</small></div></section>
      <section class="card radio">${p.radio}</section>`,
    minimal: (p) => `
      <section class="bigclock"><b data-clock>${p.time}</b><span>${esc(p.date)}</span><p>${p.hello}</p></section>
      <section class="pills"><div class="card pill">${p.weather}</div><div class="card pill">${p.radio}</div></section>`,
  };

  /* ---------- otras pantallas ---------- */
  const SCREENS = {
    radio: () => {
      const pos = ((live.freq - 88) / 20) * 100;
      let ticks = '';
      for (let i = 0; i <= 40; i++) ticks += `<u class="${i % 5 === 0 ? 'big' : ''}"></u>`;
      return `<div class="pane radio-pane">
        <div class="r-main"><small>FM</small><b>${live.freq.toFixed(1)}</b><span>MHz</span></div>
        <div class="dial">${ticks}<i style="left:${pos}%"></i></div>
        <div class="presets">${PRESETS.map((f) => `<button class="card preset${f === live.freq ? ' on' : ''}" data-freq-set="${f}">${f.toFixed(1)}</button>`).join('')}</div>
      </div>`;
    },
    bluetooth: (state) => `<div class="pane bt-pane">
      <section class="card phone"><span class="ph-ic">${icon('telefono', 54)}</span><b>Teléfono${state.car.name ? ' de ' + esc(state.car.name) : ''}</b><span class="ok">Conectado</span><small>Batería 78%</small></section>
      <section class="card keypad">${['1', '2', '3', '4', '5', '6', '7', '8', '9', '*', '0', '#'].map((k) => `<button>${k}</button>`).join('')}</section>
    </div>`,
    ecualizador: () => {
      const bands = [['60', 52], ['150', 64], ['400', 72], ['1k', 58], ['2,4k', 68], ['6k', 76], ['15k', 60]];
      return `<div class="pane eq-pane">
        <div class="eq">${bands.map(([hz, h]) => `<div class="band"><div class="track"><i style="height:${h}%"></i></div><small>${hz}</small></div>`).join('')}</div>
        <div class="chips"><span class="chip on">Plano</span><span class="chip">Graves</span><span class="chip">Voces</span><span class="chip">Rock</span></div>
      </div>`;
    },
    mantenimiento: (state) => {
      const items = maintItems(state);
      if (!items.length) return `<div class="pane"><section class="card empty"><b>Sin avisos activados</b><span>Activa aceite, documentos o combustible en el paso 4.</span></section></div>`;
      return `<div class="pane mt-pane">${items
        .slice(0, 7)
        .map(
          (i) => `<div class="card mrow${i.warn ? ' warn' : ''}"><b>${esc(i.key)}</b><span>${esc(i.text)}</span>${i.noBar ? '' : `<div class="bar"><i style="width:${Math.round(i.pct * 100)}%"></i></div>`}</div>`
        )
        .join('')}</div>`;
    },
  };

  function dock(state, ui) {
    const items = [{ go: 'inicio', ic: 'casa', t: 'Inicio' }, { go: 'radio', ic: 'radio', t: 'Radio' }, { go: 'bluetooth', ic: 'bluetooth', t: 'Bluetooth' }];
    state.style.apps.forEach((id) => {
      const a = TB.apps.find((x) => x.id === id);
      if (a) items.push({ ic: id, t: id === 'telefono' ? 'Teléfono' : a.name });
    });
    items.push({ go: 'mantenimiento', ic: 'check', t: 'Mantención' });
    return `<nav class="dock">${items
      .map((i) => `<button class="dk${i.go && i.go === ui.screen ? ' on' : ''}" ${i.go ? `data-go="${i.go}"` : ''}><span class="ic">${icon(i.ic, 30)}</span><small>${esc(i.t)}</small></button>`)
      .join('')}</nav>`;
  }

  /* ---------- render ---------- */
  function render(state, ui) {
    if (!scr) return;
    current = { state, ui };
    const { th, t } = tokens(state, ui.night);
    const season = seasonOf(state);
    const sceneId = state.style.scene === 'tema' ? th.scene : state.style.scene;
    const vars = {
      '--bg1': t.bg1, '--bg2': t.bg2, '--ink': t.ink, '--ink2': t.ink2, '--card': t.card, '--line': t.line,
      '--accent': t.accent, '--accent-ink': t.accentInk, '--scene-rgb': t.sceneRgb,
      '--radius': th.radius + 'px', '--blur': th.blur + 'px', '--shadow': th.shadow,
    };
    Object.keys(vars).forEach((k) => scr.style.setProperty(k, vars[k]));
    scr.dataset.layout = state.style.layout;
    scr.dataset.night = ui.night ? '1' : '0';
    scr.dataset.screen = ui.screen;

    let body;
    if (ui.screen === 'inicio') {
      const p = pieces(state, ui);
      body = `<main class="home l-${state.style.layout}${p.hasMaint ? ' has-maint' : ''}">${LAYOUTS[state.style.layout](p)}${p.maint}</main>`;
    } else {
      body = `<main class="home screen-${ui.screen}">${SCREENS[ui.screen](state)}</main>`;
    }
    const customBg = state.style.scene === 'color' ? '' : TB.scene(sceneId);
    scr.innerHTML =
      `<svg class="scene" viewBox="0 0 1024 600" preserveAspectRatio="none" aria-hidden="true">${customBg}</svg>` +
      `<header class="status"><span></span><b data-clock>${new Date().toLocaleTimeString('es-CL', { hour: '2-digit', minute: '2-digit', hour12: false })}</b></header>` +
      body +
      `<svg class="decor" viewBox="0 0 1024 600" aria-hidden="true">${TB.decor(season, ui.night)}</svg>` +
      dock(state, ui) +
      `<div class="alert" hidden></div>`;
    tick();
  }

  /* ---------- animación de datos de ejemplo ---------- */
  function tick() {
    if (!scr || !current) return;
    const { state } = current;
    live.n++;
    if (!reduced && live.n % 12 === 0) live.zone = (live.zone + 1) % ZONES.length;
    const limit = limitFor(state);
    const target = reduced ? limit + 8 : limit + 7 * Math.sin(live.n / 3.5) - 1;
    live.speed += (target - live.speed) * 0.4;
    const v = Math.max(0, Math.round(live.speed));

    scr.querySelectorAll('[data-speed]').forEach((el) => (el.textContent = v));
    scr.querySelectorAll('[data-limit]').forEach((el) => (el.textContent = limit));
    const time = new Date().toLocaleTimeString('es-CL', { hour: '2-digit', minute: '2-digit', hour12: false });
    scr.querySelectorAll('[data-clock]').forEach((el) => (el.textContent = time));

    const alert = scr.querySelector('.alert');
    if (alert) {
      const show = state.features.speedAlert !== 'off' && v > limit;
      alert.hidden = !show;
      if (show) alert.textContent = `Baja la velocidad · ${v} km/h · límite ${limit}`;
    }

    if (!reduced && live.n % 4 === 0) {
      const items = maintItems(state);
      const el = scr.querySelector('[data-maint]');
      if (el && items.length > 1) {
        live.maint = (live.maint + 1) % items.length;
        const it = items[live.maint];
        el.classList.toggle('warn', !!it.warn);
        el.innerHTML = `<b>${esc(it.key)}</b><span>${esc(it.text)}</span>`;
      }
    }
  }

  /* ---------- montaje y escala ---------- */
  function mount(el) {
    host = el;
    scr = document.createElement('div');
    scr.className = 'scr';
    host.appendChild(scr);
    const fit = () => {
      const s = host.clientWidth / 1024;
      scr.style.transform = `scale(${s})`;
      host.style.height = Math.round(600 * s) + 'px';
    };
    fit();
    if ('ResizeObserver' in window) new ResizeObserver(fit).observe(host);
    else window.addEventListener('resize', fit);

    host.addEventListener('click', (e) => {
      const fr = e.target.closest('[data-freq-set]');
      if (fr) {
        live.freq = parseFloat(fr.dataset.freqSet);
        if (current) render(current.state, current.ui);
        return;
      }
      const go = e.target.closest('[data-go]');
      if (go && TB.preview.onGo) TB.preview.onGo(go.dataset.go);
    });
    setInterval(() => {
      if (!document.hidden) tick();
    }, 1000);
  }

  TB.preview = { mount, render, onGo: null, maintItems };
})();
