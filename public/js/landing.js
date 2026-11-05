/* Menú de la portada: abrir y cerrar en el celular, y marcar la sección que estás viendo. */
(function () {
  const btn = document.getElementById('menu-btn');
  const menu = document.getElementById('menu');
  if (!btn || !menu) return;
  const setOpen = (open) => {
    menu.classList.toggle('open', open);
    btn.setAttribute('aria-expanded', String(open));
    btn.setAttribute('aria-label', open ? 'Cerrar el menú' : 'Abrir el menú');
  };
  btn.addEventListener('click', () => setOpen(!menu.classList.contains('open')));
  menu.addEventListener('click', (e) => { if (e.target.closest('a')) setOpen(false); });
  document.addEventListener('keydown', (e) => { if (e.key === 'Escape') setOpen(false); });

  const links = Array.from(menu.querySelectorAll('a[href^="#"]'));
  const targets = links.map((a) => document.getElementById(a.getAttribute('href').slice(1)));
  if (!('IntersectionObserver' in window)) return;
  const seen = new Set();
  const io = new IntersectionObserver((entries) => {
    entries.forEach((en) => { if (en.isIntersecting) seen.add(en.target); else seen.delete(en.target); });
    const first = targets.find((t) => t && seen.has(t));
    links.forEach((a, i) => (targets[i] && targets[i] === first ? a.setAttribute('aria-current', 'true') : a.removeAttribute('aria-current')));
  }, { rootMargin: '-30% 0px -55% 0px' });
  targets.forEach((t) => t && io.observe(t));
})();
