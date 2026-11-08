# Primera prueba en una radio real

Objetivo: comprobar que la app **se instala, arranca como pantalla de inicio y se ve bien** en tu radio. Todavía no es una versión final.

## Antes de empezar: anota los datos de tu radio

En la radio: *Ajustes > Acerca de* (o *Información del sistema*). Anota:

- Marca y modelo de la radio
- Versión de Android
- Tamaño de la pantalla / resolución (si aparece)
- Si tiene Play Store
- Si tiene GPS propio y si trae un explorador de archivos

## Radio de prueba: la del pitufo

Datos tomados de la pantalla de información de fábrica de la radio:

| | |
|---|---|
| Firmware | `YT7311B_00020_V001` |
| Android | 13 |
| Pantalla | 1280 × 720 (16:9, horizontal) |
| CPU / memoria | 8 núcleos a 2,0 GHz · 4 GB RAM · 64 GB de almacenamiento |
| MCU | 0.8 (AC8258) |
| CAN bus | 1.022, **desconectado**, perfil "Haval H…" (el texto se corta) |

Qué implica:

- **Android 13:** la app está pensada para funcionar desde Android 5, así que no hay problema de versión. Para elegir la pantalla de inicio, en Android 13 suele ser *Ajustes > Apps > Apps predeterminadas > App de inicio* (cada fabricante lo esconde en un lugar distinto).
- **1280 × 720:** es más grande que los 1024 × 600 de la vista previa del sitio. Los tamaños de la app están en dp y deberían adaptarse, pero no está verificado: lo confirmará esta prueba.
- **Potencia de sobra:** 8 núcleos y 4 GB no son un límite para esta app.
- **CAN bus desconectado:** hoy la radio no recibe datos del auto (kilometraje, combustible, luces). Por eso la app se apoya en el GPS para la velocidad y en Ajustes para el kilometraje. Leer el CAN bus sería una mejora futura y depende del fabricante de la radio.
- **No toques** los botones *Configuraciones predeterminadas*, *Actualización de Android* ni *Actualización de MCU* de esa pantalla: borran ajustes o cambian el firmware.

## 1. Compilar el APK (en tu computador personal)

1. Instala **Android Studio** y abre la carpeta `android/` de este repositorio. Acepta instalar el SDK 34.
2. Espera a que termine la sincronización de Gradle. **Si aparecen errores rojos, cópialos tal cual**: el módulo `app` nunca se ha compilado y es probable que haya que corregir detalles.
3. Menú *Build > Build Bundle(s) / APK(s) > Build APK(s)*.
4. El archivo queda en `android/app/build/outputs/apk/debug/app-debug.apk`.

## 2. Instalar en la radio

Elige una forma:

- **Pendrive:** copia `app-debug.apk` a un pendrive, conéctalo a la radio, ábrelo con el explorador de archivos y presiona instalar. Si la radio lo bloquea, activa *Instalar apps de orígenes desconocidos* en sus ajustes.
- **Descarga directa:** si la radio tiene navegador, sube el APK a un enlace y descárgalo desde ahí.
- **Cable (adb):** `adb install app-debug.apk`, si la radio permite depuración USB.

## 3. Abrirla y dejarla como inicio

1. Abre **Tablero** desde la lista de apps.
2. Presiona el botón *Inicio* de la radio. Android preguntará qué pantalla de inicio usar: elige **Tablero** y **Siempre**.
3. Para volver a la original: *Ajustes > Apps > Tablero > Borrar valores predeterminados*.

> Cuidado: si algo sale mal y no puedes volver a la pantalla original, desinstala Tablero (`adb uninstall cl.tablero.launcher` o desde Ajustes > Apps).

## 4. Qué probar (y qué contarme)

Marca cada punto con ✅ o ❌ y escríbeme lo que veas:

- [ ] Se instala sin errores
- [ ] Abre y muestra reloj, fecha y saludo
- [ ] La pantalla se ve completa (no cortada ni con letras enormes)
- [ ] Gira sola o queda en horizontal como debe
- [ ] El botón *Inicio* de la radio vuelve a Tablero
- [ ] Los atajos abren las apps que tienes instaladas
- [ ] *Ajustes* abre y se pueden escribir números (¿sale el teclado?)
- [ ] Al guardar, los avisos (aceite, documentos) aparecen en el inicio
- [ ] Pide permiso de ubicación y muestra la velocidad en movimiento (haz esta prueba con otra persona manejando)
- [ ] El saludo por voz suena (si la radio tiene voz en español instalada)
- [ ] Cuando la apagas y prendes, sigue con tus ajustes

Una foto de la pantalla de la radio con la app abierta ayuda mucho.

## Lo que ya sabemos que falta

- Los temas visuales, distribuciones y fondos de la vista previa (hoy hay un único diseño oscuro).
- Clima, música y radio FM de la vista previa: son datos de ejemplo en el sitio y requieren integración propia con cada radio.
- El límite automático de velocidad por calle (OpenStreetMap): hoy solo funciona el límite fijo.
- Modo noche automático al prender las luces.
- La foto de tu auto y las imágenes de los autos.
