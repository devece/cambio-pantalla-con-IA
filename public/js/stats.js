/*
 * Contadores públicos del pie: visitas y descargas de la app. Suma una visita por sesión (sin cookies: solo un aviso en
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
      line.textContent = n(d.visits, 'visita', 'visitas') + ' · ' + n(d.downloads, 'descarga', 'descargas');
      line.hidden = false;
    })
    .catch(function () {});
})();
