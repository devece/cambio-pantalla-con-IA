# Cambios

Todo lo que nota quien usa Tablero (el sitio y la app de la radio), por versión.
Formato [Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/); versiones [SemVer](https://semver.org/lang/es/).

Cómo se publica una versión: `docs/RELEASE.md`.

## [Sin publicar]

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
