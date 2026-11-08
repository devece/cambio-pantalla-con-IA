# Plan: conexión OBD (el "Taller")

Estado: **propuesta, nada construido**. Escrito el 2026-11-06. Para decidir qué entra y en qué orden.

## 1. Qué queremos lograr

Que Tablero lea datos reales del auto con un adaptador **ELM327** enchufado al puerto OBD, para:

1. **Dejar de pedirle el kilometraje al usuario.** Hoy los avisos de aceite y mantenciones dependen de un número que él escribe a mano y que se desactualiza.
2. Mostrar **velocidad, revoluciones, temperatura y voltaje** reales en la pantalla de inicio.
3. Leer y explicar los **códigos de falla** (la luz del motor) en palabras simples.
4. Avisar si el motor se calienta o la batería no carga.

## 2. Cómo está hoy (verificado en el código)

| Tema | Hoy |
|---|---|
| OBD / ELM327 / CAN | **No existe nada.** Ni código ni permisos de Bluetooth. |
| Kilometraje | Solo manual: `LauncherConfig.odometer`, que se escribe en Ajustes y en el primer arranque. **Nada lo actualiza.** |
| Velocidad | Del GPS (`MainActivity` → `LocationListener` → `WebLauncher.setSpeed`). |
| CAN bus de la radio | En la radio de prueba aparece **desconectado** (`docs/PRUEBA_EN_RADIO.md`). No sirve como fuente. |
| Estructura | `android/core` (Kotlin puro, 84 pruebas, corre en CI) y `android/app` (pantallas, GPS, WebView). La pantalla de inicio es web (`public/js/launcher.js`, `preview.js`) y habla con Kotlin por `WebLauncher.Bridge`. |
| Ediciones | Todo es gratis. `public/shared/editions.json` y `core/Gate.kt` ("el candado") siguen en el código pero hoy no limitan nada. |
| Privacidad | `public/privacidad.html` describe GPS y notificaciones. Habría que sumar Bluetooth/OBD. |
| Permisos | `INTERNET`, `ACCESS_FINE_LOCATION`, `QUERY_ALL_PACKAGES`. |

## 3. Decisiones que tomas tú

1. ~~¿Qué edición incluye qué?~~ **Resuelto:** no hay versión de pago, todo el OBD es gratis y opcional.
2. **Código propio.** No se copia código de LiteDash (GPL-3.0): copiarlo obligaría a abrir todo Tablero bajo GPL. Se implementa desde el estándar público (SAE J1979 y la hoja de datos del ELM327), con textos y descripciones propias.
3. **Qué adaptadores primero.** Propuesta: Bluetooth clásico y Bluetooth LE. Wi-Fi y USB después.
4. **Si el OBD es obligatorio u opcional.** Propuesta: opcional. Sin adaptador todo sigue funcionando como hoy.

## 4. Fases

### Fase 0 — Kilometraje automático por GPS (no necesita adaptador) — **hecha, falta probar en la radio**
Suma la distancia recorrida al `odometer` guardado, con filtros: descartar puntos con mucho error (> 25 m), saltos imposibles (> 200 km/h entre dos puntos), la deriva del GPS con el auto detenido y los huecos largos de tiempo (> 10 min). Ajustes permite corregir el número o apagarlo.
- **Dónde quedó:**
  - `core/.../DistanceCounter.kt` (lógica pura, 12 pruebas en `DistanceCounterTest.kt`).
  - `app/.../OdometerService.kt`: servicio en primer plano (tipo ubicación) que cuenta los km.
  - `SettingsStore` guarda el total y los metros sueltos; `MainActivity` enciende el servicio y refresca los avisos en vivo.
  - Opción "Sumar solos los km que recorro" en Ajustes (`LauncherConfig.odometerAuto`, encendida por defecto).
- **Cambio respecto a lo planeado:** no bastaba con el `LocationListener` de `MainActivity`, porque la pantalla de inicio apaga el GPS al pasar a segundo plano (`onPause`) y casi todo el manejo ocurre con Waze o Maps al frente. Por eso es un servicio aparte, que muestra un aviso fijo en la barra de notificaciones (Android lo exige).
- **Solo cuenta si el usuario ya anotó un kilometraje** (sin punto de partida no hay qué sumar).
- **Qué verificar en la radio:** que el servicio arranque, que siga sumando con Waze al frente, que la notificación no moleste y que sobreviva a apagar y prender la radio.
- **Límites:** el GPS pierde precisión en túneles y subterráneos; es una estimación. El OBD, cuando exista (fases 1 a 4), pasará a mandar.
- **Android 14:** el servicio de ubicación no puede arrancar desde el arranque del sistema, por eso lo enciende la pantalla de inicio al abrirse.

### Fase 1 — Núcleo OBD en `core` (sin hardware, todo probable por CI) — **hecha, falta probar con un adaptador real**
Kotlin puro, sin Android, detrás de una interfaz `ObdTransport` (enviar un comando, recibir texto) para poder simularlo.
- **Inicialización ELM327:** `ATZ`, `ATE0`, `ATL0`, `ATS0`, `ATH0`, `ATSP0`.
- **Lectura de datos (modo 01):**

| PID | Dato | Fórmula |
|---|---|---|
| `0C` | Revoluciones | (256·A + B) / 4 |
| `0D` | Velocidad (km/h) | A |
| `05` | Temperatura del refrigerante (°C) | A − 40 |
| `04` | Carga del motor (%) | A · 100 / 255 |
| `2F` | Nivel de combustible (%) | A · 100 / 255 (no todos los autos) |
| `42` | Voltaje del módulo (V) | (256·A + B) / 1000 |
| `0F` | Temperatura del aire de admisión | A − 40 |
| `A6` | Odómetro (km) | 4 bytes / 10 (**solo algunos autos recientes**) |
| `00/20/40/60` | Qué PIDs soporta el auto | máscaras de bits |

- **Códigos de falla:** modo `03` (guardados), `07` (pendientes), `0A` (permanentes). Los 2 primeros bits dan la letra: `00` P, `01` C, `10` B, `11` U. Estado de la luz y de los monitores con `01 01`.
- **Borrar códigos:** modo `04`, solo tras doble confirmación (ver riesgos).
- **VIN:** modo `09 02`, solo en memoria, **no se guarda ni se envía**.
- **Manejo de respuestas raras:** `NO DATA`, `SEARCHING...`, `UNABLE TO CONNECT`, `BUS INIT`, `?`, tiempos de espera y reintentos.
- **Catálogo de códigos en palabras simples:** JSON propio en `public/shared/` (como `maintenance.json`), redactado por nosotros. Los códigos genéricos (P0xxx) son un estándar; las descripciones no se copian de otros proyectos.
- **Lista blanca de comandos:** el núcleo solo permite los modos 01, 03, 04, 07, 09 y 0A. Nunca se envía texto libre al adaptador.
- **Pruebas:** transcripciones reales/simuladas de respuestas ELM327 (casos buenos y malos), cálculo de cada PID y decodificación de códigos.
- **Listo cuando:** `./gradlew :core:test` pasa con cobertura de cada PID y de los errores típicos.
- **Dónde quedó:**
  - `ObdTransport.kt`: el canal con el adaptador (interfaz) y los errores (`ObdError`, `ObdException`).
  - `ObdParser.kt`: limpia las respuestas (eco, `SEARCHING...`, espacios, respuestas en varias líneas) y detecta los errores del adaptador.
  - `ObdPids.kt`: fórmulas de cada dato. Además de la tabla de arriba se agregaron el **acelerador** (`11`) y la **presión de admisión** (`0B`).
  - `ObdDiagnostics.kt`: códigos de falla (`DtcKind`, `Dtc.decode`) y estado de los monitores (`MonitorStatus`, para motores a bencina y diésel).
  - `ObdSession.kt`: el cliente (`init`, `supportedPids`, `read`, `readDtcs`, `clearDtcs`, `monitorStatus`, `vin`, `adapterVoltage`) y la **lista blanca** (`ObdCommands`).
  - `ObdCodes.kt` y `public/shared/obd-codes.json`: 98 códigos explicados en palabras simples (textos propios) y una explicación general por letra y primer dígito para los que no están.
  - 62 pruebas nuevas, con un adaptador ELM327 simulado. Se comprobó que fallan si se rompen las fórmulas.
- **Detalles que salieron al construirlo:**
  - Para leer los códigos de falla se pregunta el protocolo (`ATDPN`): en CAN el primer dato es cuántos códigos vienen, en los antiguos no. Si el adaptador no lo dice, se deduce por el largo.
  - Se agregó `ATRV` (voltaje que mide el propio adaptador), útil con el motor apagado.
  - **Todavía no se usa desde la app**: no hay conexión Bluetooth ni pantalla. Es la fase 2.
- **Lo que no está verificado:** las respuestas de los adaptadores reales. Las pruebas usan respuestas simuladas según el estándar y lo que se sabe de los ELM327; los clones baratos pueden desviarse. Se ajustará con un adaptador de verdad.

### Fase 2 — Conexión en la app Android (necesita adaptador) — **hecha, falta probarla con un adaptador real**
Se conecta a un scanner ELM327 y deja la opción de elegir el tipo. Está pensada para los scanners básicos más comunes,
como el «Scanner Automotriz OBD2 ELM327 Bluetooth V2.1» (Bluetooth clásico).
- **Tipos de scanner:**
  - **Bluetooth clásico** (SPP): el más común. Se prueban tres formas de conexión, porque los clones baratos son caprichosos.
  - **Wi-Fi** (TCP, por defecto `192.168.0.10:35000`): la radio debe estar conectada a la red del scanner.
  - **Bluetooth LE: todavía no.** Hay que hacerlo con un scanner BLE real a mano (los servicios `FFF0` y `FFE0` se confirman con el aparato). Para sumarlo basta un valor nuevo en `ObdLinkKind` y un conector en `ObdConnectors.kt`; el resto no cambia.
- **Cómo se usa:** Ajustes → **Scanner OBD**: elegir tipo, elegir el scanner entre los emparejados (o escribir la dirección Wi-Fi) y **Probar conexión**. La prueba muestra el voltaje, si el auto responde, revoluciones, velocidad, temperatura y la luz del motor, y explica en palabras simples qué hacer si algo falla.
- **Dónde quedó:**
  - `core`: `ObdLink.kt` (tipos y configuración, con validación), `ElmStreamTransport.kt` (transporte por flujos, con plazos y sin colgarse si el adaptador calla), `ObdPoller.kt` (lectura continua con reconexión), `ObdCheck.kt` (la prueba) y `ObdMessages.kt` (errores en español). 40 pruebas nuevas.
  - `app`: `ObdConnectors.kt` (Bluetooth y Wi-Fi), `ObdClient.kt` (mantiene la conexión), sección en `SettingsActivity`, y `TBApp.setObd` en `public/js/launcher.js` (por ahora solo guarda los datos).
- **Hoy el ciclo de lectura en vivo está apagado** (`MainActivity.OBD_LIVE_DATA = false`) porque ninguna pantalla muestra todavía los datos: leer algo que nadie ve solo cansa al adaptador. Lo único que se conecta hoy es **Probar conexión**. Se enciende junto con la pantalla del Taller (fase 3), y entonces conviene que solo se pregunte al auto lo que se está mostrando.
- **Decisión: la conexión vive solo mientras el inicio está a la vista**, no en un servicio. Así el adaptador descansa cuando se usa Waze o Maps (menos gasto de batería del auto) y no hay otro aviso fijo ni otro permiso. El costo es que al volver al inicio se reconecta (unos segundos). Un servicio propio queda para la fase 4 (alertas con el motor caliente, registro de viajes).
- **Comportamiento:** reconecta solo con espera creciente (2, 4, 8... hasta 30 s); con el auto apagado avisa y reintenta cada 5 s sin cerrar el adaptador; con el motor apagado (0 rpm) consulta cada 3 s.
- **Permisos:** Bluetooth (`BLUETOOTH_CONNECT` en Android 12 o más, que se pide en pantalla; los antiguos en Android 11 o menos). Se usa solo para conectarse al scanner ya emparejado que elige el usuario; no se busca ni se lee ningún otro dispositivo. Privacidad y CHANGELOG actualizados.
- **Todavía sin decidir:** en qué edición va (hoy lo puede usar cualquiera: no se tocó `editions.json`).
- **Qué verificar con un adaptador real** (nada de esto se pudo probar sin hardware):
  1. Que el scanner emparejado aparezca en la lista y la prueba conecte.
  2. Que con el auto apagado diga "falta el auto", y con el auto encendido muestre los datos.
  3. Que al desenchufar y volver a enchufar el scanner se recupere solo.
  4. (Cuando se encienda el ciclo en vivo, en la fase 3) Que la lectura no haga lenta la pantalla de inicio.
  5. Con un scanner Wi-Fi: que la radio pueda hablar con la red del scanner (Android a veces prefiere otra red sin internet).
  6. Qué pasa con un clon "v2.1" que responde distinto (anotar la respuesta exacta para ajustar el lector).
- **Límites conocidos:** un solo programa a la vez puede hablar con un scanner Bluetooth (si el teléfono está conectado, la radio no podrá); no se lee el VIN todavía.

### Fase 3 — Pantalla "Taller" (web) — **hecha en lo esencial, falta probar con un adaptador real**
Se llama «Auto» en la app: `preview.js` (`obdScreen`, `updateObd`) y `launcher.js` (`setObd`); `ObdPoller` lee la luz del motor y los códigos cada 60 s (`ObdHealth`). **Quedó fuera a propósito:** borrar códigos desde la pantalla (reinicia los chequeos del auto; se decide aparte) y el velocímetro por OBD (sigue el GPS). Lo de abajo es el plan original.
Cambios en `public/js/launcher.js` y `preview.js` (la vista previa del sitio muestra datos de ejemplo, como ya hace con el resto).
- Tarjeta de **salud del auto**: luz del motor, cantidad de códigos, voltaje.
- **Velocímetro y revoluciones** por OBD (con GPS de respaldo).
- **Códigos de falla** con explicación, y borrado con el mismo patrón de **dos toques** que ya usa el botón "Hecho".
- **Listo cuando:** se ve bien en 1024×600 y 1280×720, con y sin adaptador conectado.

### Fase 4 — Automatizaciones
- *(Hecho: el km real se toma del PID `A6` si el auto lo informa; `SettingsStore.setOdometerFromCar`. Sin probar en un auto real.)* **Km real** del OBD (si el auto lo da) manda sobre el GPS; si no, se sigue con el GPS.
- **Alertas:** motor caliente y batería. Los umbrales (por ejemplo, > 105 °C, < 13,2 V en marcha) son un punto de partida y hay que **calibrarlos con tu auto**.
- **Cronómetro 0 a 100** y **registro de viajes** (guardado solo en la radio).

### Fase 5 — Opcionales
Adaptadores Wi-Fi (`192.168.0.10:35000` es el habitual) y USB; presión de llantas (TPMS). Dependen de hardware y se evalúan aparte.

## Lo que se aprendió de otros proyectos (LiteDash)

Se leyó el repositorio público de LiteDash (`IrvingGuerra/litedash`, licencia **GPL-3.0**) para saber cómo se comportan los adaptadores reales. **No se copió código**: se tomaron comportamientos y datos de los aparatos, y el código de Tablero es propio (la fase 2 original se escribió antes de abrir ese repositorio). Copiar su código obligaría a publicar todo Tablero bajo GPL-3.0; si algún día se quiere eso, hay que decidirlo a propósito.

**Ya aplicado en Tablero:**
- El reinicio (`ATZ`) falla o contesta distinto en muchos clones: no se corta por eso; lo que decide si sirve es que acepte la configuración. Un saludo sin «ELM» tampoco lo descarta.
- `ATAT1` (tiempos adaptables) se activa si el adaptador lo entiende; si contesta `?` se sigue.
- Los bloques altos de PIDs a veces no contestan: se queda con lo ya sabido. Solo se pregunta hasta el bloque `A0`.
- No reintentar sin parar: tras unos intentos se rinde (3 si nunca conectó, 5 si se cayó estando conectado). En una radio que reproduce música por Bluetooth, los intentos repetidos de conexión pueden entrecortarla.
- Solo se pregunta al auto lo que se muestra (por eso el ciclo en vivo está apagado hasta la fase 3).

**Para cuando se haga Bluetooth LE** (no está hecho; hay que probarlo con un scanner LE real):
- No se empareja: se busca y se conecta directo. La búsqueda necesita `BLUETOOTH_SCAN` (Android 12 o más) o la ubicación (Android 11 o menos). **No buscar y conectar a la vez**: en varios Android la conexión falla.
- Servicios de los scanners LE más comunes, para probar primero: `FFF0` (la mayoría de los clones), `FFE0` (módulos tipo HM-10), `18F0` y el de los Vgate iCar Pro BLE (`E7810A71-73AE-499D-8C15-FAA9AEF0C3F2`). Si no está ninguno, tomar cualquier servicio que tenga una característica que avise (notify/indicate) y otra que se pueda escribir, ignorando los genéricos (`1800`, `1801`, `180A`, `180F`).
- Hay que activar los avisos escribiendo el descriptor `2902`. Se escribe de a 20 bytes (el MTU de fábrica es 23 menos 3 de encabezado).
- Se arma como un flujo de bytes para reutilizar `ElmStreamTransport` tal cual.

**Otras ideas para después:** adaptadores por cable USB (necesita un controlador de puerto serie y probar velocidades), y la guía de qué adaptador comprar.

## 5. Archivos que se tocarían

| Archivo | Cambio |
|---|---|
| `android/core/.../Odometer.kt` (nuevo) | Fase 0: acumulado por GPS con filtros |
| `android/core/.../Obd*.kt` (nuevos) | Fase 1: protocolo, PIDs, códigos |
| `android/core/src/test/...` | Pruebas con transcripciones |
| `android/app/.../ObdService.kt`, `ObdSettings*.kt` (nuevos) | Fase 2 |
| `android/app/src/main/AndroidManifest.xml` | Permisos de Bluetooth y servicio |
| `android/app/.../WebLauncher.kt` | `setObd`, y leer/guardar la elección |
| `android/app/.../SettingsStore.kt`, `Gate.kt` | Dirección del adaptador, `obdOn`, candado por edición |
| `public/shared/editions.json` | Qué edición incluye OBD |
| `public/shared/obd-codes.json` (nuevo) | Códigos explicados |
| `public/js/launcher.js`, `preview.js`, `public/css/preview.css` | Pantalla Taller y velocímetro |
| `public/privacidad.html`, `CHANGELOG.md`, `docs/` | Textos y notas |

## 6. Riesgos y límites

- **Adaptadores falsos.** Hay muchos clones ELM327 baratos (los que dicen "v2.1") que responden mal o se cuelgan. Se recomendará un modelo probado y el núcleo debe tolerar fallas.
- **Datos que el auto no da.** Nivel de combustible, odómetro y otros PIDs varían por marca y año. La app debe preguntar qué soporta (`PID 00/20/40`) y ocultar lo que no hay, sin mostrar ceros falsos.
- **Borrar códigos puede perjudicar.** Al borrarlos se reinician los chequeos internos del auto (los "monitores") y vuelven a tardar en completarse, por lo general varios días de manejo. En algunos países y centros de inspección se exige que estén completos; **no verifiqué si pasa en la revisión técnica de Chile**, así que el aviso no debe afirmarlo: solo decir que el auto necesita manejar unos días para terminarlos. Además, si la falla sigue, el código vuelve. Por eso: doble confirmación y un aviso claro, y nunca borrar sin que el usuario lo pida.
- **Seguridad.** Solo comandos de la lista blanca (fase 1). Nada de texto libre.
- **Batería del auto.** Algunos adaptadores quedan consumiendo con el auto apagado. Se informa al usuario y se evita consultar sin motor andando.
- **Privacidad.** Los datos del OBD se quedan en la radio. Hay que actualizar `privacidad.html` (Bluetooth, qué se lee, que el VIN no se guarda).
- **Sin probar en hardware.** Todo lo de las fases 2 a 4 requiere un adaptador y un auto reales. La fase 1 se puede dejar sólida solo con pruebas.

## 7. Qué necesito de ti para empezar

1. **Modelo y año del auto** (para saber qué PIDs esperar).
2. **El adaptador**: marca, y si es Bluetooth, BLE o Wi-Fi. Si aún no lo tienes, lo recomendable es uno Bluetooth de una marca conocida.
3. **La radio**: la de prueba (Android 13, 1280×720) o cuál usarás.
4. **La decisión de ediciones** (punto 3.1).
5. **Si puedes probar** en el auto cuando haya un APK.

## 8. Orden recomendado

1. **Fase 0** primero: da valor ya (avisos de aceite que bajan solos) y no depende de nada.
2. **Fase 1** a continuación: se avanza sin hardware.
3. **Fases 2 a 4** cuando haya adaptador y auto para probar.
