/*
 * Configuración del sitio. Edita este archivo para cambiar marca y contacto sin tocar el resto del código.
 */
(function () {
  const TB = (window.TB = window.TB || {});
  TB.site = {
    name: 'Tablero',
    // Mientras sea true se muestra la cinta de "prototipo". Ponla en false cuando el launcher y el cobro estén listos.
    prototype: true,
    // Completa tus datos reales de contacto (se muestran en el pie de la página).
    supportEmail: '',
    whatsapp: '',
    // Instagram (usuario sin @). Opcional.
    instagram: '',
    // Opcionales: si los completas, el sitio muestra la opción gratis y el enlace "Ya tengo mi código".
    freeUrl: '',      // p. ej. la página de Releases con el APK gratuito
    downloadUrl: '',  // dirección de descarga de la app: se muestra en la guía de instalación (listo.html)
  };
})();
