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
    style: { theme: 'cristal', layout: 'tarjetas', scene: 'tema', sceneTime: 'auto', bg: null, bgMode: 'photo', season: 'ninguna', accent: null, apps: ['maps', 'spotify', 'waze'] },
    features: {
      oilOn: true, odometer: 0, lastOil: 0, oilEvery: 5000, others: [], docsOn: false, rtMonth: 1, fuelOn: false, tankL: 45, kmPerL: 12,
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

  const bridge = (fn, arg) => { try { if (window.Android && typeof window.Android[fn] === 'function') window.Android[fn](arg); } catch (e) { /* sin puente: probando en un navegador */ } };
  const hourNight = () => { const h = new Date().getHours(); return h >= 19 || h < 7; };

  let state = defaults();
  const ui = { step: 1, screen: 'inicio', night: hourNight() };
  let nightForced = null; // true/false si la radio avisa que las luces del auto están (o no) encendidas
  let mounted = false;

  const MUSIC_APPS = ['spotify', 'ytmusic', 'usb'];
  const app = {
    speed: 0,
    weather: null,
    open(kind) {
      if (kind === 'music') {
        const id = MUSIC_APPS.find((a) => state.style.apps.includes(a));
        if (id) bridge('openApp', id); else bridge('open', 'music');
        return;
      }
      bridge('open', kind);
    },
    openApp(id) { bridge('openApp', id); },
  };

  function render() {
    if (!mounted) return;
    ui.night = nightForced === null ? hourNight() : nightForced;
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
  window.TBApp = {
    init(json) {
      let design = null;
      try { design = typeof json === 'string' ? JSON.parse(json) : json; } catch (e) { design = null; }
      TB.preview.app = app;
      TB.preview.onGo = (id) => { ui.screen = id === 'inicio' ? 'inicio' : id; render(); };
      if (!mounted) { TB.preview.mount(document.getElementById('host')); mounted = true; }
      document.getElementById('key').hidden = !!(design && design.config);
      setDesign(design);
    },
    setSpeed(kmh) { app.speed = Number(kmh) || 0; },
    setLocation(lat, lon) { if (isFinite(lat) && isFinite(lon)) loadWeather(lat, lon); },
    setNight(on) { nightForced = on === null || on === undefined ? null : !!on; render(); },
    zoneNotice(text, warn) { TB.preview.showZoneNotice({ text: String(text), warn: !!warn }); },
  };

  document.getElementById('key').addEventListener('click', () => bridge('open', 'activate'));
  document.querySelector('[data-gear]').addEventListener('click', () => bridge('open', 'settings'));
  setInterval(() => { if (mounted && nightForced === null && ui.night !== hourNight()) render(); }, 60000);

  // Para probar en un navegador: launcher.html?demo=1
  if (/[?&]demo=1/.test(location.search)) {
    window.TBApp.init({
      config: {
        car: { type: 'auto', name: 'Daniel', greeting: 'Hola', phrase: '' },
        style: { theme: 'cristal', layout: 'tarjetas', apps: ['maps', 'spotify', 'waze', 'whatsapp'] },
        features: { oilOn: true, odometer: 45000, lastOil: 42000, docsOn: true, rtMonth: 6, speedShow: true, restrOn: true, restrType: 'sello', restrDigit: '8', restrDays: ['lun'] },
      },
    });
  }
})();
