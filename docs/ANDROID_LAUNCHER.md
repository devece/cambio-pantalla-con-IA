# Launcher Android

El proyecto está en `android/`. Arranca como pantalla de inicio de la radio y ya hace lo siguiente:

- Muestra reloj, fecha y saludo según la hora.
- **Tu auto en 3D** (auto, SUV o camioneta) en el color que elijas, visto en perspectiva con luces y sombras, y con un balanceo suave. Está dibujado por código (`core/Car3D.kt`), sin imágenes.
- **Avisos de aceite y otras mantenciones, revisión técnica, permiso, SOAP, combustible y restricción vehicular** (con aviso por GPS al entrar a Santiago, ver `RESTRICCION.md`), con las mismas cuentas que la vista previa del sitio (probadas en `core`).
- **Velocidad en vivo con el GPS** y alerta contra un límite fijo.
- **Saludo por voz** al abrir la app.
- **Ajustes** dentro de la radio (sin internet) y **atajos a apps**.
- **Sin códigos de activación**: el servidor `/api/license` y la pantalla de activación se retiraron. El diseño que se arma en el sitio viaja dentro de la APK (ver `README.md`). Una radio que ya había activado un código conserva su diseño y su nivel, que se guardaron en ella. Todo es gratis: el candado de ediciones sigue en el código pero hoy no limita nada.

Para probarla en una radio: `PRUEBA_EN_RADIO.md`.

## Estructura

```
android/
├── core/   Kotlin puro (sin Android): reglas de las ediciones, formato del código, respuesta del servidor
└── app/    La app Android (Activity de inicio y de activación)
```

- `core/Editions.kt`: lee `public/shared/editions.json` (el mismo archivo que usa el sitio).
- `core/Gate.kt`: el candado. Dada una configuración y los límites de un nivel, la recorta a lo permitido y lista lo que "requiere código".
- `core/ApkBlock.kt` y `core/DesignImport.kt`: leen el diseño y la foto que trae la APK descargada del sitio y los aplican.
- `app/MainActivity`: reloj, fecha, atajos a apps y pantalla de inicio; aplica el diseño de su propia APK al abrirse.
- `app/build.gradle.kts` copia `editions.json` a los assets al compilar, para que sitio y app nunca se desincronicen.

## Cómo compilarlo

1. Abre la carpeta `android/` en **Android Studio** (instala el SDK 34 cuando lo pida).
2. Para probar las reglas, sin Android: `./gradlew :core:test`
3. Para generar el APK:
   ```bash
   ./gradlew :app:assembleDebug
   ```
   El APK queda en `android/app/build/outputs/apk/debug/`.

## Qué está verificado y qué no

- **Verificado:** el módulo `core` compila y sus 37 pruebas pasan (candado de cada nivel, formato del código, respuestas del servidor, avisos y el auto 3D), leyendo los `editions.json` y `maintenance.json` reales.
- **NO verificado:** el módulo `app`. Se escribió en un entorno sin Android SDK, así que **nunca se ha compilado ni ejecutado**. Es posible que Android Studio muestre errores de compilación que haya que corregir. Los números de versión (Android Gradle Plugin 8.7.3, Kotlin 2.0.21, SDK 34) tampoco se probaron juntos.
- **NO verificado:** nada se ha probado en una radio real. Cada marca y chip se comporta distinto (permisos, arranque, rotación, identificador del equipo).

## Lo que falta

- La foto real del cliente con el fondo recortado (hoy el auto es un modelo 3D simple, no una foto).
- Los 8 temas, 4 distribuciones y 5 pantallas de `public/js/themes.js` y `public/js/preview.js` (hoy hay un único diseño oscuro).
- Clima, música y radio FM: en el sitio son datos de ejemplo. En la radio real requieren integración propia (API de clima, permiso de notificaciones para la música, y la API de cada fabricante para la radio FM).
- Límite automático de velocidad por calle (consulta a OpenStreetMap). Hoy solo funciona el límite fijo.
- Modo noche automático al prender las luces, y el aviso si la velocidad supera el límite con sonido.
- Cómo trae el cliente su pedido del configurador web a la radio (hoy se configura a mano en Ajustes).
- Ícono definitivo (el actual es provisional).
- Volver a verificar el código con el servidor de vez en cuando, para detectar códigos revocados.
- Un identificador de radio confiable: algunas radios baratas repiten el mismo `ANDROID_ID`.
- Firmar el APK de release y publicarlo en las Releases de GitHub.
