# Firma fija del APK (una sola vez)

Con una firma propia y fija, Play Protect desconfía menos y las actualizaciones se instalan encima de la versión anterior (hoy hay que desinstalar primero).

1. En tu computador (necesitas Java), crea la clave. **Guárdala y respáldala: si la pierdes no podrás actualizar las radios ya instaladas.**
   ```bash
   keytool -genkeypair -v -keystore tablero.jks -alias tablero -keyalg RSA -keysize 4096 -validity 10000
   ```
2. Conviértela a texto: `base64 -w0 tablero.jks` (en Windows PowerShell: `[Convert]::ToBase64String([IO.File]::ReadAllBytes("tablero.jks"))`).
3. En GitHub: Settings > Secrets and variables > Actions > New repository secret. Crea cuatro:
   - `TB_KEYSTORE_B64`: el texto del paso 2
   - `TB_KEYSTORE_PASSWORD`: la contraseña del almacén
   - `TB_KEY_ALIAS`: `tablero`
   - `TB_KEY_PASSWORD`: la contraseña de la clave
4. Haz push o ejecuta el workflow "Android": publicará el APK firmado en `app-latest`.

Nunca subas `tablero.jks` ni las contraseñas al repositorio.
