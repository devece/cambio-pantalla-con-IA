# Cómo actualizar las reglas

Las reglas rotan: en Colombia, cada semestre (a veces por fecha, como en Bogotá); en Chile, la tabla nueva se publica cada abril; en Quito puede cambiar el sistema.

## Cada vez que cambie una regla

1. Busca la regla vigente **en el sitio de la autoridad** (secretaría de movilidad, ministerio, decreto o resolución). Si solo encuentras prensa, déjalo como `"confidence": "prensa"`.
2. Edita la ciudad en `data/restricciones.json`:
   - actualiza `rule`, `hours`, `weekdays` y `months`;
   - pon `validFrom` y `validTo` (si la fuente no da fin, deja `null` y anótalo en `note`);
   - pon `sources` (enlaces) y `checkedAt` (el día que **leíste** la fuente, no otro);
   - sube `dataVersion` a la fecha de hoy.
3. Revisa que cada dígito 0-9 tenga restricción exactamente una vez por semana en las reglas por día de la semana (es la regla en todas las ciudades actuales; si una ciudad es distinta, anótalo en `note`).
4. Actualiza o agrega casos en `pruebas/casos.json` con el nuevo calendario (al menos: un día con restricción, uno sin, uno fuera de horario y uno vencido).
5. Corre `node pruebas/correr.js` y la prueba de Java. Deben dar «0 mal» en las dos.
6. Anota el cambio y lo que **no** pudiste verificar en `docs/FUENTES.md`.

## Agregar una ciudad

Copia una ciudad con el mismo tipo de regla, cambia `id`, `country`, `name`, el polígono `zone` (puntos `[latitud, longitud]` que cubran el área donde rige; mejor con más de 8 puntos y revisado por alguien de la ciudad), y sigue los pasos anteriores. Si el país es nuevo, agrégalo en `countries`.

## Qué hace la app cuando la regla vence

Si la fecha de hoy pasa de `validTo`, `evaluate` devuelve `expired: true` y `restricted: false`. La app **no debe afirmar** que hoy no hay restricción: debe avisar que la regla guardada venció y mandar a la fuente oficial.
