/*
 * Contadores públicos de la franja de arriba: cuántos descargaron la app, cuántos armaron su propio Tablero aquí (descarga con
 * su diseño) y las visitas. Suma una visita por sesión (sin cookies: solo un aviso en sessionStorage, que se borra al cerrar la
 * pestaña). Si algo falla o todo está en cero, la franja no se muestra.
 */
(function () {
  var bar = document.getElementById('stats-top');
  if (!bar) return;
  try {
    if (!sessionStorage.getItem('tablero:visita')) {
      sessionStorage.setItem('tablero:visita', '1');
      fetch('/api/stats', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ kind: 'visit' }), keepalive: true }).catch(function () {});
    }
  } catch (e) { /* sin sessionStorage: no se cuenta la visita, pero igual se muestran los totales */ }

  var num = function (v) { return Number(v).toLocaleString('es-CL'); };
  // Cada trozo: texto antes, número en negrita, texto después.
  var piece = function (before, value, after) {
    var span = document.createElement('span');
    span.appendChild(document.createTextNode(before));
    var b = document.createElement('b');
    b.textContent = num(value);
    span.appendChild(b);
    span.appendChild(document.createTextNode(after));
    return span;
  };

  fetch('/api/stats')
    .then(function (r) { return r.ok ? r.json() : null; })
    .then(function (d) {
      if (!d || !d.ok) return;
      var downloads = Number(d.downloads) || 0;
      var design = Number(d.downloadsDesign) || 0;
      var visits = Number(d.visits) || 0;
      if (downloads + design + visits === 0) return;
      var parts = [];
      if (downloads > 0) parts.push(downloads === 1 ? piece('Ya descargó la app ', 1, ' persona') : piece('Ya son ', downloads, ' los que han descargado la app'));
      if (design > 0) parts.push(design === 1 ? piece('', 1, ' ha creado su propio Tablero aquí') : piece('', design, ' han creado su propio Tablero aquí'));
      if (visits > 0) parts.push(piece('', visits, visits === 1 ? ' visita' : ' visitas'));
      bar.textContent = '';
      parts.forEach(function (p, i) {
        if (i) bar.appendChild(document.createTextNode(' · '));
        bar.appendChild(p);
      });
      bar.hidden = false;
    })
    .catch(function () {});
})();
