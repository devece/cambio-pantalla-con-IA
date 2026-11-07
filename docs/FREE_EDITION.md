# Edición gratuita

**Por ahora no hay versión de pago: la edición gratuita incluye todo.** El usuario descarga el launcher, lo instala en su radio y puede usar todos los diseños, decoraciones y funciones **sin código**.

El código de licencia (`TBL-XXXX-XXXX-XXXX`) sigue existiendo: sirve para traer a la radio la pantalla que se diseñó en el sitio (incluida la foto del auto). No hay planes ni precios.

La fuente de verdad de qué incluye cada nivel es `public/shared/editions.json`. `npm test` comprueba que sea coherente con `catalog.js`.

## Qué incluye

| | Gratis (hoy) |
|---|---|
| Diseños visuales | Todos |
| Distribuciones | Todas |
| Fondos | Todos |
| Decoración de temporada | Sí |
| Atajos a apps | Hasta 4 |
| Aviso de cambio de aceite | Sí |
| Velocidad y alerta | Sí (límite fijo; el automático por calle aún no existe) |
| Otras mantenciones (filtros, frenos, correa…) | Sí |
| Documentos (revisión técnica, permiso, SOAP) | Sí |
| Consumo de combustible | Sí |
| Restricción vehicular | Sí |
| Saludo por voz | Sí |

## Reglas que conviene no romper

- **Un código nunca da menos que no tener código.** Lo que puede usar quien activó un plan es lo más amplio entre ese plan y la edición gratuita (`Editions.effective`). Sin esto, activar un código Esencial habría dejado a alguien con menos que la edición gratuita.
- Los niveles `esencial` y `completo` siguen en `editions.json`, iguales a `free`, solo porque la app los reconoce en códigos ya emitidos y en radios que ya activaron uno. No hay planes: si algún día vuelve a haber un plan de pago, es una decisión a propósito y hay que volver a limitar `free` (la prueba `la edición gratis incluye todo` en `tests/editions.test.js` avisará).
- La lógica del candado (`Gate`) sigue probada con un nivel restringido de ejemplo, aunque hoy ninguna edición lo use.

## Cómo se instala

1. Se publica un **APK ya compilado** en las *Releases* de GitHub. Pedirle a un usuario común que compile Android desde el código no es realista.
2. El usuario copia el APK a la radio (pendrive o descarga) y lo instala permitiendo "orígenes desconocidos".
3. Define el launcher como inicio de la radio.
4. Para desbloquear: ingresa su código en la pantalla de activación (`POST /api/license`).

## Decisiones pendientes

- **Licencia del repositorio.** Hoy `package.json` dice `UNLICENSED`: nadie tiene permiso legal de usar lo que descargue. Elige una antes de publicar. Una licencia de código abierto (MIT, GPL) permite que otros lo modifiquen y lo redistribuyan; una no comercial permite usarlo gratis pero no venderlo.
- **El candado es de buena fe si el código es público.** Alguien podría quitar la verificación de licencia y recompilar. Hoy no hay nada de pago que proteger, así que esto solo importa si algún día vuelve a haber una versión de pago. Para dificultarlo: que los diseños y fondos de pago **no estén en el repositorio público** y se descarguen desde el servidor solo al activar un código válido.
- **Qué pasa sin internet.** Una vez activado, el launcher debe guardar el estado localmente para no bloquearse en un túnel o sin datos.

## Estado

- Hecho: el corte de ediciones definido y verificado por pruebas (hoy todo es gratis).
- Hecho: el esqueleto del launcher en `android/`. Las reglas (`android/core`) están probadas; la app (`android/app`) está escrita pero **sin compilar** (no había Android SDK).
- Falta: compilar y corregir la app en Android Studio, los temas y las funciones, y probar en radios reales. Ver `ANDROID_LAUNCHER.md`.
