# Mapa de Google en la pantalla de inicio

La pantalla de inicio puede mostrar el **mapa de Google** (tu posición y rumbo) en vez del de OpenStreetMap. Sale en los diseños **Estelar** (el de fábrica), **Consola** y **Tablero**.

## Cómo funciona

- La app usa la **Maps Static API** de Google (una imagen del mapa por HTTPS). No necesita Google Play Services, así que sirve también en radios chinas sin Google.
- La pantalla pide la imagen sin llave; la app (`WebLauncher.googleMapResponse`) le pone la llave y se identifica con su **paquete** y la **huella SHA-1 de su firma** (`X-Android-Package`, `X-Android-Cert`).
- Solo pasan las peticiones que la pantalla hace (`GoogleMaps.parse`): centro, zoom 15, 640×640, escala 2, de día o de noche. Nada más llega a Google con la llave.
- Se baja una imagen y se desliza mientras avanzas; recién se pide otra al alejarte unos 240 m del centro.
- **Sin llave** (o sin internet) el mapa sigue siendo el de OpenStreetMap, con su copia para ver sin conexión. Con Google no hay «Guardar mapa»: las condiciones de Google no permiten guardar sus imágenes.

## Cómo activarlo (una vez)

1. En [Google Cloud](https://console.cloud.google.com/) crea un proyecto, activa la facturación y habilita **Maps Static API**.
2. Crea una **llave de API** y restríngela:
   - *Restricción de aplicación:* **Apps de Android** → paquete `cl.tablero.launcher` + la huella SHA-1 de la firma (`docs/FIRMA_APK.md`).
   - *Restricción de API:* solo **Maps Static API**.
3. Pon un tope de cuota/presupuesto diario en Google Cloud (cada imagen cuenta como una carga; hay un crédito mensual gratis).
4. Guarda la llave como secreto de GitHub **`GOOGLE_MAPS_API_KEY`**. Las compilaciones (`android.yml` en `main` y `release.yml`) la ponen en la app. A mano: `./gradlew :app:assembleRelease -PgoogleMapsApiKey=...`.

La llave queda dentro de la APK (como en cualquier app con mapas), por eso es obligatoria la restricción por paquete y firma.

## Privacidad

Con Google, la posición aproximada que se pide (centro de la imagen) va a los servidores de Google. Está dicho en `public/privacidad.html`.
