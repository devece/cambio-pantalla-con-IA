# Cambios

Todo lo que nota quien usa Tablero (el sitio y la app de la radio), por versión.
Formato [Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/); versiones [SemVer](https://semver.org/lang/es/).

Cómo se publica una versión: `docs/RELEASE.md`.

## [Sin publicar]

### Agregado
- **Conectar un scanner OBD (primera parte).** En Ajustes, en **Scanner OBD**, puedes elegir cómo se conecta tu scanner
  (**Bluetooth**, el más común, como el «Scanner Automotriz OBD2 ELM327 Bluetooth V2.1», o **Wi-Fi**), elegirlo entre los
  ya emparejados con la radio y pulsar **Probar conexión**: te muestra el voltaje, si el auto responde y unos datos
  (revoluciones, velocidad, temperatura del motor) y si la luz del motor está encendida. Es opcional. Por ahora los datos
  no aparecen en la pantalla de inicio: eso llega en una próxima versión. Android pide el permiso de Bluetooth solo al
  elegir un scanner Bluetooth.
- **Kilometraje automático.** Si ya anotaste el kilometraje de tu auto, la app le suma sola los km que recorres (con el GPS),
  así los avisos de aceite y mantención bajan sin que tengas que actualizar el número. Sigue contando aunque uses Waze o Maps,
  y por eso muestra un aviso fijo en la barra de notificaciones. Es una estimación: no cuenta cuando el auto está detenido ni
  los saltos raros del GPS, y puedes corregir el número o apagarlo en Ajustes. Se guarda solo en tu radio.
- **Recordatorios que se repiten solos.** Cada mantención que anotas (filtro, bujías, frenos…) cuenta sus km con barra y alerta,
  igual que el aceite. En Mantención aparece **Hecho** cuando un aviso está por vencer (se confirma con dos toques): anota el
  kilometraje de hoy y el próximo aviso queda "cada N km" más adelante. Los que aún no tienen fecha dicen **Contar desde hoy**.
- **Día y noche por la hora del sol.** El tema oscuro entra cuando se pone el sol donde está el auto, no a una hora fija
  (en invierno oscurece a las 18 h; en verano, pasadas las 21 h). Sin internet; usa el último GPS guardado o Santiago.
  El fondo "automático" (amanecer, día, atardecer y noche) también lo sigue. Las luces del auto siguen mandando sobre todo.
- **Botones del volante.** En Ajustes → Botones del volante, **Aprender un botón**: aprietas el botón y eliges qué hace:
  controlar la música (siguiente, anterior, pausa) y el volumen, abrir el **atajo 1 o 2 de tu barra** o el teléfono,
  llamar al asistente de voz, ir a Inicio, Radio o Mantención, o cambiar entre día y noche. Los que no asignes hacen lo
  de siempre. Funciona si la radio manda esos botones como teclas normales de Android.
- **Primer arranque guiado** en 3 pasos, todos opcionales: tu auto (nombre, tipo y color), tu aceite (kilometraje y último
  cambio) y hacer de Tablero la pantalla de inicio de la radio. Sale una sola vez.
- Sin un código activado, la pantalla de inicio ya muestra tu nombre, saludo, frase, tipo de auto y color de Ajustes
  (antes solo los veía quien tenía un diseño del sitio).
- **Pantalla de Radio FM.** Muestra la frecuencia y el nombre de la emisora que suena en la radio de tu equipo, con
  emisora anterior, siguiente y play/pausa. Si la radio FM está sonando, el botón Radio y la tarjeta del inicio abren esta
  pantalla; si no, abren su app de siempre. Depende de que la app de radio de tu equipo publique la emisora (muchas lo
  hacen, algunas no): si no la publica, la pantalla lo explica y ofrece abrir su app. Pide una vez el acceso a las notificaciones.
- **Diagnóstico de la radio** (Ajustes → Diagnóstico de la radio): muestra qué Android y qué pantalla tiene tu radio, si su
  radio FM publica la emisora y qué código manda cada botón del volante al apretarlo. Se puede copiar o compartir para
  reportar un problema, sin adb ni Android Studio. No lleva tu nombre ni tu ubicación.

### Cambiado
- **Portada: sección del scanner OBD.** Explica qué funciona hoy (Bluetooth o Wi-Fi, prueba de conexión), qué llega después (datos en vivo, luz del motor explicada, kilometraje real) y en qué se diferencia de otras apps de OBD; más tres preguntas frecuentes.
- **Se quitó todo lo de pagos y planes.** Tablero es gratis, así que desaparecieron los planes Esencial y Completo, los precios y el total, el cobro con PayPal, los datos de transferencia bancaria, las etiquetas «Completo» en los diseños y la casilla de aceptar términos. El último paso del sitio ahora se llama **Pedir mi pantalla**. Se eliminaron las páginas de **Términos** y **Cambios y ayuda**; la política de **Privacidad** se mantiene porque explica qué hace la app con tus datos.
- **Todo es gratis, sin necesidad de código.** Por ahora no hay versión de pago: la edición sin código ahora incluye todos los diseños, las decoraciones de temporada y todas las funciones (documentos, combustible, restricción vehicular, saludo por voz, otras mantenciones, hasta 4 atajos y el límite fijo de velocidad). Antes, varias de esas cosas pedían un código. El código sigue sirviendo para traer a la radio la pantalla que diseñaste en el sitio, y nunca da menos que la edición sin código.
- **El sitio ya no promete el límite automático de velocidad.** La app todavía no lee el límite de cada calle, y en modo «Automático» no avisa nada; los textos del sitio decían lo contrario. Ahora dicen que, para recibir el aviso, hay que elegir «Manual» y poner el límite. En la radio, sin límite fijado ya no dice «Límite de la vía».
- En el botón de música de la pantalla, si no hay música sonando ahora avisa qué hacer en vez de no hacer nada.

### Para quien publica
- Nuevo workflow **Release**: al crear una etiqueta `vX.Y.Z` compila el APK firmado y lo publica en GitHub Releases con estas notas.
- El pedido ahora se envía a `/api/order` (antes `/api/checkout`). En el panel de administración, «Confirmar pago» pasó a **Aprobar** y el código se crea sin elegir plan. Se eliminaron `api/paypal-return.js`, `lib/paypal.js` y las variables `PAYPAL_*` y `SITE_URL`. La base de datos no cambia: las columnas de plan y cobro se conservan y cada pedido nuevo las trae con un valor fijo.

## [0.1.0] - 2026-10-30

Prototipo inicial (todavía sin etiqueta en GitHub; el APK de cada compilación se publica en `app-latest`).

### Agregado
- **Sitio configurador** con vista previa en vivo de la radio (1024×600): 8 temas visuales de día y noche, distribuciones,
  fondos, tu foto o logo, autos dibujados y decoración de temporada (Fiestas Patrias, Halloween, Navidad, Año Nuevo).
- **Planes** Esencial (US$ 3) y Completo (US$ 8), pago con PayPal o por transferencia, y código de licencia ligado a una sola radio.
- **App Android** como pantalla de inicio: reloj, saludo (también por voz), auto en 3D, velocidad por GPS con alerta de límite,
  clima, música en vivo con carátula y atajos a Maps, Waze, Spotify, YouTube Music, YouTube y WhatsApp.
- **Avisos** de cambio de aceite, mantenciones, revisión técnica, permiso de circulación, SOAP, combustible y
  **restricción vehicular** de la Región Metropolitana con aviso por GPS al entrar a Santiago.
- **Edición gratuita** sin código, con funciones limitadas, y **activación** de códigos dentro de la radio.
- Foto real del auto con el fondo quitado por IA, versión nocturna con luces encendidas, millas y galones.
- Panel de administración para crear y gestionar códigos.

### Arreglado
- Pantalla negra en radios con un WebView muy antiguo; ahora se explica qué actualizar.
- El APK incluye sus archivos de datos (antes se cerraba al abrir).
