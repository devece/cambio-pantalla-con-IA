/*
 * Hora del sol sin internet: amanecer y atardecer de un día en un lugar, con las ecuaciones publicadas por la NOAA.
 * La radio lo usa para cambiar entre tema de día y de noche cuando sale y se pone el sol donde está el auto,
 * en vez de una hora fija (en invierno oscurece a las 18 h; en verano, pasadas las 21 h).
 * Funciona en el navegador (window.TBDaylight) y en Node (tests).
 */
(function (root, factory) {
  if (typeof module === 'object' && module.exports) module.exports = factory();
  else root.TBDaylight = factory();
})(typeof self !== 'undefined' ? self : this, function () {
  const RAD = Math.PI / 180;
  /** Donde se cuenta el amanecer y el atardecer: el borde del sol, con la refracción de la atmósfera. */
  const ZENITH = 90.833;

  /** Día del año (1 a 366) de la fecha local de `date`. */
  function dayOfYear(date) {
    const start = new Date(date.getFullYear(), 0, 0);
    const today = new Date(date.getFullYear(), date.getMonth(), date.getDate());
    return Math.round((today - start) / 86400000);
  }

  /**
   * Minutos del día (hora local) en que sale y se pone el sol, o `null` en cada uno si ese día no hay (sol de medianoche o noche polar).
   * @param {Date} date    el día
   * @param {number} lat   latitud en grados (sur negativa)
   * @param {number} lon   longitud en grados (oeste negativa)
   * @param {number} [tzOffsetMin] minutos al este de UTC; si falta, el de la zona horaria de `date`
   */
  function sunTimes(date, lat, lon, tzOffsetMin) {
    const tz = Number.isFinite(tzOffsetMin) ? tzOffsetMin : -date.getTimezoneOffset();
    const g = (2 * Math.PI / 365) * (dayOfYear(date) - 1);
    const eqTime = 229.18 * (0.000075 + 0.001868 * Math.cos(g) - 0.032077 * Math.sin(g) - 0.014615 * Math.cos(2 * g) - 0.040849 * Math.sin(2 * g));
    const decl = 0.006918 - 0.399912 * Math.cos(g) + 0.070257 * Math.sin(g) - 0.006758 * Math.cos(2 * g) + 0.000907 * Math.sin(2 * g) - 0.002697 * Math.cos(3 * g) + 0.00148 * Math.sin(3 * g);
    const cosHa = Math.cos(ZENITH * RAD) / (Math.cos(lat * RAD) * Math.cos(decl)) - Math.tan(lat * RAD) * Math.tan(decl);
    if (cosHa > 1) return { sunrise: null, sunset: null, polar: 'noche' };
    if (cosHa < -1) return { sunrise: null, sunset: null, polar: 'dia' };
    const ha = Math.acos(cosHa) / RAD;
    return { sunrise: 720 - 4 * (lon + ha) - eqTime + tz, sunset: 720 - 4 * (lon - ha) - eqTime + tz, polar: null };
  }

  const minutesOf = (date) => date.getHours() * 60 + date.getMinutes() + date.getSeconds() / 60;

  /** ¿Ya se puso el sol (o aún no sale) en ese lugar? */
  function isNight(date, lat, lon, tzOffsetMin) {
    const s = sunTimes(date, lat, lon, tzOffsetMin);
    if (s.polar) return s.polar === 'noche';
    const m = minutesOf(date);
    return m < s.sunrise || m >= s.sunset;
  }

  /**
   * Momento del día para el fondo: 'amanecer', 'dia', 'atardecer' o 'noche'.
   * El amanecer va desde 45 min antes de salir el sol hasta 75 min después; el atardecer, desde 90 min antes de ponerse hasta 30 min después.
   */
  function phase(date, lat, lon, tzOffsetMin) {
    const s = sunTimes(date, lat, lon, tzOffsetMin);
    if (s.polar) return s.polar;
    const m = minutesOf(date);
    if (m >= s.sunrise - 45 && m < s.sunrise + 75) return 'amanecer';
    if (m >= s.sunset - 90 && m < s.sunset + 30) return 'atardecer';
    return m >= s.sunrise && m < s.sunset ? 'dia' : 'noche';
  }

  return { sunTimes, isNight, phase, dayOfYear };
});
