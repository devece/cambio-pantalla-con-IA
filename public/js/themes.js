/*
 * Temas visuales de Tablero.
 * Cada tema define su paleta de día y de noche (luces encendidas), su forma (radio de esquinas, vidrio, sombra)
 * y el fondo que lleva por defecto. `tier: 'plus'` requiere el plan Completo (ver shared/catalog.js).
 */
(function () {
  const TB = (window.TB = window.TB || {});

  TB.themes = [
    {
      id: 'ruta',
      name: 'Ruta',
      desc: 'Vidrio sobre un degradado vivo. Moderno y limpio.',
      tier: 'base',
      scene: 'aurora',
      radius: 28,
      blur: 26,
      shadow: '0 18px 40px rgba(8,10,60,.35), inset 0 1px 0 rgba(255,255,255,.45)',
      swatch: ['#5a3df0', '#19b6e8'],
      day: { bg1: '#4b2fd6', bg2: '#0b2a6f', orb1: '#ff5fa8', orb2: '#19c3f2', orb3: '#7a4dff', ink: '#ffffff', ink2: 'rgba(255,255,255,.72)', card: 'rgba(255,255,255,.14)', line: 'rgba(255,255,255,.34)', accent: '#ffd166', accentInk: '#2b1c00', sceneRgb: '255,255,255' },
      night: { bg1: '#150f3d', bg2: '#050a1f', orb1: '#8a2f7a', orb2: '#0f6c93', orb3: '#3b24a8', ink: '#f2f4ff', ink2: 'rgba(235,238,255,.62)', card: 'rgba(255,255,255,.08)', line: 'rgba(255,255,255,.20)', accent: '#ffd166', accentInk: '#2b1c00', sceneRgb: '255,255,255' },
    },
    {
      id: 'papel',
      name: 'Papel',
      desc: 'Alto contraste. Se lee a pleno sol.',
      tier: 'base',
      scene: 'none',
      radius: 10,
      blur: 0,
      shadow: '0 2px 0 rgba(0,0,0,.06)',
      swatch: ['#f4f4ef', '#d2352b'],
      day: { bg1: '#f6f6f1', bg2: '#e4e4db', ink: '#17181a', ink2: '#5b5e63', card: '#ffffff', line: 'rgba(0,0,0,.12)', accent: '#d2352b', accentInk: '#ffffff', sceneRgb: '0,0,0' },
      night: { bg1: '#1b1c1e', bg2: '#0e0f10', ink: '#f1f1ee', ink2: '#9a9da2', card: '#26282b', line: 'rgba(255,255,255,.10)', accent: '#ff6b5e', accentInk: '#1b0a08', sceneRgb: '255,255,255' },
    },
    {
      id: 'cristal',
      name: 'Cristal',
      desc: 'Paneles de vidrio sobre el fondo que elijas.',
      tier: 'base',
      scene: 'cordillera',
      radius: 28,
      blur: 20,
      shadow: '0 14px 30px rgba(10,30,60,.25)',
      swatch: ['#4f8fb8', '#0e5fa8'],
      day: { bg1: '#4f8fb8', bg2: '#bcd9e8', ink: '#0c2231', ink2: '#2f536b', card: 'rgba(255,255,255,.38)', line: 'rgba(255,255,255,.55)', accent: '#0e5fa8', accentInk: '#ffffff', sceneRgb: '255,255,255' },
      night: { bg1: '#0b1b2e', bg2: '#1e2f4a', ink: '#eaf2fa', ink2: '#9db6d1', card: 'rgba(255,255,255,.08)', line: 'rgba(255,255,255,.16)', accent: '#7cc4ff', accentInk: '#06202f', sceneRgb: '255,255,255' },
    },
    {
      id: 'alumbrado',
      name: 'Alumbrado',
      desc: 'Ciudad de noche con luz de sodio, cálida.',
      tier: 'plus',
      scene: 'ciudad',
      radius: 14,
      blur: 0,
      shadow: '0 10px 24px rgba(0,0,0,.4)',
      swatch: ['#3c2b1f', '#ff9a3c'],
      day: { bg1: '#40302a', bg2: '#1d140d', ink: '#fbefdc', ink2: '#cdb597', card: 'rgba(255,170,90,.10)', line: 'rgba(255,170,90,.24)', accent: '#ff9a3c', accentInk: '#2a1300', sceneRgb: '255,200,140' },
      night: { bg1: '#1a0f08', bg2: '#090503', ink: '#f6e6cd', ink2: '#b49a78', card: 'rgba(255,170,90,.07)', line: 'rgba(255,170,90,.16)', accent: '#ffb15c', accentInk: '#2a1300', sceneRgb: '255,200,140' },
    },
    {
      id: 'cordillera',
      name: 'Cordillera',
      desc: 'Nieve y cielo despejado. Luminoso y sereno.',
      tier: 'plus',
      scene: 'cordillera',
      radius: 18,
      blur: 6,
      shadow: '0 8px 22px rgba(31,95,168,.18)',
      swatch: ['#cfe3f2', '#1f5fa8'],
      day: { bg1: '#cfe3f2', bg2: '#f4f8fb', ink: '#0f2236', ink2: '#4a6580', card: 'rgba(255,255,255,.72)', line: 'rgba(15,34,54,.12)', accent: '#1f5fa8', accentInk: '#ffffff', sceneRgb: '31,95,168' },
      night: { bg1: '#0d1b2e', bg2: '#1b2f4b', ink: '#e8f1fb', ink2: '#9db6d1', card: 'rgba(255,255,255,.07)', line: 'rgba(255,255,255,.14)', accent: '#8cc8ff', accentInk: '#06202f', sceneRgb: '255,255,255' },
    },
    {
      id: 'costa',
      name: 'Costa',
      desc: 'Turquesa del Pacífico y arena. Redondeado.',
      tier: 'plus',
      scene: 'costa',
      radius: 32,
      blur: 0,
      shadow: '0 12px 26px rgba(0,60,70,.3)',
      swatch: ['#19a7a0', '#ffd166'],
      day: { bg1: '#19a7a0', bg2: '#0b5963', ink: '#fdfaf0', ink2: '#cfeae6', card: 'rgba(255,255,255,.16)', line: 'rgba(255,255,255,.28)', accent: '#ffd166', accentInk: '#3a2a00', sceneRgb: '255,255,255' },
      night: { bg1: '#06292e', bg2: '#03141a', ink: '#eaf6f4', ink2: '#86b5b3', card: 'rgba(255,255,255,.07)', line: 'rgba(255,255,255,.13)', accent: '#ffd166', accentInk: '#3a2a00', sceneRgb: '255,255,255' },
    },
    {
      id: 'atacama',
      name: 'Atacama',
      desc: 'Desierto, ocre y terracota. Esquinas firmes.',
      tier: 'plus',
      scene: 'desierto',
      radius: 12,
      blur: 0,
      shadow: '0 10px 24px rgba(60,20,5,.35)',
      swatch: ['#d9733a', '#ffe2a8'],
      day: { bg1: '#d9733a', bg2: '#7a2c14', ink: '#fff4e8', ink2: '#f2cdb0', card: 'rgba(255,255,255,.14)', line: 'rgba(255,255,255,.24)', accent: '#ffe2a8', accentInk: '#4a1d08', sceneRgb: '255,226,168' },
      night: { bg1: '#2a1109', bg2: '#0e0503', ink: '#f7e6d6', ink2: '#b98f75', card: 'rgba(255,255,255,.06)', line: 'rgba(255,255,255,.12)', accent: '#f59e5b', accentInk: '#2a1004', sceneRgb: '255,200,150' },
    },
    {
      id: 'patagonia',
      name: 'Patagonia',
      desc: 'Bosque húmedo y bruma. Verde profundo.',
      tier: 'plus',
      scene: 'bosque',
      radius: 20,
      blur: 4,
      shadow: '0 10px 24px rgba(0,0,0,.35)',
      swatch: ['#2e5d4b', '#c8e6a0'],
      day: { bg1: '#2e5d4b', bg2: '#10261f', ink: '#eef5ef', ink2: '#b3cbbb', card: 'rgba(255,255,255,.09)', line: 'rgba(255,255,255,.18)', accent: '#c8e6a0', accentInk: '#14240b', sceneRgb: '255,255,255' },
      night: { bg1: '#0a1a14', bg2: '#040a07', ink: '#e6efe8', ink2: '#86a392', card: 'rgba(255,255,255,.05)', line: 'rgba(255,255,255,.10)', accent: '#b5da86', accentInk: '#14240b', sceneRgb: '255,255,255' },
    },
  ];

  TB.layouts = [
    { id: 'tarjetas', name: 'Tarjetas', desc: 'Clima, radio y música a la vista.' },
    { id: 'centro', name: 'Auto al centro', desc: 'Tu auto como protagonista.' },
    { id: 'manejo', name: 'Modo manejo', desc: 'Velocidad grande y límite de la vía.' },
    { id: 'minimal', name: 'Minimalista', desc: 'Reloj grande y tus apps, sin distracciones.' },
    { id: 'tablero', name: 'Tablero', desc: 'Velocidad y auto a la izquierda; tu ruta y el mapa a la derecha.' },
    { id: 'lateral', name: 'Menú lateral', desc: 'Un menú a la izquierda y las tarjetas al lado.' },
    { id: 'todoterreno', name: 'Todoterreno', desc: 'Letras fuertes, tu camioneta grande, altitud y brújula.', only4x4: true },
  ];

  TB.scenes = [
    { id: 'tema', name: 'Del diseño' },
    { id: 'cordillera', name: 'Cordillera' },
    { id: 'costa', name: 'Costa' },
    { id: 'desierto', name: 'Desierto' },
    { id: 'bosque', name: 'Bosque' },
    { id: 'ciudad', name: 'Ciudad' },
    { id: 'carretera', name: 'Carretera' },
    { id: 'montana', name: 'Montaña', level: 1 },
    { id: 'nieve', name: 'Nieve', level: 2 },
    { id: 'rocas', name: 'Rocas', level: 2 },
    { id: 'barro', name: 'Barro', level: 2 },
    { id: 'color', name: 'Solo color' },
  ];

  TB.seasons = [
    { id: 'ninguna', name: 'Sin decoración', desc: 'Pantalla limpia todo el año.' },
    { id: 'auto', name: 'Automática', desc: 'Cambia sola según la fecha.' },
    { id: 'patrias', name: 'Fiestas Patrias', desc: 'Banderines, del 10 al 21 de septiembre.' },
    { id: 'halloween', name: 'Halloween', desc: 'Telarañas y zapallos, del 24 de octubre al 1 de noviembre.' },
    { id: 'navidad', name: 'Navidad', desc: 'Luces y nieve, del 1 al 25 de diciembre.' },
    { id: 'anonuevo', name: 'Año Nuevo', desc: 'Fuegos artificiales, del 26 de diciembre al 2 de enero.' },
  ];

  /** Decoración que corresponde a una fecha (la usa la opción "Automática"). */
  TB.seasonFor = function (d) {
    d = d || new Date();
    const m = d.getMonth() + 1;
    const day = d.getDate();
    if (m === 9 && day >= 10 && day <= 21) return 'patrias';
    if ((m === 10 && day >= 24) || (m === 11 && day === 1)) return 'halloween';
    if (m === 12 && day <= 25) return 'navidad';
    if ((m === 12 && day >= 26) || (m === 1 && day <= 2)) return 'anonuevo';
    return 'ninguna';
  };

  TB.apps = [
    { id: 'maps', name: 'Maps' },
    { id: 'waze', name: 'Waze' },
    { id: 'spotify', name: 'Spotify' },
    { id: 'ytmusic', name: 'YT Music' },
    { id: 'youtube', name: 'YouTube' },
    { id: 'whatsapp', name: 'WhatsApp' },
    { id: 'camara', name: 'Cámara retro' },
    { id: 'usb', name: 'Música USB' },
    { id: 'telefono', name: 'Android Auto / CarPlay' },
  ];

  TB.vehicles = [
    { id: 'auto', name: 'Auto', desc: 'Sedán o hatchback.' },
    { id: 'suv', name: 'SUV', desc: 'Más alto, con baúl largo.' },
    { id: 'camioneta', name: 'Camioneta', desc: 'Cabina y tolva.' },
    { id: '4x4', name: 'Camioneta 4x4', desc: 'Todoterreno: barro, rocas, nieve.' },
  ];
  /** Nivel del vehículo: desbloquea fondos y diseños (0 ciudad, 1 SUV/camioneta, 2 4x4). */
  TB.vehicleLevel = { auto: 0, suv: 1, camioneta: 1, '4x4': 2 };
  TB.sceneTimes = [
    { id: 'auto', name: 'Automática', desc: 'Cambia sola con la hora.' },
    { id: 'amanecer', name: 'Amanecer' },
    { id: 'dia', name: 'Día' },
    { id: 'atardecer', name: 'Atardecer' },
    { id: 'noche', name: 'Noche' },
  ];
  TB.accents = ['#ffd166', '#ff6b6b', '#4cc9f0', '#06d6a0', '#a78bfa', '#ff9f68', '#f472b6', '#ffffff'];

  TB.carColors = ['#b3261e', '#1f5fa8', '#1f1f23', '#e8e8e4', '#7c8591', '#2e7d4f', '#e08a00', '#6a3fa0'];
  TB.greetings = ['Hola', 'Buen día', 'Wena', 'Bienvenido'];
  TB.phrases = ['Dios te bendiga', 'Buen viaje', 'Maneja con cuidado', 'Vamos con calma', 'A disfrutar el camino'];
  TB.weekdays = [
    { id: 'lun', name: 'Lunes' },
    { id: 'mar', name: 'Martes' },
    { id: 'mie', name: 'Miércoles' },
    { id: 'jue', name: 'Jueves' },
    { id: 'vie', name: 'Viernes' },
  ];
  TB.months = ['enero', 'febrero', 'marzo', 'abril', 'mayo', 'junio', 'julio', 'agosto', 'septiembre', 'octubre', 'noviembre', 'diciembre'];
})();
