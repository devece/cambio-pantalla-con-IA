/*
 * Lo que la app Android le pasa a la pantalla de la radio cuando no hay un diseño completo del sitio: el «perfil»
 * (nombre, saludo, frase, auto, color, apariencia) y, desde que se agregó este módulo, también las opciones del cliente
 * (`features`: aceite, documentos, combustible, restricción, país, unidades…). Sin ellas la radio mostraba valores de
 * fábrica aunque el cliente hubiera elegido otra cosa.
 *
 * Todo se valida aquí con las mismas reglas que DesignImport.kt (rangos y listas); lo que no sirve se ignora y queda el valor
 * que ya había. Corre igual en el navegador (window.TBProfile) y en Node (para las pruebas de contrato).
 * El contrato de claves está en design-contract.json.
 */
(function (root, factory) {
  if (typeof module === 'object' && module.exports) module.exports = factory(require('./catalog.js'));
  else root.TBProfile = factory(root.TBCatalog);
})(typeof self !== 'undefined' ? self : this, function (C) {
  const HEX = /^#[0-9a-f]{6}$/i;
  const COUNTRIES = ['CL', 'CO', 'EC', 'MX'];
  const WEEKDAYS = ['lun', 'mar', 'mie', 'jue', 'vie'];

  const text = (v, max) => (typeof v === 'string' ? v.trim().slice(0, max) : '');
  const isInt = (v) => typeof v === 'number' && Number.isInteger(v);
  const setBool = (f, k, v) => { if (typeof v === 'boolean') f[k] = v; };
  const setInt = (f, k, v, lo, hi) => { if (isInt(v) && v >= lo && v <= hi) f[k] = v; };
  const setOne = (f, k, v, list) => { if (typeof v === 'string' && list.includes(v)) f[k] = v; };
  // De una lista solo se toman los elementos válidos (texto que existe en el catálogo): un id desconocido se descarta, pero no arrastra a los buenos.
  const setList = (f, k, v, list) => { if (Array.isArray(v)) f[k] = [...new Set(v.filter((x) => typeof x === 'string' && list.includes(x)))]; };

  /** Aplica las opciones del cliente sobre `state.features`. */
  function applyFeatures(state, x) {
    if (!x || typeof x !== 'object' || Array.isArray(x)) return;
    const f = state.features;
    setOne(f, 'country', x.country, COUNTRIES);
    setBool(f, 'oilOn', x.oilOn);
    setInt(f, 'odometer', x.odometer, 0, 999999);
    setInt(f, 'lastOil', x.lastOil, 0, 999999);
    setInt(f, 'oilEvery', x.oilEvery, 1000, 30000);
    setList(f, 'others', x.others, ((C && C.maintenance) || []).map((m) => m.id));
    setBool(f, 'docsOn', x.docsOn);
    setInt(f, 'rtMonth', x.rtMonth, 1, 12);
    setBool(f, 'fuelOn', x.fuelOn);
    setInt(f, 'tankL', x.tankL, 10, 150);
    setInt(f, 'kmPerL', x.kmPerL, 3, 40);
    setBool(f, 'speedShow', x.speedShow);
    setOne(f, 'speedAlert', x.speedAlert, (C && C.speedAlerts) || ['auto', 'manual', 'off']);
    setInt(f, 'manualLimit', x.manualLimit, 20, 140);
    setBool(f, 'restrOn', x.restrOn);
    setOne(f, 'restrType', x.restrType, ((C && C.restriction && C.restriction.types) || []).map((t) => t.id));
    if (typeof x.restrDigit === 'string' && /^[0-9]$/.test(x.restrDigit)) f.restrDigit = x.restrDigit;
    setList(f, 'restrDays', x.restrDays, WEEKDAYS);
    setBool(f, 'voice', x.voice);
    setBool(f, 'miles', x.miles);
    setBool(f, 'gallons', x.gallons);
    setBool(f, 'clock12', x.clock12);
  }

  /**
   * Aplica el perfil sobre `state` (lo que el usuario contó en el primer arranque o en Ajustes). Un diseño del sitio manda sobre esto.
   * Lo que no existe en el catálogo o no es válido se ignora.
   */
  function apply(state, p) {
    if (!p || typeof p !== 'object') return;
    const car = state.car;
    if (text(p.name, 24)) car.name = text(p.name, 24);
    if (text(p.greeting, 16)) car.greeting = text(p.greeting, 16);
    if (text(p.phrase, 28)) car.phrase = text(p.phrase, 28);
    if (['auto', 'suv', 'camioneta'].includes(p.type)) car.type = p.type;
    if (typeof p.color === 'string' && HEX.test(p.color)) car.color = p.color;
    if (text(p.plate, 10)) car.plate = text(p.plate, 10).toUpperCase();
    if (p.clock12 === true) state.features.clock12 = true;
    applyFeatures(state, p.features);
    // Apariencia elegida en Ajustes: solo se acepta lo que existe en el catálogo; lo demás queda como el diseño por defecto.
    const st = p.style;
    if (st && typeof st === 'object') {
      const set = (key, list) => { if (typeof st[key] === 'string' && list.includes(st[key])) state.style[key] = st[key]; };
      set('theme', C.themes);
      set('layout', C.layouts);
      set('scene', C.scenes);
      set('sceneTime', C.sceneTimes);
      set('season', C.seasons);
      if (typeof st.accent === 'string' && HEX.test(st.accent)) state.style.accent = st.accent;
    }
  }

  return { apply, applyFeatures };
});
