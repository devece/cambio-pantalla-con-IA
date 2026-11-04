/*
 * Versión nocturna de la foto del auto: la oscurece un poco y enciende los faros donde el cliente (o la IA) los marcó.
 * Todo ocurre en el navegador; la foto no se sube.
 */
(function () {
  const TB = (window.TB = window.TB || {});

  const glow = (x, g, X, Y, R, stops) => {
    const grd = x.createRadialGradient(X, Y, 0, X, Y, R);
    stops.forEach(([o, c]) => grd.addColorStop(o, c));
    x.fillStyle = grd;
    x.fillRect(X - R, Y - R, R * 2, R * 2);
  };

  /**
   * src: canvas con el auto recortado (fondo transparente). pts: [{ kind, x, y, size }] con x e y entre 0 y 1.
   * Devuelve un canvas nuevo con el auto de noche.
   */
  function night(src, pts) {
    const w = src.width;
    const h = src.height;
    const c = document.createElement('canvas');
    c.width = w;
    c.height = h;
    const x = c.getContext('2d');
    x.drawImage(src, 0, 0);
    // Anochece: solo sobre el auto (no toca lo transparente).
    x.globalCompositeOperation = 'source-atop';
    x.fillStyle = 'rgba(10, 14, 44, .34)';
    x.fillRect(0, 0, w, h);
    x.globalCompositeOperation = 'lighter';
    const base = Math.max(w, h) * 0.034;
    (pts || []).forEach((p) => {
      const R = base * (p.size || 1);
      const X = p.x * w;
      const Y = p.y * h;
      const rear = p.kind === 'luz_trasera';
      const warm = rear ? ['255,60,50', '255,120,100'] : ['255,236,190', '255,248,228'];
      glow(x, null, X, Y, R * 4.5, [[0, `rgba(${warm[0]},.5)`], [0.3, `rgba(${warm[0]},.16)`], [1, `rgba(${warm[0]},0)`]]);
      if (!rear) {
        // destello alargado hacia los lados, como el de una luz real
        x.save();
        x.translate(X, Y);
        x.scale(5, 0.3);
        glow(x, null, 0, 0, R, [[0, `rgba(${warm[1]},.55)`], [1, `rgba(${warm[1]},0)`]]);
        x.restore();
      }
      glow(x, null, X, Y, R, [[0, 'rgba(255,255,255,1)'], [0.45, `rgba(${warm[1]},.85)`], [1, `rgba(${warm[1]},0)`]]);
    });
    x.globalCompositeOperation = 'source-over';
    return c;
  }

  TB.lights = { night };
})();
