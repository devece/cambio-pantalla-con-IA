# Primera prueba en una radio real

Objetivo: comprobar que la app **se instala, arranca como pantalla de inicio, aplica el diseño que traía y se ve bien** en una radio real. Es una versión de prueba: nadie la ha instalado todavía en un aparato, así que cualquier cosa que pase es información valiosa.

No necesitas compilar nada ni tener un código: la app se baja lista desde el sitio.

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

## 1. Conseguir la APK

Hay dos caminos. Elige uno:

- **Con tu diseño y tu foto (recomendado):** entra a https://tablero-pearl.vercel.app/, arma tu pantalla y en el último paso pulsa **«Descargar la app con mi diseño»**. La APK ya trae tu tema, tu nombre, tus avisos y, si la subiste, tu foto. Esto es lo que más falta probar.
- **Normal:** https://tablero-pearl.vercel.app/app. Trae la app sin diseño, y lo eliges todo dentro de ella.

Siempre es la última compilación (`app-latest` en GitHub). No hace falta Android Studio.

## 2. Pasarla a la radio e instalarla

Elige una forma:

- **Desde el navegador de la radio:** abre la dirección de arriba y descarga. Si la radio no tiene navegador, usa el pendrive.
- **Pendrive:** baja la APK en tu celular o computador, cópiala a un pendrive, conéctalo a la radio, ábrela con el explorador de archivos y presiona instalar.
- **Cable (adb):** `adb install tablero.apk`, si la radio permite depuración USB.

Si la radio bloquea la instalación, activa *Instalar apps de orígenes desconocidos* (o te lleva ahí cuando pulses instalar). Si aparece «Aplicación bloqueada para proteger tu dispositivo» (Google Play Protect), es porque la app no viene de Play Store: toca *Más detalles* y luego *Instalar de todos modos*. La guía con pasos está en https://tablero-pearl.vercel.app/listo.

> **Si ya tenías una versión anterior** y la radio dice que no puede actualizar o hay un conflicto, desinstala la anterior y vuelve a instalar. No está confirmado que todas las compilaciones se firmen con la misma clave.

## 3. Abrirla y dejarla como inicio

1. Abre **Tablero** desde la lista de apps. La primera vez te guía en tres pasos, todos opcionales (tu auto, tu aceite y dejarla como inicio).
2. Presiona el botón *Inicio* de la radio. Android preguntará qué pantalla de inicio usar: elige **Tablero** y **Siempre**.
3. Para volver a la original: *Ajustes > Apps > Tablero > Borrar valores predeterminados*.

> Cuidado: si algo sale mal y no puedes volver a la pantalla original, desinstala Tablero (`adb uninstall cl.tablero.launcher` o desde Ajustes > Apps).

## 4. Qué probar (y qué contarme)

Marca cada punto con ✅ o ❌ y escríbeme lo que veas. Están en orden de importancia.

**A. Instalación y arranque**
- [ ] Se instala sin errores
- [ ] Abre y muestra reloj, fecha y saludo
- [ ] La pantalla se ve completa (no cortada ni con letras enormes), en 1280 × 720 o el tamaño de tu radio
- [ ] Queda en horizontal como debe
- [ ] El botón *Inicio* de la radio vuelve a Tablero
- [ ] Cuando la apagas y prendes, sigue con tus ajustes

**B. El diseño que venía dentro de la APK** (solo si la bajaste con «Descargar la app con mi diseño»)
- [ ] Al abrirla por primera vez ya trae el tema, la distribución y el fondo que elegiste en el sitio
- [ ] Trae tu nombre y tu saludo
- [ ] Trae tus avisos (aceite, documentos, restricción) tal como los dejaste
- [ ] Si subiste foto, aparece tu auto (y de noche, con las luces, si tenías la versión de noche)
- [ ] Si la bajaste con un diseño distinto sobre una versión ya instalada, se aplica el nuevo

**C. Ajustes → Apariencia**
- [ ] Se pueden elegir tema, distribución, fondo, hora del fondo, decoración y color de acento
- [ ] Al *Guardar* y volver al inicio, se ve lo elegido
- [ ] La opción «Como viene» deja la pantalla por defecto
- [ ] Los menús desplegables se leen bien (este es un punto que no pude ver)

**D. Ajustes → Foto de tu auto**
- [ ] *Elegir foto* abre la galería o el explorador de archivos (muchas radios no traen ninguno: debe salir un aviso, no cerrarse)
- [ ] Una foto normal se ve en la pantalla de inicio
- [ ] Un PNG ya sin fondo (lo bajas del sitio con «Descargar mi auto sin fondo») se ve recortado
- [ ] *Quitar foto* la saca

**E. Lo básico**
- [ ] Los atajos abren las apps que tienes instaladas
- [ ] *Ajustes* abre y se pueden escribir números (¿sale el teclado?)
- [ ] Al guardar, los avisos (aceite, documentos) aparecen en el inicio
- [ ] Pide permiso de ubicación y muestra la velocidad en movimiento (haz esta prueba con otra persona manejando)
- [ ] El saludo por voz suena (si la radio tiene voz en español instalada)
- [ ] Los botones del volante (*Ajustes → Botones del volante → Aprender un botón*)
- [ ] La canción que suena aparece con título y carátula (hay que dar el acceso a las notificaciones una vez; ver abajo)
- [ ] La pantalla de Radio FM muestra la emisora (si tu radio la informa)
- [ ] Pasa a modo noche cuando se pone el sol

> **Si la canción no aparece:** Tablero necesita el *acceso a las notificaciones* para leer qué suena (una sola vez, desde el botón «Dar el permiso» de la tarjeta de música). En **Android 13 o más nuevo**, como la app no viene de Play Store, ese interruptor puede salir **gris o bloqueado**: *Ajustes → Apps → Tablero → menú ⋮ → «Permitir ajustes restringidos»*, y luego volver a dar el permiso. Si aun así no aparece, el reproductor de la radio puede no informar la canción (Tablero entonces muestra al menos el nombre de la app que suena). *Ajustes → Ayuda → Diagnóstico de la radio* lista qué sesiones de música ve la app.

**F. Scanner OBD (solo si tienes un scanner ELM327 y un auto, con el contacto puesto)**
- [ ] Primero empareja el scanner en los ajustes de Bluetooth de Android de la radio
- [ ] *Ajustes → Scanner OBD*: elígelo y pulsa *Probar conexión* (te dice el voltaje, si el auto responde y la luz del motor)
- [ ] Aparece la pantalla **Auto** en la barra de abajo, con revoluciones, temperatura y voltaje en vivo
- [ ] Si hay una falla guardada, se explica en palabras simples (Tablero solo lee: no borra códigos)

**G. Kilometraje por GPS**
- [ ] Si anotaste el kilometraje, sube solo mientras se maneja (muestra un aviso fijo en la barra de notificaciones)

## 5. Qué enviarme

- Marca de la radio, versión de Android y resolución (arriba, «Antes de empezar»).
- Qué camino usaste (con diseño o normal).
- El resultado de cada punto, y para lo que falle, **una foto de la pantalla** o del mensaje de error.
- Si algo no funciona, entra a *Ajustes → Ayuda → Diagnóstico de la radio* y mándame una foto: muestra qué ve la radio (por ejemplo, si tiene navegador o galería).

## Lo que ya sabemos que falta

- El diseño **Todoterreno** y los fondos de nieve, rocas y barro: esperan al tipo de auto «camioneta 4x4», que la app todavía no tiene.
- Quitar el fondo de una foto **dentro de la app**: se hace en el sitio. En la app solo se puede poner una foto ya recortada.
- El límite automático de velocidad por calle: hoy solo funciona el límite fijo que tú pones.
- Alertas con el motor caliente y registro de viajes (fase 4 del scanner).
- Leer el CAN bus de la radio: depende del fabricante.
- El clima y los mapas nuevos necesitan internet (los mapas ya guardados se ven sin conexión).
