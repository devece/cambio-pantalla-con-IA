/*
 * Lógica de pedido compartida: validación, restricción vehicular y celulares.
 * Corre igual en el navegador (window.TBOrder) y en Node (require).
 */
(function (root, factory) {
  if (typeof module === 'object' && module.exports) module.exports = factory(require('./catalog.js'));
  else root.TBOrder = factory(root.TBCatalog);
})(typeof self !== 'undefined' ? self : this, function (C) {
  const HEX = /^#[0-9a-fA-F]{6}$/;
  const EMAIL = /^[^\s@]{1,64}@[^\s@]{1,190}\.[^\s@]{2,}$/;
  // Celulares: Chile +56 9 XXXX XXXX · Colombia +57 3XX XXX XXXX · Ecuador +593 9X XXX XXXX (con o sin espacios y con o sin el prefijo)
  const MOBILE = {
    CL: { re: /^(?:\+?56)?9\d{8}$/, prefix: '+56', tail: 9, example: '+56 9 1234 5678', name: 'chileno' },
    CO: { re: /^(?:\+?57)?3\d{9}$/, prefix: '+57', tail: 10, example: '+57 300 123 4567', name: 'colombiano' },
    EC: { re: /^(?:\+?593)?0?9\d{8}$/, prefix: '+593', tail: 9, example: '+593 99 123 4567', name: 'ecuatoriano' },
  };
  const COUNTRIES = ['CL', 'CO', 'EC'];

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
  /** Días de la semana en que le toca restricción a una patente, según la tabla de catalog.restriction. */
  function restrDaysFor(f) {
    const r = C.restriction;
    const x = f || {};
    if (!x.restrOn) return [];
    const table = r.digits[x.restrType];
    const d = Number(x.restrDigit);
    if (!table || x.restrDigit === '' || x.restrDigit == null || !Number.isInteger(d) || d < 0 || d > 9) return [];
    return C.weekdays.filter((id) => table[id].includes(d));
  }

  /** ¿Está el punto (lat, lon) dentro de la zona donde rige la restricción? (algoritmo del rayo) */
  function inRestrictionZone(lat, lon) {
    const poly = C.restriction.zone.polygon;
    if (typeof lat !== 'number' || typeof lon !== 'number' || !isFinite(lat) || !isFinite(lon)) return false;
    let inside = false;
    for (let i = 0, j = poly.length - 1; i < poly.length; j = i++) {
      const [yi, xi] = poly[i];
      const [yj, xj] = poly[j];
      if (yi > lat !== yj > lat && lon < ((xj - xi) * (lat - yi)) / (yj - yi) + xi) inside = !inside;
    }
    return inside;
  }

  /**
   * Aviso al entrar a la zona. `ctx`: { weekday: 'lun'..'vie'|'sab'|'dom', month: 1-12, minutes: minutos desde la medianoche }.
   * Devuelve { warn, text } o null si no hay nada que avisar. No sabe de festivos (la radio no los conoce).
   */
  function restrictionNotice(f, ctx) {
    const r = C.restriction;
    const x = f || {};
    if (!x.restrOn || x.restrType === 'nuevo') return null;
    const c = ctx || {};
    if (!r.season.months.includes(c.month)) return null;
    if (!C.weekdays.includes(c.weekday)) return null;
    if (c.minutes < 7 * 60 + 30 || c.minutes >= 21 * 60) return null;
    const parts = [];
    let warn = false;
    if (restrDaysFor(x).includes(c.weekday)) {
      warn = true;
      parts.push(`Hoy tienes restricción vehicular (patente terminada en ${x.restrDigit}). Rige hasta las 21:00.`);
    }
    if (x.restrType === 'sin') {
      warn = true;
      parts.push('Sin sello verde no puedes circular hoy dentro del anillo Américo Vespucio.');
    }
    return parts.length ? { warn, text: `Entraste a ${r.zone.name}. ${parts.join(' ')}` } : null;
  }

  /** Normaliza un celular del país (Chile por defecto) a +569XXXXXXXX, +573XXXXXXXXX o +5939XXXXXXXX; devuelve null si no corresponde. */
  function normalizeMobile(raw, country) {
    const m = MOBILE[country] || MOBILE.CL;
    const digits = String(raw || '').replace(/[\s().-]/g, '');
    if (!m.re.test(digits)) return null;
    return m.prefix + digits.slice(-m.tail);
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
      errors.push('Confirma que tu radio es Android de pantalla grande antes de hacer tu pedido.');
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
        greeting: str(car.greeting, 16) || 'Hola',
        phrase: str(car.phrase, 28),
        plate: str(car.plate, 10).toUpperCase(),
      },
      style: {
        theme: pick(style.theme, C.themes, 'ruta'),
        layout: pick(style.layout, C.layouts, 'tarjetas'),
        scene: pick(style.scene, C.scenes, 'tema'),
        sceneTime: pick(style.sceneTime, C.sceneTimes, 'auto'),
        season: pick(style.season, C.seasons, 'ninguna'),
        accent: HEX.test(style.accent) ? style.accent.toLowerCase() : null,
        apps,
      },
      features: {
        country: pick(f.country, COUNTRIES, 'CL'),
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
        restrType: pick(f.restrType, C.restriction.types.map((t) => t.id), 'sello'),
        restrDigit: /^[0-9]$/.test(String(f.restrDigit)) ? String(f.restrDigit) : '',
        restrDays: [],
        voice: f.voice !== false,
        miles: !!f.miles,
        gallons: !!f.gallons,
        clock12: !!f.clock12,
      },
      contact: { kind: contact.kind === 'email' ? 'email' : 'whatsapp', value: '' },
    };
    // Los días de restricción se calculan en el servidor a partir del dígito: nunca se confía en los del navegador.
    const ft = clean.features;
    ft.restrDays = ft.country === 'CL' ? restrDaysFor(ft) : [];
    if (ft.restrOn && ft.restrDigit === '' && (ft.country !== 'CL' || ft.restrType !== 'nuevo')) {
      errors.push(ft.country === 'CL' ? 'Elige el último dígito de tu patente para la restricción vehicular.' : 'Elige el último dígito de tu patente para el pico y placa.');
    }
    // Contacto
    if (clean.contact.kind === 'email') {
      const v = str(contact.value, 254).toLowerCase();
      if (EMAIL.test(v)) clean.contact.value = v;
      else errors.push('Escribe un correo válido para enviarte el código.');
    } else {
      const v = normalizeMobile(contact.value, clean.features.country);
      if (v) clean.contact.value = v;
      else errors.push(`Escribe un celular ${MOBILE[clean.features.country].name} válido, por ejemplo ${MOBILE[clean.features.country].example}.`);
    }

    return { ok: errors.length === 0, errors, clean };
  }

  return { restrDaysFor, inRestrictionZone, restrictionNotice, validateOrder, normalizeMobile };
});
