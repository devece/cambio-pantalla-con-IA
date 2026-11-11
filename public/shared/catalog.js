/*
 * Catálogo compartido entre el navegador y el servidor (api/).
 * Tablero es gratis: no hay planes, precios ni cobros. Aquí solo están las opciones que existen.
 */
(function (root, factory) {
  if (typeof module === 'object' && module.exports) module.exports = factory();
  else root.TBCatalog = factory();
})(typeof self !== 'undefined' ? self : this, function () {
  // Los diseños visuales disponibles (todos son gratis).
  const themes = ['sobrio', 'ruta', 'papel', 'cristal', 'alumbrado', 'cordillera', 'costa', 'atacama', 'patagonia'];

  const seasons = ['ninguna', 'auto', 'patrias', 'halloween', 'navidad', 'anonuevo'];
  const layouts = ['consola', 'estelar', 'tarjetas', 'centro', 'manejo', 'minimal', 'tablero', 'lateral', 'todoterreno'];
  const scenes = ['tema', 'cordillera', 'costa', 'desierto', 'bosque', 'ciudad', 'carretera', 'montana', 'nieve', 'rocas', 'barro', 'color'];
  const sceneTimes = ['auto', 'amanecer', 'dia', 'atardecer', 'noche'];
  const vehicles = ['auto', 'suv', 'camioneta', '4x4'];
  const apps = ['maps', 'waze', 'spotify', 'ytmusic', 'youtube', 'whatsapp', 'camara', 'usb', 'telefono'];
  const radioTypes = ['android', 'original', 'nose'];
  const speedAlerts = ['auto', 'manual', 'off'];
  const weekdays = ['lun', 'mar', 'mie', 'jue', 'vie'];

  // Intervalos referenciales. Cada auto tiene los suyos: el cliente debe revisar el manual.
  const maintenance = [
    { id: 'filtro', name: 'Filtro de aire', every: 15000 },
    { id: 'neumaticos', name: 'Rotación de neumáticos', every: 10000 },
    { id: 'bujias', name: 'Bujías', every: 30000 },
    { id: 'pastillas', name: 'Pastillas de freno', every: 30000 },
    { id: 'frenos', name: 'Líquido de frenos y refrigerante', every: 40000 },
    { id: 'correa', name: 'Correa de distribución', every: 80000 },
  ];

  /*
   * Restricción vehicular permanente. En Chile, para autos particulares, solo existe de forma permanente en la
   * Región Metropolitana (Provincia de Santiago, San Bernardo y Puente Alto). Revisar cada año: el MTT publica
   * la resolución nueva en abril. Datos de la temporada 2026: Resolución Exenta N.º 1698/2026 de la SEREMI de Transportes RM,
   * contrastados línea por línea con el PDF oficial el 2026-11-11 (temporada, horario, zona y las dos tablas de dígitos).
   * Los dígitos cambian cada año: la radio y el sitio solo afirman esta tabla durante {year} (ver restrictionNotice).
   */
  const restriction = {
    year: 2026,
    verifiedAt: '2026-11-11',
    season: { from: '1 de mayo', to: '31 de agosto', months: [5, 6, 7, 8], hours: '7:30 a 21:00', days: 'de lunes a viernes, excepto festivos' },
    // Zona donde rige (la radio la detecta con el GPS). Polígono aproximado de la Provincia de Santiago más
    // San Bernardo y Puente Alto, en [latitud, longitud]. Se afinará con los límites comunales oficiales (BCN).
    zone: {
      name: 'Santiago',
      polygon: [
        [-33.3, -70.8], [-33.28, -70.6], [-33.3, -70.48], [-33.45, -70.44], [-33.62, -70.43], [-33.74, -70.5],
        [-33.74, -70.62], [-33.68, -70.78], [-33.58, -70.9], [-33.42, -70.9], [-33.32, -70.86],
      ],
    },
    types: [
      { id: 'sello', name: 'Con sello verde, inscrito antes de septiembre de 2011', desc: 'Restricción de 2 dígitos por día.' },
      { id: 'sin', name: 'Sin sello verde (más antiguo)', desc: 'Restricción de 4 dígitos por día y no puede circular dentro del anillo Américo Vespucio.' },
      { id: 'nuevo', name: 'Inscrito desde septiembre de 2011', desc: 'Sin restricción permanente; puede haber en episodios de preemergencia.' },
    ],
    // Último dígito de la patente que no puede circular, por día.
    digits: {
      sello: { lun: [8, 9], mar: [0, 1], mie: [2, 3], jue: [4, 5], vie: [6, 7] },
      sin: { lun: [6, 7, 8, 9], mar: [0, 1, 2, 3], mie: [4, 5, 6, 7], jue: [8, 9, 0, 1], vie: [2, 3, 4, 5] },
    },
    sources: [
      'https://mtt.gob.cl/inicia-periodo-de-restriccion-vehicular-2026-region-metropolitana/',
      'https://mtt.gob.cl/wp-content/uploads/2026/04/Res-2026-Restriccion-vehicular-permanente-ano-2026_firmado_GCB.pdf',
    ],
  };

  return {
    themes,
    seasons,
    layouts,
    scenes,
    sceneTimes,
    vehicles,
    apps,
    radioTypes,
    speedAlerts,
    weekdays,
    restriction,
    maintenance,
  };
});
