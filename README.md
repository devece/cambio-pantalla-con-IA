# Tablero

Un launcher para radios Android de auto, pensado para Chile (restricción vehicular por GPS, revisión técnica, permiso de circulación, SOAP, teléfono +56 9), y el sitio donde el cliente diseña su pantalla.

**Tablero es gratis.** El cliente diseña su pantalla en el sitio y descarga la app **con su diseño ya puesto** (o la descarga normal y lo elige en la radio). No hay códigos, planes, precios ni cobros.

> **Estado: prototipo.** El sitio y la vista previa funcionan. La app Android compila y está en pruebas en radios reales (ver `docs/PRUEBA_EN_RADIO.md`).

## Qué incluye

- **Sitio** estático (HTML/CSS/JS sin build) en `public/`: vista previa de la radio (1024×600) con 9 temas visuales (día y noche), 9 distribuciones, 5 pantallas, fondos, autos en 3D y decoración de temporada (Fiestas Patrias, Halloween, Navidad, Año Nuevo).
- **App Android** en `android/` (el launcher de la radio): `core` (reglas en Kotlin puro, con pruebas) y `app` (pantallas, GPS, ajustes, scanner OBD opcional).
- **Funciones de API** (Vercel) en `api/`: `app` (entrega la APK con el diseño y la foto del cliente ya adentro), `analyze-radio` (analiza la captura de la radio) y `detect-lights` (ubica los faros en la foto del auto). Las dos últimas usan IA y son opcionales. **No hay cuentas ni datos de usuarios**: lo único que se guarda son dos totales, los contadores públicos de visitas y descargas (`api/stats.js`, `lib/stats.js`).

## Cómo se usa (sin pedidos ni códigos)

1. El cliente diseña su pantalla en el sitio; puede bajar su auto ya recortado (PNG).
2. Descarga la app con su diseño: `GET /api/app?d=…` (o `POST /api/app` con la foto, que no cabe en una dirección; `api/app.js`) toma la APK de `app-latest` y le agrega el diseño y la foto en el bloque de firmas, sin romper la firma (`lib/apk-inject.js`). La foto no se guarda y tiene un tope de tamaño (la respuesta de una función de Vercel no puede pasar de 4,5 MB). Sin diseño, `/app` entrega la APK normal.
3. Al abrirse por primera vez, la app lee su propio archivo (`ApkBlock.kt`) y aplica el diseño (`DesignImport.kt`). También puede elegir todo en Ajustes → Apariencia y Foto de tu auto. Se guarda en la radio.

El antiguo servidor de pedidos y códigos (`api/order`, `api/order-photo`, `api/license`, `api/admin`, la base de datos y el panel `/admin.html`) se retiró: ya no hay códigos. Está en el historial de git si hiciera falta.

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
| `ANTHROPIC_API_KEY` | Analizar con IA la captura de la radio y los faros de la foto (opcional) |
| `AI_DAILY_MAX` | Tope diario de llamadas a la IA. Sin él, la IA queda apagada aunque haya clave (ver `docs/SEGURIDAD.md`) |
| `STATS_SUPABASE_URL`, `STATS_SUPABASE_ANON_KEY`, `STATS_HIT_KEY` | Contadores de visitas y descargas (opcionales). Viven en dos tablas de Supabase (`tablero_stats` y `tablero_stats_key`) que solo se tocan por las funciones `tablero_hit` y `tablero_totals`; sumar exige la clave `STATS_HIT_KEY`. Las descargas se cuentan en total y por tipo (`downloads_design`, `downloads_plain`) |
| `TABLERO_APK_URL` | De dónde toma el botón «Descargar la app con mi diseño» la APK base (opcional; por defecto, la última compilación) |

## Publicar en Vercel

1. Sube el repositorio a GitHub e impórtalo en Vercel (la configuración está en `vercel.json`).
2. Carga las variables de entorno en Vercel (todas son opcionales).
3. Completa `supportEmail` y `whatsapp` en `public/js/site.js` si quieres que aparezcan en el sitio.

## Privacidad y licencia

- La política de privacidad (`public/privacidad.html`) describe lo que la app y el sitio hacen con los datos (GPS, notificaciones, fotos, scanner OBD). Hay que mantenerla al día cuando cambie algo de eso.
- El sitio que inspiró este proyecto (CleanLauncher) es del mismo autor, que autorizó reutilizar su contenido. Aun así, el código, los textos y el diseño de este repositorio son propios; conviene evitar fotos de autos de terceros con logos de marca.
- `package.json` está como `UNLICENSED`; elige la licencia que corresponda antes de hacer público el repo.
