# Cambios

Todo lo que nota quien usa Tablero (el sitio y la app de la radio), por versión.
Formato [Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/); versiones [SemVer](https://semver.org/lang/es/).

Cómo se publica una versión: `docs/RELEASE.md`.

## [Sin publicar]

### Cambiado
- **Se retiró el código de activación de la app.** Ya no existen la pantalla para escribir un código, su cliente del servidor ni el botón «Activar código» del lanzador. La app queda más liviana y sin nada que apunte a un servidor que ya no existe. Una radio que ya había activado un código conserva su diseño y puede soltarlo desde Ajustes → Apariencia.
- **Se retiró el servidor de pedidos y códigos.** Ya no existen las funciones `order`, `order-photo`, `license` y `admin`, la base de datos de pedidos ni el panel `/admin.html`: nada del sitio ni de la app nueva las usa. Quedan solo `app` (descarga con tu diseño), `analyze-radio` y `detect-lights`. Una radio que ya había activado un código conserva su diseño en la propia radio.
- **Ya no hay códigos de activación.** El sitio dejó de pedir contacto y de enviar pedidos: el último paso del configurador es «Tu diseño y la descarga», con un resumen y el botón para bajar la app. La guía «Instalar en tu radio» ya no habla de códigos, y la portada, las preguntas y la privacidad se reescribieron. En la app no aparece el botón «Activar código». Hay dos caminos: diseñas en el sitio y bajas la app **con tu diseño ya puesto** (ver más abajo), o bajas la app normal y eliges todo dentro de ella (Ajustes → Apariencia y Foto de tu auto). Quien ya había activado un código conserva su diseño y puede soltarlo desde Ajustes → Apariencia.
- **Fondo del sitio en azul suave con degradado**, en vez de negro. Las tarjetas, la barra superior y los textos secundarios se ajustaron al nuevo fondo. La vista previa de la radio sigue oscura, como la pantalla real.

### Agregado
- **Guía para probar Tablero en tu radio, en el sitio** (`/prueba`): cómo conseguir la app (con tu diseño o normal), instalarla, qué revisar con casillas para ir marcando y qué contarme. Hay enlaces desde la portada y desde la guía de instalación.
- **Contadores públicos de visitas y descargas.** El pie del sitio muestra cuántas visitas tuvo la página y cuántas veces se descargó la app (con o sin diseño; la ruta `/app` ahora pasa por el servidor para poder contarlas). Son dos números que suben de a uno, sin cookies ni datos de nadie; una visita por sesión y los robots no cuentan. Si el contador falla, la descarga y el sitio funcionan igual.
- **«Descargar la app con mi diseño».** En el último paso del configurador, la APK que bajas ya trae lo que elegiste: tema, distribución, fondo, hora, decoración, color, tu nombre y saludo, y tus avisos de mantención, documentos y restricción. La app lo aplica sola al abrirse por primera vez, sin código. Si instalas después otra APK con otro diseño, se aplica ese; si cambias algo en Ajustes y no vuelves a instalar, no se pisa. **La foto de tu auto también viaja dentro de la app**: si la subiste en el configurador, viene con su versión recortada y la de noche con las luces encendidas, y se pone sola al abrir la app. La foto pasa una sola vez por nuestro servidor para ponerla en la APK y no se guarda; si es muy grande o algo falla, bajas la app con tu diseño y la eliges en Ajustes → Foto de tu auto.
- **Elige la apariencia dentro de la app, sin código.** En Ajustes → **Apariencia** escoges el tema, la distribución, el fondo, la hora del fondo, la decoración de temporada y el color de acento. Se guarda en la radio, funciona sin internet y se ve al volver al inicio. «Como viene» deja la pantalla por defecto. Todavía no están el diseño Todoterreno ni los fondos de nieve, rocas y barro (van con el tipo «camioneta 4x4»).
- **Pon la foto de tu auto desde la app.** En Ajustes → **Foto de tu auto** eliges una foto de la galería o del explorador de archivos de la radio. Se achica y se queda guardada en la radio, sin internet ni código. Si es un PNG ya sin fondo, se muestra recortada; si es una foto normal, se muestra tal cual (la app todavía no le quita el fondo). Algunas radios no traen galería ni explorador: ahí la app avisa.
- **Botón «Descargar mi auto sin fondo» en el sitio.** Cuando subes la foto de tu auto en el configurador y le quitamos el fondo, puedes bajar el auto ya recortado (un PNG) y elegirlo en la app, en Ajustes → Foto de tu auto, para que se vea recortado en la radio.
- **Más lugares para «Invítame un café» en el sitio.** Sección propia antes del cierre de la portada, enlace en el menú y en la barra superior, junto al botón de pedir y en la página de instalación. Sigue siendo opcional.
- **«Invítame un café».** Un enlace opcional a Ko-fi en el pie del sitio, en la página de instalación y al final de Ajustes en la app. Tablero sigue siendo gratis y nada depende de ese enlace. En una radio sin navegador, el botón avisa en vez de no hacer nada. Además se quitaron dos frases viejas de «te devolvemos tu dinero» de la página de instalación.
- **Pantalla «Auto» con los datos del scanner.** Con un scanner OBD elegido, aparece «Auto» en la barra de abajo: revoluciones, temperatura
  del motor y voltaje en vivo, la luz del motor y los códigos de falla explicados en palabras simples (solo se leen, no se borran).
  Si el auto informa su kilometraje real, los avisos de mantención lo usan en vez del que suma el GPS. En el sitio se ve con datos de
  ejemplo en la pestaña «Scanner OBD». Sin probar todavía en un auto real: hay scanners clon que no entregan todos los datos.
- **Conectar un scanner OBD (primera parte).** En Ajustes, en **Scanner OBD**, puedes elegir cómo se conecta tu scanner
  (**Bluetooth**, el más común, como el «Scanner Automotriz OBD2 ELM327 Bluetooth V2.1», o **Wi-Fi**), elegirlo entre los
  ya emparejados con la radio y pulsar **Probar conexión**: te muestra el voltaje, si el auto responde y unos datos
  (revoluciones, velocidad, temperatura del motor) y si la luz del motor está encendida. Es opcional. Los datos
  se ven en la pantalla **Auto** (ver abajo). Android pide el permiso de Bluetooth solo al
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
- **Portada: sección del scanner OBD.** Explica qué funciona hoy (Bluetooth o Wi-Fi, prueba de conexión), qué llega después (datos en vivo, luz del motor explicada, kilometraje real) y en qué se diferencia de otras apps de OBD; más tres preguntas frecuentes (se actualiza con la pantalla «Auto»).
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
