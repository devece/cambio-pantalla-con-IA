# Restricción vehicular

Cómo funciona en el sitio y en el launcher, qué ciudades de Chile aplican y cómo mantenerlo al día.

## Dónde existe en Chile (investigado el 2026-11-02)

| Zona | Qué se aplica | ¿Lo cubre Tablero? |
|---|---|---|
| **Región Metropolitana: Provincia de Santiago, San Bernardo y Puente Alto** | Restricción **permanente** a autos particulares, del 1 de mayo al 31 de agosto, de lunes a viernes (excepto festivos), de 7:30 a 21:00. Según el último dígito de la patente. | **Sí** |
| Santiago, en **preemergencia o emergencia ambiental** | Se suman dígitos y puede alcanzar a más autos. Se anuncia el día anterior. | Solo se avisa en el sitio; la radio no lo consulta |
| Rancagua | Solo a **colectivos rurales** (por último dígito, lunes a viernes 8:00 a 18:00), no a autos particulares. | No (no es para autos particulares) |
| Temuco y Padre Las Casas | La gestión de episodios críticos (1 de abril al 15 de septiembre) apunta a la **leña**, no a los autos. | No |
| Gran Concepción y Valparaíso | No se encontró restricción vehicular permanente para autos particulares. | No |

Un sitio comercial afirma que Temuco, Rancagua, Talca, Chillán, Los Ángeles y Osorno tendrían restricción vehicular en invierno. **No pudimos confirmarlo en fuentes oficiales**, así que no se incluyó. Si alguien lo confirma con una resolución de la Seremi, se agrega como una zona más.

## Calendario de la temporada 2026 (Región Metropolitana)

| Día | Con sello verde (inscritos antes del 1-sep-2011): 2 dígitos | Sin sello verde: 4 dígitos |
|---|---|---|
| Lunes | 8 y 9 | 6, 7, 8 y 9 |
| Martes | 0 y 1 | 0, 1, 2 y 3 |
| Miércoles | 2 y 3 | 4, 5, 6 y 7 |
| Jueves | 4 y 5 | 8, 9, 0 y 1 |
| Viernes | 6 y 7 | 2, 3, 4 y 5 |

- Los autos **sin sello verde** tampoco pueden circular dentro del anillo **Américo Vespucio**, cualquier día de la semana.
- Los autos inscritos desde septiembre de 2011 no tienen restricción permanente.
- Multa: 1 a 1,5 UTM.

**Fuentes:** [MTT, inicio del período 2026](https://mtt.gob.cl/inicia-periodo-de-restriccion-vehicular-2026-region-metropolitana/) y [Resolución Exenta 2026 de la Seremi RM](https://mtt.gob.cl/wp-content/uploads/2026/04/Res-2026-Restriccion-vehicular-permanente-ano-2026_firmado_GCB.pdf).

> **Verificado contra la fuente oficial el 2026-11-11.** Se leyó el PDF de la Resolución Exenta N.º 1698/2026 de la SEREMI de Transportes y Telecomunicaciones RM: la temporada (1 de mayo al 31 de agosto), el horario (7:30 a 21:00), la zona (Provincia de Santiago, San Bernardo y Puente Alto), el anillo Américo Vespucio para los autos sin sello verde y **las dos tablas de dígitos** coinciden con las de arriba. Una nota de prensa que traía otra tabla de sello verde estaba equivocada. `tests/restriccion-oficial.test.js` y `RestrictionTest.kt` fijan esa transcripción.

## Cómo se aplica en el producto

1. El cliente elige el tipo de auto (con sello verde, sin sello verde, o inscrito desde 2011) y el último dígito de su patente. El sitio calcula los días con la tabla.
2. La radio usa el **GPS**. Al **entrar** a la zona de Santiago (de afuera hacia adentro) avisa en pantalla y por voz, **solo si corresponde**: temporada vigente, día hábil, entre 7:30 y 21:00, y ese día le toca. Si el auto no tiene sello verde, avisa también del anillo Américo Vespucio.
3. No molesta fuera de Santiago.

La zona es un **polígono aproximado** (`public/shared/catalog.js` → `restriction.zone`). Conviene afinarlo con los límites comunales oficiales de la BCN antes de vender: cerca del borde puede avisar unos cientos de metros antes o después.

La radio no conoce los **festivos**: en un festivo avisará igual. Es preferible un aviso de más que uno de menos.

## Dónde está el código

- Datos y reglas: `public/shared/catalog.js` (`restriction`) y `public/shared/order.js` (`restrDaysFor`, `inRestrictionZone`, `restrictionNotice`). El servidor **recalcula los días** a partir del dígito; no confía en los del navegador.
- Copia para la radio: `public/shared/restriction.json`. Una prueba exige que sea idéntica al catálogo.
- Android: `android/core/.../Restriction.kt` (misma lógica, con prueba `RestrictionTest.kt`), aviso en `MainActivity.kt` y formulario en `SettingsActivity.kt`.

## Qué pasa en otro año
Los dígitos de cada día **cambian con la resolución de cada año**. La tabla cargada solo se afirma durante su año (`restriction.year`): en otro año, el aviso al entrar a Santiago no dice «te toca» ni «no te toca», sino «Tablero trae la tabla de 2026 y estamos en 2027: revisa en mtt.gob.cl cuál rige este año». Así la radio no da un aviso falso mientras no se cargue la resolución nueva.

## Otros países
Solo Chile y Bogotá están contrastados con su fuente oficial (la Secretaría Distrital de Movilidad, semana del 21 al 27 de septiembre de 2026: días impares circulan las placas 1 a 5, días pares 6, 7, 8, 9 y 0, de 6:00 a 21:00). **Medellín, Cali, Quito y Ciudad de México siguen como «prensa»**: no se pudieron verificar (páginas sin contenido legible o inaccesibles desde el entorno de desarrollo). La app lo dice en la descripción de cada ciudad.

## Actualización anual

Cada abril el MTT publica la resolución del año. Para actualizar: editar `restriction` en `catalog.js` (año, fechas, tabla), regenerar `restriction.json`, **actualizar la transcripción de `tests/restriccion-oficial.test.js` y `RestrictionTest.kt` con lo que dice la resolución nueva** y correr `npm test`. Sin ese cambio, a partir del 1 de enero la radio avisa que la tabla es del año anterior. Revisar también si cambió el horario o el alcance geográfico.
