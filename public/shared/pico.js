/*
 * Pico y placa de Colombia y Ecuador, y Hoy No Circula de México (Chile tiene su propio módulo: catalog.restriction).
 * Corre igual en el navegador (window.TBPico) y en Node (require).
 *
 * IMPORTANTE: estas reglas CAMBIAN. En Colombia rotan cada semestre (Cali, Medellín) o incluso por fecha (Bogotá), y en Ecuador
 * el sistema de Quito puede cambiar. Cada ciudad trae su fuente, cuándo se revisó y hasta cuándo vale. Pasada la fecha de
 * vigencia, la radio ya no afirma nada: avisa que hay que revisar la regla oficial.
 * Las zonas (polígonos) son aproximadas: cubren el área urbana, no el límite legal exacto.
 */
(function (root, factory) {
  if (typeof module === 'object' && module.exports) module.exports = factory();
  else root.TBPico = factory();
})(typeof self !== 'undefined' ? self : this, function () {
  const countries = [
    { id: 'CL', name: 'Chile', label: 'restricción vehicular' },
    { id: 'CO', name: 'Colombia', label: 'pico y placa' },
    { id: 'EC', name: 'Ecuador', label: 'pico y placa' },
    { id: 'MX', name: 'México', label: 'Hoy No Circula' },
  ];

  const WEEK = ['dom', 'lun', 'mar', 'mie', 'jue', 'vie', 'sab'];
  const DAY_NAME = { lun: 'lunes', mar: 'martes', mie: 'miércoles', jue: 'jueves', vie: 'viernes' };
  const LABORAL = ['lun', 'mar', 'mie', 'jue', 'vie'];

  // rule.kind 'weekday': dígitos restringidos por día de la semana. rule.kind 'parity': por día del mes (impar o par).
  // hours: ventanas en minutos desde la medianoche [inicio, fin).
  const cities = [
    {
      id: 'bogota', country: 'CO', name: 'Bogotá', label: 'pico y placa',
      rule: { kind: 'parity', odd: [6, 7, 8, 9, 0], even: [1, 2, 3, 4, 5] },
      weekdays: LABORAL, hours: [[360, 1260]],
      validFrom: null, validTo: null,
      confidence: 'oficial', checkedAt: '2026-11-05',
      source: 'https://bogota.gov.co/mi-ciudad/movilidad/pico-y-placa-en-bogota-para-la-semana-del-21-al-27-de-septiembre-2026',
      note: 'Según el calendario de la Secretaría Distrital de Movilidad: en días impares circulan las placas terminadas en 1 a 5, y en días pares las terminadas en 6, 7, 8, 9 y 0. No rige en festivos.',
      zone: [[4.47, -74.18], [4.47, -74.09], [4.56, -74.04], [4.76, -74.01], [4.84, -74.04], [4.84, -74.14], [4.78, -74.2], [4.66, -74.21], [4.55, -74.19]],
    },
    {
      id: 'medellin', country: 'CO', name: 'Medellín y Valle de Aburrá', label: 'pico y placa',
      rule: { kind: 'weekday', days: { lun: [5, 8], mar: [1, 4], mie: [0, 2], jue: [3, 6], vie: [7, 9] } },
      weekdays: LABORAL, hours: [[300, 1200]],
      validFrom: '2026-08-03', validTo: '2026-12-31',
      confidence: 'prensa', checkedAt: '2026-11-05',
      source: 'https://www.eltiempo.com/colombia/medellin/el-nuevo-pico-y-placa-en-medellin-rige-desde-este-3-de-agosto-recuerde-los-horarios-multas-y-vias-exentas-de-la-medida-3575755',
      note: 'Rotación del segundo semestre de 2026. Hay vías exentas (por ejemplo la Autopista Sur en Medellín y la vía Las Palmas) y los corregimientos no tienen restricción. No rige en festivos.',
      zone: [[6.14, -75.62], [6.14, -75.55], [6.22, -75.53], [6.3, -75.52], [6.37, -75.53], [6.37, -75.6], [6.3, -75.64], [6.22, -75.66]],
    },
    {
      id: 'cali', country: 'CO', name: 'Cali', label: 'pico y placa',
      rule: { kind: 'weekday', days: { lun: [9, 0], mar: [1, 2], mie: [3, 4], jue: [5, 6], vie: [7, 8] } },
      weekdays: LABORAL, hours: [[360, 1140]],
      validFrom: '2026-07-01', validTo: '2026-12-31',
      confidence: 'prensa', checkedAt: '2026-11-05',
      source: 'https://www.elpais.com.co/cali/pico-y-placa-en-cali-este-miercoles-1-de-julio-ojo-evite-multas-con-la-nueva-rotacion-3023.html',
      note: 'Rotación del segundo semestre de 2026 (Decreto 0814 de 2026, según la prensa). Puede haber cambios temporales por emergencias. No rige en festivos.',
      zone: [[3.33, -76.56], [3.33, -76.48], [3.45, -76.46], [3.53, -76.47], [3.56, -76.52], [3.5, -76.57], [3.4, -76.58]],
    },
    {
      id: 'quito', country: 'EC', name: 'Quito', label: 'pico y placa',
      rule: { kind: 'weekday', days: { lun: [1, 2], mar: [3, 4], mie: [5, 6], jue: [7, 8], vie: [9, 0] } },
      weekdays: LABORAL, hours: [[360, 570], [960, 1200]],
      validFrom: null, validTo: null,
      confidence: 'prensa', checkedAt: '2026-11-05',
      source: 'https://www.expreso.ec/quito/pico-placa-quito-2026-restriccion-vehicular-placas-terminadas-3-4-286671.html',
      note: 'Según la AMT (por la prensa). Se habla de un sistema de "tercera placa" para fines de 2026, que cambiaría las reglas. No rige en feriados.',
      zone: [[-0.33, -78.54], [-0.33, -78.48], [-0.2, -78.44], [-0.05, -78.44], [0.0, -78.46], [-0.05, -78.52], [-0.2, -78.56]],
    },
    {
      id: 'cdmx', country: 'MX', name: 'Ciudad de México', label: 'Hoy No Circula',
      rule: { kind: 'weekday', days: { lun: [5, 6], mar: [7, 8], mie: [3, 4], jue: [1, 2], vie: [9, 0] } },
      weekdays: LABORAL, hours: [[300, 1320]],
      validFrom: null, validTo: null,
      confidence: 'prensa', checkedAt: '2026-11-09',
      source: 'https://www.nmas.com.mx/ciudad-de-mexico/movilidad/hoy-no-circula-19-24-octubre-2026-cdmx-edomex-engomados-placas-con-restriccion-dia-horario/',
      note: 'Calendario de lunes a viernes para autos con holograma 1 y 2. Los autos con holograma 0 y 00, los eléctricos y los híbridos están exentos. Los sábados también rige para holograma 1 y 2 con otras reglas, y en contingencia ambiental pueden sumarse más restricciones: esos casos no están incluidos.',
      extra: 'Si tu auto tiene holograma 0 o 00 (o es eléctrico o híbrido), no te aplica.',
      zone: [[19.2, -99.35], [19.55, -99.3], [19.6, -99.05], [19.5, -98.95], [19.3, -98.95], [19.15, -99.05], [19.15, -99.25]],
    },
  ];

  const countryOf = (id) => countries.find((c) => c.id === id) || countries[0];
  const citiesOf = (countryId) => cities.filter((c) => c.country === countryId);
  const pad = (n) => String(n).padStart(2, '0');
  const hm = (min) => `${Math.floor(min / 60)}:${pad(min % 60)}`;
  const isoOf = (d) => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
  const hoursText = (city) => city.hours.map(([a, b]) => `de ${hm(a)} a ${hm(b)}`).join(' y ');

  /** ¿Está el punto dentro del polígono (aproximado) de la ciudad? */
  function inZone(city, lat, lon) {
    if (typeof lat !== 'number' || typeof lon !== 'number' || !isFinite(lat) || !isFinite(lon)) return false;
    const poly = city.zone;
    let inside = false;
    for (let i = 0, j = poly.length - 1; i < poly.length; j = i++) {
      const [yi, xi] = poly[i];
      const [yj, xj] = poly[j];
      if (yi > lat !== yj > lat && lon < ((xj - xi) * (lat - yi)) / (yj - yi) + xi) inside = !inside;
    }
    return inside;
  }

  /** Ciudad de ese país en la que está el punto, o null. */
  function cityAt(lat, lon, countryId) {
    return citiesOf(countryId).find((c) => inZone(c, lat, lon)) || null;
  }

  /** Dígitos con restricción ese día (array), o null si ese día de la semana no rige. */
  function restrictedDigits(city, date) {
    const wd = WEEK[date.getDay()];
    if (!city.weekdays.includes(wd)) return null;
    if (city.rule.kind === 'parity') return date.getDate() % 2 === 1 ? city.rule.odd : city.rule.even;
    return city.rule.days[wd] || null;
  }

  const expired = (city, date) => !!city.validTo && isoOf(date) > city.validTo;
  const notStarted = (city, date) => !!city.validFrom && isoOf(date) < city.validFrom;

  /** ¿Le toca a esa placa (dígito 0-9) ese día? */
  function isRestricted(city, digit, date) {
    const list = restrictedDigits(city, date);
    return !!list && list.includes(Number(digit));
  }

  /** Aviso al entrar a la ciudad: { warn, text } o null si no hay nada que avisar. */
  function notice(city, digit, now) {
    const date = now || new Date();
    if (expired(city, date)) {
      return { warn: false, text: `Entraste a ${city.name}. Tu regla de ${city.label} valía hasta el ${city.validTo}: revísala en la fuente oficial, puede haber cambiado.` };
    }
    if (!isRestricted(city, digit, date)) return null;
    const minutes = date.getHours() * 60 + date.getMinutes();
    if (!city.hours.some(([, end]) => minutes < end)) return null; // ya pasó el horario de hoy
    const inside = city.hours.some(([start, end]) => minutes >= start && minutes < end);
    return {
      warn: inside,
      text: `Entraste a ${city.name}. Hoy tienes ${city.label} (placa terminada en ${digit}): rige ${hoursText(city)}.${city.extra ? ' ' + city.extra : ''}`,
    };
  }

  /** Texto para el configurador: qué le toca a esa placa en esa ciudad. */
  function describe(city, digit) {
    const d = Number(digit);
    let when;
    if (city.rule.kind === 'parity') {
      const odd = city.rule.odd.includes(d);
      when = `no puedes circular los días ${odd ? 'impares' : 'pares'} del mes`;
    } else {
      const days = city.weekdays.filter((w) => (city.rule.days[w] || []).includes(d)).map((w) => DAY_NAME[w]);
      when = days.length ? `te toca el ${days.join(' y ')}` : 'no tienes día de restricción';
    }
    const vig = city.validTo ? ` Vigente hasta el ${city.validTo}; después hay que revisar la nueva rotación.` : '';
    const src = city.confidence === 'oficial' ? 'Fuente oficial.' : 'Fuente: prensa; confírmalo en la secretaría de movilidad de tu ciudad.';
    const dias = city.rule.kind === 'parity' ? ', de lunes a viernes' : '';
    return `${city.name}: con placa terminada en ${d}, ${when}, ${hoursText(city)}${dias}.${vig}${city.extra ? ' ' + city.extra : ''} ${src}`;
  }

  /** Un día (a las 10:00) en que a esa placa le toca, dentro de las próximas 2 semanas; null si no hay. */
  function sampleDate(city, digit, from) {
    const start = from || new Date();
    for (let i = 0; i < 14; i++) {
      const d = new Date(start.getFullYear(), start.getMonth(), start.getDate() + i, 10, 0);
      if (isRestricted(city, digit, d) && !expired(city, d) && !notStarted(city, d)) return d;
    }
    return null;
  }

  return { countries, cities, countryOf, citiesOf, inZone, cityAt, restrictedDigits, isRestricted, notice, describe, sampleDate, hoursText };
});
