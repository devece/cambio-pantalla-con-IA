# Edición gratuita

El usuario descarga el launcher, lo instala en su radio y lo usa **sin código** con funciones limitadas. Un código de licencia (`TBL-XXXX-XXXX-XXXX`) desbloquea el resto.

La fuente de verdad de lo que es gratis es `public/shared/editions.json`. Cambiar el corte es editar ese archivo; `npm test` comprueba que sea coherente con `catalog.js`.

## Qué incluye

| | Gratis | Con código (Esencial / Completo) |
|---|---|---|
| Diseños visuales | 1 (Ruta) | 3 en Esencial, 8 en Completo |
| Distribuciones | 2 (Tarjetas, Minimal) | las 4 |
| Fondos | Del tema o color liso | los 5 paisajes |
| Decoración de temporada | No | Completo |
| Atajos a apps | 2 | hasta 4 |
| Aviso de cambio de aceite | Sí | Sí |
| Velocidad y alerta | Sí (automática o apagada) | Sí, con límite manual |
| Otras mantenciones (filtros, frenos, correa…) | No | Sí |
| Documentos (revisión técnica, permiso, SOAP) | No | Sí |
| Consumo de combustible | No | Sí |
| Restricción vehicular | No | Sí |
| Saludo por voz | No | Sí |

Criterio del corte: lo gratis da una pantalla útil y bonita para probar (el aceite y la velocidad son lo que más se usa a diario). Lo que más valor tiene para el mercado chileno (documentos al día, restricción vehicular) y los diseños extra quedan tras el código.

## Cómo se instala

1. Se publica un **APK ya compilado** en las *Releases* de GitHub. Pedirle a un usuario común que compile Android desde el código no es realista.
2. El usuario copia el APK a la radio (pendrive o descarga) y lo instala permitiendo "orígenes desconocidos".
3. Define el launcher como inicio de la radio.
4. Para desbloquear: ingresa su código en la pantalla de activación (`POST /api/license`).

## Decisiones que debes tomar tú

- **Licencia del repositorio.** Hoy `package.json` dice `UNLICENSED`: nadie tiene permiso legal de usar lo que descargue. Elige una antes de publicar. Una licencia de código abierto (MIT, GPL) permite que otros lo modifiquen y lo redistribuyan; una no comercial permite usarlo gratis pero no venderlo.
- **El candado es de buena fe si el código es público.** Alguien podría quitar la verificación de licencia y recompilar. Es el caso normal del software gratis con versión de pago. Para dificultarlo: que los diseños y fondos de pago **no estén en el repositorio público** y se descarguen desde el servidor solo al activar un código válido.
- **Qué pasa sin internet.** Una vez activado, el launcher debe guardar el estado localmente para no bloquearse en un túnel o sin datos.

## Estado

- Hecho: el corte gratis/pago definido y verificado por pruebas.
- Falta: **el launcher Android** (`ANDROID_LAUNCHER.md`), que debe leer `editions.json`, funcionar sin código en modo gratis y desbloquear con licencia. En el entorno donde se escribió esto no hay Android SDK, así que nada de eso se ha compilado ni probado en una radio.
