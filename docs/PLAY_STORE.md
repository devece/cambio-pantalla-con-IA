# Subir Tablero a Play Store: estrategia y estado

Este documento deja preparado el camino. **No se ha publicado nada en Play.**

## Dos variantes de la misma app

| | Descarga directa (sitio) | Play Store |
|---|---|---|
| Se compila con | `:app:assembleRelease` | `:app:assemblePlay` (APK) o `:app:bundlePlay` (AAB, lo que pide Play) |
| Actualizador dentro de la app | Sí (aviso, descarga y instala con confirmación) | **No** (Play prohíbe que una app se actualice por su cuenta) |
| Permiso de instalar apps (`REQUEST_INSTALL_PACKAGES`) | Sí | Se quita (`src/play/AndroidManifest.xml`) |
| Ajustes | «Actualizaciones» con botón | «Versión» solo informativa |

La CI compila la variante de Play en cada ejecución para que no se rompa sin que nadie lo note.

## Lo que falta para publicar (decisiones y trabajo)

1. **Cuenta de Play Console** (pago único de US$25 y verificación de identidad). Una cuenta personal nueva debe pasar una prueba cerrada con testers antes de publicar: confirma el número y los días vigentes en la consola.
2. **Firma (decisión que no se puede deshacer).** Play firma la app con su propia clave («Play App Signing») y tu clave pasa a ser la de subida. Si no se registra bien, quien instale desde Play no podrá actualizar sobre tu APK del sitio, ni al revés. Se puede registrar la clave actual en Play App Signing al crear la app, no después. Decídelo antes de subir nada.
3. **`targetSdk`.** Hoy es 34; Play exige una versión más reciente para apps nuevas (confirmar la cifra vigente en la consola). Subirlo cambia comportamientos de Android: se prueba aparte, en su propia rama.
4. **Permisos sensibles que hay que justificar en la consola:** `QUERY_ALL_PACKAGES` (mostrar las apps instaladas como atajos, uso central de un launcher), ubicación y servicio en primer plano de ubicación (velocidad, mapa y kilometraje), acceso a las notificaciones (leer qué canción suena) y Bluetooth (scanner OBD).
5. **Ficha:** ícono de 512 px, imagen de 1024×500, capturas, descripciones, la política de privacidad (`/privacidad`), el formulario de seguridad de los datos y la clasificación de contenido.
6. **Versiones:** el `versionCode` de la app sigue `scripts/version.js` (1000 + commits): Play exige que cada subida lo aumente, y así es.
7. Muchas radios de auto no traen Play Store: la descarga directa del sitio se mantiene.
