/*
 * Configuración del sitio. Edita este archivo para cambiar marca y contacto sin tocar el resto del código.
 */
(function () {
  const TB = (window.TB = window.TB || {});
  TB.site = {
    name: 'Tablero',
    // Completa tus datos reales de contacto (se muestran en el pie de la página).
    supportEmail: '',
    whatsapp: '',
    // Forma de cobro: 'contact' = el pago se coordina en el contacto de Daniexpress (el sitio no muestra cuentas);
    // 'transfer' = el sitio muestra los datos de `bank` para transferir.
    payment: 'contact',
    // Datos de TU cuenta para recibir transferencias. Si falta alguno, el sitio avisa que aún no están y no muestra la cuenta.
    bank: { bank: '', type: '', number: '', holder: '', rut: '' },
    // Tu página web como contacto. Si `url` está vacía, se muestra solo el nombre (sin enlace).
    web: { name: 'Daniexpress', url: 'https://wa.me/56958897906' },
    // Instagram (usuario sin @). Opcional.
    instagram: '',
    // Opcionales: si los completas, el sitio muestra la opción gratis y el enlace "Ya tengo mi código".
    freeUrl: '',      // p. ej. la página de Releases con el APK gratuito
    downloadUrl: 'https://tablero-pearl.vercel.app/app',  // dirección corta para descargar la app: se muestra en la guía (listo.html). Redirige al instalador.
  };
})();
