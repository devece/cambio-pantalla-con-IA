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
)

/**
 * Explica los códigos de falla en palabras simples, con el catálogo de `public/shared/obd-codes.json`.
 * Un código que no está en el catálogo igual recibe una explicación general según su letra y su primer dígito
 * (por ejemplo, los `P1xxx` son propios de cada marca), nunca un texto vacío ni inventado.
 */
class ObdCodes private constructor(
    private val codes: Map<String, Pair<String, String>>,
    private val generic: Map<String, String>,
) {
    val size: Int get() = codes.size

    fun describe(code: String): CodeInfo {
        val c = code.trim().uppercase()
        codes[c]?.let { return CodeInfo(c, it.first, it.second, known = true) }
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
                    o.getString("sistema") to o.getString("texto")
                },
                generic.keys().asSequence().associateWith { generic.getString(it) },
            )
        }
    }
}
