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
| Planes | `public/shared/editions.json` y `core/Gate.kt` ("el candado") definen qué es gratis y qué requiere código. |
| Privacidad | `public/privacidad.html` describe GPS y notificaciones. Habría que sumar Bluetooth/OBD. |
| Permisos | `INTERNET`, `ACCESS_FINE_LOCATION`, `QUERY_ALL_PACKAGES`. |

## 3. Decisiones que tomas tú

1. **¿Qué edición incluye qué?** Propuesta, a confirmar con el negocio:
   - **Gratis:** km automático por GPS (fase 0).
   - **Esencial:** velocidad, revoluciones, temperatura y voltaje por OBD; km real si el auto lo entrega.
   - **Completo:** Taller (códigos de falla, borrado, gráficas, alertas, registro de viajes).
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

### Fase 2 — Conexión en la app Android (necesita adaptador)
- **Permisos:** Android 12 o más pide `BLUETOOTH_CONNECT` (y `BLUETOOTH_SCAN` si se busca) en tiempo de ejecución; en Android 11 o menos, `BLUETOOTH` y `BLUETOOTH_ADMIN` con `maxSdkVersion="30"`. La búsqueda en Android 11 o menos requiere ubicación, que ya está. `minSdk` sigue en 21.
- **Pantalla "Conectar OBD"** en Ajustes: lista los dispositivos ya emparejados, prueba la conexión, guarda la dirección elegida.
- **Servicio `ObdService`:** abre el socket (Bluetooth clásico, UUID SPP `00001101-0000-1000-8000-00805F9B34FB`; para BLE, los servicios más comunes son `FFF0` y `FFE0`, **a confirmar con el adaptador real**), consulta en bucle con frecuencia adaptativa (rápida para revoluciones y velocidad, lenta para temperatura y voltaje) y se reconecta con espera creciente.
- **Puente a la pantalla web:** nuevo `TBApp.setObd(json)` en `WebLauncher`, igual que `setSpeed` y `setLocation`.
- **Qué pasa si se cae:** la pantalla vuelve a GPS y avisa una vez, sin molestar.
- **Listo cuando:** se conecta a un adaptador real, muestra datos y se recupera tras desenchufarlo y volver a enchufarlo.

### Fase 3 — Pantalla "Taller" (web)
Cambios en `public/js/launcher.js` y `preview.js` (la vista previa del sitio muestra datos de ejemplo, como ya hace con el resto).
- Tarjeta de **salud del auto**: luz del motor, cantidad de códigos, voltaje.
- **Velocímetro y revoluciones** por OBD (con GPS de respaldo).
- **Códigos de falla** con explicación, y borrado con el mismo patrón de **dos toques** que ya usa el botón "Hecho".
- **Listo cuando:** se ve bien en 1024×600 y 1280×720, con y sin adaptador conectado.

### Fase 4 — Automatizaciones
- **Km real** del OBD (si el auto lo da) manda sobre el GPS; si no, se sigue con el GPS.
- **Alertas:** motor caliente y batería. Los umbrales (por ejemplo, > 105 °C, < 13,2 V en marcha) son un punto de partida y hay que **calibrarlos con tu auto**.
- **Cronómetro 0 a 100** y **registro de viajes** (guardado solo en la radio).

### Fase 5 — Opcionales
Adaptadores Wi-Fi (`192.168.0.10:35000` es el habitual) y USB; presión de llantas (TPMS). Dependen de hardware y se evalúan aparte.

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
