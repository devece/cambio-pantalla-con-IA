/*
 * Contadores públicos del pie: visitas y descargas de la app (con el desglose de descargas con diseño y normales). Suma una visita por sesión (sin cookies: solo un aviso en
 * sessionStorage, que se borra al cerrar la pestaña) y muestra los totales. Si algo falla, el pie queda sin contadores.
 */
(function () {
  var line = document.getElementById('stats-line');
  if (!line) return;
  try {
    if (!sessionStorage.getItem('tablero:visita')) {
      sessionStorage.setItem('tablero:visita', '1');
      fetch('/api/stats', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ kind: 'visit' }), keepalive: true }).catch(function () {});
    }
  } catch (e) { /* sin sessionStorage: no se cuenta la visita, pero igual se muestran los totales */ }
  var n = function (v, uno, otros) { return Number(v).toLocaleString('es-CL') + ' ' + (Number(v) === 1 ? uno : otros); };
  fetch('/api/stats')
    .then(function (r) { return r.ok ? r.json() : null; })
    .then(function (d) {
      if (!d || !d.ok) return;
      var text = n(d.visits, 'visita', 'visitas') + ' · ' + n(d.downloads, 'descarga', 'descargas');
      // Desglose de las descargas. Las que se contaron antes de separarlas no tienen tipo: se nombran aparte para que todo cuadre.
      var design = Number(d.downloadsDesign) || 0;
      var plain = Number(d.downloadsPlain) || 0;
      if (design + plain > 0) {
        var parts = ['con diseño ' + design.toLocaleString('es-CL'), 'normal ' + plain.toLocaleString('es-CL')];
        var before = Number(d.downloads) - design - plain;
        if (before > 0) parts.push('antes del desglose ' + before.toLocaleString('es-CL'));
        text += ' (' + parts.join(' · ') + ')';
      }
      line.textContent = text;
      line.hidden = false;
    })
    .catch(function () {});
})();
