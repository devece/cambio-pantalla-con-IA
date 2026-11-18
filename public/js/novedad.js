/* Aviso de la novedad (pop up): sale una sola vez por novedad en cada navegador. ?novedad=1 lo fuerza para probarlo. */
(function () {
  const dlg = document.getElementById('novedad');
  if (!dlg) return;
  const KEY = 'tablero:novedad';
  const id = dlg.dataset.id;
  const seen = () => { try { return localStorage.getItem(KEY) === id; } catch (_) { return false; } };
  const mark = () => { try { localStorage.setItem(KEY, id); } catch (_) { /* sin almacenamiento: puede volver a salir */ } };
  const forced = /[?&]novedad=1/.test(location.search);
  if (seen() && !forced) return;

  dlg.addEventListener('close', () => {
    mark();
    if (dlg.returnValue === 'probar') {
      const target = document.getElementById('configurador');
      if (target) target.scrollIntoView({ behavior: window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 'auto' : 'smooth' });
    }
  });
  // Un toque fuera de la tarjeta también la cierra.
  dlg.addEventListener('click', (e) => { if (e.target === dlg) dlg.close('cerrar'); });

  setTimeout(() => {
    if (typeof dlg.showModal === 'function') dlg.showModal();
    else dlg.setAttribute('open', ''); // navegador antiguo: se muestra sin fondo oscuro
  }, 1200);
})();
