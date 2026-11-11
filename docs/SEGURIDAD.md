# Seguridad: secretos, límites y respaldos

Qué se protege, qué hace el código y qué pasos hay que hacer a mano (el código no puede tocar la configuración de Vercel, GitHub ni Anthropic).

## 1. Variables de Vercel que ya no se usan

Estas cuatro variables del proyecto `tablero` (producción) no aparecen en ningún archivo del repositorio, y vienen de un servidor de pedidos y licencias que ya no existe:

| Variable | Qué era |
|---|---|
| `SUPABASE_SERVICE_ROLE_KEY` | Clave con acceso total a la base de Supabase |
| `ADMIN_TOKEN` | Clave del panel de pedidos (retirado) |
| `LICENSE_PEPPER` | Sal para los códigos de licencia (retirados) |
| `SUPABASE_URL` | Dirección de esa base (la que usan los contadores es otra: `STATS_SUPABASE_URL`) |

**Por qué conviene retirarlas:** el proyecto de Supabase se comparte con otras aplicaciones. Con la clave `service_role` en el Vercel de Tablero, un compromiso de este proyecto daría acceso a datos de otras.

**Antes de borrar** (ya comprobado el 2026-11-11, repetir si pasó tiempo):
- `git grep -nw SUPABASE_SERVICE_ROLE_KEY ADMIN_TOKEN LICENSE_PEPPER SUPABASE_URL` no devuelve nada.
- Son variables del propio proyecto (no variables compartidas del equipo), así que borrarlas no afecta a los demás proyectos.

**Cómo** (Vercel → proyecto `tablero` → Settings → Environment Variables), de una en una y empezando por `SUPABASE_URL`:
1. Borrar la variable.
2. En el siguiente despliegue, comprobar `https://tablero-pearl.vercel.app/api/stats` (200 con números) y `https://tablero-pearl.vercel.app/app` (descarga la APK).

> Las variables «Sensitive» no se pueden leer de vuelta: una vez borradas no se pueden restaurar. Como nada las usa, no hace falta.

**Rotar la clave de servicio de Supabase:** no corresponde solo por este motivo (no hay evidencia de que se haya filtrado). Sí corresponde si alguna vez se pegó en un chat, un log o un documento. Ojo: otras aplicaciones del mismo proyecto de Supabase pueden usar esa misma clave; antes de rotarla hay que cambiarla también en ellas.

## 2. Clave de firma de la app (Android)

- La APK pública se firma con una clave fija; quien la tenga puede publicar «actualizaciones» que los teléfonos aceptan. Perderla impide actualizar lo ya instalado.
- **Respaldo:** guarda `tablero.jks` y su contraseña en dos lugares cifrados distintos, fuera de GitHub. Los secretos de GitHub no se pueden leer de vuelta.
- **En el código:** `android.yml` solo entrega los secretos `TB_*` en la rama `main` (en cualquier otra rama se compila la APK de prueba, sin firma fija). El trabajo que compila es de solo lectura; el que publica (`publish`) es el único con permiso de escribir y no recibe ningún secreto de firma. `tests/workflow-security.test.js` lo comprueba.
- **Límite del código:** una rama con un workflow modificado podría pedir los secretos igualmente mientras estén a nivel de repositorio. Para cerrarlo del todo hay que usar un *Environment* de GitHub:
  1. Settings → Environments → New environment → `firma`.
  2. «Deployment branches and tags» → Selected branches and tags → agregar `main` y `v*`.
  3. (Opcional) «Required reviewers»: tú, para aprobar cada firma.
  4. Mover ahí los cuatro secretos (`TB_KEYSTORE_B64`, `TB_KEYSTORE_PASSWORD`, `TB_KEY_ALIAS`, `TB_KEY_PASSWORD`) y borrarlos del nivel de repositorio.
  5. Agregar `environment: firma` a los trabajos que firman (hoy es el trabajo `apk` en `main` y el de `release.yml`; habrá que separar la firma en su propio trabajo para que las ramas sigan compilando sin el entorno).
- Fijar las acciones de terceros por SHA (en vez de `@v4`) o configurar Dependabot para `github-actions`.

## 3. IA (análisis de la captura y detección de faros)

Hoy `ANTHROPIC_API_KEY` no está en Vercel, así que los dos endpoints responden 503 y el sitio usa la alternativa manual. **Antes de activarla:**

1. En console.anthropic.com → Limits: poner un **límite de gasto mensual** a la cuenta.
2. En Vercel, agregar `AI_DAILY_MAX` (por ejemplo `50`). Sin esa variable la IA queda apagada aunque exista la clave. Es un tope por instancia (mejor esfuerzo).
3. En Vercel → Firewall → Rate limiting, una regla para `/api/analyze-radio` y `/api/detect-lights`: 10 peticiones cada 10 minutos por IP (el código ya limita a 4 y 6, pero cada instancia cuenta por su cuenta).
4. Recién entonces cargar `ANTHROPIC_API_KEY`.

`ANALYSIS_MODEL` (por defecto `claude-opus-5-5`) define el modelo. Es una decisión de costo: un modelo más económico puede bastar para ubicar faros y evaluar capturas.

## 4. Lo que el límite por IP puede y no puede

`lib/http.js` cuenta peticiones en la memoria de cada instancia serverless. Frena un abuso desde un mismo lugar, pero cada instancia cuenta aparte; el freno real contra un abuso repartido lo da el Firewall de Vercel.
