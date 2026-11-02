/*
 * Lógica de pedido compartida: plan requerido, cotización y validación.
 * Corre igual en el navegador (window.TBOrder) y en Node (require).
 */
(function (root, factory) {
  if (typeof module === 'object' && module.exports) module.exports = factory(require('./catalog.js'));
  else root.TBOrder = factory(root.TBCatalog);
})(typeof self !== 'undefined' ? self : this, function (C) {
  const HEX = /^#[0-9a-fA-F]{6}$/;
  const EMAIL = /^[^\s@]{1,64}@[^\s@]{1,190}\.[^\s@]{2,}$/;
  // Celular chileno: +56 9 XXXX XXXX (con o sin espacios, con o sin +56)
  const CL_MOBILE = /^(?:\+?56)?9\d{8}$/;

  const str = (v, max) =>
    typeof v === 'string' ? v.replace(/[\u0000-\u001f\u007f]/g, '').trim().slice(0, max) : '';
  const num = (v, min, max, def) => {
    const n = Number(v);
    return Number.isFinite(n) ? Math.min(max, Math.max(min, Math.round(n))) : def;
  };
  const pick = (v, list, def) => (list.includes(v) ? v : def);
  const screenOf = (s) => {
    const w = s && Number.isInteger(s.w) ? s.w : 0;
    const h = s && Number.isInteger(s.h) ? s.h : 0;
    return w >= 200 && w <= 8000 && h >= 200 && h <= 8000 ? { w, h } : null;
  };
  const money = (n) => new Intl.NumberFormat(C.locale, { style: 'currency', currency: C.currency, maximumFractionDigits: 0 }).format(n);

  /** Plan mínimo que necesita lo elegido en "estilo". */
  function requiredPlan(style) {
    const s = style || {};
    const plusTheme = C.themeTiers[s.theme] === 'plus';
    const plusSeason = C.plusSeasons.includes(s.season);
    return plusTheme || plusSeason ? 'completo' : 'esencial';
  }

  /** Por qué se necesita el plan Completo (para mostrarlo al cliente). */
  function planReasons(style) {
    const s = style || {};
    const out = [];
    if (C.themeTiers[s.theme] === 'plus') out.push('el diseño visual que elegiste');
    if (C.plusSeasons.includes(s.season)) out.push('las decoraciones de temporada');
    return out;
  }

  /** Cotización: líneas y total, calculado con el catálogo (nunca con precios del cliente). */
  function quote(order) {
    const o = order || {};
    const plan = C.plans.find((p) => p.id === o.plan) || C.plans[0];
    const lines = [{ id: plan.id, name: 'Plan ' + plan.name, price: plan.price }];
    const chosen = o.addons || {};
    C.addons.forEach((a) => {
      if (a.available !== false && chosen[a.id]) lines.push({ id: a.id, name: a.name, price: a.price });
    });
    const total = lines.reduce((s, l) => s + l.price, 0);
    return { currency: C.currency, lines, total };
  }

  /** Normaliza un celular chileno a +569XXXXXXXX o devuelve null. */
  function normalizeMobile(raw) {
    const digits = String(raw || '').replace(/[\s().-]/g, '');
    if (!CL_MOBILE.test(digits)) return null;
    return '+56' + digits.slice(-9);
  }

  /**
   * Valida y limpia un pedido. Devuelve { ok, errors, clean }.
   * `clean` solo contiene campos conocidos: lo demás se descarta.
   */
  function validateOrder(input) {
    const o = input && typeof input === 'object' ? input : {};
    const errors = [];
    const radio = o.radio || {};
    const car = o.car || {};
    const style = o.style || {};
    const f = o.features || {};
    const contact = o.contact || {};

    const radioType = pick(radio.type, C.radioTypes, null);
    if (!radioType) errors.push('Cuéntanos cómo es tu radio.');
    if (radioType === 'original') {
      errors.push('Las radios originales de fábrica que no permiten instalar apps no son compatibles.');
    } else if (radioType === 'nose') {
      errors.push('Confirma que tu radio es Android de pantalla grande antes de pagar.');
    }

    const apps = Array.isArray(style.apps)
      ? [...new Set(style.apps.filter((a) => C.apps.includes(a)))].slice(0, 4)
      : [];

    const clean = {
      radio: {
        type: radioType,
        brand: str(radio.brand, 24),
        model: str(radio.model, 40),
        // Medidas de la captura que subió el cliente (solo números; la imagen nunca viaja con el pedido).
        screen: screenOf(radio.screen),
      },
      car: {
        type: pick(car.type, C.vehicles, 'auto'),
        color: HEX.test(car.color) ? car.color.toLowerCase() : '#b3261e',
        name: str(car.name, 24),
        greeting: str(car.greeting, 24) || 'Hola',
        phrase: str(car.phrase, 28),
        plate: str(car.plate, 10).toUpperCase(),
      },
      style: {
        theme: pick(style.theme, Object.keys(C.themeTiers), 'ruta'),
        layout: pick(style.layout, C.layouts, 'tarjetas'),
        scene: pick(style.scene, C.scenes, 'tema'),
        sceneTime: pick(style.sceneTime, C.sceneTimes, 'auto'),
        season: pick(style.season, C.seasons, 'ninguna'),
        accent: HEX.test(style.accent) ? style.accent.toLowerCase() : null,
        apps,
      },
      features: {
        oilOn: !!f.oilOn,
        odometer: num(f.odometer, 0, 999999, 0),
        lastOil: num(f.lastOil, 0, 999999, 0),
        oilEvery: pick(Number(f.oilEvery), [3000, 5000, 7500, 10000], 5000),
        others: Array.isArray(f.others) ? f.others.filter((id) => C.maintenance.some((m) => m.id === id)) : [],
        docsOn: !!f.docsOn,
        rtMonth: num(f.rtMonth, 1, 12, 1),
        fuelOn: !!f.fuelOn,
        tankL: num(f.tankL, 10, 150, 45),
        kmPerL: num(f.kmPerL, 3, 40, 12),
        speedShow: f.speedShow !== false,
        speedAlert: pick(f.speedAlert, C.speedAlerts, 'auto'),
        manualLimit: num(f.manualLimit, 20, 140, 60),
        restrOn: !!f.restrOn,
        restrDays: Array.isArray(f.restrDays) ? [...new Set(f.restrDays.filter((d) => C.weekdays.includes(d)))] : [],
        voice: f.voice !== false,
      },
      plan: pick(o.plan, C.plans.map((p) => p.id), null),
      addons: {},
      contact: { kind: contact.kind === 'email' ? 'email' : 'whatsapp', value: '' },
      terms: o.terms === true,
    };
    C.addons.forEach((a) => {
      if (a.available !== false && o.addons && o.addons[a.id]) clean.addons[a.id] = true;
    });

    // Plan: debe existir y alcanzar para lo elegido.
    const needed = requiredPlan(clean.style);
    if (!clean.plan) {
      errors.push('Elige un plan.');
    } else if (needed === 'completo' && clean.plan === 'esencial') {
      errors.push('Tu diseño usa funciones del plan Completo.');
    }

    // Contacto
    if (clean.contact.kind === 'email') {
      const v = str(contact.value, 254).toLowerCase();
      if (EMAIL.test(v)) clean.contact.value = v;
      else errors.push('Escribe un correo válido para enviarte el código.');
    } else {
      const v = normalizeMobile(contact.value);
      if (v) clean.contact.value = v;
      else errors.push('Escribe un celular chileno válido, por ejemplo +56 9 1234 5678.');
    }

    if (!clean.terms) errors.push('Debes aceptar los términos para continuar.');

    return { ok: errors.length === 0, errors, clean };
  }

  return { requiredPlan, planReasons, quote, validateOrder, normalizeMobile, money };
});
