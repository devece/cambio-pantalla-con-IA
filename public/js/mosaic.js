/*
 * Fondo en mosaico: varias tomas del mismo auto (y, si se quiere, el logo y la marca) armadas en una sola imagen de 1024 × 600.
 * Todo ocurre en el navegador; las fotos no se suben. El resultado se usa como fondo de pantalla (ver app.js).
 */
(function () {
  const W = 1024;
  const H = 600;
  const GAP = 6;
  const MAX = 6;

  // Cómo se reparte la pantalla según cuántas tomas hay. Cada tile es [x, y, ancho, alto] como fracción de la pantalla.
  const LAYOUTS = {
    1: [[0, 0, 1, 1]],
    2: [[0, 0, 0.5, 1], [0.5, 0, 0.5, 1]],
    3: [[0, 0, 0.62, 1], [0.62, 0, 0.38, 0.5], [0.62, 0.5, 0.38, 0.5]],
    4: [[0, 0, 0.5, 0.5], [0.5, 0, 0.5, 0.5], [0, 0.5, 0.5, 0.5], [0.5, 0.5, 0.5, 0.5]],
    5: [[0, 0, 0.5, 0.5], [0.5, 0, 0.5, 0.5], [0, 0.5, 1 / 3, 0.5], [1 / 3, 0.5, 1 / 3, 0.5], [2 / 3, 0.5, 1 / 3, 0.5]],
    6: [[0, 0, 1 / 3, 0.5], [1 / 3, 0, 1 / 3, 0.5], [2 / 3, 0, 1 / 3, 0.5], [0, 0.5, 1 / 3, 0.5], [1 / 3, 0.5, 1 / 3, 0.5], [2 / 3, 0.5, 1 / 3, 0.5]],
  };

  /** Los rectángulos (en píxeles, con el espacio entre tomas) para n tomas. n se limita a 1..6. */
  function tiles(n) {
    const k = Math.max(1, Math.min(MAX, Math.floor(n) || 1));
    return LAYOUTS[k].map(([x, y, w, h]) => {
      const x0 = Math.round(x * W);
      const y0 = Math.round(y * H);
      const x1 = Math.round((x + w) * W);
      const y1 = Math.round((y + h) * H);
      // Los bordes que tocan el marco de la pantalla no llevan espacio; los de adentro, medio espacio por lado.
      const l = x0 === 0 ? 0 : GAP / 2;
      const t = y0 === 0 ? 0 : GAP / 2;
      const r = x1 === W ? 0 : GAP / 2;
      const b = y1 === H ? 0 : GAP / 2;
      return { x: x0 + l, y: y0 + t, w: x1 - x0 - l - r, h: y1 - y0 - t - b };
    });
  }

  /** Dibuja la imagen llenando el rectángulo (recortando lo que sobre, centrado). */
  function cover(x, img, t) {
    const iw = img.width || img.naturalWidth;
    const ih = img.height || img.naturalHeight;
    if (!iw || !ih) return;
    const k = Math.max(t.w / iw, t.h / ih);
    const sw = t.w / k;
    const sh = t.h / k;
    x.drawImage(img, (iw - sw) / 2, (ih - sh) / 2, sw, sh, t.x, t.y, t.w, t.h);
  }

  /** Cuántas tomas caben: si hay logo o marca, uno de los recuadros es para ellos. */
  const room = (hasBrand) => (hasBrand ? MAX - 1 : MAX);

  /** Recuadro del logo y/o la marca: fondo oscuro con el logo arriba y el nombre debajo, centrados. */
  function brandTile(x, t, logo, text) {
    const g = x.createLinearGradient(t.x, t.y, t.x + t.w, t.y + t.h);
    g.addColorStop(0, '#1a2030');
    g.addColorStop(1, '#0b0e13');
    x.fillStyle = g;
    x.fillRect(t.x, t.y, t.w, t.h);
    const pad = Math.min(t.w, t.h) * 0.12;
    const textH = text ? Math.min(46, t.h * 0.22) : 0;
    const logoH = logo ? t.h - pad * 2 - (text ? textH + 10 : 0) : 0;
    const total = logoH + (logo && text ? 10 : 0) + textH;
    let y = t.y + (t.h - total) / 2;
    if (logo) {
      const iw = logo.width || logo.naturalWidth;
      const ih = logo.height || logo.naturalHeight;
      const k = Math.min((t.w - pad * 2) / iw, logoH / ih);
      const w = iw * k;
      const h = ih * k;
      x.drawImage(logo, t.x + (t.w - w) / 2, y + (logoH - h) / 2, w, h);
      y += logoH + 10;
    }
    if (text) {
      x.fillStyle = '#fff';
      x.textAlign = 'center';
      x.textBaseline = 'middle';
      let size = Math.round(textH * 0.85);
      x.font = `700 ${size}px system-ui, sans-serif`;
      while (x.measureText(text).width > t.w - pad * 2 && size > 12) { size -= 2; x.font = `700 ${size}px system-ui, sans-serif`; }
      x.fillText(text, t.x + t.w / 2, y + textH / 2);
    }
  }

  /**
   * shots: imágenes (ImageBitmap, canvas o <img>) con las tomas del auto. logo: imagen opcional. brand: texto opcional.
   * Devuelve un canvas de 1024 × 600, oscurecido para que se lean los datos de la pantalla encima.
   */
  function compose({ shots, logo, brand }) {
    const c = document.createElement('canvas');
    c.width = W;
    c.height = H;
    const x = c.getContext('2d');
    x.fillStyle = '#0b0e13';
    x.fillRect(0, 0, W, H);
    const text = String(brand || '').trim().slice(0, 24);
    const hasBrand = !!(logo || text);
    const list = (shots || []).filter(Boolean).slice(0, room(hasBrand));
    const grid = tiles(list.length + (hasBrand ? 1 : 0) || 1);
    const brandAt = hasBrand ? Math.min(1, grid.length - 1) : -1; // el logo va en el segundo recuadro (o el único)
    let k = 0;
    grid.forEach((t, i) => {
      if (i === brandAt) brandTile(x, t, logo, text);
      else if (list[k]) cover(x, list[k++], t);
    });
    // El velo oscuro deja leer la hora, los botones y las tarjetas.
    x.fillStyle = 'rgba(0,0,0,.36)';
    x.fillRect(0, 0, W, H);
    return c;
  }

  const api = { compose, tiles, room, MAX };
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
  if (typeof window !== 'undefined') (window.TB = window.TB || {}).mosaic = api;
})();
