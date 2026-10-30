# Hoja de ruta

## Antes de vender (obligatorio)
1. **Medio de pago**: Flow, Mercado Pago o Webpay. Crear la orden en `api/checkout.js`, recibir el webhook de confirmación y recién ahí emitir la licencia (`lib/license.js`).
2. **Páginas legales**: términos, privacidad y devoluciones, revisadas por un abogado.
3. **Correo transaccional** (por ejemplo Resend) con dominio propio para enviar el código.
4. **Contacto real**: `supportEmail` y `whatsapp` en `public/js/site.js`; quitar la cinta de prototipo.
5. **Launcher Android** (ver `ANDROID_LAUNCHER.md`): sin él, el sitio no entrega producto.

## Después
- Procesar la foto de fondo (hoy se queda en el navegador y el complemento está oculto).
- Panel simple para ver pedidos y reenviar códigos.
- Confirmar precios con costos reales y soporte postventa.
