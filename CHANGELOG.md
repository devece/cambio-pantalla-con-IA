# Cambios

Todo lo que nota quien usa Tablero (el sitio y la app de la radio), por versión.
Formato [Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/); versiones [SemVer](https://semver.org/lang/es/).

Cómo se publica una versión: `docs/RELEASE.md`.

## [Sin publicar]

### Corregido
- **La restricción vehicular ya no afirma una tabla de otro año.** Los dígitos de cada día cambian con la resolución de cada año; desde ahora, al entrar a Santiago en un año distinto al de la tabla cargada (2026), la radio no dice «te toca» ni «no te toca», sino que avisa que hay que revisar la regla vigente. Las tablas de Chile se contrastaron con la resolución oficial N.º 1698/2026 y coinciden; la regla de Bogotá también coincide con la Secretaría de Movilidad.

## [0.2.0] - 2026-11-10

Es la primera versión publicada en GitHub Releases. Junta todo lo que se sumó desde el prototipo 0.1.0.

### Agregado

**Tu pantalla**
- **Diseño para radios verticales.** En una radio de pantalla vertical (por ejemplo 768 × 1024, de 9,7", o las de 9:16) la pantalla de inicio ya no es la horizontal achicada con franjas: las 9 distribuciones se reordenan para ocupar toda la pantalla (en Consola, el mapa grande arriba y tu auto y la música abajo; en Estelar, la hora arriba, el auto al centro y la música y el clima abajo; y así cada una). La barra de abajo pasa a dos filas, y Radio, Bluetooth, Mantención y Auto también se acomodan a lo alto. La app ya no se fuerza a horizontal: usa la orientación de fábrica de la pantalla de la radio y elige sola el diseño. Las radios horizontales quedan igual. Ajustes, bienvenida y diagnóstico se ven normales. En el configurador del sitio hay un interruptor **Horizontal / Vertical** sobre la vista previa. Probado en el navegador a 768 × 1024, 800 × 1280 y 1080 × 1920; falta probarlo en una radio vertical real.
- **Elige la apariencia dentro de la app, sin código.** En Ajustes → **Apariencia** escoges el tema, la distribución, el fondo, la hora del fondo, la decoración de temporada y el color de acento. Se guarda en la radio, funciona sin internet y se ve al volver al inicio. «Como viene» deja la pantalla por defecto. Sin un diseño del sitio, la pantalla de inicio muestra tu nombre, saludo, frase, tipo de auto y color de Ajustes. Todavía no están el diseño Todoterreno ni los fondos de nieve, rocas y barro (van con el tipo «camioneta 4x4»).
- **Pon la foto de tu auto desde la app.** En Ajustes → **Foto de tu auto** eliges una foto de la galería o del explorador de archivos de la radio. Se achica y se queda guardada en la radio, sin internet. Si es un PNG ya sin fondo, se muestra recortada; si es una foto normal, se muestra tal cual (la app todavía no le quita el fondo). Algunas radios no traen galería ni explorador: ahí la app avisa. En el sitio, el botón **«Descargar mi auto sin fondo»** baja tu auto ya recortado (un PNG) para elegirlo en la app.
- **Día y noche por la hora del sol.** El tema oscuro entra cuando se pone el sol donde está el auto, no a una hora fija (en invierno oscurece a las 18 h; en verano, pasadas las 21 h). Sin internet; usa el último GPS guardado o Santiago. El fondo «automático» (amanecer, día, atardecer y noche) también lo sigue. Las luces del auto siguen mandando sobre todo.
- **Reloj de 12 horas.** En Ajustes → Apariencia hay una casilla «Hora en formato de 12 horas» (9:05 PM en vez de 21:05), y en el configurador, en el paso de funciones, la misma opción, que viaja con tu diseño. El «AM/PM» va chico al lado de la hora para que no se parta en dos líneas.
- **Botones «Apps» y «Ajustes» en la barra de abajo de todas las distribuciones.** Antes, «Apps» (ver todas las apps instaladas) solo estaba en Consola y los ajustes solo en un engranaje gris en la esquina, que ahora se ve más (más grande y con borde).

**Al volante**
- **Pantalla de Radio FM.** Muestra la frecuencia y el nombre de la emisora que suena en la radio de tu equipo, con emisora anterior, siguiente y play/pausa. Si la radio FM está sonando, el botón Radio y la tarjeta del inicio abren esta pantalla; si no, abren su app de siempre. Depende de que la app de radio de tu equipo publique la emisora (muchas lo hacen, algunas no): si no la publica, la pantalla lo explica y ofrece abrir su app. Pide una vez el acceso a las notificaciones.
- **Botones del volante.** En Ajustes → Botones del volante, **Aprender un botón**: aprietas el botón y eliges qué hace: controlar la música (siguiente, anterior, pausa) y el volumen, abrir el atajo 1 o 2 de tu barra o el teléfono, llamar al asistente de voz, ir a Inicio, Radio o Mantención, o cambiar entre día y noche. Los que no asignes hacen lo de siempre. Funciona si la radio manda esos botones como teclas normales de Android.
- **Música: más fácil de activar y menos muda.** Para mostrar la canción que suena, Android exige el «acceso a las notificaciones»; en Android 13 o más nuevo ese interruptor sale bloqueado para las apps instaladas por APK, y ahora la app avisa cómo desbloquearlo («Permitir ajustes restringidos») cuando pides el permiso. Si el reproductor avisa que suena algo pero no entrega la canción, la tarjeta muestra el nombre de la app en vez de quedar vacía. El botón de play de «Tu música» abre el reproductor de música del equipo, y si no hay música sonando avisa qué hacer en vez de no hacer nada.

**Mantención y restricción**
- **Kilometraje automático.** Si ya anotaste el kilometraje de tu auto, la app le suma sola los km que recorres (con el GPS), así los avisos de aceite y mantención bajan sin que tengas que actualizar el número. Sigue contando aunque uses Waze o Maps, y por eso muestra un aviso fijo en la barra de notificaciones. Es una estimación: no cuenta cuando el auto está detenido ni los saltos raros del GPS, y puedes corregir el número o apagarlo en Ajustes. Se guarda solo en tu radio.
- **Recordatorios que se repiten solos.** Cada mantención que anotas (filtro, bujías, frenos…) cuenta sus km con barra y alerta, igual que el aceite. En Mantención aparece **Hecho** cuando un aviso está por vencer (se confirma con dos toques): anota el kilometraje de hoy y el próximo aviso queda «cada N km» más adelante. Los que aún no tienen fecha dicen **Contar desde hoy**.
- **México: Hoy No Circula de la Ciudad de México.** Nuevo país en el configurador y en la radio: al entrar a la Ciudad de México avisa si ese día le toca a tu placa (lunes 5 y 6, martes 7 y 8, miércoles 3 y 4, jueves 1 y 2, viernes 9 y 0; de 5:00 a 22:00). El aviso recuerda que los autos con holograma 0 y 00, los eléctricos y los híbridos están exentos. **No incluye** los sábados ni las contingencias ambientales. La fuente es prensa (no se pudo abrir el sitio oficial); confirma siempre en la autoridad. Perú y España no se agregaron: Lima no tiene una restricción por placa confirmada para autos particulares, y las zonas de bajas emisiones de España dependen de la etiqueta ambiental del auto, no de la placa.

**Scanner OBD** (opcional; sin probar todavía en un auto real: hay scanners clon que no entregan todos los datos)
- **Conectar un scanner.** En Ajustes → Scanner OBD eliges cómo se conecta (**Bluetooth**, el más común, como el «Scanner Automotriz OBD2 ELM327 Bluetooth V2.1», o **Wi-Fi**), lo eliges entre los ya emparejados con la radio y pulsas **Probar conexión**: te muestra el voltaje, si el auto responde, unos datos (revoluciones, velocidad, temperatura del motor) y si la luz del motor está encendida. Android pide el permiso de Bluetooth solo al elegir un scanner Bluetooth.
- **Buscar el scanner desde la app.** El botón «Buscar scanner cerca» encuentra el scanner Bluetooth y lo elige sin pasar por los ajustes de Bluetooth de la radio, que en varias radios solo dejan emparejar teléfonos. Si el scanner no está emparejado, la app prueba primero la conexión sin clave que aceptan casi todos los ELM327, y si hace falta Android pide la clave (1234 o 0000). Los scanners Bluetooth 4.0 (BLE) siguen sin ser compatibles.
- **Pantalla «Auto».** Con un scanner elegido aparece «Auto» en la barra de abajo: revoluciones, temperatura del motor y voltaje en vivo, la luz del motor y los códigos de falla explicados en palabras simples (solo se leen, no se borran). Si el auto informa su kilometraje real, los avisos de mantención lo usan en vez del que suma el GPS. En el sitio se ve con datos de ejemplo en la pestaña «Scanner OBD».
- **Combustible y rendimiento.** La pantalla «Auto» muestra el combustible del estanque (%), el gasto en litros por hora y el rendimiento en kilómetros por litro. Si el auto entrega el gasto directamente, se usa ese dato; si no, se estima desde el flujo de aire suponiendo un motor a bencina (en diésel la estimación no sirve). El rendimiento solo aparece en movimiento (desde 5 km/h). Si el auto no entrega un dato, se muestra «—».

**Descargar e instalar**
- **«Descargar la app con mi diseño».** En el último paso del configurador, la APK que bajas ya trae lo que elegiste: tema, distribución, fondo, hora, decoración, color, tu nombre y saludo, y tus avisos de mantención, documentos y restricción. La app lo aplica sola al abrirse por primera vez, sin código. Si instalas después otra APK con otro diseño, se aplica ese; si cambias algo en Ajustes y no vuelves a instalar, no se pisa. **La foto de tu auto también viaja dentro de la app**: si la subiste, viene con su versión recortada y la de noche con las luces encendidas. La foto pasa una sola vez por nuestro servidor para ponerla en la APK y no se guarda; si es muy grande o algo falla, bajas la app con tu diseño y la eliges en Ajustes → Foto de tu auto.
- **Aviso de actualización dentro de la app.** Al abrir Tablero (con internet) revisa si hay una versión nueva y ofrece «Actualizar ahora» o «Después»; descarga el instalador y Android pide confirmar. Los ajustes y la foto se conservan. En Ajustes → Actualizaciones hay un botón «Buscar actualización». Requiere que la versión instalada ya esté firmada con la clave fija.
- **Primer arranque guiado** en 3 pasos, todos opcionales: tu auto (nombre, tipo y color), tu aceite (kilometraje y último cambio) y hacer de Tablero la pantalla de inicio de la radio. Sale una sola vez.
- **Diagnóstico de la radio** (Ajustes → Diagnóstico de la radio): muestra qué Android y qué pantalla tiene tu radio, si su radio FM publica la emisora y qué código manda cada botón del volante al apretarlo. Se puede copiar o compartir para reportar un problema, sin adb ni Android Studio. No lleva tu nombre ni tu ubicación.

**En el sitio**
- **Video de presentación en la portada**, antes de «¿Te suena?»: un minuto y medio con los diseños, tu auto, las decoraciones de temporada, los avisos y el scanner OBD, con pantallas reales de la app. No se descarga hasta que aprietas reproducir (pesa 6 MB).
- **Contadores públicos arriba del sitio.** Una franja en el tope de la portada dice, por ejemplo, «Ya son 64 los que han descargado la app · 7 han creado su propio Tablero aquí · 152 visitas». La app se cuenta cada vez que alguien la descarga (con o sin diseño; la ruta `/app` ahora pasa por el servidor para poder contarla), «crearon su propio Tablero» son las descargas que llevaban un diseño armado en el sitio, y las visitas se suman una por sesión. Son solo números, sin cookies ni datos de nadie, y los robots no cuentan. Si el contador falla o está en cero, la franja no aparece y la descarga funciona igual. El desglose completo se ve en `/api/stats`.
- **Guía para probar Tablero en tu radio** (`/prueba`): cómo conseguir la app (con tu diseño o normal), instalarla, qué revisar con casillas para ir marcando y qué contarme. No tiene enlaces desde el resto del sitio (se comparte con el enlace directo) y no aparece en buscadores.
- **«Invítame un café».** Un enlace opcional a Ko-fi en el pie del sitio, en una sección propia antes del cierre de la portada, en el menú y la barra superior, en la página de instalación y al final de Ajustes en la app. Tablero sigue siendo gratis y nada depende de ese enlace. En una radio sin navegador, el botón avisa en vez de no hacer nada.

### Cambiado
- **Tablero es gratis y no necesita códigos.** La edición sin código incluye todos los diseños, las decoraciones de temporada y todas las funciones (documentos, combustible, restricción vehicular, saludo por voz, otras mantenciones, hasta 4 atajos y el límite fijo de velocidad). El último paso del configurador es «Tu diseño y la descarga», con un resumen y el botón para bajar la app. Hay dos caminos: diseñas en el sitio y bajas la app **con tu diseño ya puesto**, o bajas la app normal y eliges todo dentro de ella (Ajustes → Apariencia y Foto de tu auto). La guía «Instalar en tu radio», la portada, las preguntas y la privacidad se reescribieron para esto.
- **Fondo del sitio en azul suave con degradado**, en vez de negro. Las tarjetas, la barra superior y los textos secundarios se ajustaron al nuevo fondo. La vista previa de la radio sigue oscura, como la pantalla real.
- **El sitio ya no promete el límite automático de velocidad.** La app todavía no lee el límite de cada calle, y en modo «Automático» no avisa nada; los textos del sitio decían lo contrario. Ahora dicen que, para recibir el aviso, hay que elegir «Manual» y poner el límite. En la radio, sin límite fijado ya no dice «Límite de la vía».
- **Portada: sección del scanner OBD.** Explica qué funciona hoy, qué llega después y en qué se diferencia de otras apps de OBD; más tres preguntas frecuentes.

### Quitado
- **Planes y pagos.** Ya no existen los planes Esencial y Completo, los precios, el cobro con PayPal, los datos de transferencia bancaria, las etiquetas «Completo» en los diseños, la casilla de aceptar términos ni las páginas de **Términos** y **Cambios y ayuda**. La política de **Privacidad** se mantiene porque explica qué hace la app con tus datos. Se quitaron también dos frases viejas de «te devolvemos tu dinero».
- **Códigos de activación.** La app ya no tiene la pantalla para escribir un código ni el botón «Activar código», y el sitio dejó de pedir contacto y de enviar pedidos. Ya no existen el servidor de pedidos y códigos (`order`, `order-photo`, `license` y `admin`), su base de datos ni el panel `/admin.html`; quedan solo `app` (descarga con tu diseño), `analyze-radio` y `detect-lights`. Una radio que ya había activado un código conserva su diseño en la propia radio y puede soltarlo desde Ajustes → Apariencia.

## [0.1.0] - 2026-10-30

Prototipo inicial. Nunca se publicó como versión en GitHub: el APK de cada compilación se publica en `app-latest`.

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
