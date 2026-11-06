# Cambios

Todo lo que nota quien usa Tablero (el sitio y la app de la radio), por versión.
Formato [Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/); versiones [SemVer](https://semver.org/lang/es/).

Cómo se publica una versión: `docs/RELEASE.md`.

## [Sin publicar]

### Agregado
- **Recordatorios que se repiten solos.** Cada mantención que anotas (filtro, bujías, frenos…) cuenta sus km con barra y alerta,
  igual que el aceite. En Mantención aparece **Hecho** cuando un aviso está por vencer (se confirma con dos toques): anota el
  kilometraje de hoy y el próximo aviso queda "cada N km" más adelante. Los que aún no tienen fecha dicen **Contar desde hoy**.
- **Día y noche por la hora del sol.** El tema oscuro entra cuando se pone el sol donde está el auto, no a una hora fija
  (en invierno oscurece a las 18 h; en verano, pasadas las 21 h). Sin internet; usa el último GPS guardado o Santiago.
  El fondo "automático" (amanecer, día, atardecer y noche) también lo sigue. Las luces del auto siguen mandando sobre todo.
- **Botones del volante.** En Ajustes → Botones del volante, **Aprender un botón**: aprietas el botón y eliges qué hace
  (play/pausa, siguiente, anterior, volumen, silenciar, mapa, música, asistente de voz o ir al inicio). Los que no asignes
  hacen lo de siempre. Funciona si la radio manda esos botones como teclas normales de Android.
- **Primer arranque guiado** en 3 pasos, todos opcionales: tu auto (nombre, tipo y color), tu aceite (kilometraje y último
  cambio) y hacer de Tablero la pantalla de inicio de la radio. Sale una sola vez.
- Sin un código activado, la pantalla de inicio ya muestra tu nombre, saludo, frase, tipo de auto y color de Ajustes
  (antes solo los veía quien tenía un diseño del sitio).
- **Pantalla de Radio FM.** Muestra la frecuencia y el nombre de la emisora que suena en la radio de tu equipo, con
  emisora anterior, siguiente y play/pausa. Si la radio FM está sonando, el botón Radio y la tarjeta del inicio abren esta
  pantalla; si no, abren su app de siempre. Depende de que la app de radio de tu equipo publique la emisora (muchas lo
  hacen, algunas no): si no la publica, la pantalla lo explica y ofrece abrir su app. Pide una vez el acceso a las notificaciones.

### Cambiado
- En el botón de música de la pantalla, si no hay música sonando ahora avisa qué hacer en vez de no hacer nada.

### Para quien publica
- Nuevo workflow **Release**: al crear una etiqueta `vX.Y.Z` compila el APK firmado y lo publica en GitHub Releases con estas notas.

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
