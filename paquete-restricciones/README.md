# Restricciones vehiculares de Latinoamérica

Reglas de **restricción vehicular / pico y placa** en un formato abierto, con su **fuente, fecha de revisión y vigencia**, más una implementación de referencia en **JavaScript** y en **Java** (pensada para Android antiguo) y casos de prueba que cualquiera puede usar para comprobar su propia versión.

Nació dentro de [Tablero](https://tablero-pearl.vercel.app) y se ofrece para que otros proyectos, como los launchers de pantallas Android de auto, usen las mismas reglas y las mantengamos entre todos.

> **Léelo antes de usarlo.** Estas reglas cambian cada semestre, cada año o por decreto. Los datos pueden estar desactualizados o equivocados, y las zonas son **aproximadas**. Es una ayuda para recordar, no asesoría legal: confirma siempre con la autoridad de tránsito de tu ciudad.

## Cobertura

| Ciudad | País | Confianza | Vigencia guardada |
|---|---|---|---|
| Santiago (Región Metropolitana) | Chile | oficial (MTT) | hasta 2026-12-31 |
| Bogotá | Colombia | oficial (Alcaldía) | sin fecha: confirmar cada semestre |
| Medellín y Valle de Aburrá | Colombia | prensa | 2026-08-03 a 2026-12-31 |
| Cali | Colombia | prensa | 2026-07-01 a 2026-12-31 |
| Quito | Ecuador | prensa | sin fecha: se anuncia un cambio a fines de 2026 |

«Oficial» significa que se leyó en un sitio de la autoridad. «Prensa» significa que viene de medios de comunicación que citan a la autoridad. Detalle de cada fuente y lo que **no** se pudo comprobar en [docs/FUENTES.md](docs/FUENTES.md).

## Qué incluye

```
data/restricciones.json      las reglas (CC0)
schema/…schema.json          describe cada campo, para validar el archivo
referencia/restricciones.js  implementación de referencia en JavaScript (MIT)
referencia/Restricciones.java  lo mismo en Java 7, solo org.json, para Android 4.1+ (MIT)
pruebas/casos.json           casos «ciudad + placa + fecha → resultado esperado» (CC0)
pruebas/correr.js, PruebaCasos.java   ejecutan esos casos
docs/FUENTES.md, ACTUALIZAR.md
```

## Formato de los datos

Cada ciudad trae:

- `rule`: cómo se decide quién tiene restricción. Hay tres tipos:
  - `weekday`: dígitos por día de la semana (Medellín, Cali, Quito).
  - `parity`: dígitos según si el **día del mes** es impar o par (Bogotá).
  - `classes`: distinto según la clase de vehículo (Chile: con sello verde, sin sello, inscrito desde 2011), con la opción `alwaysBanned` para quien no puede circular en la zona sin importar el dígito.
- `weekdays`, `hours` (franjas `[inicio, fin)` en minutos desde la medianoche) y `months` (temporada, o `null` si rige todo el año).
- `validFrom` y `validTo`: entre qué fechas vale la regla guardada. **Pasado `validTo`, no se debe afirmar nada.**
- `confidence`, `sources`, `checkedAt`: de dónde sale, qué tan confiable es y cuándo se revisó.
- `zone`: polígono aproximado como `[latitud, longitud]`.

## Cómo usarlo

**JavaScript**

```js
const R = require('./referencia/restricciones.js')(require('./data/restricciones.json'));
const ciudad = R.cityAt(4.65, -74.08, 'CO');             // ciudad en la que está el punto, o null
const e = R.evaluate(ciudad, { digit: 7 }, new Date());  // qué le toca hoy a esa placa
if (e.restricted && e.inHours) console.log(R.notice(ciudad, { digit: 7 }).text);
```

**Java (Android)**

```java
JSONObject datos = new JSONObject(textoDelArchivo);
JSONObject ciudad = Restricciones.cityAt(datos, lat, lon, "CO");
if (ciudad != null) {
    Restricciones.Evaluacion e = Restricciones.evaluate(ciudad, null, 7, Calendar.getInstance());
    if (e.restricted && e.inHours) { /* avisar */ }
}
```

Para Chile, pasa también la clase del vehículo: `"sello"`, `"sin"` o `"nuevo"`.

**Resultado de `evaluate`** (todos booleanos, iguales en ambos lenguajes):

| Campo | Significado |
|---|---|
| `expired` | la regla guardada ya venció (`validTo`): no afirmar nada, mandar a revisar la fuente |
| `notStarted` | la regla guardada empieza más adelante (`validFrom`) |
| `inSeason` | el mes está dentro de la temporada (si no tiene temporada, siempre `true`) |
| `restricted` | hoy esa placa tiene restricción (regla vigente, en temporada y en un día que rige) |
| `inHours` | la hora actual cae dentro de una franja con restricción |
| `remaining` | todavía queda alguna franja hoy (ahora o más tarde) |

Un aviso razonable al entrar a la ciudad: `restricted && remaining`, y destacarlo cuando además `inHours`. No avisar una y otra vez: una vez por entrada a la ciudad. Los festivos no están incluidos: la app no los conoce.

## Mantenerlo al día sin reinstalar

El archivo de datos es independiente del código. Lo recomendable es publicarlo en un lugar fijo y que la app lo baje al abrirse (si ya consultas un archivo de versión por internet, sirve el mismo mecanismo), guardando la última copia buena en el equipo y comparando `dataVersion`. Proceso de actualización en [docs/ACTUALIZAR.md](docs/ACTUALIZAR.md).

## Probar

```bash
node pruebas/correr.js
```

```bash
javac -cp json.jar -d salida referencia/Restricciones.java pruebas/PruebaCasos.java
java -cp salida:json.jar PruebaCasos data/restricciones.json pruebas/casos.json
```

(`json.jar` es la librería [org.json](https://github.com/stleary/JSON-java); en Android ya viene incluida.) Si escribes tu propia implementación, debe dar **exactamente** lo que dice `pruebas/casos.json`.

## Límites conocidos

- Zonas dibujadas a mano y con pocos puntos: cerca de los bordes puede avisar o no avisar de más.
- No incluye festivos, episodios de preemergencia o emergencia ambiental, pico y placa solidario, vías exentas, ni reglas de motos o taxis.
- Chile: el aviso de «sin sello verde» no puede distinguir el anillo Américo Vespucio; se da al entrar a Santiago.
- Varias ciudades vienen de prensa y no de la autoridad. Si puedes confirmarlas con la fuente oficial, sería un gran aporte.

## Cómo colaborar

Para corregir una regla o sumar una ciudad: edita `data/restricciones.json` siguiendo el esquema, cita la **fuente** (ojalá oficial) y la fecha en que la leíste, agrega casos en `pruebas/casos.json` y comprueba que pasen en las dos implementaciones. Más detalle en [docs/ACTUALIZAR.md](docs/ACTUALIZAR.md).

## Licencias

- **Código**: MIT (ver `LICENSE`).
- **Datos**: CC0 1.0, dominio público (ver `LICENSE-DATOS`).

Ambas son compatibles con proyectos GPL-3.0.
