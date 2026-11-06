/*
 * Restricciones vehiculares de Latinoamérica: implementación de referencia (JavaScript).
 * Licencia MIT. Los datos (data/restricciones.json) son CC0.
 *
 * No trae los datos adentro: se los pasas. Así puedes actualizar el archivo de datos sin tocar el código.
 *
 *   const R = require('./restricciones.js')(require('../data/restricciones.json'));
 *   const ciudad = R.cityAt(4.65, -74.08, 'CO');             // la ciudad en la que está el punto, o null
 *   const e = R.evaluate(ciudad, { digit: 7 }, new Date());  // qué le toca hoy a esa placa
 *   if (e.restricted && e.inHours) { ... }                   // avisar
 *
 * Todas las fechas se interpretan en la hora local del dispositivo (la de la ciudad por la que se circula).
 */
(function (root, factory) {
  if (typeof module === 'object' && module.exports) module.exports = factory;
  else root.TBRestricciones = factory;
})(typeof self !== 'undefined' ? self : this, function (data) {
  const WEEK = ['dom', 'lun', 'mar', 'mie', 'jue', 'vie', 'sab'];
  const DAY_NAME = { lun: 'lunes', mar: 'martes', mie: 'miércoles', jue: 'jueves', vie: 'viernes' };
  const pad = (n) => String(n).padStart(2, '0');
  const iso = (d) => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
  const hm = (min) => `${Math.floor(min / 60)}:${pad(min % 60)}`;

  const cities = data.cities;
  const countries = data.countries;
  const citiesOf = (countryId) => cities.filter((c) => c.country === countryId);
  const cityById = (id) => cities.find((c) => c.id === id) || null;
  const hoursText = (city) => city.hours.map(([a, b]) => `de ${hm(a)} a ${hm(b)}`).join(' y ');

  /** ¿Está el punto dentro del polígono (aproximado) de la ciudad? Zonas como [lat, lon]. Algoritmo del rayo. */
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

  function cityAt(lat, lon, countryId) {
    return citiesOf(countryId).find((c) => inZone(c, lat, lon)) || null;
  }

  /** La clase del vehículo (Chile: sello, sin, nuevo) o null si la ciudad no distingue clases. */
  function classOf(city, vehicleClass) {
    if (city.rule.kind !== 'classes') return null;
    return city.rule.classes.find((c) => c.id === vehicleClass) || city.rule.classes[0];
  }

  /**
   * Qué le toca a esa placa en esa fecha y hora. Devuelve siempre los mismos campos (todos booleanos):
   *   expired      la regla guardada ya venció (validTo): no se puede afirmar nada
   *   notStarted   la regla guardada empieza después (validFrom)
   *   inSeason     el mes está dentro de la temporada de la regla (si no tiene temporada, siempre true)
   *   restricted   hoy esa placa tiene restricción (solo si la regla está vigente, en temporada y es un día que rige)
   *   inHours      la hora actual cae dentro de una franja con restricción
   *   remaining    todavía queda alguna franja de restricción hoy (ahora o más tarde)
   * plate: { digit: 0-9, vehicleClass?: 'sello' | 'sin' | 'nuevo' (solo Chile) }
   */
  function evaluate(city, plate, date) {
    const d = date || new Date();
    const minutes = d.getHours() * 60 + d.getMinutes();
    const expired = !!city.validTo && iso(d) > city.validTo;
    const notStarted = !!city.validFrom && iso(d) < city.validFrom;
    const inSeason = !city.months || city.months.includes(d.getMonth() + 1);
    const wd = WEEK[d.getDay()];
    const ruleDay = city.weekdays.includes(wd);
    const digit = Number(plate.digit);
    let byDigit = false;
    let banned = false;
    const rule = city.rule;
    if (rule.kind === 'parity') {
      byDigit = (d.getDate() % 2 === 1 ? rule.odd : rule.even).includes(digit);
    } else if (rule.kind === 'weekday') {
      byDigit = (rule.days[wd] || []).includes(digit);
    } else if (rule.kind === 'classes') {
      const cls = classOf(city, plate.vehicleClass);
      if (cls && !cls.exempt) {
        byDigit = ((cls.days || {})[wd] || []).includes(digit);
        banned = !!cls.alwaysBanned && cls.alwaysBanned.weekdays.includes(wd);
      }
    }
    const active = !expired && !notStarted && inSeason && ruleDay;
    return {
      expired,
      notStarted,
      inSeason,
      restricted: active && (byDigit || banned),
      inHours: city.hours.some(([a, b]) => minutes >= a && minutes < b),
      remaining: city.hours.some(([, b]) => minutes < b),
    };
  }

  /** Aviso en español al entrar a la ciudad: { warn, text } o null si no hay nada que decir. */
  function notice(city, plate, date) {
    const d = date || new Date();
    const e = evaluate(city, plate, d);
    if (e.expired) {
      return { warn: false, text: `Entraste a ${city.name}. Tu regla de ${city.label} valía hasta el ${city.validTo}: revísala en la fuente oficial, puede haber cambiado.` };
    }
    if (!e.restricted || !e.remaining) return null;
    return { warn: e.inHours, text: `Entraste a ${city.name}. Hoy tienes ${city.label} (placa terminada en ${plate.digit}): rige ${hoursText(city)}.` };
  }

  /** Explica en español qué le toca a esa placa en esa ciudad, con su fuente. */
  function describe(city, plate) {
    const d = Number(plate.digit);
    let when;
    if (city.rule.kind === 'parity') {
      when = `no puedes circular los días ${city.rule.odd.includes(d) ? 'impares' : 'pares'} del mes`;
    } else {
      const table = city.rule.kind === 'classes' ? (classOf(city, plate.vehicleClass).days || {}) : city.rule.days;
      const days = city.weekdays.filter((w) => (table[w] || []).includes(d)).map((w) => DAY_NAME[w]);
      when = days.length ? `te toca el ${days.join(' y ')}` : 'no tienes día de restricción';
    }
    const vig = city.validTo ? ` Vigente hasta el ${city.validTo}.` : '';
    const src = city.confidence === 'oficial' ? 'Fuente oficial.' : 'Fuente: prensa; confírmalo con la autoridad de tu ciudad.';
    return `${city.name}: con placa terminada en ${d}, ${when}, ${hoursText(city)}.${vig} ${src}`;
  }

  return { countries, cities, citiesOf, cityById, inZone, cityAt, evaluate, notice, describe, hoursText, version: data.dataVersion };
});
