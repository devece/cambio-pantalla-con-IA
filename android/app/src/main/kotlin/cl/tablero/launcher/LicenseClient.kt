package cl.tablero.launcher

import android.content.Context
import android.provider.Settings
import cl.tablero.core.ActivationResponse
import cl.tablero.core.ActivationResult
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Llama a POST /api/license. Bloquea el hilo: usar siempre fuera del hilo principal. */
object LicenseClient {
    /**
     * Identificador de la radio. El servidor solo guarda un hash.
     * Ojo: algunas radios baratas repiten el mismo ANDROID_ID en todas las unidades; si eso pasa en las
     * radios reales, hay que cambiar a otro identificador (por ejemplo uno generado y guardado al instalar).
     */
    fun deviceId(context: Context): String =
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID).orEmpty()

    fun activate(code: String, deviceId: String): ActivationResult {
        if (deviceId.length < 8) return ActivationResult.Unavailable("No pudimos identificar esta radio.")

        val conn = URL(BuildConfig.API_BASE_URL + "/api/license").openConnection() as HttpURLConnection
        return try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            val payload = JSONObject().put("code", code).put("deviceId", deviceId).toString()
            conn.outputStream.use { it.write(payload.toByteArray()) }

            val status = conn.responseCode
            val stream = if (status in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }
            ActivationResponse.parse(status, body)
        } catch (e: IOException) {
            ActivationResult.Unavailable("Sin conexión. Revisa el internet de la radio e intenta de nuevo.")
        } finally {
            conn.disconnect()
        }
    }
}
