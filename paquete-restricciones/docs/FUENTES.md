# Fuentes y lo que se pudo (y no se pudo) comprobar

Revisado el **2026-11-05**, salvo Chile (**2026-11-02**). Para cada ciudad se anota qué se leyó realmente. Donde no se pudo abrir la fuente oficial, se dice.

## Santiago (Región Metropolitana), Chile · oficial

- Fuentes del Ministerio de Transportes (MTT): [anuncio del período 2026](https://mtt.gob.cl/inicia-periodo-de-restriccion-vehicular-2026-region-metropolitana/) y la [resolución de la restricción permanente 2026](https://mtt.gob.cl/wp-content/uploads/2026/04/Res-2026-Restriccion-vehicular-permanente-ano-2026_firmado_GCB.pdf).
- Datos: con sello verde, 2 dígitos por día; sin sello, 4 dígitos por día y prohibición dentro del anillo Américo Vespucio; inscritos desde septiembre de 2011, sin restricción permanente. Temporada de mayo a agosto, de lunes a viernes, de 7:30 a 21:00.
- **Pendiente:** la tabla se armó a partir de esas fuentes el 2026-11-02 y **no se volvió a contrastar** con el documento oficial al armar este paquete. Una persona de Chile debería revisarla dígito por dígito.
- El polígono es una aproximación (11 puntos).

## Bogotá, Colombia · oficial

- Fuente: [Alcaldía de Bogotá, semana del 21 al 27 de septiembre de 2026](https://bogota.gov.co/mi-ciudad/movilidad/pico-y-placa-en-bogota-para-la-semana-del-21-al-27-de-septiembre-2026), que reproduce el calendario de la Secretaría Distrital de Movilidad.
- Se leyó: en días impares pueden circular las placas terminadas en 1 a 5, y en días pares las terminadas en 6, 7, 8, 9 y 0 (por lo tanto tienen restricción las otras). Horario de 6:00 a 21:00, de lunes a viernes, sin festivos.
- **No se pudo abrir** `movilidadbogota.gov.co` (la Secretaría), ni el decreto que fija la regla. No hay `validTo`: conviene revisar cada semestre.
- Una página de trámites daba otra tabla y horarios en dos franjas; contradice a la Alcaldía y **no se usó**.

## Medellín y Valle de Aburrá, Colombia · prensa

- Fuente: [El Tiempo, 3 de agosto de 2026](https://www.eltiempo.com/colombia/medellin/el-nuevo-pico-y-placa-en-medellin-rige-desde-este-3-de-agosto-recuerde-los-horarios-multas-y-vias-exentas-de-la-medida-3575755).
- Lo que **sí** se leyó: rotación del segundo semestre de 2026 desde el 3 de agosto; de lunes a viernes, de 5:00 a 20:00; el lunes 3 de agosto, placas 5 y 8; vías exentas (Avenida Regional, Autopista Sur en Medellín, vía Las Palmas, entre otras).
- Lo que **no** se pudo leer directamente: los dígitos de martes a viernes (1 y 4, 0 y 2, 3 y 6, 7 y 9). Salen de resúmenes de búsqueda que coinciden en varios medios; una nota aislada daba otra tabla. Cada dígito aparece exactamente una vez a la semana, lo que es consistente.
- `validTo` = 2026-12-31 es una **suposición**: se habla de «segundo semestre».
- No se pudo abrir `medellin.gov.co`.

## Cali, Colombia · prensa

- Fuente: [El País (Cali), 1 de julio de 2026](https://www.elpais.com.co/cali/pico-y-placa-en-cali-este-miercoles-1-de-julio-ojo-evite-multas-con-la-nueva-rotacion-3023.html). Cita el Decreto 0814 de 2026 y una vigencia hasta el 31 de diciembre.
- Se leyó: lunes 9 y 0, martes 1 y 2, miércoles 3 y 4, jueves 5 y 6, viernes 7 y 8; de 6:00 a 19:00, sin fines de semana ni festivos.
- **No se leyó el decreto.** Hubo reportes de cambios temporales por emergencia (agosto y septiembre) y de un horario de cierre distinto (20:00) en una nota de octubre: **no están reflejados** y no se pudieron confirmar.

## Quito, Ecuador · prensa

- Fuentes: [Expreso, 22 de junio de 2026](https://www.expreso.ec/quito/pico-placa-quito-2026-restriccion-vehicular-placas-terminadas-3-4-286671.html), que cita a la Agencia Metropolitana de Tránsito (AMT), y notas del diario Extra.
- Se leyó: martes, placas 3 y 4; franjas de 6:00 a 9:30 y de 16:00 a 20:00; sin fines de semana ni feriados. La tabla completa (lunes 1 y 2, miércoles 5 y 6, jueves 7 y 8, viernes 9 y 0) sale de varias notas.
- **No se leyó la ordenanza ni un comunicado de la AMT.** Se menciona un sistema de «tercera placa» para fines de 2026 que cambiaría la regla.
- La nota no aclara si la regla es para todo el Distrito Metropolitano o solo la zona urbana; el polígono cubre el área urbana.

## Zonas

Todos los polígonos son aproximados y los dibujó una persona a ojo sobre el área urbana. No son límites legales.
