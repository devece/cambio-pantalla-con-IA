package cl.tablero.core

import org.json.JSONObject

/** Código de licencia TBL-XXXX-XXXX-XXXX. Mismas reglas que lib/license.js del servidor. */
object LicenseCode {
    // Sin 0/O ni 1/I/L para que no se confundan al leerlos.
    private const val ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ"
    private val FORMAT = Regex("^TBL-[$ALPHABET]{4}-[$ALPHABET]{4}-[$ALPHABET]{4}$")

    /** Acepta minúsculas, espacios y sin guiones. Devuelve null si no es un código posible. */
    fun normalize(raw: String?): String? {
        val s = (raw ?: "").uppercase().filter { it in 'A'..'Z' || it in '0'..'9' }
        if (!s.startsWith("TBL") || s.length != 15) return null
        val body = s.substring(3)
        val code = "TBL-${body.substring(0, 4)}-${body.substring(4, 8)}-${body.substring(8, 12)}"
        return if (FORMAT.matches(code)) code else null
    }
}

sealed interface ActivationResult {
    /** El servidor aceptó el código: la radio queda en este nivel. */
    data class Activated(val tier: Tier, val design: String? = null) : ActivationResult

    /** El código no sirve (no existe, ya está en otra radio...). Reintentar no ayuda. */
    data class Rejected(val message: String) : ActivationResult

    /** Problema temporal (sin internet, servidor caído, demasiados intentos). Se puede reintentar. */
    data class Unavailable(val message: String) : ActivationResult
}

/** Interpreta la respuesta de POST /api/license. */
object ActivationResponse {
    fun parse(httpStatus: Int, body: String?): ActivationResult {
        val json = try {
            JSONObject(body ?: "")
        } catch (e: Exception) {
            null
        }

        if (httpStatus == 200 && json != null && json.optBoolean("ok")) {
            val tier = Tier.fromId(json.optString("plan"))?.takeIf { it != Tier.FREE }
            // Lo que el cliente armó en el sitio (configuración y enlaces a su foto); null si el código se creó a mano.
            val design = json.optJSONObject("design")?.toString()
            return if (tier != null) ActivationResult.Activated(tier, design)
            else ActivationResult.Unavailable("Respuesta incompleta del servidor. Intenta de nuevo.")
        }

        val message = json?.optString("error")?.takeIf { it.isNotBlank() }
        return when (httpStatus) {
            400, 404, 409 -> ActivationResult.Rejected(message ?: "Código no válido.")
            else -> ActivationResult.Unavailable(message ?: "No pudimos verificar el código. Intenta de nuevo.")
        }
    }
}
