/*
 * Configurador de Tablero: estado, pasos, enlace de campos, vista previa y pago.
 * Todo vive en `state`; cada cambio llama a update(), que repinta la radio y sincroniza la interfaz.
 */
(function () {
  const TB = window.TB;
  const C = window.TBCatalog;
  const O = window.TBOrder;
  const $ = (s, r) => (r || document).querySelector(s);
  const $$ = (s, r) => Array.from((r || document).querySelectorAll(s));
  const esc = (s) => String(s).replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
  const fmt = (n) => new Intl.NumberFormat('es-CL').format(Math.round(n));
  const STORE = 'tablero:v1';

  /* ---------- estado ---------- */
  const defaults = () => ({
    radio: { type: null, model: '', screen: null, analysis: null },
    car: { type: 'auto', color: TB.carColors[0], name: '', greeting: 'Hola', phrase: '', plate: '', photo: null, photoCut: false, photoNight: null },
    style: { theme: 'ruta', layout: 'tarjetas', scene: 'tema', sceneTime: 'auto', bg: null, bgMode: 'photo', season: 'ninguna', accent: null, apps: ['maps', 'spotify', 'waze'] },
    features: {
      oilOn: true, odometer: 45000, lastOil: 42000, oilEvery: 5000,
      others: ['neumaticos'], docsOn: true, rtMonth: 6,
      fuelOn: false, tankL: 45, kmPerL: 12,
      speedShow: true, speedAlert: 'auto', manualLimit: 60, country: 'CL', restrOn: false, restrType: 'sello', restrDigit: '', restrDays: [], voice: true, miles: false, gallons: false,
    },
    plan: 'completo',
    addons: {},
    contact: { kind: 'whatsapp', value: '' },
    terms: false,
  });
  const ui = { step: 1, screen: 'inicio', night: false };
  const flags = { autoPlan: false };
  let state = defaults();

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
  function load() {
    try {
      const raw = localStorage.getItem(STORE);
      if (raw) state = merge(defaults(), JSON.parse(raw));
    } catch (_) { /* sin almacenamiento: seguimos con los valores por defecto */ }
    state.car.photo = null;
    state.car.photoCut = false;
    state.car.photoNight = null;
    state.style.bg = null;
  }
  function save() {
    try {
      const copy = JSON.parse(JSON.stringify(state));
      copy.car.photo = null; // la foto no se guarda
      copy.car.photoNight = null;
      copy.style.bg = null;
      delete copy.contact; // no guardamos datos de contacto en el navegador
      delete copy.terms;
      localStorage.setItem(STORE, JSON.stringify(copy));
    } catch (_) { /* ignorar */ }
  }

  const get = (o, p) => p.split('.').reduce((a, k) => (a == null ? a : a[k]), o);
  const set = (o, p, v) => {
    const ks = p.split('.');
    const last = ks.pop();
    ks.reduce((a, k) => a[k], o)[last] = v;
  };

  /* ---------- catálogos para las opciones ---------- */
  const TB_PICO = window.TBPico;
  const CHOICES = {
    'radio.type': () => [
      { id: 'android', thumb: TB.radioThumbs.android, name: 'Pantalla grande con apps', desc: 'Se instaló después. Tiene Play Store, YouTube, Waze o similares.' },
      { id: 'original', thumb: TB.radioThumbs.original, name: 'La original del auto', desc: 'Vino de fábrica, con la marca del auto.' },
      { id: 'nose', thumb: TB.radioThumbs.nose, name: 'No sé', desc: 'Te mostramos cómo saberlo en 10 segundos.' },
    ],
    'car.type': () => TB.vehicles,
    'style.theme': () => TB.themes.map((t) => ({ id: t.id, name: t.name, desc: t.desc, tier: t.tier, swatch: t.swatch })),
    'style.layout': () => TB.layouts.map((l) => ({ id: l.id, name: l.name, desc: l.desc, thumb: TB.layoutThumbs[l.id], tag4x4: l.only4x4 })),
    'style.scene': () => TB.scenes,
    'style.sceneTime': () => TB.sceneTimes,
    'style.bgMode': () => [
      { id: 'photo', name: 'Foto completa', desc: 'Llena la pantalla' },
      { id: 'logo', name: 'Logo de fondo', desc: 'Centrado y suave' },
      { id: 'badge', name: 'Logo pequeño', desc: 'En la barra de arriba' },
    ],
    'style.season': () => TB.seasons.map((s) => ({ id: s.id, name: s.name, desc: s.desc, tier: s.id === 'ninguna' ? 'base' : 'plus' })),
    'features.speedAlert': () => [
      { id: 'auto', name: 'Automático' },
      { id: 'manual', name: 'Manual' },
      { id: 'off', name: 'Sin aviso' },
    ],
    'features.restrType': () => C.restriction.types,
    'features.country': () => TB_PICO.countries.map((c) => ({ id: c.id, name: c.name })),
    plan: () => C.plans.map((p) => ({ id: p.id, name: p.name, desc: p.blurb, price: p.price })),
    'contact.kind': () => [
      { id: 'whatsapp', name: 'WhatsApp' },
      { id: 'email', name: 'Correo' },
    ],
  };
  const MULTI = {
    'style.apps': () => TB.apps,
    'features.others': () => C.maintenance.map((m) => ({ id: m.id, name: m.name, desc: 'cada ' + fmt(m.every) + ' km' })),
  };
  const STEPS = ['Radio', 'Auto', 'Diseño', 'Funciones', 'Pago'];
  const SCREENS = [
    { id: 'inicio', name: 'Inicio' },
    { id: 'radio', name: 'Radio' },
    { id: 'bluetooth', name: 'Bluetooth' },
    { id: 'ecualizador', name: 'Ecualizador' },
    { id: 'mantenimiento', name: 'Mantención' },
  ];

  /* ---------- construcción de la interfaz ---------- */
  function choiceHtml(it, role, extra) {
    const th = it.thumb ? `<span class="thumb">${it.thumb}</span>` : '';
    const sw = it.swatch ? `<span class="sw" style="background:linear-gradient(135deg,${it.swatch[0]} 0 55%,${it.swatch[1]} 55%)"></span>` : '';
    const tag = it.tier === 'plus' ? '<span class="tag">Completo</span>' : it.tag4x4 ? '<span class="tag">4x4</span>' : '';
    const price = it.price ? `<span class="price">${O.money(it.price)}</span>` : '';
    const attr = role === 'radio' ? 'role="radio" aria-checked="false"' : 'role="checkbox" aria-pressed="false"';
    return `<button type="button" class="choice" ${attr} data-id="${esc(it.id)}"${extra || ''}>${th}${sw}<b>${esc(it.name)}${tag}</b>${it.desc ? `<small>${esc(it.desc)}</small>` : ''}${price}</button>`;
  }

  function build() {
    $('#contact-kind').dataset.choices = 'contact.kind';
    $$('[data-choices]').forEach((el) => {
      el.innerHTML = CHOICES[el.dataset.choices]().map((it) => choiceHtml(it, 'radio')).join('');
    });
    $$('[data-multi]').forEach((el) => {
      el.innerHTML = MULTI[el.dataset.multi]().map((it) => choiceHtml(it, 'checkbox')).join('');
    });
    $('#accent-swatches').innerHTML = TB.accents.map((c) => `<button type="button" class="swatch" role="radio" aria-checked="false" data-accent="${c}" style="background:${c}" aria-label="Acento ${c}"></button>`).join('');
    $('#greet-slots').innerHTML = TB.greetings.map((g) => `<button type="button" class="slot" data-greet="${esc(g)}">${esc(g)}</button>`).join('');
    $('#phrase-slots').innerHTML = TB.phrases.map((p) => `<button type="button" class="slot" data-phrase="${esc(p)}">${esc(p)}</button>`).join('');
    $('#car-colors').innerHTML = TB.carColors
      .map((c) => `<button type="button" class="swatch" role="radio" aria-checked="false" data-color="${c}" style="background:${c}" aria-label="Color ${c}"></button>`)
      .join('');
    $('#rt-month').innerHTML = TB.months.map((m, i) => `<option value="${i + 1}">${m[0].toUpperCase() + m.slice(1)}</option>`).join('');
    $('#screen-tabs').innerHTML = SCREENS.map((s) => `<button type="button" class="tab" role="tab" data-screen="${s.id}" aria-selected="false">${s.name}</button>`).join('');
    $('#route').innerHTML = STEPS.map((n, i) => `<li data-n="${i + 1}"><button type="button" data-n="${i + 1}" aria-label="Paso ${i + 1}: ${n}"><span class="km"></span><small>${i + 1}. ${n}</small></button></li>`).join('');

    const addons = C.addons.filter((a) => a.available !== false);
    $('#addons').innerHTML = addons
      .map((a) => `<label class="check"><input type="checkbox" data-bind="addons.${a.id}"><span>${esc(a.name)} · ${O.money(a.price)}<br><small class="hint">${esc(a.blurb)}</small></span></label>`)
      .join('');
    if (!addons.length) $('#addons').hidden = true;
  }

  /* ---------- sincronizar la interfaz con el estado ---------- */
  const radioOk = () => state.radio.type === 'android' || state.radio.type === 'nose';

  function applyConstraints() {
    const need = O.requiredPlan(state.style);
    if (need === 'completo' && state.plan === 'esencial') {
      state.plan = 'completo';
      flags.autoPlan = true;
    } else if (need === 'esencial' && flags.autoPlan && state.plan === 'completo') {
      state.plan = 'esencial';
      flags.autoPlan = false;
    }
  }

  const vehicleLevel = () => TB.vehicleLevel[state.car.type] || 0;
  const VEHICLE_HINT = {
    '4x4': 'Te armamos un estilo 4x4: diseño Todoterreno y escena de barro. Puedes cambiar todo abajo.',
    suv: 'Te pusimos la escena de montaña. Puedes cambiarla abajo.',
    camioneta: 'Con camioneta se desbloquea la escena de montaña.',
  };
  function applyVehicle() {
    const lv = vehicleLevel();
    const t = state.car.type;
    if (t === '4x4') { state.style.layout = 'todoterreno'; state.style.scene = 'barro'; }
    else if (t === 'suv' || t === 'camioneta') {
      if (state.style.layout === 'todoterreno') state.style.layout = 'tarjetas';
      state.style.scene = 'montana';
    } else {
      if (state.style.layout === 'todoterreno') state.style.layout = 'tarjetas';
      const sc = TB.scenes.find((s) => s.id === state.style.scene);
      if (sc && sc.level > lv) state.style.scene = 'tema';
    }
    const hint = $('#vt-hint');
    hint.textContent = VEHICLE_HINT[t] || '';
    hint.hidden = !VEHICLE_HINT[t];
  }

  function syncChoices() {
    const need = O.requiredPlan(state.style);
    $$('[data-choices]').forEach((el) => {
      const path = el.dataset.choices;
      const cur = get(state, path);
      $$('.choice', el).forEach((b) => {
        b.setAttribute('aria-checked', String(cur === b.dataset.id));
        if (path === 'plan' && b.dataset.id === 'esencial') b.disabled = need === 'completo';
        if (path === 'style.layout' && b.dataset.id === 'todoterreno') b.disabled = vehicleLevel() < 2;
        if (path === 'style.scene') { const sc = TB.scenes.find((s) => s.id === b.dataset.id); b.disabled = !!(sc && sc.level && sc.level > vehicleLevel()); }
      });
    });
    $$('[data-multi]').forEach((el) => {
      const path = el.dataset.multi;
      const cur = get(state, path) || [];
      $$('.choice', el).forEach((b) => b.setAttribute('aria-pressed', String(cur.includes(b.dataset.id))));
    });
    $$('#accent-swatches .swatch').forEach((b) => b.setAttribute('aria-checked', String(b.dataset.accent === state.style.accent)));
    $$('.slot[data-phrase]').forEach((b) => b.classList.toggle('on', b.dataset.phrase === state.car.phrase));
    $$('.slot[data-greet]').forEach((b) => b.classList.toggle('on', b.dataset.greet === state.car.greeting));
    $$('#car-colors .swatch').forEach((b) => b.setAttribute('aria-checked', String(b.dataset.color === state.car.color)));
    $('#apps-count').textContent = `${state.style.apps.length} de 4`;
  }

  function syncBindings() {
    $$('[data-bind]').forEach((el) => {
      const v = get(state, el.dataset.bind);
      if (el.type === 'checkbox') el.checked = !!v;
      else if (document.activeElement !== el) el.value = v == null ? '' : v;
    });
    const th = TB.themes.find((t) => t.id === state.style.theme) || TB.themes[0];
    const accent = state.style.accent || (ui.night ? th.night.accent : th.day.accent);
    $('#accent-input').value = accent;
  }

  function showHide() {
    $$('[data-show]').forEach((el) => {
      const expr = el.dataset.show;
      let on;
      if (expr.includes('=')) {
        const [p, v] = expr.split('=');
        on = String(get(state, p)) === v;
      } else on = !!get(state, expr);
      el.hidden = !on;
    });
  }

  const radioNotes = {
    android: ['', 'Muy bien: este es el tipo de radio para el que está pensado Tablero.'],
    original: ['bad', 'Las radios originales de fábrica que no permiten instalar apps no son compatibles. Si solo tiene Android Auto o CarPlay, eso es tu celular mostrándose en la pantalla: no se puede instalar nada dentro de la radio. Con una radio Android de pantalla grande sí funcionaría.'],
    nose: ['warn', 'Para saberlo, abre los ajustes de tu radio y busca “Acerca del dispositivo”, “Sistema” o “Información”. Si ves una versión de Android y puedes abrir la Play Store, es compatible. Puedes diseñar igual, pero necesitamos que confirmes el tipo antes de pagar.'],
  };

  function syncRestriction() {
    const f = state.features;
    const r = C.restriction;
    const sel = $('#restr-digit');
    if (!sel.options.length) sel.innerHTML = '<option value="">Elige un dígito</option>' + [0, 1, 2, 3, 4, 5, 6, 7, 8, 9].map((d) => `<option value="${d}">${d}</option>`).join('');
    sel.value = f.restrDigit;
    $('#restr-year').textContent = r.year;
    const country = TB_PICO.countryOf(f.country);
    $('#restr-label').textContent = country.label;
    $('#restr-digit-field').hidden = f.country === 'CL' && f.restrType === 'nuevo';
    $$('[data-show-not]').forEach((el) => {
      const [p, v] = el.dataset.showNot.split('=');
      el.hidden = String(get(state, p)) === v;
    });
    if (f.country !== 'CL') {
      const list = TB_PICO.citiesOf(f.country);
      $('#pico-hint').textContent = `La radio usa el GPS: cuando entres a ${list.map((c) => c.name).join(', ').replace(/, ([^,]*)$/, ' o $1')} te avisa si ese día te toca. En el resto de ${country.name} no te molesta.`;
      const n2 = $('#restr-note');
      if (f.restrDigit === '') {
        n2.className = 'note warn';
        n2.textContent = 'Elige el último dígito de tu patente para calcular tu día.';
      } else {
        n2.className = 'note';
        n2.textContent = list.map((c) => TB_PICO.describe(c, f.restrDigit)).join('\n\n');
      }
      return;
    }
    const n = $('#restr-note');
    const win = `Rige del ${r.season.from} al ${r.season.to}, ${r.season.days}, de ${r.season.hours}.`;
    let cls = '';
    let text = '';
    if (f.restrType === 'nuevo') {
      text = 'Tu auto no tiene restricción permanente. Solo puede afectarle una preemergencia o emergencia ambiental, que se anuncia el día anterior.';
    } else if (f.restrDigit === '') {
      cls = 'warn';
      text = 'Elige el último dígito de tu patente para calcular tu día.';
    } else {
      const names = TB.weekdays.filter((d) => f.restrDays.includes(d.id)).map((d) => d.name.toLowerCase());
      text = `Con patente terminada en ${f.restrDigit}, te toca el ${names.join(' y ')}. ${win}${f.restrType === 'sin' ? ' Además, sin sello verde no puedes circular dentro del anillo Américo Vespucio, cualquier día de la semana.' : ''} Fuera de esas fechas no hay restricción. Al entrar a Santiago, la radio te avisa en pantalla y por voz.`;
    }
    n.className = 'note' + (cls ? ' ' + cls : '');
    n.textContent = text;
  }

  function syncNotes() {
    const n = $('#radio-note');
    const r = radioNotes[state.radio.type];
    n.className = 'note' + (r && r[0] ? ' ' + r[0] : '');
    n.textContent = r ? r[1] : '';

    $('#nose-test').hidden = state.radio.type !== 'nose';
    $('#shot').hidden = state.radio.type !== 'android' && !state.radio.screen && !state.radio.analysis;

    syncRestriction();

    const f = state.features;
    const left = f.lastOil + f.oilEvery - f.odometer;
    $('#oil-hint').textContent = left >= 0 ? `Faltan ${fmt(left)} km para el próximo cambio. Cuando falten 500 km o menos, la radio muestra un aviso grande.` : `Tu cambio de aceite está pasado por ${fmt(-left)} km.`;
    $('#fuel-hint').textContent = `Con el tanque lleno te alcanza para unos ${fmt(f.tankL * f.kmPerL)} km. Cada vez que cargues, tocas “Llené el tanque” en la radio.`;

    const pn = $('#plan-note');
    const reasons = O.planReasons(state.style);
    if (reasons.length) {
      pn.hidden = false;
      pn.className = 'note warn';
      pn.textContent = `Se necesita el plan Completo por ${reasons.join(' y ')}. Si prefieres el Esencial, vuelve al paso 3 y elige un tema básico (Ruta, Papel o Cristal) sin decoración.`;
    } else pn.hidden = true;
  }

  function syncSummary() {
    const th = TB.themes.find((t) => t.id === state.style.theme);
    const lay = TB.layouts.find((l) => l.id === state.style.layout);
    const veh = TB.vehicles.find((v) => v.id === state.car.type);
    const season = TB.seasons.find((s) => s.id === state.style.season);
    const f = state.features;
    const feats = [];
    if (f.oilOn) feats.push('aceite');
    if (f.others.length) feats.push(`${f.others.length} mantención${f.others.length > 1 ? 'es' : ''}`);
    if (f.docsOn) feats.push('documentos');
    if (f.fuelOn) feats.push('combustible');
    if (f.speedAlert !== 'off') feats.push('aviso de velocidad');
    if (f.voice) feats.push('saludo por voz');
    if (f.restrOn && (f.country === 'CL' ? f.restrDays.length : f.restrDigit !== '')) feats.push(TB_PICO.countryOf(f.country).label);
    const radio = { android: 'Android de pantalla grande', nose: 'Por confirmar', original: 'Original del auto (no compatible)' }[state.radio.type] || 'Sin elegir';
    const apps = state.style.apps.map((id) => (TB.apps.find((a) => a.id === id) || {}).name).filter(Boolean);
    const rows = [
      ['Radio', radio + (state.radio.model ? ` · ${esc(state.radio.model)}` : '')],
      ['Vehículo', `${veh.name}${state.car.name ? ' de ' + esc(state.car.name) : ''}${state.car.photo ? ' · con tu foto' : ''}`],
      ['Diseño', `${th.name} · ${lay.name}${season.id !== 'ninguna' ? ' · ' + season.name : ''}`],
      ['Fondo', state.style.bg ? { photo: 'Tu foto', logo: 'Tu logo de fondo', badge: 'Tu logo en la barra' }[state.style.bgMode] : `${(TB.scenes.find((s) => s.id === state.style.scene) || {}).name || 'Del diseño'} · ${(TB.sceneTimes.find((s) => s.id === state.style.sceneTime) || {}).name || 'Automática'}`],
      ['Saludo', `${state.features.voice ? 'Por voz' : 'Solo en pantalla'} · “${esc(state.car.greeting)}, ${esc(state.car.name || 'amigo')}”${state.car.phrase ? ' · “' + esc(state.car.phrase) + '”' : ''}`],
      ['Apps', apps.length ? esc(apps.join(', ')) : 'Sin apps en la barra'],
      ['Funciones', feats.length ? feats.join(', ') : 'Ninguna activada'],
    ];
    $('#summary').innerHTML = rows.map(([k, v]) => `<div><dt>${k}</dt><dd>${v}</dd></div>`).join('');
    const q = O.quote(state);
    $('#total').textContent = q.total > 0 ? O.money(q.total) : 'Gratis';
    $('#pay').textContent = (TB.site || {}).payment === 'transfer' ? `Pagar ${O.money(q.total)} por transferencia` : (TB.site || {}).payment === 'contact' ? `Pedir mi pantalla · ${O.money(q.total)}` : `Pagar ${O.money(q.total)}`;
  }

  function syncSteps() {
    $$('.step').forEach((s) => (s.hidden = Number(s.dataset.step) !== ui.step));
    $$('#route li').forEach((li) => {
      const n = Number(li.dataset.n);
      li.classList.toggle('on', n === ui.step);
      li.classList.toggle('visto', n < ui.step);
      $('button', li).disabled = n > 1 && !radioOk();
      if (n === ui.step) $('button', li).setAttribute('aria-current', 'step');
      else $('button', li).removeAttribute('aria-current');
    });
    $('#back').style.visibility = ui.step === 1 ? 'hidden' : 'visible';
    const next = $('#next');
    next.hidden = ui.step === 5;
    next.disabled = ui.step === 1 && !(state.radio.type && state.radio.type !== 'original');
    $$('#screen-tabs .tab').forEach((t) => t.setAttribute('aria-selected', String(t.dataset.screen === ui.screen)));
    $('#mode-day').classList.toggle('on', !ui.night);
    $('#mode-night').classList.toggle('on', ui.night);
    $('#mode-day').setAttribute('aria-pressed', String(!ui.night));
    $('#mode-night').setAttribute('aria-pressed', String(ui.night));
    $('#photo-clear').hidden = !state.car.photo;
    // El color es solo del auto de ejemplo: con la foto del cliente no se usa.
    $('#car-color-group').hidden = !!state.car.photo;
    $('#photo-sample').hidden = !!state.car.photo;
    $('#bg-clear').hidden = !state.style.bg;
    $('#bg-mode').hidden = !state.style.bg;
  }

  const PHONE_EXAMPLE = { CL: '+56 9 1234 5678', CO: '+57 300 123 4567', EC: '+593 99 123 4567' };
  function update() {
    state.features.restrDays = state.features.country === 'CL' ? O.restrDaysFor(state.features) : [];
    applyConstraints();
    TB.preview.render(state, ui);
    const tel = document.querySelector('input[type=tel][data-bind="contact.value"]');
    if (tel) tel.placeholder = PHONE_EXAMPLE[state.features.country] || PHONE_EXAMPLE.CL;
    syncChoices();
    syncBindings();
    showHide();
    syncNotes();
    syncSummary();
    syncSteps();
    save();
  }

  /* ---------- navegación ---------- */
  function scrollToPanel() {
    const stage = $('.stage');
    const mobile = window.matchMedia('(max-width: 920px)').matches;
    const top = $('#panel').getBoundingClientRect().top + window.scrollY - (mobile ? stage.offsetHeight : 0) - 8;
    if (Math.abs(window.scrollY - top) > 40) window.scrollTo({ top: Math.max(0, top), behavior: 'smooth' });
  }

  function go(n, opts) {
    if (n < 1 || n > 5) return;
    if (n > 1 && !radioOk()) n = 1;
    ui.step = n;
    ui.screen = n === 4 ? 'mantenimiento' : 'inicio';
    update();
    if (!opts || !opts.silent) {
      scrollToPanel();
      const h = $(`.step[data-step="${n}"] h2`);
      if (h) { h.tabIndex = -1; h.focus({ preventScroll: true }); }
    }
  }

  /** Lo que diría la radio al encender: saludo, nombre y la frase (o el saludo del momento del día). */
  function greetingLine() {
    const c = state.car;
    const h = new Date().getHours();
    const tod = h < 12 ? 'Buenos días' : h < 20 ? 'Buenas tardes' : 'Buenas noches';
    return `${(c.greeting || 'Hola').trim()}, ${(c.name || 'amigo').trim()}. ${(c.phrase || tod).trim()}`;
  }
  function listenGreeting() {
    const synth = window.speechSynthesis;
    if (!synth || typeof SpeechSynthesisUtterance === 'undefined') { $('#greet-listen').hidden = true; return; }
    synth.cancel();
    const u = new SpeechSynthesisUtterance(greetingLine());
    u.lang = 'es-CL';
    const voice = synth.getVoices().find((v) => /^es[-_](CL|MX|419|US|AR|CO)/i.test(v.lang)) || synth.getVoices().find((v) => /^es/i.test(v.lang));
    if (voice) u.voice = voice;
    synth.speak(u);
  }

  /* ---------- eventos ---------- */
  function wire() {
    document.addEventListener('click', (e) => {
      const choice = e.target.closest('.choice[data-id]');
      if (choice && !choice.disabled) {
        const single = choice.closest('[data-choices]');
        const multi = choice.closest('[data-multi]');
        if (single) {
          const path = single.dataset.choices;
          set(state, path, choice.dataset.id);
          if (path === 'plan') flags.autoPlan = false;
          if (path === 'contact.kind') state.contact.value = '';
          if (path === 'style.theme') state.style.accent = null; // cada tema trae su acento
          if (path === 'car.type') applyVehicle();
        } else if (multi) {
          const path = multi.dataset.multi;
          const max = Number(multi.dataset.max) || Infinity;
          const arr = get(state, path).slice();
          const i = arr.indexOf(choice.dataset.id);
          if (i >= 0) arr.splice(i, 1);
          else if (arr.length < max) arr.push(choice.dataset.id);
          else return;
          set(state, path, arr);
        }
        update();
        return;
      }
      const sw = e.target.closest('.swatch');
      if (sw) { state.car.color = sw.dataset.color; update(); return; }
      const tab = e.target.closest('.tab');
      if (tab) { ui.screen = tab.dataset.screen; update(); return; }
      const km = e.target.closest('#route button');
      if (km && !km.disabled) { go(Number(km.dataset.n)); return; }
    });

    document.addEventListener('input', (e) => {
      const el = e.target.closest('[data-bind]');
      if (!el) return;
      let v;
      if (el.type === 'checkbox') v = el.checked;
      else if (el.type === 'number' || el.hasAttribute('data-number')) v = el.value === '' ? 0 : Number(el.value);
      else v = el.value;
      set(state, el.dataset.bind, v);
      update();
    });

    $('#restr-demo').addEventListener('click', () => {
      const f = state.features;
      if (f.country !== 'CL') {
        const box2 = $('#restr-note');
        const city = TB_PICO.citiesOf(f.country).find((c) => f.restrDigit !== '' && TB_PICO.sampleDate(c, f.restrDigit));
        if (!city) { box2.className = 'note warn'; box2.textContent = f.restrDigit === '' ? 'Elige el último dígito de tu patente para ver el aviso.' : 'No encontramos un día cercano en que te toque con la regla guardada (puede haber vencido).'; return; }
        const n2 = TB_PICO.notice(city, f.restrDigit, TB_PICO.sampleDate(city, f.restrDigit));
        ui.screen = 'inicio';
        update();
        if (n2) TB.preview.showZoneNotice(n2);
        return;
      }
      // Ejemplo: un día de tu restricción, en plena temporada y a media mañana.
      const ctx = { weekday: f.restrDays[0] || 'lun', month: 6, minutes: 9 * 60 };
      const n = O.restrictionNotice(f, ctx);
      const box = $('#restr-note');
      if (!n) { box.className = 'note warn'; box.textContent = f.restrType === 'nuevo' ? 'Con tu auto no hay aviso permanente: no hay nada que mostrar.' : 'Elige el último dígito de tu patente para ver el aviso.'; return; }
      TB.preview.showZoneNotice(n);
      ui.screen = 'inicio';
      update();
      TB.preview.showZoneNotice(n);
    });
    $('#accent-input').addEventListener('input', (e) => { state.style.accent = e.target.value; update(); });
    $('#accent-swatches').addEventListener('click', (e) => { const b = e.target.closest('[data-accent]'); if (b) { state.style.accent = b.dataset.accent; update(); } });
    $('#greet-slots').addEventListener('click', (e) => { const b = e.target.closest('[data-greet]'); if (b) { state.car.greeting = b.dataset.greet; update(); } });
    $('#greet-listen').addEventListener('click', listenGreeting);
    $('#phrase-slots').addEventListener('click', (e) => { const b = e.target.closest('[data-phrase]'); if (b) { state.car.phrase = b.dataset.phrase; update(); } });
    $('#accent-reset').addEventListener('click', () => { state.style.accent = null; update(); });
    $('#next').addEventListener('click', () => go(ui.step + 1));
    $('#back').addEventListener('click', () => go(ui.step - 1));
    $('#mode-day').addEventListener('click', () => { ui.night = false; update(); });
    $('#mode-night').addEventListener('click', () => { ui.night = true; update(); });
    TB.preview.onGo = (id) => { ui.screen = id; update(); };

    $('#photo').addEventListener('change', (e) => { const f = e.target.files && e.target.files[0]; if (f) loadPhoto(f); e.target.value = ''; });
    $('#photo-clear').addEventListener('click', clearPhoto);
    $('#lights-stage').addEventListener('click', (e) => {
      if (!cutCanvas || lightPts.length >= MAX_LIGHTS) return;
      const r = $('#lights-cv').getBoundingClientRect();
      const x = (e.clientX - r.left) / r.width;
      const y = (e.clientY - r.top) / r.height;
      if (x < 0 || x > 1 || y < 0 || y > 1) return;
      lightPts.push({ kind: 'faro', x: Math.round(x * 1000) / 1000, y: Math.round(y * 1000) / 1000, size: 1 });
      $('#lights-status').textContent = `${lightPts.length} ${lightPts.length > 1 ? 'luces marcadas' : 'luz marcada'}. Mira cómo queda de noche con el botón “Luces encendidas” de la pantalla.`;
      drawLights();
    });
    $('#lights-undo').addEventListener('click', () => { lightPts.pop(); drawLights(); });
    $('#lights-clear').addEventListener('click', () => { lightPts = []; $('#lights-status').textContent = 'Luces borradas.'; drawLights(); });
    $('#lights-auto').addEventListener('click', detectLights);
    $('#bg-file').addEventListener('change', (e) => { const f = e.target.files && e.target.files[0]; if (f) loadBg(f); e.target.value = ''; });
    $('#bg-clear').addEventListener('click', () => { if (state.style.bg) URL.revokeObjectURL(state.style.bg); state.style.bg = null; update(); });
    $('#photo-mode').addEventListener('click', (e) => { const b = e.target.closest('[data-mode]'); if (b) showPhoto(b.dataset.mode); });
    $('#pay').addEventListener('click', pay);
    $('#pay-done').addEventListener('click', (e) => {
      const order = e.target.closest('[data-copy-order]');
      if (order) {
        const text = $('#pay-done').dataset.order || '';
        const ok = () => { $('#copy-note').textContent = '¡Copiado! Pégalo en el mensaje de contacto.'; };
        if (navigator.clipboard) navigator.clipboard.writeText(text).then(ok, () => { $('#copy-note').textContent = 'No pudimos copiar. Usa el botón de enviar.'; }); else ok();
        return;
      }
      const b = e.target.closest('[data-copy]');
      if (!b) return;
      const done = () => { const t = b.textContent; b.textContent = '¡Copiado!'; setTimeout(() => { b.textContent = t; }, 1500); };
      if (navigator.clipboard) navigator.clipboard.writeText(b.dataset.copy).then(done, () => {}); else done();
    });
    $('#nose-test').addEventListener('click', (e) => { const b = e.target.closest('[data-test]'); if (!b) return; state.radio.type = b.dataset.test; update(); });
    $('#shot-file').addEventListener('change', (e) => { const f = e.target.files && e.target.files[0]; if (f) loadShot(f); e.target.value = ''; });
    $('#shot-clear').addEventListener('click', () => { state.radio.screen = null; state.radio.analysis = null; $('#shot-error').hidden = true; renderShot(); update(); });
    $('#shot-result').addEventListener('click', (e) => { if (e.target.closest('#shot-apply')) applyRecommendation(); });
    renderShot();
  }

  /* ---------- captura de la radio: análisis ---------- */
  const COMPAT = { si: ['', 'Compatible'], quizas: ['warn', 'Posiblemente compatible'], no: ['bad', 'No parece compatible'] };
  const ratioText = (w, h) => {
    const r = Math.max(w, h) / Math.min(w, h);
    const known = [[16 / 9, '16:9'], [16 / 10, '16:10'], [2, '2:1'], [4 / 3, '4:3'], [15 / 9, '15:9'], [3, '3:1']];
    const best = known.reduce((a, b) => (Math.abs(b[0] - r) < Math.abs(a[0] - r) ? b : a));
    return Math.abs(best[0] - r) < 0.08 ? best[1] : r.toFixed(2) + ':1';
  };

  function renderShot() {
    const box = $('#shot-result');
    const { screen, analysis: a } = state.radio;
    $('#shot-clear').hidden = !screen && !a;
    if (!screen && !a) { box.hidden = true; box.innerHTML = ''; return; }
    const parts = [];
    if (screen && !a) parts.push(`<dl class="specs"><dt>Pantalla</dt><dd>${screen.w} × ${screen.h} (${esc(ratioText(screen.w, screen.h))}${screen.h > screen.w ? ', vertical' : ''})</dd></dl>`);
    if (a) {
      const c = COMPAT[a.compatible] || COMPAT.quizas;
      parts.push(`<p class="verdict ${c[0]}"><b>${c[1]}.</b> ${esc(a.reason)}</p>`);
      const specs = [];
      if (a.brand_guess) specs.push(['Modelo', a.brand_guess]);
      if (a.android_version) specs.push(['Android', a.android_version]);
      if (screen) specs.push(['Pantalla', `${screen.w} × ${screen.h} (${ratioText(screen.w, screen.h)}${screen.h > screen.w ? ', vertical' : ''})`]);
      if (specs.length) parts.push(`<dl class="specs">${specs.map(([k, v]) => `<dt>${esc(k)}</dt><dd>${esc(v)}</dd>`).join('')}</dl>`);
      const rows = [];
      if (a.current_launcher) rows.push(`<li>Pantalla actual: ${esc(a.current_launcher)}</li>`);
      if (a.visible_apps.length) rows.push(`<li>Apps que vimos: ${esc(a.visible_apps.join(', '))}</li>`);
      a.notes.forEach((n) => rows.push(`<li>${esc(n)}</li>`));
      if (rows.length) parts.push(`<ul>${rows.join('')}</ul>`);
      if (a.compatible !== 'no') {
        const lay = (TB.layouts.find((l) => l.id === a.recommended.layout) || {}).name || a.recommended.layout;
        const th = (TB.themes.find((t) => t.id === a.recommended.theme) || {}).name || a.recommended.theme;
        parts.push(`<p class="rec">Te recomendamos: distribución <b>${esc(lay)}</b>, tema <b>${esc(th)}</b> y ${a.recommended.dock_apps.length} apps en la barra.</p><button type="button" class="btn ghost" id="shot-apply">Aplicar esta recomendación</button>`);
      }
    }
    box.innerHTML = parts.join('');
    box.hidden = false;
  }

  function setShotBusy(on) {
    const lab = $('label[for="shot-file"]');
    lab.textContent = on ? 'Analizando tu captura…' : 'Subir captura de mi radio';
    lab.setAttribute('aria-disabled', String(on));
    $('#shot-file').disabled = on;
  }

  async function loadShot(file) {
    const err = $('#shot-error');
    err.hidden = true;
    const fail = (m) => { err.textContent = m; err.hidden = false; };
    if (!file.type.startsWith('image/')) return fail('Elige un archivo de imagen.');
    if (file.size > 20 * 1024 * 1024) return fail('La imagen pesa más de 20 MB. Elige una más liviana.');
    let dataUrl;
    try {
      const bmp = await createImageBitmap(file);
      state.radio.screen = { w: bmp.width, h: bmp.height };
      state.radio.analysis = null;
      const k = Math.min(1, 1280 / Math.max(bmp.width, bmp.height));
      const c = document.createElement('canvas');
      c.width = Math.round(bmp.width * k);
      c.height = Math.round(bmp.height * k);
      c.getContext('2d').drawImage(bmp, 0, 0, c.width, c.height);
      dataUrl = c.toDataURL('image/jpeg', 0.82);
      bmp.close && bmp.close();
    } catch (_) {
      return fail('No pudimos leer esa imagen. Prueba con un PNG o JPG.');
    }
    renderShot();
    update();
    setShotBusy(true);
    try {
      const r = await fetch('/api/analyze-radio', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ image: dataUrl }) });
      const data = await r.json().catch(() => ({}));
      if (r.ok && data.ok) {
        state.radio.analysis = data.analysis;
        const a = data.analysis;
        if (state.radio.type === 'nose' || !state.radio.type) {
          if (a.compatible === 'si') state.radio.type = 'android';
          else if (a.compatible === 'no' && a.shows_head_unit && !a.android_detected) state.radio.type = 'original';
        }
        if (!state.radio.model && a.brand_guess) state.radio.model = a.brand_guess.slice(0, 40);
      } else if (r.status === 503) {
        fail('El análisis automático aún no está disponible. Guardamos las medidas de tu pantalla para adaptar el diseño.');
      } else {
        fail((data && data.error) || 'No pudimos analizar la captura. Inténtalo de nuevo.');
      }
    } catch (_) {
      fail('No hay conexión para analizar la captura. Guardamos las medidas de tu pantalla.');
    }
    setShotBusy(false);
    renderShot();
    update();
  }

  function applyRecommendation() {
    const a = state.radio.analysis;
    if (!a) return;
    state.style.layout = a.recommended.layout;
    state.style.theme = a.recommended.theme;
    state.style.apps = a.recommended.dock_apps.slice(0, 4);
    update();
  }

  /* ---------- foto (queda en el navegador) ---------- */
  /* ---------- luces encendidas (versión nocturna de la foto) ---------- */
  let cutCanvas = null; // el auto recortado, para dibujar su versión de noche
  let lightPts = []; // [{ kind, x, y, size }] con x e y entre 0 y 1
  let nightUrl = null;
  const MAX_LIGHTS = 8;

  function drawLights() {
    const box = $('#lights-box');
    box.hidden = !cutCanvas;
    if (!cutCanvas) return;
    const night = TB.lights.night(cutCanvas, lightPts);
    const cv = $('#lights-cv');
    cv.width = night.width;
    cv.height = night.height;
    cv.getContext('2d').drawImage(night, 0, 0);
    $('#lights-marks').innerHTML = lightPts.map((p) => `<i class="mark ${p.kind === 'luz_trasera' ? 'rear' : ''}" style="left:${p.x * 100}%;top:${p.y * 100}%"></i>`).join('');
    $('#lights-undo').disabled = !lightPts.length;
    $('#lights-clear').disabled = !lightPts.length;
    night.toBlob((b) => {
      if (!b) return;
      if (nightUrl) URL.revokeObjectURL(nightUrl);
      nightUrl = URL.createObjectURL(b);
      state.car.photoNight = nightUrl;
      update();
    }, 'image/png');
  }

  function setCut(canvas) {
    cutCanvas = canvas;
    lightPts = [];
    $('#lights-status').textContent = canvas ? 'Aún no marcaste ningún faro: de noche tu auto se verá apagado.' : '';
    if (!canvas && nightUrl) { URL.revokeObjectURL(nightUrl); nightUrl = null; state.car.photoNight = null; }
    drawLights();
  }

  async function detectLights() {
    if (!cutCanvas) return;
    const status = $('#lights-status');
    const btn = $('#lights-auto');
    btn.disabled = true;
    status.textContent = 'Buscando los faros de tu auto…';
    try {
      const k = Math.min(1, 800 / Math.max(cutCanvas.width, cutCanvas.height));
      const c = document.createElement('canvas');
      c.width = Math.round(cutCanvas.width * k);
      c.height = Math.round(cutCanvas.height * k);
      const x = c.getContext('2d');
      x.fillStyle = '#808080';
      x.fillRect(0, 0, c.width, c.height);
      x.drawImage(cutCanvas, 0, 0, c.width, c.height);
      const r = await fetch('/api/detect-lights', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ image: c.toDataURL('image/jpeg', 0.85) }) });
      const data = await r.json().catch(() => ({}));
      if (r.ok && data.ok) {
        lightPts = data.lights.slice(0, MAX_LIGHTS);
        status.textContent = lightPts.length ? `Encontramos ${lightPts.length} ${lightPts.length > 1 ? 'luces' : 'luz'}. Si falta alguna, tócala en la foto; si sobra, usa “Deshacer”.` : 'No vimos faros claros en esta foto. Tócalos tú en la imagen.';
      } else {
        status.textContent = (data && data.error) || 'No pudimos detectar los faros. Tócalos tú en la foto.';
      }
    } catch (_) {
      status.textContent = 'No hay conexión para detectar los faros. Tócalos tú en la foto.';
    }
    btn.disabled = false;
    drawLights();
  }

  const photoUrls = { cut: null, orig: null };
  function showPhoto(mode) {
    const url = mode === 'cut' && photoUrls.cut ? photoUrls.cut : photoUrls.orig;
    state.car.photo = url || null;
    state.car.photoCut = mode === 'cut' && !!photoUrls.cut;
    $$('#photo-mode [data-mode]').forEach((b) => b.setAttribute('aria-checked', String((b.dataset.mode === 'cut') === state.car.photoCut)));
    update();
  }
  function clearPhoto() {
    Object.keys(photoUrls).forEach((k) => { if (photoUrls[k]) URL.revokeObjectURL(photoUrls[k]); photoUrls[k] = null; });
    state.car.photo = null;
    state.car.photoCut = false;
    $('#photo-mode').hidden = true;
    $('#photo-status').hidden = true;
    setCut(null);
    update();
  }
  async function loadPhoto(file) {
    const err = $('#photo-error');
    const status = $('#photo-status');
    err.hidden = true;
    const fail = (m) => { err.textContent = m; err.hidden = false; status.hidden = true; };
    if (!file.type.startsWith('image/')) return fail('Elige un archivo de imagen.');
    if (file.size > 15 * 1024 * 1024) return fail('La foto pesa más de 15 MB. Elige una más liviana.');
    let c;
    try {
      const bmp = await createImageBitmap(file);
      const k = Math.min(1, 1100 / Math.max(bmp.width, bmp.height));
      c = document.createElement('canvas');
      c.width = Math.round(bmp.width * k);
      c.height = Math.round(bmp.height * k);
      c.getContext('2d').drawImage(bmp, 0, 0, c.width, c.height);
    } catch (_) {
      return fail('No pudimos leer esa foto. Prueba con otra.');
    }
    const toUrl = (cv, type, q) => new Promise((r) => cv.toBlob((b) => r(URL.createObjectURL(b)), type, q));
    Object.keys(photoUrls).forEach((k) => { if (photoUrls[k]) URL.revokeObjectURL(photoUrls[k]); photoUrls[k] = null; });
    photoUrls.orig = await toUrl(c, 'image/jpeg', 0.85);
    showPhoto('orig');
    status.textContent = 'Quitando el fondo de tu foto… la primera vez puede tardar unos segundos.';
    status.hidden = false;
    $('#photo-mode').hidden = true;
    setCut(null);
    let res = null;
    try { res = TB.cutout ? await TB.cutout(c) : null; } catch (_) { res = null; }
    if (res) {
      photoUrls.cut = await toUrl(res.canvas, 'image/png');
      showPhoto('cut');
      setCut(res.canvas);
      status.textContent = res.method === 'ia'
        ? 'Listo: le quitamos el fondo a tu foto. Si no se ve bien, elige “Foto original”.'
        : 'Le quitamos el fondo con un método simple (sin conexión). Si no se ve bien, elige “Foto original”.';
      $('#photo-mode').hidden = false;
    } else {
      status.textContent = 'No pudimos separar el auto del fondo en esta foto. Usamos la foto tal cual; prueba con otra en que se vea el auto completo.';
    }
  }

  /* ---------- imagen de fondo propia (queda en el navegador) ---------- */
  async function loadBg(file) {
    const err = $('#bg-error');
    err.hidden = true;
    const fail = (m) => { err.textContent = m; err.hidden = false; };
    if (!file.type.startsWith('image/')) return fail('Elige un archivo de imagen.');
    if (file.size > 15 * 1024 * 1024) return fail('La imagen pesa más de 15 MB. Elige una más liviana.');
    try {
      const bmp = await createImageBitmap(file);
      const k = Math.min(1, 1280 / Math.max(bmp.width, bmp.height));
      const c = document.createElement('canvas');
      c.width = Math.round(bmp.width * k);
      c.height = Math.round(bmp.height * k);
      c.getContext('2d').drawImage(bmp, 0, 0, c.width, c.height);
      // PNG si tiene transparencia (logos); si no, JPG liviano.
      const px = c.getContext('2d').getImageData(0, 0, c.width, c.height).data;
      let alpha = false;
      for (let i = 3; i < px.length; i += 4 * 97) if (px[i] < 250) { alpha = true; break; }
      const blob = await new Promise((r) => c.toBlob(r, alpha ? 'image/png' : 'image/jpeg', 0.85));
      if (state.style.bg) URL.revokeObjectURL(state.style.bg);
      state.style.bg = URL.createObjectURL(blob);
      if (alpha && state.style.bgMode === 'photo') state.style.bgMode = 'logo';
      update();
    } catch (_) {
      fail('No pudimos leer esa imagen. Prueba con otra.');
    }
  }

  /* ---------- pago ---------- */
  function payload() {
    const copy = JSON.parse(JSON.stringify(state));
    delete copy.car.photo; // la foto nunca viaja en el pedido
    delete copy.car.photoNight;
    delete copy.style.bg;
    return copy;
  }

  function showErrors(list) {
    const ul = $('#pay-errors');
    ul.innerHTML = list.map((m) => `<li>${esc(m)}</li>`).join('');
    ul.hidden = !list.length;
    if (list.length) ul.scrollIntoView({ block: 'center', behavior: 'smooth' });
  }

  /* ---------- foto del pedido (se envía solo al hacer el pedido) ---------- */
  const blobToDataUrl = (url) => fetch(url).then((r) => r.blob()).then((b) => new Promise((ok, fail) => {
    const f = new FileReader();
    f.onload = () => ok(f.result);
    f.onerror = fail;
    f.readAsDataURL(b);
  }));

  /** Sube la foto del auto (y su versión de noche) junto al pedido. Devuelve 'ok', 'none' (no hay foto) o 'fail'. */
  async function uploadOrderPhotos(orderId, token) {
    if (!state.car.photo) return 'none';
    const send = async (kind, url) => {
      const image = await blobToDataUrl(url);
      const r = await fetch('/api/order-photo', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ orderId, token, kind, image, cut: state.car.photoCut === true, lights: lightPts }),
      });
      const d = await r.json().catch(() => ({}));
      return r.ok && d.ok;
    };
    try {
      if (!(await send('day', state.car.photo))) return 'fail';
      if (state.car.photoCut && nightUrl) await send('night', nightUrl); // la de noche es un extra: si falla, igual queda la de día
      return 'ok';
    } catch (_) {
      return 'fail';
    }
  }

  /* ---------- pago por transferencia bancaria ---------- */
  const waNumber = (raw) => {
    const d = String(raw || '').replace(/\D/g, '');
    return d.length === 9 && d[0] === '9' ? '56' + d : d;
  };

  /** Texto del pedido para mandárselo a quien arma la pantalla: resumen legible y los datos para armarla. */
  function orderMessage(orderId, total, photo) {
    const rows = $$('#summary div').map((d) => `${$('dt', d).textContent}: ${$('dd', d).textContent}`);
    const q = O.quote(state);
    const clean = O.validateOrder(payload()).clean;
    return [
      `Hola, soy ${state.car.name || 'cliente'}. Hice el pedido ${orderId} en Tablero y ya transferí ${O.money(total)}.`,
      `Plan: ${q.lines.map((l) => l.name).join(' + ')}`,
      ...rows,
      `Contacto: ${state.contact.kind === 'email' ? 'correo' : 'WhatsApp'} ${state.contact.value}`,
      photo === 'ok' ? 'Mi foto ya quedó guardada con el pedido.' : state.car.photo ? 'No pude subir mi foto: te la envío por aquí.' : 'Te adjunto el comprobante y la foto de mi auto.',
      '',
      'Datos para armarlo (no lo borres):',
      JSON.stringify(clean),
    ].join('\n');
  }

  /** Pago coordinado en el contacto de Daniexpress: el cliente copia su pedido y lo pega allá. */
  function showContactOrder(data) {
    const site = TB.site || {};
    const web = site.web || {};
    const msg = orderMessage(data.orderId, data.total, data.photo).replace(' y ya transferí ', ' por ');
    const wa = waNumber(site.whatsapp);
    let go = '';
    if (wa) go = `<a class="btn cta" href="https://wa.me/${wa}?text=${encodeURIComponent(msg)}" target="_blank" rel="noopener">Enviar mi pedido por WhatsApp</a>`;
    else if (site.supportEmail) go = `<a class="btn cta" href="mailto:${esc(site.supportEmail)}?subject=${encodeURIComponent('Pedido ' + data.orderId)}&body=${encodeURIComponent(msg)}">Enviar mi pedido por correo</a>`;
    else if (web.url) go = `<a class="btn cta" href="${esc(web.url)}" target="_blank" rel="noopener">Ir al contacto de ${esc(web.name || 'Daniexpress')}</a>`;
    const done = $('#pay-done');
    done.hidden = false;
    done.innerHTML = `<b>Pedido ${esc(data.orderId)}</b>
      <p>Tu pedido está listo. Tu pantalla es <b>gratis</b>. Envíaselo a <b>${esc(web.name || 'Daniexpress')}</b> y ellos te mandan tu código de activación.</p>
      <ol class="mini-steps">
        <li>Copia tu pedido (botón de abajo).</li>
        <li>Ábrelo en ${esc(web.name || 'Daniexpress')} y pégalo en el mensaje de contacto.</li>
        ${data.photo === 'ok' ? '<li>Tu foto ya quedó guardada con tu pedido: no tienes que enviarla.</li>' : '<li>Envía también la foto de tu auto.</li>'}
      </ol>
      <div class="codebtns"><button type="button" class="btn primary" data-copy-order>Copiar mi pedido</button>${go}</div>
      <p class="hint" id="copy-note">Tu pedido incluye todo lo que elegiste, así que no tienes que explicar nada.</p>
      ${data.photo === 'fail' ? '<p class="error">No pudimos guardar tu foto. Envíasela a Daniexpress junto con tu pedido.</p>' : ''}`;
    done.dataset.order = msg;
    done.scrollIntoView({ block: 'center', behavior: 'smooth' });
  }

  function showTransfer(data) {
    const site = TB.site || {};
    const bank = site.bank || {};
    const total = data.total;
    const done = $('#pay-done');
    const bankOk = ['bank', 'type', 'number', 'holder', 'rut'].every((k) => String(bank[k] || '').trim());
    const row = (label, value) => `<div class="brow"><span>${esc(label)}</span><b>${esc(value)}</b><button type="button" class="btn ghost sm" data-copy="${esc(value)}">Copiar</button></div>`;
    const msg = orderMessage(data.orderId, total);
    const wa = waNumber(site.whatsapp);
    let send = '';
    if (wa) send = `<a class="btn cta" href="https://wa.me/${wa}?text=${encodeURIComponent(msg)}" target="_blank" rel="noopener">Enviar mi pedido y comprobante por WhatsApp</a>`;
    else if (site.supportEmail) send = `<a class="btn cta" href="mailto:${esc(site.supportEmail)}?subject=${encodeURIComponent('Pedido ' + data.orderId)}&body=${encodeURIComponent(msg)}">Enviar mi pedido y comprobante por correo</a>`;
    else if (site.web && site.web.url) send = `<a class="btn cta" href="${esc(site.web.url)}" target="_blank" rel="noopener">Escríbenos en ${esc(site.web.name || 'nuestra web')} con tu pedido ${esc(data.orderId)}</a>`;
    done.hidden = false;
    done.innerHTML = `<b>Pedido ${esc(data.orderId)}</b>
      <p>${bankOk ? 'Desde la app de tu banco, transfiere <b>exactamente ' + esc(O.money(total)) + '</b> a esta cuenta:' : 'Aún no están cargados los datos de la cuenta en el sitio. Escríbenos con tu número de pedido y te los enviamos.'}</p>
      ${bankOk ? `<div class="bank">${row('Banco', bank.bank)}${row('Tipo de cuenta', bank.type)}${row('Número de cuenta', bank.number)}${row('Titular', bank.holder)}${row('RUT', bank.rut)}${row('Monto', String(Math.round(total)))}${row('Referencia', data.orderId)}</div><p class="hint">Escribe <b>${esc(data.orderId)}</b> como referencia o comentario para reconocer tu pago más rápido.</p>` : ''}
      <p><b>Después:</b> envíanos el comprobante y la foto de tu auto. Te mandamos tu código apenas confirmamos el pago.</p>
      <div class="codebtns">${send}</div>
      ${!send ? '<p class="hint">Escríbenos en la sección de contacto con tu número de pedido.</p>' : '<p class="hint">Se abre un mensaje ya escrito con tu pedido: solo adjunta el comprobante y la foto de tu auto.</p>'}`;
    done.scrollIntoView({ block: 'center', behavior: 'smooth' });
  }

  async function pay() {
    $('#pay-done').hidden = true;
    const local = O.validateOrder(payload());
    if (!local.ok) return showErrors(local.errors);
    showErrors([]);

    const btn = $('#pay');
    const label = btn.textContent;
    btn.disabled = true;
    btn.textContent = 'Enviando pedido…';
    try {
      const res = await fetch('/api/checkout', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload()) });
      let data = null;
      try { data = await res.json(); } catch (_) { /* respuesta sin JSON */ }
      if (!data) { showErrors(['No pudimos conectar con el servidor. Revisa tu internet e intenta de nuevo.']); return; }
      if (!data.ok) { showErrors(data.errors || [data.error || 'No pudimos procesar tu pedido.']); return; }
      if ((TB.site || {}).payment === 'contact') {
        if (state.car.photo) btn.textContent = 'Guardando tu foto…';
        data.photo = await uploadOrderPhotos(data.orderId, data.photoToken);
        showContactOrder(data);
        return;
      }
      if ((TB.site || {}).payment === 'transfer') { showTransfer(data); return; }
      if (data.paymentUrl) { window.location.href = data.paymentUrl; return; }
      const done = $('#pay-done');
      done.hidden = false;
      done.innerHTML = `<b>Pedido ${esc(data.orderId)}</b><br>${esc(data.message || 'Recibimos tu pedido. Te escribiremos con los pasos para pagar.')}`;
      done.scrollIntoView({ block: 'center', behavior: 'smooth' });
    } catch (_) {
      showErrors(['No pudimos conectar con el servidor. Revisa tu internet e intenta de nuevo.']);
    } finally {
      btn.disabled = false;
      btn.textContent = label;
    }
  }

  /* ---------- arranque ---------- */
  function init() {
    const site = TB.site || {};
    $('#brand-name').textContent = site.name || 'Tablero';
    $('#foot-name').textContent = site.name || 'Tablero';
    $('#year').textContent = new Date().getFullYear();
    const web = site.web && site.web.name ? site.web : null;
    const webUrl = web && /^https?:\/\//i.test(web.url || '') ? web.url : web && web.url ? 'https://' + web.url : '';
    const ig = site.instagram ? `<a href="https://instagram.com/${esc(site.instagram)}" rel="noopener">Instagram @${esc(site.instagram)}</a>` : '';
    const contact = [];
    if (site.supportEmail) contact.push(`<a href="mailto:${esc(site.supportEmail)}">${esc(site.supportEmail)}</a>`);
    if (site.whatsapp) contact.push(`WhatsApp ${esc(site.whatsapp)}`);
    if (ig) contact.push(ig);
    if (web) contact.push(webUrl ? `<a href="${esc(webUrl)}" rel="noopener">${esc(web.name)}</a>` : esc(web.name));
    $('#foot-contact').innerHTML = contact.join(' · ');

    const links = [];
    if (site.supportEmail) links.push(`<a class="btn cta" href="mailto:${esc(site.supportEmail)}">${esc(site.supportEmail)}</a>`);
    if (web) links.push(webUrl ? `<a class="btn cta" href="${esc(webUrl)}" rel="noopener">Contáctanos en ${esc(web.name)}</a>` : `<span class="contact-name">${esc(web.name)}</span>`);
    if (site.instagram) links.push(`<a class="btn ghost" href="https://instagram.com/${esc(site.instagram)}" rel="noopener">Instagram @${esc(site.instagram)}</a>`);
    $('#contact-links').innerHTML = links.join('') || 'Pronto publicaremos aquí nuestros datos de contacto.';
    const extra = [site.supportEmail && 'por correo', site.instagram && 'por Instagram'].filter(Boolean).join(' o ');
    const how = web ? `Contáctanos en ${web.name}${extra ? ' o ' + extra : ''}` : extra ? `Escríbenos ${extra}` : '';
    if (how) $('#contact-text').textContent = `${how} y te respondemos. Te respondemos por WhatsApp.`;
    if (site.freeUrl) { $('#free-link').href = site.freeUrl; $('#free-option').hidden = false; }
    const plans = C.plans;
    const prices = plans ? Object.values(plans).map((p) => p.price).filter((n) => typeof n === 'number') : [];
    if (prices.length) $('#price-from').textContent = Math.min(...prices) > 0 ? 'Desde $' + Math.min(...prices).toLocaleString('es-CL') : 'Gratis.';

    load();
    build();
    TB.preview.mount($('#device'));
    wire();
    update();

    // Volvió de PayPal sin pagar: su configuración sigue guardada, lo dejamos en el paso de pago.
    const params = new URLSearchParams(window.location.search);
    if (params.get('pago') === 'cancelado') {
      go(5);
      const done = $('#pay-done');
      done.hidden = false;
      done.textContent = 'Cancelaste el pago en PayPal. Tu pedido sigue aquí: cuando quieras, presiona Pagar de nuevo.';
      window.history.replaceState(null, '', window.location.pathname);
    }
  }
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', init);
  else init();
})();
