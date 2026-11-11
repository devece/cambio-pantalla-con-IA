# Calidad: qué se prueba, con qué y qué no se puede probar aquí

## Qué corre en cada cambio (GitHub Actions, flujo «Android»)

| Revisión | Qué comprueba | Dónde |
|---|---|---|
| `npm test` | Sitio y servidor: validación de pedidos, APK, contrato del diseño, restricción, contadores… | `tests/` |
| `:core:test` | Reglas de la app sin Android: OBD, restricción, importación del diseño, actualizaciones… | `android/core/src/test` |
| `:app:testDebugUnitTest` | **El módulo de la app** con Robolectric, que simula Android en el PC en cada versión que se le pida (hoy 5, 6, 8, 10, 13 y 14): guardado de Ajustes, permisos de búsqueda de scanners por versión de Android y apertura de Ajustes, bienvenida y diagnóstico | `android/app/src/test` |
| `:app:lintDebug` | Lint contra la versión mínima (Android 5): llamadas a funciones que no existen en versiones antiguas, permisos mal usados | informe en el log del flujo |
| Compilación del APK, de la variante de Play y revisión de su contenido y firma | | `android.yml` |
| Pantalla de la radio en un navegador real | Lo que se dibuja con los datos que entrega la app | `e2e.yml` |

## Lo que Robolectric sí y no prueba

- **Sí:** que el código no use una función que no existe en una versión de Android (falla con `NoSuchMethodError` en esa versión), que lo guardado vuelva igual, que las pantallas se abran y se cierren, y la lógica de permisos por versión.
- **No:** un dispositivo real. No prueba el WebView de la radio (cada fabricante trae uno distinto), el GPS, el Bluetooth, el instalador de Android ni cómo se ve en una pantalla concreta. Eso solo lo prueba una radio real; la guía está en `PRUEBA_EN_RADIO.md`.
- La pantalla principal (`MainActivity`, que carga el WebView) todavía no tiene prueba de apertura en Robolectric.

## Lint

Su causa de fallo original era que no esperaba a las tareas que generan los assets (`copyEditions`, `copyWeb`); eso ya está declarado en `android/app/build.gradle.kts`. Por ahora **informa y no bloquea** (`abortOnError = false`). Cuando el informe quede limpio de lo que importa (`NewApi`, `MissingPermission`, componentes exportados), se vuelve estricto.

## El puente JavaScript de la pantalla

La pantalla de la radio es una página local que llama a la app (`window.Android`). Lo que se revisó:

- `launchPkg(pkg)` abre apps con `getLaunchIntentForPackage`, que solo devuelve algo para apps que tienen ícono de lanzador: es lo mismo que el usuario puede abrir desde el cajón de apps. No abre actividades arbitrarias.
- Los textos que vienen de fuera (título de la canción, nombre de la emisora, nombre de las apps) se escapan antes de dibujarse (`esc()` en `preview.js` y `launcher.js`).
- **Pendiente, con una radio real:** una política de contenido (CSP) para esa página. Hoy no es seguro agregarla sin probarla en un dispositivo: la pantalla usa manejadores en línea y el WebView de cada radio interpreta CSP distinto. Un error ahí dejaría la pantalla de inicio en blanco.

## Compatibilidad con versiones de Android

- Se declara Android 5 como mínimo. Robolectric y lint cubren el código; **no hay evidencia en dispositivos con Android 5 a 7**.
- Una prueba con emulador (por ejemplo API 21, 23, 26, 29, 33 y 34) sería el siguiente paso; es lenta (varios minutos por versión), por eso conviene como flujo aparte, manual o semanal, y no en cada cambio. No está implementada.
