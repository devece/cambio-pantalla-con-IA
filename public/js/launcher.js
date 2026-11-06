/*
 * La pantalla de la radio: usa el mismo motor del sitio (preview.js) pero con datos reales.
 * La app Android (WebView) llama a window.TBApp con el diseño del cliente, la velocidad del GPS y su ubicación;
 * esta página le pide a la app que abra apps (window.Android). En un navegador sirve para probar: ?demo=1
 */
(function () {
  const TB = window.TB;
  const C = window.TBCatalog;

  const defaults = () => ({
    radio: { type: 'android', model: '' },
    car: { type: 'auto', color: TB.carColors[0], name: '', greeting: 'Hola', phrase: '', plate: '', photo: null, photoCut: false, photoNight: null },
    style: { theme: 'cristal', layout: 'estelar', scene: 'tema', sceneTime: 'auto', bg: null, bgMode: 'photo', season: 'ninguna', accent: null, apps: ['maps', 'spotify', 'waze'] },
    features: {
      oilOn: true, odometer: 0, lastOil: 0, oilEvery: 5000, others: [], maintDone: {}, docsOn: false, rtMonth: 1, fuelOn: false, tankL: 45, kmPerL: 12,
      speedShow: true, speedAlert: 'auto', manualLimit: 60, restrOn: false, restrType: 'sello', restrDigit: '', restrDays: [], voice: false, miles: false, gallons: false,
    },
  });
  function merge(base, saved) {
    if (!saved || typeof saved !== 'object') return base;
    Object.keys(base).forEach((k) => {
      const b = base[k];
      const s = saved[k];
      if (s === undefined || s === null) return;
      if (b && typeof b === 'object' && !Array.isArray(b)) base[k] = merge(b, s);
      else if (Array.isArray(b)) { if (Array.isArray(s)) base[k] = s; }
      else if (typeof b === typeof s || b === null) base[k] = s;
    });
    return base;
  }

  // WebView muy antiguo (anterior a Chrome 57): no entiende CSS grid y la pantalla se desarma (tarjetas apiladas, íconos fuera de su tecla).
  // En vez de mostrarla rota, se explica qué actualizar. ?oldwebview=1 fuerza este aviso para probarlo en un navegador.
  const gridOk = (() => {
    if (/[?&]oldwebview=1/.test(location.search)) return false;
    try { const d = document.createElement('div'); d.style.display = 'grid'; return d.style.display === 'grid'; } catch (e) { return false; }
  })();
  let allowOld = false;

  const bridge = (fn, arg) => { try { if (window.Android && typeof window.Android[fn] === 'function') window.Android[fn](arg); } catch (e) { /* sin puente: probando en un navegador */ } };
  const hourNight = () => { const h = new Date().getHours(); return h >= 19 || h < 7; };

  /* ---------- día y noche por la hora del sol ---------- */
  // Dónde está el auto: el último GPS (se guarda para el próximo arranque, aunque aún no haya señal); si nunca hubo, Santiago.
  const SANTIAGO = { lat: -33.45, lon: -70.65 };
  const D = window.TBDaylight;
  const loadPlace = () => {
    try {
      const p = JSON.parse(localStorage.getItem('tb-place'));
      if (p && Math.abs(p.lat) <= 90 && Math.abs(p.lon) <= 180) return p;
    } catch (e) { /* sin almacenamiento: se usa Santiago */ }
    return { lat: SANTIAGO.lat, lon: SANTIAGO.lon };
  };
  let place = loadPlace();
  function rememberPlace(lat, lon) {
    // Con moverse unos kilómetros el amanecer casi no cambia: no se escribe en cada fix del GPS.
    if (Math.abs(place.lat - lat) < 0.5 && Math.abs(place.lon - lon) < 0.5 && place.known) return;
    place = { lat, lon, known: true };
    try { localStorage.setItem('tb-place', JSON.stringify(place)); } catch (e) { /* sin almacenamiento */ }
  }
  /** Noche según el sol; sin el cálculo (algo falló al cargar) vuelve a la hora fija de antes. */
  const sunNight = () => (D ? D.isNight(new Date(), place.lat, place.lon) : hourNight());
  // El fondo "automático" (amanecer, día, atardecer, noche) sigue al sol en la radio.
  if (D) TB.sunPhase = () => D.phase(new Date(), place.lat, place.lon);

  let state = defaults();
  const ui = { step: 1, screen: 'inicio', night: sunNight() };
  let nightForced = null; // true/false si la radio avisa que las luces del auto están (o no) encendidas
  let mounted = false;

  const MUSIC_APPS = ['spotify', 'ytmusic', 'usb'];
  const app = {
    speed: 0,
    weather: null,
    music: { access: null, active: false },
    media(cmd) { bridge('media', cmd); },
    open(kind) {
      if (kind === 'music') {
        const id = MUSIC_APPS.find((a) => state.style.apps.includes(a));
        if (id) bridge('openApp', id); else bridge('open', 'music');
        return;
      }
      bridge('open', kind);
    },
    openApp(id) { bridge('openApp', id); },
    /** "Hecho": la app guarda el kilometraje de hoy y devuelve el avance con TBApp.setProgress. */
    markDone(id) { bridge('done', JSON.stringify({ id, odometer: state.features.odometer })); },
  };

  function render() {
    if (!mounted) return;
    ui.night = nightForced === null ? sunNight() : nightForced;
    TB.preview.render(state, ui);
  }

  function setDesign(design) {
    state = defaults();
    if (design && design.config) state = merge(state, design.config);
    const ph = design && design.photo;
    if (ph && ph.day) {
      state.car.photo = ph.day;
      state.car.photoCut = ph.cut === true;
      state.car.photoNight = ph.night || null;
    }
    ui.screen = 'inicio';
    render();
  }

  /* ---------- clima real (Open-Meteo) ---------- */
  const WX_TEXT = (c) => (c === 0 ? 'Despejado' : c <= 3 ? 'Parcialmente nublado' : c <= 48 ? 'Neblina' : c <= 57 ? 'Llovizna' : c <= 67 ? 'Lluvia' : c <= 77 ? 'Nieve' : c <= 82 ? 'Chubascos' : 'Tormenta');
  let lastWeather = 0;
  function loadWeather(lat, lon) {
    if (Date.now() - lastWeather < 30 * 60 * 1000) return;
    lastWeather = Date.now();
    const url = 'https://api.open-meteo.com/v1/forecast?latitude=' + lat.toFixed(3) + '&longitude=' + lon.toFixed(3) +
      '&current=temperature_2m,apparent_temperature,relative_humidity_2m,weather_code&timezone=auto';
    fetch(url).then((r) => r.json()).then((d) => {
      const c = d && d.current;
      if (!c) return;
      app.weather = { temp: c.temperature_2m, feels: c.apparent_temperature, humidity: c.relative_humidity_2m, code: c.weather_code, text: WX_TEXT(c.weather_code) };
      render();
    }).catch(() => { lastWeather = Date.now() - 25 * 60 * 1000; /* reintenta en 5 minutos */ });
  }

  /* ---------- lo que la app Android le dice a la pantalla ---------- */
  function showOldWebview(json) {
    const host = document.getElementById('host');
    const btn = 'border:0;border-radius:28px;padding:14px 28px;font-size:19px;font-weight:600;margin:6px;';
    host.innerHTML =
      '<div style="position:absolute;left:0;right:0;top:0;bottom:0;display:flex;align-items:center;justify-content:center;text-align:center;color:#fff;font-family:sans-serif;padding:0 8%;background:#101418">' +
      '<div style="max-width:720px"><div style="font-size:32px;font-weight:700;margin-bottom:14px">Falta actualizar un componente de tu radio</div>' +
      '<div style="font-size:20px;line-height:1.5;opacity:.85;margin-bottom:26px">Esta radio trae una versión muy antigua de <b>Android System WebView</b> y no puede dibujar tu pantalla como la elegiste. ' +
      'Actualízalo (es gratis y toma un minuto) y vuelve a abrir Tablero.</div>' +
      '<button id="upd-webview" style="' + btn + 'background:#f5c400;color:#101418">Actualizar ahora</button>' +
      '<button id="upd-anyway" style="' + btn + 'background:rgba(255,255,255,.16);color:#fff">Ver igual</button></div></div>';
    document.getElementById('upd-webview').addEventListener('click', () => bridge('open', 'webview'));
    document.getElementById('upd-anyway').addEventListener('click', () => { allowOld = true; host.innerHTML = ''; mounted = false; window.TBApp.init(json); });
  }

  window.TBApp = {
    init(json) {
      if (!gridOk && !allowOld) { showOldWebview(json); return; }
      let design = null;
      try { design = typeof json === 'string' ? JSON.parse(json) : json; } catch (e) { design = null; }
      TB.preview.app = app;
      TB.preview.onGo = (id) => { ui.screen = id === 'inicio' ? 'inicio' : id; render(); };
      if (!mounted) { TB.preview.mount(document.getElementById('host')); mounted = true; }
      document.getElementById('key').hidden = !!(design && design.config);
      setDesign(design);
    },
    setSpeed(kmh) { app.speed = Number(kmh) || 0; },
    setLocation(lat, lon) {
      if (!isFinite(lat) || !isFinite(lon)) return;
      rememberPlace(lat, lon);
      loadWeather(lat, lon);
      if (mounted && nightForced === null && ui.night !== sunNight()) render();
    },
    setMusic(json) {
      try { app.music = typeof json === 'string' ? JSON.parse(json) : json; } catch (e) { return; }
      if (TB.preview.updateMusic) TB.preview.updateMusic();
    },
    /** Avance guardado en la radio: kilometraje y fecha (km) del último servicio de cada aviso. */
    setProgress(json) {
      let p = null;
      try { p = typeof json === 'string' ? JSON.parse(json) : json; } catch (e) { return; }
      if (!p || typeof p !== 'object') return;
      const f = state.features;
      if (Number.isFinite(p.odometer) && p.odometer > 0) f.odometer = p.odometer;
      if (Number.isFinite(p.lastOil)) f.lastOil = p.lastOil;
      if (p.maintDone && typeof p.maintDone === 'object') f.maintDone = Object.assign({}, p.maintDone);
      render();
    },
    setNight(on) { nightForced = on === null || on === undefined ? null : !!on; render(); },
    zoneNotice(text, warn) { TB.preview.showZoneNotice({ text: String(text), warn: !!warn }); },
  };

  document.getElementById('key').addEventListener('click', () => bridge('open', 'activate'));
  document.querySelector('[data-gear]').addEventListener('click', () => bridge('open', 'settings'));
  setInterval(() => { if (mounted && nightForced === null && ui.night !== sunNight()) render(); }, 60000);

  // Para probar en un navegador: launcher.html?demo=1
  if (/[?&]demo=1/.test(location.search)) {
    window.TBApp.init({
      config: {
        car: { type: 'auto', name: 'Daniel', greeting: 'Hola', phrase: '' },
        style: { theme: 'cristal', layout: 'estelar', apps: ['maps', 'spotify', 'waze', 'whatsapp'] },
        features: { oilOn: true, odometer: 45000, lastOil: 42000, docsOn: true, rtMonth: 6, speedShow: true, restrOn: true, restrType: 'sello', restrDigit: '8', restrDays: ['lun'] },
      },
    });
  }
})();
