/*
 * Arte generado por código: íconos, autos, fondos y decoraciones de temporada.
 * Todo es original (formas propias); no hay logos ni marcas.
 */
(function () {
  const TB = (window.TB = window.TB || {});

  /* ---------- Íconos (viewBox 24, trazo) ---------- */
  TB.icons = {
    radio: '<rect x="3.5" y="9" width="17" height="10" rx="2"/><path d="M7 6l11-3M8 14h5"/><circle cx="17" cy="14" r="1.4"/>',
    bluetooth: '<path d="M7 7.5l10 9-5 4.5V3l5 4.5-10 9"/>',
    maps: '<path d="M12 21s6.5-5.6 6.5-10.5a6.5 6.5 0 1 0-13 0C5.5 15.4 12 21 12 21z"/><circle cx="12" cy="10.5" r="2.3"/>',
    waze: '<path d="M5 12.5a7 7 0 1 1 12.4 4.5l1.6 3.2-3.6-1.1A7 7 0 0 1 5 12.5z"/><circle cx="9.5" cy="11" r=".6"/><circle cx="14.5" cy="11" r=".6"/><path d="M9.5 14c1.4 1.2 3.6 1.2 5 0"/>',
    spotify: '<path d="M9 18V6.5l10-2V16"/><circle cx="6.5" cy="18" r="2.5"/><circle cx="16.5" cy="16" r="2.5"/>',
    ytmusic: '<circle cx="12" cy="12" r="9"/><path d="M10 8.5l5.5 3.5-5.5 3.5z"/>',
    youtube: '<rect x="3" y="5" width="18" height="14" rx="4"/><path d="M10 9l5 3-5 3z"/>',
    whatsapp: '<path d="M4 20l1.4-4.1A8 8 0 1 1 8.3 18.7z"/><path d="M9 9.5c.5 2.4 2.600 4.300 5 5l1.200-1.300-1.800-1-.9.700c-.8-.4-1.500-1.100-1.900-1.900l.7-.9-1-1.800z"/>',
    camara: '<path d="M4 8.5h4l1.600-2.500h4.800L16 8.500h4V19H4z"/><circle cx="12" cy="13.500" r="3.200"/>',
    usb: '<path d="M12 3v14M12 17a1.800 1.800 0 1 0 .01 0M12 9l4 2v2.500M12 12.500l-4 2V16"/><rect x="6.500" y="16" width="3" height="3"/>',
    telefono: '<path d="M6.500 3.500h3.800l1.500 4-2 1.300a11 11 0 0 0 4.900 4.900l1.300-2 4 1.500v3.800a2 2 0 0 1-2.100 2A16 16 0 0 1 4.500 5.600a2 2 0 0 1 2-2.100z"/>',
    todas: '<rect x="4" y="4" width="6.500" height="6.500" rx="1.500"/><rect x="13.500" y="4" width="6.500" height="6.500" rx="1.500"/><rect x="4" y="13.500" width="6.500" height="6.500" rx="1.500"/><rect x="13.500" y="13.500" width="6.500" height="6.500" rx="1.500"/>',
    lluvia: '<path d="M7 15a4 4 0 1 1 .8-7.900A5 5 0 0 1 17 9a3.500 3.500 0 0 1 0 7H7z"/><path d="M9 19l-1 2M13 19l-1 2M17 19l-1 2"/>',
    sol: '<circle cx="12" cy="12" r="4"/><path d="M12 3v2M12 19v2M3 12h2M19 12h2M5.600 5.600L7 7M17 17l1.400 1.400M5.600 18.400L7 17M17 7l1.400-1.400"/>',
    prev: '<path d="M6 6v12M18 6v12L8 12z"/>',
    next: '<path d="M18 6v12M6 6l10 6-10 6z"/>',
    play: '<path d="M8 5.500v13l11-6.500z"/>',
    giro: '<path d="M8 20v-8a3 3 0 0 1 3-3h8M15 5l4 4-4 4"/>',
    check: '<path d="M5 12.500l4.500 4.500L19 7.500"/>',
  };

  /* ---------- Autos (vista lateral, de frente a la derecha) ---------- */
  const CARS = {
    auto: {
      body: 'M14 106 L14 90 Q14 82 26 80 L74 74 L108 48 Q116 42 130 42 L238 42 Q252 42 262 52 L292 74 L358 82 Q386 86 386 102 L386 108 Q386 112 380 112 L14 112 Z',
      glass: ['M118 52 Q124 48 134 48 L198 48 L198 74 L96 74 Z', 'M206 48 L238 48 Q246 48 252 55 L274 74 L206 74 Z'],
      wheels: [102, 300],
    },
    suv: {
      body: 'M14 106 L14 86 Q14 78 26 76 L66 72 L100 42 Q106 36 120 36 L284 36 Q296 36 302 44 L330 74 L362 80 Q386 84 386 100 L386 108 Q386 112 380 112 L14 112 Z',
      glass: ['M112 46 Q116 42 124 42 L190 42 L190 72 L90 72 Z', 'M198 42 L284 42 Q290 42 294 48 L314 72 L198 72 Z'],
      wheels: [100, 304],
    },
    camioneta: {
      body: 'M14 108 L14 62 L150 62 L162 42 Q168 36 180 36 L262 36 Q272 36 278 44 L300 72 L360 78 Q386 82 386 100 L386 108 Q386 112 380 112 L14 112 Z',
      glass: ['M170 44 L226 44 L226 70 L156 70 Z', 'M234 44 L262 44 Q268 44 272 50 L288 70 L234 70 Z'],
      wheels: [96, 306],
      extra: '<rect x="22" y="66" width="122" height="6" rx="2" fill="rgba(0,0,0,.28)"/>',
    },
  };

  /** Auto en SVG. `uid` evita choques de ids cuando hay más de un auto en pantalla. */
  TB.carSVG = function (type, color, night, uid) {
    const c = CARS[type] || CARS.auto;
    const id = 'c' + (uid || '0');
    const glass = night ? '#1a2531' : '#d4e8f4';
    const wheels = c.wheels
      .map(
        (cx) =>
          `<circle cx="${cx}" cy="108" r="29" fill="rgba(0,0,0,.55)"/><circle cx="${cx}" cy="108" r="25" fill="#101214"/><circle cx="${cx}" cy="108" r="13" fill="#a3acb3"/><circle cx="${cx}" cy="108" r="5" fill="#3a4047"/>`
      )
      .join('');
    const beam = night
      ? `<polygon points="384,92 560,56 560,132" fill="url(#${id}b)"/><ellipse cx="388" cy="94" rx="44" ry="20" fill="url(#${id}g)"/>`
      : '';
    return `<svg class="car-svg" viewBox="0 0 400 150" style="overflow:visible" aria-hidden="true">
      <defs>
        <linearGradient id="${id}s" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#fff" stop-opacity=".32"/><stop offset=".45" stop-color="#fff" stop-opacity="0"/><stop offset="1" stop-color="#000" stop-opacity=".34"/></linearGradient>
        <linearGradient id="${id}b" x1="0" y1="0" x2="1" y2="0"><stop offset="0" stop-color="#fff6c8" stop-opacity=".55"/><stop offset="1" stop-color="#fff6c8" stop-opacity="0"/></linearGradient>
        <radialGradient id="${id}g"><stop offset="0" stop-color="#fff6c8" stop-opacity=".9"/><stop offset="1" stop-color="#fff6c8" stop-opacity="0"/></radialGradient>
      </defs>
      <ellipse cx="200" cy="118" rx="190" ry="9" fill="rgba(0,0,0,.38)"/>
      ${beam}
      <path d="${c.body}" fill="${color}"/>
      <path d="${c.body}" fill="url(#${id}s)"/>
      ${c.extra || ''}
      ${c.glass.map((g) => `<path d="${g}" fill="${glass}"/>`).join('')}
      ${wheels}
      <path d="M368 88 L384 90 L384 98 L366 96 Z" fill="${night ? '#fff6c8' : '#e6edf0'}"/>
      <rect x="14" y="88" width="6" height="10" rx="1.500" fill="${night ? '#ff3b2f' : '#9c2a24'}"/>
    </svg>`;
  };

  /* ---------- Fondos (escenas) ---------- */
  function rng(seed) {
    let s = seed;
    return () => (s = (s * 16807) % 2147483647) / 2147483647;
  }
  function ridge(seed, baseY, amp, steps) {
    const r = rng(seed);
    const pts = ['0,600'];
    for (let i = 0; i <= steps; i++) {
      const x = Math.round((i * 1024) / steps);
      const y = Math.round(baseY - r() * amp - (i % 2 ? amp * 0.35 : 0));
      pts.push(`${x},${y}`);
    }
    pts.push('1024,600');
    return pts.join(' ');
  }
  const layer = (shape, a) => `<g style="fill:rgba(var(--scene-rgb),${a})">${shape}</g>`;

  TB.scene = function (id) {
    switch (id) {
      case 'aurora':
        // Manchas de luz difusas: dan profundidad al degradado y algo que el vidrio pueda desenfocar.
        return (
          '<defs><filter id="aur-b" x="-50%" y="-50%" width="200%" height="200%"><feGaussianBlur stdDeviation="70"/></filter></defs>' +
          '<g filter="url(#aur-b)">' +
          '<circle cx="150" cy="120" r="190" style="fill:var(--orb1);opacity:.85"/>' +
          '<circle cx="880" cy="90" r="170" style="fill:var(--orb2);opacity:.8"/>' +
          '<circle cx="760" cy="520" r="230" style="fill:var(--orb3);opacity:.75"/>' +
          '<circle cx="220" cy="560" r="170" style="fill:var(--orb2);opacity:.55"/>' +
          '</g>'
        );
      case 'cordillera':
        return (
          layer(`<polygon points="${ridge(11, 400, 190, 12)}"/>`, 0.1) +
          layer(`<polygon points="${ridge(23, 450, 130, 14)}"/>`, 0.16) +
          layer(`<polygon points="${ridge(37, 500, 80, 16)}"/>`, 0.24)
        );
      case 'costa': {
        const wave = (y, amp) => `<path d="M0 ${y} Q 128 ${y - amp} 256 ${y} T 512 ${y} T 768 ${y} T 1024 ${y} V600 H0Z"/>`;
        return (
          '<circle cx="880" cy="120" r="62" style="fill:var(--accent);opacity:.2"/>' +
          layer(wave(400, 34), 0.06) + layer(wave(450, 28), 0.09) + layer(wave(505, 22), 0.13)
        );
      }
      case 'desierto':
        return (
          '<circle cx="860" cy="125" r="66" style="fill:var(--accent);opacity:.2"/>' +
          layer('<path d="M0 430 C 200 350 360 470 560 410 S 900 370 1024 430 V600 H0Z"/>', 0.07) +
          layer('<path d="M0 490 C 240 430 420 540 640 480 S 900 450 1024 500 V600 H0Z"/>', 0.12)
        );
      case 'bosque': {
        const pines = (seed, base, minH, maxH, a) => {
          const r = rng(seed);
          let d = '';
          for (let x = -10; x < 1060; x += 44 + Math.round(r() * 24)) {
            const h = minH + r() * (maxH - minH);
            d += `<path d="M${x - 30} ${base} L${x} ${base - h} L${x + 30} ${base}Z"/>`;
          }
          return layer(d, a);
        };
        return pines(5, 470, 120, 210, 0.07) + pines(9, 530, 130, 230, 0.12);
      }
      case 'ciudad': {
        const r = rng(17);
        let b = '';
        let w = '';
        for (let x = 0; x < 1024; ) {
          const bw = 44 + Math.round(r() * 46);
          const bh = 110 + Math.round(r() * 200);
          b += `<rect x="${x}" y="${560 - bh}" width="${bw - 4}" height="${bh}"/>`;
          for (let wy = 560 - bh + 14; wy < 548; wy += 22)
            for (let wx = x + 8; wx < x + bw - 14; wx += 14) if (r() > 0.55) w += `<rect x="${wx}" y="${wy}" width="6" height="9"/>`;
          x += bw;
        }
        return layer(b, 0.08) + '<g style="fill:var(--accent);opacity:.2">' + w + '</g>';
      }
      case 'carretera': {
        let dashes = '';
        for (let k = 0; k < 6; k++) {
          const t1 = Math.pow(k / 6, 1.7);
          const t2 = Math.pow((k + 0.55) / 6, 1.7);
          const y1 = 330 + 270 * t1;
          const y2 = 330 + 270 * t2;
          const w1 = 2 + 16 * t1;
          const w2 = 2 + 16 * t2;
          dashes += `<polygon points="${512 - w1},${y1} ${512 + w1},${y1} ${512 + w2},${y2} ${512 - w2},${y2}"/>`;
        }
        return (
          layer(`<polygon points="${ridge(3, 345, 40, 10)}"/>`, 0.1) +
          '<polygon points="470,332 554,332 1180,600 -156,600" style="fill:rgba(0,0,0,.18)"/>' +
          '<g style="fill:var(--accent);opacity:.38">' + dashes + '</g>'
        );
      }
      default:
        return '';
    }
  };

  /* ---------- Decoraciones de temporada ----------
   * Viven en la franja superior (y < 44) y en las esquinas, para no tapar el texto de las tarjetas.
   * Lo único que cae sobre el contenido son puntitos (nieve, confeti), pequeños y translúcidos. */
  TB.decor = function (season, night) {
    const r = rng(season.length * 97 + 3);
    switch (season) {
      case 'patrias': {
        const cols = ['#d52b1e', '#ffffff', '#0039a6'];
        let s = '<path d="M0 4 Q 512 22 1024 4" stroke="rgba(255,255,255,.6)" stroke-width="1.6" fill="none"/>';
        for (let i = 0; i < 26; i++) {
          const x = 18 + i * 40;
          const y = 4 + 14 * Math.sin((x / 1024) * Math.PI);
          s += `<polygon points="${x - 14},${y} ${x + 14},${y} ${x},${y + 28}" fill="${cols[i % 3]}" opacity=".95"/>`;
        }
        return s;
      }
      case 'halloween': {
        const web = (flip) => {
          const o = flip ? 'transform="translate(1024 0) scale(-1 1)"' : '';
          let d = '';
          for (let i = 0; i <= 5; i++) {
            const a = (i / 5) * (Math.PI / 2);
            d += `<line x1="0" y1="0" x2="${Math.cos(a) * 120}" y2="${Math.sin(a) * 120}"/>`;
          }
          for (let k = 1; k <= 4; k++) {
            const rr = k * 28;
            d += `<path d="M${rr} 0 Q ${rr * 0.62} ${rr * 0.62} 0 ${rr}" fill="none"/>`;
          }
          return `<g ${o} stroke="rgba(255,255,255,.45)" stroke-width="1.2">${d}</g>`;
        };
        const pumpkin = (x) =>
          `<g transform="translate(${x} 22) scale(.55)"><ellipse rx="26" ry="21" fill="#f08a1d"/><ellipse cx="-11" rx="11" ry="20" fill="#f9a23c" opacity=".7"/><rect x="-3" y="-28" width="6" height="9" rx="2" fill="#4c7a2a"/><polygon points="-12,-4 -5,-4 -8,-11" fill="#2a1400"/><polygon points="12,-4 5,-4 8,-11" fill="#2a1400"/><path d="M-12 6 L-6 11 L0 7 L6 11 L12 6" stroke="#2a1400" stroke-width="3" fill="none"/></g>`;
        return web(false) + web(true) + pumpkin(150) + pumpkin(874);
      }
      case 'navidad': {
        const colors = ['#ff5a4d', '#ffd166', '#5ad17a', '#6fb7ff'];
        let s = '<path d="M0 4 Q 512 26 1024 4" stroke="rgba(0,0,0,.45)" stroke-width="1.8" fill="none"/>';
        for (let i = 0; i < 24; i++) {
          const x = 20 + i * 43;
          const y = 4 + 17 * Math.sin((x / 1024) * Math.PI) + 8;
          const c = colors[i % 4];
          s += `<circle cx="${x}" cy="${y}" r="${night ? 6 : 5}" fill="${c}"/>`;
          if (night) s += `<circle cx="${x}" cy="${y}" r="13" fill="${c}" opacity=".26"/>`;
        }
        for (let i = 0; i < 26; i++)
          s += `<circle class="snow" cx="${Math.round(r() * 1024)}" cy="${Math.round(60 + r() * 450)}" r="${(1.2 + r() * 1.8).toFixed(1)}" fill="#fff" opacity="${(0.25 + r() * 0.3).toFixed(2)}"/>`;
        return s;
      }
      case 'anonuevo': {
        const burst = (x, y, c, n, len) => {
          let d = '';
          for (let i = 0; i < n; i++) {
            const a = (i / n) * Math.PI * 2;
            d += `<line x1="${x + Math.cos(a) * 5}" y1="${y + Math.sin(a) * 5}" x2="${x + Math.cos(a) * len}" y2="${y + Math.sin(a) * len}"/>`;
          }
          return `<g stroke="${c}" stroke-width="2.2" stroke-linecap="round" opacity=".95">${d}</g><circle cx="${x}" cy="${y}" r="2.6" fill="${c}"/>`;
        };
        let s = burst(70, 22, '#ffd166', 12, 19) + burst(190, 20, '#ff9fb0', 10, 15) + burst(800, 20, '#9fe0ff', 10, 15) + burst(884, 22, '#ffffff', 12, 18) + burst(24, 62, '#9fe0ff', 8, 14) + burst(1000, 62, '#ffd166', 8, 14);
        for (let i = 0; i < 22; i++) {
          const c = ['#ffd166', '#ffffff', '#ff9fb0', '#9fe0ff'][i % 4];
          s += `<rect x="${Math.round(r() * 1000)}" y="${Math.round(60 + r() * 440)}" width="6" height="2.5" rx="1.2" fill="${c}" opacity=".55" transform="rotate(${Math.round(r() * 180)})"/>`;
        }
        return s;
      }
      default:
        return '';
    }
  };

  TB.seasonLabel = {
    patrias: '¡Felices Fiestas Patrias!',
    halloween: 'Feliz Halloween',
    navidad: 'Feliz Navidad',
    anonuevo: 'Feliz Año Nuevo',
  };
})();
