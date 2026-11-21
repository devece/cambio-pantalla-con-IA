package cl.tablero.core

import org.json.JSONObject

/** Lo que se le muestra al usuario de un código de falla. */
data class CodeInfo(
    val code: String,
    /** A qué parte del auto pertenece (por ejemplo "Encendido"). */
    val system: String,
    val text: String,
    /** False si el código no está en el catálogo y [text] es una explicación general según su letra. */
    val known: Boolean,
    /** Qué tan urgente es: 1 = se puede seguir y revisar en el próximo servicio, 2 = revisar pronto, 3 = detenerse o ir al taller ya. 0 = no se sabe. */
    val severity: Int = 0,
    /** Qué hacer (revisiones simples y cuándo ir al taller). Vacío si no hay un consejo específico. */
    val action: String = "",
)

/** Una entrada del catálogo. */
internal class Entry(val system: String, val text: String, val severity: Int, val action: String)

/**
 * Explica los códigos de falla en palabras simples, con el catálogo de `public/shared/obd-codes.json`.
 * Un código que no está en el catálogo igual recibe una explicación general según su letra y su primer dígito
 * (por ejemplo, los `P1xxx` son propios de cada marca), nunca un texto vacío ni inventado.
 */
class ObdCodes private constructor(
    private val codes: Map<String, Entry>,
    private val generic: Map<String, String>,
) {
    val size: Int get() = codes.size

    fun describe(code: String): CodeInfo {
        val c = code.trim().uppercase()
        codes[c]?.let { return CodeInfo(c, it.system, it.text, known = true, severity = it.severity, action = it.action) }
        val text = generic[c.take(2)] ?: generic[c.take(1)] ?: "Código que esta app no reconoce."
        return CodeInfo(c, systemOfLetter(c.firstOrNull()), text, known = false)
    }

    private fun systemOfLetter(letter: Char?): String = when (letter) {
        'P' -> "Motor y transmisión"
        'C' -> "Frenos y chasis"
        'B' -> "Carrocería"
        'U' -> "Comunicación"
        else -> ""
    }

    companion object {
        /** Lee `public/shared/obd-codes.json`. */
        fun parse(json: String): ObdCodes {
            val root = JSONObject(json)
            val codes = root.getJSONObject("codigos")
            val generic = root.getJSONObject("genericos")
            return ObdCodes(
                codes.keys().asSequence().associateWith { key ->
                    val o = codes.getJSONObject(key)
                    Entry(o.getString("sistema"), o.getString("texto"), o.optInt("gravedad", 0).coerceIn(0, 3), o.optString("accion", ""))
                },
                generic.keys().asSequence().associateWith { generic.getString(it) },
            )
        }
    }
}
