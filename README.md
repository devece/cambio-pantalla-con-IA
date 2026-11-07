# Tablero

Un launcher para radios Android de auto, pensado para Chile (restricción vehicular por GPS, revisión técnica, permiso de circulación, SOAP, teléfono +56 9), y el sitio donde el cliente diseña su pantalla.

**Tablero es gratis.** El cliente elige radio, auto, diseño y funciones, ve su pantalla funcionando en vivo, hace su pedido y recibe un código para activar su diseño en la radio. No hay planes, precios ni cobros.

> **Estado: prototipo.** El sitio, la vista previa y el pedido funcionan. La app Android compila y está en pruebas en radios reales (ver `docs/PRUEBA_EN_RADIO.md`).

## Qué incluye

- **Sitio** estático (HTML/CSS/JS sin build) en `public/`: vista previa de la radio (1024×600) con 9 temas visuales (día y noche), 9 distribuciones, 5 pantallas, fondos, autos en 3D y decoración de temporada (Fiestas Patrias, Halloween, Navidad, Año Nuevo).
- **App Android** en `android/` (el launcher de la radio): `core` (reglas en Kotlin puro, con pruebas) y `app` (pantallas, GPS, ajustes, scanner OBD opcional).
- **Funciones de API** (Vercel) en `api/`: `order` (valida y guarda el pedido), `license` (activa un código vinculado a una sola radio), `admin` (panel para entregar códigos), más el análisis opcional de la captura de la radio y de la foto del auto.
- **Base de datos** Supabase en `supabase/schema.sql` (RLS activado, sin políticas públicas).

## Cómo funciona un pedido

1. El cliente diseña su pantalla en el sitio y pulsa **Pedir mi pantalla**: `api/order.js` valida y guarda el pedido (y su foto).
2. Le mostramos su pedido listo para enviarlo por el contacto del sitio.
3. Quien arma las pantallas lo **aprueba** en `/admin.html` y le entrega su código (`TBL-XXXX-XXXX-XXXX`).
4. El cliente escribe el código en la app, en su radio: queda ligado a esa radio.

## Cómo correrlo

```bash
npm test          # pruebas del sitio y del servidor
npm run dev       # http://localhost:3000
```

Sin variables de entorno funciona en **modo demostración** (el pedido se acepta pero no se guarda).

Las reglas de la app Android se prueban con Gradle: `cd android && ./gradlew :core:test`.

## Variables de entorno

Copia `.env.example` a `.env`:

| Variable | Para qué |
|---|---|
| `SUPABASE_URL`, `SUPABASE_SERVICE_ROLE_KEY` | Guardar pedidos y licencias |
| `LICENSE_PEPPER` | Secreto largo y aleatorio para vincular licencias a una radio |
| `ADMIN_TOKEN` | Clave del panel `/admin.html` |
| `ANTHROPIC_API_KEY` | Analizar con IA la captura de la radio y los faros de la foto (opcional) |

## Publicar en Vercel

1. Sube el repositorio a GitHub e impórtalo en Vercel (la configuración está en `vercel.json`).
2. Crea el proyecto en Supabase y ejecuta `supabase/schema.sql`.
3. Carga las variables de entorno en Vercel.
4. Completa `supportEmail` y `whatsapp` en `public/js/site.js` si quieres que aparezcan en el sitio.

## Privacidad y licencia

- La política de privacidad (`public/privacidad.html`) describe lo que la app y el sitio hacen con los datos (GPS, notificaciones, fotos, scanner OBD). Hay que mantenerla al día cuando cambie algo de eso.
- El sitio que inspiró este proyecto (CleanLauncher) es del mismo autor, que autorizó reutilizar su contenido. Aun así, el código, los textos y el diseño de este repositorio son propios; conviene evitar fotos de autos de terceros con logos de marca.
- `package.json` está como `UNLICENSED`; elige la licencia que corresponda antes de hacer público el repo.
