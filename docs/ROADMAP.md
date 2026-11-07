# Hoja de ruta

Tablero es gratis: no hay planes, precios ni cobros. Lo que sigue es hacer que la app funcione muy bien en radios reales.

## Ahora
1. **Probar la app en radios reales** (ver `PRUEBA_EN_RADIO.md`): kilometraje automático, botones del volante, radio FM, scanner OBD.
2. **Licencia del repositorio** (hoy `UNLICENSED`): elegirla antes de hacerlo público.
3. **Contacto real**: `supportEmail` y `whatsapp` en `public/js/site.js`.

## Después
- **Conexión OBD (el "Taller")**: km automático, datos del motor y códigos de falla. Plan por fases en [`PLAN_OBD.md`](PLAN_OBD.md).
- **Límite de velocidad automático por calle**: hoy solo existe el límite fijo (ver `FREE_EDITION.md`).
- Correo con el código (por ejemplo Resend) con dominio propio, para no depender del mensaje manual.
- Procesar la foto de fondo (hoy se queda en el navegador y el complemento está oculto).
- Panel simple para ver pedidos y reenviar códigos (hoy `/admin.html` entrega códigos y administra licencias).
