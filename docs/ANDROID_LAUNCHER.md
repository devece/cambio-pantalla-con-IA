# Launcher Android

Este repositorio **no incluye** el launcher nativo; es la parte difícil y falta construirla.

Requisitos mínimos:
- App Android (Kotlin) con intent `HOME`/`LAUNCHER`, pantalla horizontal 1024×600 y modo día/noche según las luces del auto.
- Reproducir los 8 temas, 4 distribuciones y 5 pantallas definidos en `public/js/themes.js` y `public/js/preview.js`.
- Pantalla de activación que llame a `POST /api/license` con el código `TBL-XXXX-XXXX-XXXX` y un identificador estable de la radio.
- Funciones: avisos de mantención, documentos (revisión técnica, permiso, SOAP), alerta de velocidad (GPS), saludo por voz.
- **Probar en radios reales**: cada marca/chip se comporta distinto (permisos, arranque, rotación).
