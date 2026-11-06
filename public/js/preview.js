/*
 * Vista previa en vivo de la radio (1024 x 600).
 * Recibe el estado del configurador y pinta la pantalla. Los datos (clima, canción, emisora) son de ejemplo.
 */
(function () {
  const TB = (window.TB = window.TB || {});
  const fmt = (n) => new Intl.NumberFormat('es-CL').format(Math.round(n));
  const esc = (s) => String(s).replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
  const clamp = (n, a, b) => Math.min(b, Math.max(a, n));
  const MI = 0.621371;
  const GAL = 3.78541;
  const imp = (state) => !!(state && state.features && state.features.miles);
  const dist = (km, state) => (imp(state) ? Math.round(km * MI) : Math.round(km));
  const du = (state) => (imp(state) ? 'mi' : 'km');
  const su = (state) => (imp(state) ? 'mph' : 'km/h');
  const icon = (id, size) =>
    `<svg viewBox="0 0 24 24" width="${size || 28}" height="${size || 28}" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${TB.icons[id] || ''}</svg>`;

  TB.icons.pausa = '<path d="M8 5v14M16 5v14" stroke-width="3.2"/>';
  TB.icons.casa = '<path d="M4 11l8-7 8 7M6.500 10v10h11V10"/>';

  const ZONES = [
    { limit: 50, name: 'Urbana' },
    { limit: 100, name: 'Ruta' },
    { limit: 120, name: 'Autopista' },
  ];
  const PRESETS = [88.9, 92.5, 96.1, 99.3, 101.7, 105.3];
  const reduced = window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  const live = { speed: 58, zone: 0, n: 0, maint: 0, freq: 96.1, zoneMsg: null, zoneUntil: 0 };
  let scr = null;
  let host = null;
  let current = null;
  let uid = 0;

  /* ---------- modo app ----------
   * Dentro de la app de la radio (js/launcher.js) `TB.preview.app` trae datos reales: velocidad por GPS, clima y
   * acciones para abrir apps. Sin él (en el sitio) todo es la demostración de siempre. */
  const appMode = () => !!TB.preview.app;
  const WX = (c) => (c >= 51 && c <= 67) || (c >= 80 && c <= 99) ? 'lluvia' : 'sol';
  const openAttr = (kind) => (appMode() ? `data-open="${kind}"` : `data-go="${kind === 'bluetooth' ? 'bluetooth' : 'radio'}"`);

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
  /** En la radio no conocemos el límite de cada calle: solo hay límite si el cliente fijó uno. */
  const appLimit = (state) => (state.features.speedAlert === 'manual' ? state.features.manualLimit : null);
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
        text: left >= 0 ? `faltan ${fmt(dist(left, state))} ${du(state)}` : `pasado por ${fmt(dist(-left, state))} ${du(state)}`,
        pct: clamp(1 - left / f.oilEvery, 0, 1),
        warn: left <= 500,
      });
    }
    TBCatalog.maintenance.forEach((m) => {
      if (f.others.includes(m.id)) out.push({ key: m.name, text: `faltan ${fmt(dist(m.every, state))} ${du(state)}`, pct: 0.02, warn: false });
    });
    if (f.docsOn) {
      const now = new Date().getMonth() + 1;
      const diff = (f.rtMonth - now + 12) % 12;
      out.push({ key: 'Revisión técnica', text: diff === 0 ? 'vence este mes' : `vence en ${TB.months[f.rtMonth - 1]}`, pct: clamp(1 - diff / 12, 0, 1), warn: diff <= 1 });
      const nextPermiso = now <= 3 ? 'marzo' : now <= 8 ? 'agosto' : 'marzo';
      out.push({ key: 'Permiso de circulación', text: `próximo pago en ${nextPermiso}`, pct: 0.3, warn: false });
      out.push({ key: 'Seguro obligatorio (SOAP)', text: 'vence el 31 de marzo', pct: 0.3, warn: false });
    }
    if (f.fuelOn) out.push({ key: 'Combustible', text: `~${fmt(dist(f.tankL * f.kmPerL, state))} ${du(state)} con el tanque lleno${f.gallons ? ` (${(f.tankL / GAL).toFixed(1).replace('.', ',')} gal)` : ''}`, pct: 1, warn: false });
    if (f.restrOn && f.restrCity === 'rm' && f.restrDays.length) {
      const names = TB.weekdays.filter((d) => f.restrDays.includes(d.id)).map((d) => d.name.toLowerCase());
      const r = TBCatalog.restriction;
      const m = new Date().getMonth() + 1;
      const inSeason = r.season.months.includes(m);
      const todayId = ['dom', 'lun', 'mar', 'mie', 'jue', 'vie', 'sab'][new Date().getDay()];
      const today = inSeason && f.restrDays.includes(todayId);
      out.push({
        key: 'Restricción vehicular',
        text: today ? `HOY tienes restricción (${names.join(', ')})` : inSeason ? `te toca el ${names.join(' y ')}` : `te toca el ${names.join(' y ')} · rige desde el ${r.season.from}`,
        pct: 0, warn: today, noBar: true,
      });
    }
    return out;
  }

  /** Tarjeta del clima con datos reales (Open-Meteo); sin datos todavía, lo dice. */
  function appWeather(state) {
    const w = TB.preview.app.weather;
    if (!w) return `<div class="weather"><span class="wx">${icon('sol', 44)}</span><b>—</b><span class="wd">Clima<small>sin datos aún</small></span></div>`;
    const t = (v) => Math.round(v) + '°';
    return `<div class="weather"><span class="wx">${icon(WX(w.code), 44)}</span><b>${t(w.temp)}</b><span class="wd">${esc(w.text)}<small>Sensación ${t(w.feels)} · ${Math.round(w.humidity)}%</small></span></div>`;
  }


  /* ---------- reproductor (música en vivo desde la radio) ---------- */
  const DEMO_MUSIC = { access: true, active: true, playing: true, title: 'Camino a casa', artist: 'Artista de ejemplo', art: '', src: 'EJEMPLO', dur: 218000, pos: 84000, at: Date.now() };
  function playerInner() {
    const app = TB.preview.app;
    const m = app ? app.music : DEMO_MUSIC;
    const ctl = (playing) => `<div class="ctl"><button class="rbtn" data-media="prev" aria-label="Anterior">${icon('prev', 24)}</button><button class="rbtn play" data-media="toggle" aria-label="${playing ? 'Pausar' : 'Reproducir'}">${icon(playing ? 'pausa' : 'play', 26)}</button><button class="rbtn" data-media="next" aria-label="Siguiente">${icon('next', 24)}</button></div>`;
    if (m && m.active) {
      const art = m.art ? `<img class="art" src="${m.art}" alt="">` : `<div class="art disc"></div>`;
      return `<span class="src">${m.playing ? 'Sonando' : 'En pausa'}</span>${art}<b>${esc(m.title || 'Sin título')}</b><span>${esc(m.artist || '')}</span>${ctl(m.playing)}`;
    }
    if (m && m.access === false) {
      return `<span class="src">Música</span><div class="art disc"></div><b>Ver lo que suena</b><span>Da permiso una vez para mostrar tu canción</span><div class="ctl"><button class="rbtn play wide" data-open="musicaccess" aria-label="Dar permiso">${icon('check', 26)}</button></div>`;
    }
    return `<span class="src">Música</span><div class="art disc"></div><b>Tu música</b><span>Abre tu app favorita</span><div class="ctl"><button class="rbtn play" data-open="music" aria-label="Abrir música">${icon('play', 26)}</button></div>`;
  }
  function updateMusic() {
    if (!scr) return;
    scr.querySelectorAll('.player').forEach((el) => { el.innerHTML = playerInner(); });
    scr.querySelectorAll('.music-card .music').forEach((el) => { el.innerHTML = playerInner(); });
    scr.querySelectorAll('.cm-player').forEach((el) => { el.innerHTML = consolaPlayerInner(); });
  }


  /* ---------- mapa en vivo (solo en la radio, con GPS) ---------- */
  TB.icons.nav = '<path d="M12 3l7 17-7-4-7 4z"/>';
  TB.icons.cel = '<rect x="7" y="2.500" width="10" height="19" rx="2.200"/><path d="M11 18.500h2"/>';
  const MAP_Z = 15;
  const MAP_PLACEHOLDER = '<svg class="mapsvg" viewBox="0 0 300 160" preserveAspectRatio="xMidYMid slice" aria-hidden="true"><g fill="none" stroke="currentColor" stroke-opacity=".14" stroke-width="9" stroke-linecap="round"><path d="M-10 120 L120 108 L170 56 L310 66"/><path d="M60 -10 L82 90 L70 170"/><path d="M205 -10 L192 70 L232 170"/><path d="M-10 40 L300 24"/></g><g transform="translate(150 84)"><circle r="15" fill="var(--accent)" opacity=".22"/><path d="M0 -9 L7 8 L0 4 L-7 8z" fill="var(--accent)"/></g></svg>';
  function mapInner(night) {
    const app = TB.preview.app;
    const loc = app && app.loc;
    if (!loc) {
      return MAP_PLACEHOLDER;
    }
    const n = Math.pow(2, MAP_Z);
    const x = ((loc.lon + 180) / 360) * n;
    const lr = (loc.lat * Math.PI) / 180;
    const y = ((1 - Math.log(Math.tan(lr) + 1 / Math.cos(lr)) / Math.PI) / 2) * n;
    const tx = Math.floor(x);
    const ty = Math.floor(y);
    const fx = Math.round((x - tx) * 256);
    const fy = Math.round((y - ty) * 256);
    const style = night ? 'dark_all' : 'light_all';
    let tiles = '';
    for (let dy = -1; dy <= 1; dy++) {
      for (let dx = -1; dx <= 1; dx++) {
        const wx = (((tx + dx) % n) + n) % n;
        tiles += `<img alt="" draggable="false" onerror="this.style.visibility='hidden'" style="left:${(dx + 1) * 256}px;top:${(dy + 1) * 256}px" src="https://a.basemaps.cartocdn.com/${style}/${MAP_Z}/${wx}/${ty + dy}.png">`;
      }
    }
    return `${MAP_PLACEHOLDER}<div class="map-tiles" style="left:calc(50% - ${256 + fx}px);top:calc(50% - ${256 + fy}px)">${tiles}</div><div class="map-me" style="transform:translate(-50%,-50%) rotate(${Math.round(loc.bearing || 0)}deg)">${icon('nav', 22)}</div><span class="map-credit">© OpenStreetMap · CARTO</span>`;
  }
  function updateMap() {
    if (!scr || !current) return;
    scr.querySelectorAll('.map-view').forEach((el) => { el.innerHTML = mapInner(current.ui.night); });
  }

  /* ---------- reproductor del diseño Consola: carátula con anillo de progreso ---------- */
  const fmtT = (ms) => { const s = Math.max(0, Math.floor(ms / 1000)); return Math.floor(s / 60) + ':' + String(s % 60).padStart(2, '0'); };
  function consolaPlayerInner() {
    const app = TB.preview.app;
    const m = app ? app.music : DEMO_MUSIC;
    const ring = (art) => `<div class="cm-ring"><svg viewBox="0 0 120 120" aria-hidden="true"><circle cx="60" cy="60" r="55" class="trk"/><circle cx="60" cy="60" r="55" class="val" data-mring pathLength="100" stroke-dasharray="0 100" transform="rotate(-90 60 60)"/></svg>${art}</div>`;
    const ctl = (playing) => `<div class="ctl"><button class="rbtn" data-media="prev" aria-label="Anterior">${icon('prev', 24)}</button><button class="rbtn play" data-media="toggle" aria-label="${playing ? 'Pausar' : 'Reproducir'}">${icon(playing ? 'pausa' : 'play', 26)}</button><button class="rbtn" data-media="next" aria-label="Siguiente">${icon('next', 24)}</button></div>`;
    if (m && m.active) {
      const art = m.art ? `<img class="art" src="${m.art}" alt="">` : '<div class="art disc"></div>';
      return `<div class="cm-head"><span>AUDIO</span><span class="cm-src">${esc(m.src || (m.playing ? 'SONANDO' : 'EN PAUSA'))}</span></div>${ring(art)}<b>${esc(m.title || 'Sin título')}</b><span class="cm-art">${esc(m.artist || '')}</span><small data-mtime>${m.dur > 0 ? '0:00 / ' + fmtT(m.dur) : ''}</small>${ctl(m.playing)}`;
    }
    if (m && m.access === false) {
      return `<div class="cm-head"><span>AUDIO</span><span class="cm-src"></span></div>${ring('<div class="art disc"></div>')}<b>Ver lo que suena</b><span class="cm-art">Da permiso una vez para mostrar tu canción</span><small></small><div class="ctl"><button class="rbtn play wide" data-open="musicaccess" aria-label="Dar permiso">${icon('check', 26)}</button></div>`;
    }
    return `<div class="cm-head"><span>AUDIO</span><span class="cm-src"></span></div>${ring('<div class="art disc"></div>')}<b>Tu música</b><span class="cm-art">Abre tu app favorita</span><small></small><div class="ctl"><button class="rbtn play" data-open="music" aria-label="Abrir música">${icon('play', 26)}</button></div>`;
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
      ? `<img class="car-photo${car.photoCut ? ' cut' : ''}${night && car.photoCut ? ' night' : ''}" src="${night && car.photoCut && car.photoNight ? car.photoNight : car.photo}" alt="Tu auto">`
      : TB.carSVG(car.type, car.color, night, ++uid);
    const limit = appMode() ? appLimit(state) : limitFor(state);
    const spd = dist(appMode() ? TB.preview.app.speed || 0 : live.speed, state);
    const items = maintItems(state);
    return {
      hello: `${esc(car.greeting)}, ${car.name ? esc(car.name) : 'amigo'}`,
      sub: (car.phrase ? car.phrase : TB.seasonLabel[season] || tod) + (f.odometer ? ` · ${fmt(dist(f.odometer, state))} ${du(state)}` : ''),
      car: carHtml,
      time,
      date,
      dateShort,
      speed: f.speedShow ? `<div class="spd"><b data-speed>${spd}</b><span>${su(state)}</span></div>` : '',
      weather: appMode() ? appWeather(state) : `<div class="weather"><span class="wx">${icon('lluvia', 44)}</span><b>14°</b><span class="wd">Lluvioso<small>Sensación 13° · 82%</small></span></div>`,
      radio: appMode()
        ? `<div class="radio-mini"><div><small>Radio</small><b>FM</b><i>Toca para abrir</i></div><button class="rbtn play" data-open="radio" aria-label="Abrir la radio">${icon('play', 26)}</button></div>`
        : `<div class="radio-mini"><button class="rbtn" data-go="radio" aria-label="Anterior">${icon('prev', 22)}</button><div><small>FM</small><b>${live.freq.toFixed(1)}</b><i>MHz</i></div><button class="rbtn" data-go="radio" aria-label="Siguiente">${icon('next', 22)}</button></div>`,
      player: `<div class="music player">${playerInner()}</div>`,
      vehName: car.name ? car.name : 'Mi vehículo',
      wxMini: appMode()
        ? (TB.preview.app.weather ? `<span class="c-wx">${icon(WX(TB.preview.app.weather.code), 28)}<b>${Math.round(TB.preview.app.weather.temp)}°</b></span>` : '<span class="c-wx">' + icon('sol', 28) + '<b>—</b></span>')
        : `<span class="c-wx">${icon('lluvia', 28)}<b>14°</b></span>`,
      speedBlock: f.speedShow ? `<div class="c-speed"><div><small>Velocidad</small><div class="c-spd"><b data-speed>${spd}</b><span>${su(state)}</span></div></div><div class="c-lim"><small>Límite</small><b data-limit>${limit == null ? '—' : dist(limit, state)}</b></div></div>` : '',
      mapLive: mapInner(night),
      consolaPlayer: consolaPlayerInner(),
      music: appMode()
        ? `<div class="music">${playerInner()}</div>`
        : `<div class="music"><span class="src">Bluetooth</span><div class="disc"></div><b>Camino a casa</b><span>Artista de ejemplo</span><div class="ctl"><button class="rbtn" aria-label="Anterior">${icon('prev', 24)}</button><button class="rbtn play" aria-label="Reproducir">${icon('play', 26)}</button><button class="rbtn" aria-label="Siguiente">${icon('next', 24)}</button></div></div>`,
      limitSign: `<div class="limit"><div class="sign"><b data-limit>${limit == null ? '—' : dist(limit, state)}</b></div><small>${f.speedAlert === 'manual' ? 'Límite fijo' : 'Límite de la vía'}</small></div>`,
      routeCard: `<div class="route-card"><div><small>Altitud</small><b>2.850 m</b></div><div class="compass" aria-hidden="true"><svg viewBox="0 0 60 60" width="64" height="64"><circle cx="30" cy="30" r="27" fill="none" stroke="currentColor" stroke-width="2" opacity=".5"/><text x="30" y="11" text-anchor="middle" font-size="9" fill="currentColor">N</text><path d="M30 12 L36 32 L30 28 L24 32Z" fill="var(--accent)" transform="rotate(42 30 30)"/><path d="M30 48 L24 32 L30 36 L36 32Z" fill="currentColor" opacity=".45" transform="rotate(42 30 30)"/></svg></div><div><small>Rumbo</small><b>NE 42°</b></div></div>`,
      mapCard: `<svg class="mapsvg" viewBox="0 0 300 160" preserveAspectRatio="xMidYMid slice" aria-hidden="true"><rect width="300" height="160" fill="rgba(255,255,255,.06)"/><g fill="none" stroke="rgba(255,255,255,.22)" stroke-width="6" stroke-linecap="round"><path d="M-10 120 L120 110 L170 60 L310 70"/><path d="M60 -10 L80 90 L70 170"/><path d="M200 -10 L190 70 L230 170"/></g><path d="M-10 120 L120 110 L170 60 L260 66" fill="none" stroke="var(--accent)" stroke-width="5" stroke-linecap="round"/><circle cx="170" cy="60" r="9" fill="var(--accent)"/><circle cx="170" cy="60" r="3.5" fill="var(--accent-ink)"/></svg><div class="map-info"><b>Ruta a casa</b><small>18 min · ${imp(state) ? '7,7 mi' : '12,4 km'}</small></div>`,
      menu: `<nav class="side-menu card">${[['inicio', 'casa', 'Inicio'], ['radio', 'radio', 'Radio'], ['bluetooth', 'bluetooth', 'Bluetooth'], ['mantenimiento', 'check', 'Mantención']].map(([go, ic, t], i) => `<button class="sm${i === 0 ? ' on' : ''}" ${go === 'radio' || go === 'bluetooth' ? openAttr(go) : `data-go="${go}"`}>${icon(ic, 28)}<span>${t}</span></button>`).join('')}</nav>`,
      plate: car.plate ? `<span class="plate">${esc(car.plate)}</span>` : '',
      maint: items.length ? `<div class="maint${items[0].warn ? ' warn' : ''}" data-maint><b>${esc(items[0].key)}</b><span>${esc(items[0].text)}</span></div>` : '',
      hasMaint: items.length > 0,
      spd,
      su: su(state),
    };
  }

  const LAYOUTS = {
    consola: (p) => `
      <header class="c-top"><div class="c-clock"><b data-clock>${p.time}</b><span>${esc(p.date)}</span></div><div class="c-right">${p.wxMini}<span class="c-greet">${p.hello}</span></div></header>
      <section class="card c-veh"><div class="c-vhead"><h3>${esc(p.vehName)}</h3>${p.plate}</div><div class="car c-car">${p.car}</div>${p.speedBlock}</section>
      <section class="card c-map"><div class="map-view">${p.mapLive}</div><span class="c-maplabel">${icon('maps', 18)} Mapa en vivo</span>${appMode() ? '<button type="button" class="map-save" data-open="mapsave"><span data-mapsave>Guardar mapa</span></button>' : ''}<div class="c-proj"><button type="button" class="pj on" data-open="androidauto"><span class="pj-ic">${icon('nav', 26)}</span><span><b>Android Auto</b><small>USB o tu pantalla</small></span></button><button type="button" class="pj" data-open="carplay"><span class="pj-ic">${icon('cel', 26)}</span><span><b>CarPlay</b><small>Pantalla o adaptador</small></span></button></div></section>
      <section class="card c-music"><div class="music cm-player">${p.consolaPlayer}</div></section>`,
    estelar: (p) => `
      <section class="st-left"><div class="st-clock"><b data-clock>${p.time}</b><span>${esc(p.dateShort)}</span></div>
        <div class="st-ring"><svg viewBox="0 0 120 120" aria-hidden="true"><circle cx="60" cy="60" r="52" class="trk"/><circle cx="60" cy="60" r="52" class="val" data-ring pathLength="100" stroke-dasharray="0 100"/></svg><div class="st-spd"><b data-speed>${p.spd}</b><span>${p.su}</span></div></div></section>
      <section class="st-stage"><div class="st-hello"><h2>${p.hello}</h2><p>${esc(p.sub)}</p></div><div class="st-streaks" aria-hidden="true"></div><div class="st-glow" aria-hidden="true"></div><div class="car st-car">${p.car}</div></section>
      <section class="st-right"><div class="card music-card st-player">${p.player}</div><div class="card st-wx">${p.weather}</div></section>`,
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
      <section class="speedbig"><b data-speed>${p.spd}</b><span>${p.su}</span><div class="car mini-car">${p.car}</div></section>
      ${p.limitSign}
      <section class="card nav"><span class="turn">${icon('giro', 46)}</span><div><b>En 300 m, gira a la derecha</b><small>Av. Costanera · ejemplo de indicación</small></div></section>
      <section class="card radio">${p.radio}</section>`,
    tablero: (p) => `
      <section class="card t-left"><div class="t-speed"><b data-speed>${p.spd}</b><span>${p.su}</span></div><div class="car">${p.car}${p.plate}</div><div class="t-clock"><b data-clock>${p.time}</b><span>${esc(p.dateShort)}</span></div></section>
      <section class="card t-route">${p.routeCard}</section>
      <section class="card t-map">${p.mapCard}</section>`,
    lateral: (p) => `
      ${p.menu}
      <section class="card clock"><div class="clk"><b data-clock>${p.time}</b><span>${esc(p.dateShort)}</span></div>${p.speed}</section>
      <section class="card wx-card">${p.weather}</section>
      <section class="card music-card">${p.music}<small class="swipe">‹ desliza para cambiar de canción ›</small></section>`,
    todoterreno: (p) => `
      <section class="tt-top"><div class="tt-speed"><b data-speed>${p.spd}</b><span>${p.su}</span></div><div class="tt-clock"><b data-clock>${p.time}</b><span>${esc(p.dateShort)}</span></div></section>
      <section class="tt-car"><div class="car">${p.car}</div></section>
      <section class="tt-info card">${p.routeCard}</section>`,
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
    const consola = state.style.layout === 'consola';
    const items = consola
      ? [].concat(ui.screen !== 'inicio' ? [{ go: 'inicio', ic: 'casa', t: 'Inicio' }] : [], [{ go: 'radio', open: 'radio', ic: 'radio', t: 'Radio' }, { go: 'bluetooth', open: 'bluetooth', ic: 'bluetooth', t: 'Bluetooth' }, { open: 'camera', ic: 'camara', t: 'Cámara' }])
      : [{ go: 'inicio', ic: 'casa', t: 'Inicio' }, { go: 'radio', open: 'radio', ic: 'radio', t: 'Radio' }, { go: 'bluetooth', open: 'bluetooth', ic: 'bluetooth', t: 'Bluetooth' }];
    state.style.apps.forEach((id) => {
      const a = TB.apps.find((x) => x.id === id);
      if (a) items.push({ app: id, ic: id, t: id === 'telefono' ? 'Teléfono' : a.name });
    });
    items.push({ go: 'mantenimiento', ic: 'check', t: 'Mantención' });
    if (consola) items.push({ open: 'drawer', ic: 'todas', t: 'Apps' });
    return `<nav class="dock">${items
      .map((i) => `<button class="dk${i.go && i.go === ui.screen ? ' on' : ''}" ${i.open && appMode() ? `data-open="${i.open}"` : i.go ? `data-go="${i.go}"` : ''} ${i.app ? `data-app="${i.app}"` : ''}><span class="ic">${icon(i.ic, 30)}</span><small>${esc(i.t)}</small></button>`)
      .join('')}</nav>`;
  }

  function sceneTime(state) {
    const s = state.style.sceneTime || 'auto';
    if (s !== 'auto') return s;
    const h = new Date().getHours();
    return h >= 6 && h < 9 ? 'amanecer' : h >= 9 && h < 18 ? 'dia' : h >= 18 && h < 21 ? 'atardecer' : 'noche';
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
      '--orb1': t.orb1 || t.accent, '--orb2': t.orb2 || t.bg1, '--orb3': t.orb3 || t.bg1,
      '--radius': th.radius + 'px', '--blur': th.blur + 'px', '--shadow': th.shadow,
    };
    Object.keys(vars).forEach((k) => scr.style.setProperty(k, vars[k]));
    scr.dataset.layout = state.style.layout;
    scr.dataset.theme = th.id;
    scr.dataset.night = ui.night ? '1' : '0';
    scr.dataset.screen = ui.screen;

    let body;
    if (ui.screen === 'inicio') {
      const p = pieces(state, ui);
      body = `<main class="home l-${state.style.layout}${p.hasMaint ? ' has-maint' : ''}">${LAYOUTS[state.style.layout](p)}${p.maint}</main>`;
    } else {
      body = `<main class="home screen-${ui.screen}">${SCREENS[ui.screen](state)}</main>`;
    }
    const bg = state.style.bg;
    const mode = state.style.bgMode;
    // Los temas de vidrio llevan manchas de luz difusas debajo del fondo, para que el desenfoque tenga qué mostrar.
    const orbs = t.orb1 && sceneId !== 'aurora' && state.style.scene !== 'color' && !(bg && mode === 'photo') ? TB.scene('aurora') : '';
    let customBg = state.style.scene === 'color' || (bg && mode === 'photo') ? '' : orbs + TB.scene(sceneId);
    if (bg && mode === 'photo') customBg = `<image href="${bg}" x="0" y="0" width="1024" height="600" preserveAspectRatio="xMidYMid slice"/><rect width="1024" height="600" fill="rgba(0,0,0,.38)"/>`;
    else if (bg && mode === 'logo') customBg += `<image href="${bg}" x="262" y="110" width="500" height="380" preserveAspectRatio="xMidYMid meet" opacity=".14"/>`;
    scr.innerHTML =
      `<svg class="scene" viewBox="0 0 1024 600" preserveAspectRatio="none" aria-hidden="true">${customBg}</svg>` +
      `<div class="tint tint-${sceneTime(state)}"></div>` +
      `<header class="status">${bg && mode === 'badge' ? `<img class="badge-logo" src="${bg}" alt="">` : '<span></span>'}<b data-clock>${new Date().toLocaleTimeString('es-CL', { hour: '2-digit', minute: '2-digit', hour12: false })}</b></header>` +
      body +
      `<svg class="decor" viewBox="0 0 1024 600" aria-hidden="true">${TB.decor(season, ui.night)}</svg>` +
      `<canvas class="fx" width="1024" height="600" aria-hidden="true"></canvas>` +
      dock(state, ui) +
      `<div class="alert" hidden></div>` +
      `<div class="zone-alert${live.zoneMsg && live.zoneMsg.warn ? ' warn' : ''}"${live.zoneMsg && Date.now() < live.zoneUntil ? '' : ' hidden'}><b>${live.zoneMsg ? esc(live.zoneMsg.text) : ''}</b></div>`;
    TB.tintCars(scr);
    if (TB.fx) TB.fx.attach(scr.querySelector('.fx'), season, ui.night);
    tick();
  }

  /* ---------- animación de datos de ejemplo ---------- */
  function tick() {
    if (!scr || !current) return;
    const { state } = current;
    live.n++;
    if (!reduced && live.n % 12 === 0) live.zone = (live.zone + 1) % ZONES.length;
    const real = TB.preview.app;
    const limit = real ? appLimit(state) : limitFor(state);
    let v;
    if (real) {
      v = Math.max(0, Math.round(real.speed || 0)); // velocidad real del GPS
    } else {
      const target = reduced ? limit + 8 : limit + 7 * Math.sin(live.n / 3.5) - 1;
      live.speed += (target - live.speed) * 0.4;
      v = Math.max(0, Math.round(live.speed));
    }

    scr.querySelectorAll('[data-speed]').forEach((el) => (el.textContent = dist(v, state)));
    const ringMax = state.features.miles ? 120 : 200;
    scr.querySelectorAll('[data-ring]').forEach((el) => el.setAttribute('stroke-dasharray', Math.min(100, (v / ringMax) * 100).toFixed(1) + ' 100'));
    const mm = real ? real.music : DEMO_MUSIC;
    if (mm && mm.active && mm.dur > 0) {
      let pos = (mm.pos || 0) + (mm.playing ? (Date.now() - (mm.at || Date.now())) * (mm.speed || 1) : 0);
      if (!real && pos >= mm.dur) { mm.at = Date.now(); pos = mm.pos || 0; }
      pos = Math.min(mm.dur, Math.max(0, pos));
      scr.querySelectorAll('[data-mring]').forEach((el) => el.setAttribute('stroke-dasharray', ((pos / mm.dur) * 100).toFixed(1) + ' 100'));
      scr.querySelectorAll('[data-mtime]').forEach((el) => { el.textContent = fmtT(pos) + ' / ' + fmtT(mm.dur); });
    }
    scr.querySelectorAll('[data-limit]').forEach((el) => (el.textContent = limit == null ? '—' : dist(limit, state)));
    const time = new Date().toLocaleTimeString('es-CL', { hour: '2-digit', minute: '2-digit', hour12: false });
    scr.querySelectorAll('[data-clock]').forEach((el) => (el.textContent = time));

    const za = scr.querySelector('.zone-alert');
    if (za && live.zoneMsg && Date.now() >= live.zoneUntil) { za.hidden = true; live.zoneMsg = null; }
    const alert = scr.querySelector('.alert');
    if (alert) {
      const show = state.features.speedAlert !== 'off' && limit != null && v > limit;
      alert.hidden = !show;
      if (show) alert.textContent = `Baja la velocidad · ${dist(v, state)} ${su(state)} · límite ${dist(limit, state)}`;
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
      if (appMode()) {
        // Pantalla completa: la radio se ajusta a lo ancho o a lo alto, lo que quepa, y queda centrada.
        // Si el WebView de la radio es viejo y el contenedor midió 0, se usa el tamaño de la ventana: nunca dejar la pantalla en escala 0 (se vería negra).
        const w = host.clientWidth || window.innerWidth || 1024;
        const h = host.clientHeight || window.innerHeight || 600;
        const s = Math.min(w / 1024, h / 600);
        scr.style.transform = `scale(${s})`;
        scr.style.left = Math.round((w - 1024 * s) / 2) + 'px';
        scr.style.top = Math.round((h - 600 * s) / 2) + 'px';
        return;
      }
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
      const app = TB.preview.app;
      if (app) {
        const media = e.target.closest('[data-media]');
        if (media && app.media) { app.media(media.dataset.media); return; }
        const open = e.target.closest('[data-open]');
        if (open) { app.open(open.dataset.open); return; }
        const a = e.target.closest('[data-app]');
        if (a) { app.openApp(a.dataset.app); return; }
      }
      const go = e.target.closest('[data-go]');
      if (go && TB.preview.onGo) TB.preview.onGo(go.dataset.go);
    });
    setInterval(() => {
      if (!document.hidden) tick();
    }, 1000);
  }

  /** Muestra por unos segundos el aviso de "entraste a la zona" (demo en la web; en la radio lo dispara el GPS). */
  function showZoneNotice(n) {
    live.zoneMsg = n;
    live.zoneUntil = Date.now() + 9000;
    const za = scr && scr.querySelector('.zone-alert');
    if (za) { za.hidden = false; za.className = 'zone-alert' + (n.warn ? ' warn' : ''); za.firstElementChild.textContent = n.text; }
  }

  TB.preview = { mount, render, onGo: null, maintItems, showZoneNotice, updateMusic, updateMap, app: null };
})();
