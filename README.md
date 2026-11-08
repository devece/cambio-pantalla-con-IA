# Tablero

Un launcher para radios Android de auto, pensado para Chile (restricción vehicular por GPS, revisión técnica, permiso de circulación, SOAP, teléfono +56 9), y el sitio donde el cliente diseña su pantalla.

**Tablero es gratis.** El cliente diseña su pantalla en el sitio y descarga la app **con su diseño ya puesto** (o la descarga normal y lo elige en la radio). No hay códigos, planes, precios ni cobros.

> **Estado: prototipo.** El sitio y la vista previa funcionan. La app Android compila y está en pruebas en radios reales (ver `docs/PRUEBA_EN_RADIO.md`).

## Qué incluye

- **Sitio** estático (HTML/CSS/JS sin build) en `public/`: vista previa de la radio (1024×600) con 9 temas visuales (día y noche), 9 distribuciones, 5 pantallas, fondos, autos en 3D y decoración de temporada (Fiestas Patrias, Halloween, Navidad, Año Nuevo).
- **App Android** en `android/` (el launcher de la radio): `core` (reglas en Kotlin puro, con pruebas) y `app` (pantallas, GPS, ajustes, scanner OBD opcional).
- **Funciones de API** (Vercel) en `api/`: `order` (valida y guarda el pedido), `license` (activa un código vinculado a una sola radio), `admin` (panel para entregar códigos), más el análisis opcional de la captura de la radio y de la foto del auto.
- **Base de datos** Supabase en `supabase/schema.sql` (RLS activado, sin políticas públicas).

## Cómo se usa (sin pedidos ni códigos)

1. El cliente diseña su pantalla en el sitio; puede bajar su auto ya recortado (PNG).
2. Descarga la app con su diseño: `GET /api/app?d=…` (o `POST /api/app` con la foto, que no cabe en una dirección; `api/app.js`) toma la APK de `app-latest` y le agrega el diseño y la foto en el bloque de firmas, sin romper la firma (`lib/apk-inject.js`). La foto no se guarda y tiene un tope de tamaño (la respuesta de una función de Vercel no puede pasar de 4,5 MB). Sin diseño, `/app` entrega la APK normal.
3. Al abrirse por primera vez, la app lee su propio archivo (`ApkBlock.kt`) y aplica el diseño (`DesignImport.kt`). También puede elegir todo en Ajustes → Apariencia y Foto de tu auto. Se guarda en la radio.

Las funciones `api/order`, `api/license` y el panel `admin` quedaron sin uso desde el sitio (solo sirven a radios que ya activaron un código) y se pueden retirar más adelante.

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
