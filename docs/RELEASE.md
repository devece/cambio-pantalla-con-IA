# Publicar una versión

Una versión es una etiqueta `vX.Y.Z` en `main`. Al crearla, el workflow **Release** (`.github/workflows/release.yml`) compila el APK firmado y lo publica en **GitHub Releases** con las notas de `CHANGELOG.md`.

Distinto del workflow **Android**, que en cada cambio actualiza `app-latest` (la última compilación, de prueba o firmada) y no cuenta como versión.

## Una sola vez
- Crea la clave de firma y los cuatro secretos de GitHub: `docs/FIRMA_APK.md`. Sin ellos el Release se detiene con un mensaje claro: una versión publicada siempre va firmada, y **debe ser siempre la misma clave** o las radios no podrán actualizar encima.

## Cada versión
1. **Elige el número** (SemVer): parche `1.2.3 → 1.2.4` si solo arreglas; menor `1.3.0` si agregas algo; mayor `2.0.0` si cambia algo grande. El menor y el parche van de 0 a 99.
2. **Edita `CHANGELOG.md`**: cambia `## [Sin publicar]` por `## [X.Y.Z] - AAAA-MM-DD` y deja arriba un `## [Sin publicar]` vacío. Escribe lo que nota quien usa la app, no lo que cambió en el código.
3. **Une todo a `main`** (por PR).
4. **Crea la etiqueta desde `main`:**
   ```bash
   git checkout main && git pull
   git tag vX.Y.Z
   git push origin vX.Y.Z
   ```
5. Mira **Actions → Release**. Cuando termina, en **Releases** quedan `tablero-X.Y.Z.apk` y su huella `.sha256`, con las notas.

## Qué revisa el workflow antes de publicar
- La etiqueta tiene formato `vX.Y.Z` y `CHANGELOG.md` trae esa versión con cambios.
- El commit está en `main`.
- Están los cuatro secretos de firma.
- Pasan las pruebas del sitio y de las reglas de la app.
- El APK trae la versión de la etiqueta, está firmado con la clave fija (no la de prueba) y lleva sus datos y la pantalla de inicio.

Si algo falla no se publica nada. Para repetir: corrige, y si ya habías subido la etiqueta, bórrala y créala de nuevo (`git push --delete origin vX.Y.Z`, luego `git tag -d vX.Y.Z`).

## Cómo se numera el APK
- `versionName` es la etiqueta sin la `v` (`1.2.3`).
- `versionCode` (lo que Android compara para actualizar) es `mayor×10000 + menor×100 + parche`: `1.2.3 → 10203`. Siempre crece con la versión.
- Para ver el cálculo: `node scripts/changelog.js code 1.2.3`. Para ver las notas que se publicarán: `node scripts/changelog.js notes 1.2.3`.
