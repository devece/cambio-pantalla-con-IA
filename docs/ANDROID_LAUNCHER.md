# Launcher Android

El proyecto está en `android/`. Es un **esqueleto**: arranca como pantalla de inicio de la radio, sabe si está en modo gratis o activado, y activa códigos contra `/api/license`. Todavía no tiene los temas visuales ni las pantallas de funciones.

## Estructura

```
android/
├── core/   Kotlin puro (sin Android): reglas de las ediciones, formato del código, respuesta del servidor
└── app/    La app Android (Activity de inicio y de activación)
```

- `core/Editions.kt`: lee `public/shared/editions.json` (el mismo archivo que usa el sitio).
- `core/Gate.kt`: el candado. Dada una configuración y los límites de un nivel, la recorta a lo permitido y lista lo que "requiere código".
- `core/LicenseCode.kt`: normaliza `TBL-XXXX-XXXX-XXXX` e interpreta la respuesta de `POST /api/license`.
- `app/MainActivity`: reloj, fecha, atajos a apps (limitados según el nivel) y botón **Activar código** en modo gratis.
- `app/ActivationActivity`: pide el código, lo valida con el servidor y guarda el nivel en la radio, así sigue funcionando sin internet.
- `app/build.gradle.kts` copia `editions.json` a los assets al compilar, para que sitio y app nunca se desincronicen.

`/api/license` ahora responde también el plan: `{ ok, status: "active", plan: "esencial" | "completo" }`.

## Cómo compilarlo

1. Abre la carpeta `android/` en **Android Studio** (instala el SDK 34 cuando lo pida).
2. Para probar las reglas, sin Android: `./gradlew :core:test`
3. Para generar el APK, indicando dónde corre tu sitio:
   ```bash
   ./gradlew :app:assembleDebug -PtableroApiBase=https://tu-dominio.com
   ```
   El APK queda en `android/app/build/outputs/apk/debug/`.

Sin `-PtableroApiBase` la app usa `https://TU-DOMINIO.vercel.app`, que es un marcador: la activación fallará hasta que lo cambies.

## Qué está verificado y qué no

- **Verificado:** el módulo `core` compila y sus 13 pruebas pasan (candado de cada nivel, formato del código, respuestas del servidor), leyendo el `editions.json` real.
- **NO verificado:** el módulo `app`. Se escribió en un entorno sin Android SDK, así que **nunca se ha compilado ni ejecutado**. Es posible que Android Studio muestre errores de compilación que haya que corregir. Los números de versión (Android Gradle Plugin 8.7.3, Kotlin 2.0.21, SDK 34) tampoco se probaron juntos.
- **NO verificado:** nada se ha probado en una radio real. Cada marca y chip se comporta distinto (permisos, arranque, rotación, identificador del equipo).

## Lo que falta

- Los 8 temas, 4 distribuciones y 5 pantallas de `public/js/themes.js` y `public/js/preview.js`.
- Las funciones: avisos de mantención, documentos (revisión técnica, permiso, SOAP), alerta de velocidad (GPS), consumo, restricción vehicular y saludo por voz.
- Una pantalla de ajustes (hoy la configuración es la de fábrica) y cómo trae el cliente su pedido del configurador web.
- Ícono definitivo (el actual es provisional).
- Volver a verificar el código con el servidor de vez en cuando, para detectar códigos revocados.
- Un identificador de radio confiable: algunas radios baratas repiten el mismo `ANDROID_ID`.
- Firmar el APK de release y publicarlo en las Releases de GitHub.
