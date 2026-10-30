/*
 * Catálogo compartido entre el navegador y el servidor (api/).
 * Los PRECIOS SON DE EJEMPLO: defínelos tú antes de vender.
 * El servidor siempre recalcula el total con este archivo; nunca confía en el precio que manda el navegador.
 */
(function (root, factory) {
  if (typeof module === 'object' && module.exports) module.exports = factory();
  else root.TBCatalog = factory();
})(typeof self !== 'undefined' ? self : this, function () {
  const plans = [
    {
      id: 'esencial',
      name: 'Esencial',
      price: 9990,
      blurb: 'Tu pantalla con 3 diseños visuales y todas las funciones de manejo.',
    },
    {
      id: 'completo',
      name: 'Completo',
      price: 14990,
      blurb: 'Los 8 diseños visuales, decoraciones de temporada y cambios ilimitados.',
    },
  ];

  const addons = [
    {
      id: 'foto',
      name: 'Foto de tu auto con luces',
      price: 3990,
      blurb: 'Preparamos tu foto de día y de noche, con el fondo recortado.',
      // false = el servicio todavía no existe: ni se muestra ni se cobra. Cámbialo cuando esté listo.
      available: false,
    },
  ];

  // 'base' viene en Esencial; 'plus' requiere el plan Completo.
  const themeTiers = {
    ruta: 'base',
    papel: 'base',
    cristal: 'base',
    alumbrado: 'plus',
    cordillera: 'plus',
    costa: 'plus',
    atacama: 'plus',
    patagonia: 'plus',
  };

  const seasons = ['ninguna', 'auto', 'patrias', 'halloween', 'navidad', 'anonuevo'];
  const plusSeasons = ['auto', 'patrias', 'halloween', 'navidad', 'anonuevo'];
  const layouts = ['tarjetas', 'centro', 'manejo', 'minimal'];
  const scenes = ['tema', 'cordillera', 'costa', 'desierto', 'bosque', 'ciudad', 'carretera', 'color'];
  const vehicles = ['auto', 'suv', 'camioneta'];
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

  return {
    currency: 'CLP',
    locale: 'es-CL',
    plans,
    addons,
    themeTiers,
    seasons,
    plusSeasons,
    layouts,
    scenes,
    vehicles,
    apps,
    radioTypes,
    speedAlerts,
    weekdays,
    maintenance,
  };
});
