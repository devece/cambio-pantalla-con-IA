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
    radio: { type: null, model: '' },
    car: { type: 'auto', color: TB.carColors[0], name: '', greeting: 'Hola', photo: null },
    style: { theme: 'ruta', layout: 'tarjetas', scene: 'tema', season: 'ninguna', accent: null, apps: ['maps', 'spotify', 'waze'] },
    features: {
      oilOn: true, odometer: 45000, lastOil: 42000, oilEvery: 5000,
      others: ['neumaticos'], docsOn: true, rtMonth: 6,
      fuelOn: false, tankL: 45, kmPerL: 12,
      speedShow: true, speedAlert: 'auto', manualLimit: 60, restrOn: false, restrDays: [], voice: true,
    },
    plan: 'esencial',
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
  }
  function save() {
    try {
      const copy = JSON.parse(JSON.stringify(state));
      copy.car.photo = null; // la foto no se guarda
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
  const CHOICES = {
    'radio.type': () => [
      { id: 'android', name: 'Pantalla grande con apps', desc: 'Se instaló después. Tiene Play Store, YouTube, Waze o similares.' },
      { id: 'original', name: 'La original del auto', desc: 'Vino de fábrica, con la marca del auto.' },
      { id: 'nose', name: 'No sé', desc: 'Te mostramos cómo saberlo en 10 segundos.' },
    ],
    'car.type': () => TB.vehicles,
    'car.greeting': () => TB.greetings.map((g) => ({ id: g, name: g })),
    'style.theme': () => TB.themes.map((t) => ({ id: t.id, name: t.name, desc: t.desc, tier: t.tier, swatch: t.swatch })),
    'style.layout': () => TB.layouts,
    'style.scene': () => TB.scenes,
    'style.season': () => TB.seasons.map((s) => ({ id: s.id, name: s.name, desc: s.desc, tier: s.id === 'ninguna' ? 'base' : 'plus' })),
    'features.speedAlert': () => [
      { id: 'auto', name: 'Automático' },
      { id: 'manual', name: 'Manual' },
      { id: 'off', name: 'Sin aviso' },
    ],
    plan: () => C.plans.map((p) => ({ id: p.id, name: p.name, desc: p.blurb, price: p.price })),
    'contact.kind': () => [
      { id: 'whatsapp', name: 'WhatsApp' },
      { id: 'email', name: 'Correo' },
    ],
  };
  const MULTI = {
    'style.apps': () => TB.apps,
    'features.others': () => C.maintenance.map((m) => ({ id: m.id, name: m.name, desc: 'cada ' + fmt(m.every) + ' km' })),
    'features.restrDays': () => TB.weekdays,
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
    const sw = it.swatch ? `<span class="sw" style="background:linear-gradient(135deg,${it.swatch[0]} 0 55%,${it.swatch[1]} 55%)"></span>` : '';
    const tag = it.tier === 'plus' ? '<span class="tag">Completo</span>' : '';
    const price = it.price ? `<span class="price">${O.money(it.price)}</span>` : '';
    const attr = role === 'radio' ? 'role="radio" aria-checked="false"' : 'role="checkbox" aria-pressed="false"';
    return `<button type="button" class="choice" ${attr} data-id="${esc(it.id)}"${extra || ''}>${sw}<b>${esc(it.name)}${tag}</b>${it.desc ? `<small>${esc(it.desc)}</small>` : ''}${price}</button>`;
  }

  function build() {
    $('#contact-kind').dataset.choices = 'contact.kind';
    $$('[data-choices]').forEach((el) => {
      el.innerHTML = CHOICES[el.dataset.choices]().map((it) => choiceHtml(it, 'radio')).join('');
    });
    $$('[data-multi]').forEach((el) => {
      el.innerHTML = MULTI[el.dataset.multi]().map((it) => choiceHtml(it, 'checkbox')).join('');
    });
    $('#car-colors').innerHTML = TB.carColors
      .map((c) => `<button type="button" class="swatch" role="radio" aria-checked="false" data-color="${c}" style="background:${c}" aria-label="Color ${c}"></button>`)
      .join('');
    $('#rt-month').innerHTML = TB.months.map((m, i) => `<option value="${i + 1}">${m[0].toUpperCase() + m.slice(1)}</option>`).join('');
    $('#screen-tabs').innerHTML = SCREENS.map((s) => `<button type="button" class="tab" role="tab" data-screen="${s.id}" aria-selected="false">${s.name}</button>`).join('');
    $('#route').innerHTML = STEPS.map((n, i) => `<li data-n="${i + 1}"><button type="button" data-n="${i + 1}" aria-label="Paso ${i + 1}: ${n}"><span class="km">${i + 1}</span><small>${n}</small></button></li>`).join('');

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

  function syncChoices() {
    const need = O.requiredPlan(state.style);
    $$('[data-choices]').forEach((el) => {
      const path = el.dataset.choices;
      const cur = get(state, path);
      $$('.choice', el).forEach((b) => {
        b.setAttribute('aria-checked', String(cur === b.dataset.id));
        if (path === 'plan' && b.dataset.id === 'esencial') b.disabled = need === 'completo';
      });
    });
    $$('[data-multi]').forEach((el) => {
      const path = el.dataset.multi;
      const cur = get(state, path) || [];
      $$('.choice', el).forEach((b) => b.setAttribute('aria-pressed', String(cur.includes(b.dataset.id))));
    });
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

  function syncNotes() {
    const n = $('#radio-note');
    const r = radioNotes[state.radio.type];
    n.className = 'note' + (r && r[0] ? ' ' + r[0] : '');
    n.textContent = r ? r[1] : '';

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
    if (f.restrOn && f.restrDays.length) feats.push('restricción vehicular');
    const radio = { android: 'Android de pantalla grande', nose: 'Por confirmar', original: 'Original del auto (no compatible)' }[state.radio.type] || 'Sin elegir';
    const apps = state.style.apps.map((id) => (TB.apps.find((a) => a.id === id) || {}).name).filter(Boolean);
    const rows = [
      ['Radio', radio + (state.radio.model ? ` · ${esc(state.radio.model)}` : '')],
      ['Auto', `${veh.name}${state.car.name ? ' de ' + esc(state.car.name) : ''}${state.car.photo ? ' · con tu foto' : ''}`],
      ['Diseño', `${th.name} · ${lay.name}${season.id !== 'ninguna' ? ' · ' + season.name : ''}`],
      ['Apps', apps.length ? esc(apps.join(', ')) : 'Sin apps en la barra'],
      ['Funciones', feats.length ? feats.join(', ') : 'Ninguna activada'],
    ];
    $('#summary').innerHTML = rows.map(([k, v]) => `<div><dt>${k}</dt><dd>${v}</dd></div>`).join('');
    const q = O.quote(state);
    $('#total').textContent = O.money(q.total);
    $('#pay').textContent = `Pagar ${O.money(q.total)}`;
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
  }

  function update() {
    applyConstraints();
    TB.preview.render(state, ui);
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

    $('#accent-input').addEventListener('input', (e) => { state.style.accent = e.target.value; update(); });
    $('#accent-reset').addEventListener('click', () => { state.style.accent = null; update(); });
    $('#next').addEventListener('click', () => go(ui.step + 1));
    $('#back').addEventListener('click', () => go(ui.step - 1));
    $('#mode-day').addEventListener('click', () => { ui.night = false; update(); });
    $('#mode-night').addEventListener('click', () => { ui.night = true; update(); });
    TB.preview.onGo = (id) => { ui.screen = id; update(); };

    $('#photo').addEventListener('change', (e) => { const f = e.target.files && e.target.files[0]; if (f) loadPhoto(f); e.target.value = ''; });
    $('#photo-clear').addEventListener('click', () => {
      if (state.car.photo) URL.revokeObjectURL(state.car.photo);
      state.car.photo = null;
      update();
    });
    $('#pay').addEventListener('click', pay);
  }

  /* ---------- foto (queda en el navegador) ---------- */
  async function loadPhoto(file) {
    const err = $('#photo-error');
    err.hidden = true;
    const fail = (m) => { err.textContent = m; err.hidden = false; };
    if (!file.type.startsWith('image/')) return fail('Elige un archivo de imagen.');
    if (file.size > 15 * 1024 * 1024) return fail('La foto pesa más de 15 MB. Elige una más liviana.');
    try {
      const bmp = await createImageBitmap(file);
      const k = Math.min(1, 1100 / Math.max(bmp.width, bmp.height));
      const c = document.createElement('canvas');
      c.width = Math.round(bmp.width * k);
      c.height = Math.round(bmp.height * k);
      c.getContext('2d').drawImage(bmp, 0, 0, c.width, c.height);
      const blob = await new Promise((r) => c.toBlob(r, 'image/jpeg', 0.85));
      if (state.car.photo) URL.revokeObjectURL(state.car.photo);
      state.car.photo = URL.createObjectURL(blob);
      update();
    } catch (_) {
      fail('No pudimos leer esa foto. Prueba con otra.');
    }
  }

  /* ---------- pago ---------- */
  function payload() {
    const copy = JSON.parse(JSON.stringify(state));
    delete copy.car.photo; // la foto nunca viaja en el pedido
    return copy;
  }

  function showErrors(list) {
    const ul = $('#pay-errors');
    ul.innerHTML = list.map((m) => `<li>${esc(m)}</li>`).join('');
    ul.hidden = !list.length;
    if (list.length) ul.scrollIntoView({ block: 'center', behavior: 'smooth' });
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
    if (site.prototype) $('#ribbon').hidden = false;
    $('#brand-name').textContent = site.name || 'Tablero';
    $('#foot-name').textContent = site.name || 'Tablero';
    $('#year').textContent = new Date().getFullYear();
    const contact = [];
    if (site.supportEmail) contact.push(`<a href="mailto:${esc(site.supportEmail)}">${esc(site.supportEmail)}</a>`);
    if (site.whatsapp) contact.push(`WhatsApp ${esc(site.whatsapp)}`);
    $('#foot-contact').innerHTML = contact.join(' · ');

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
