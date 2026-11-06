package cl.tablero.core

/** Lo que se sabe de la emisora que suena: [frequency] en MHz (null si la radio no la dice) y su [name] (puede venir vacío). */
data class FmStation(val frequency: Double?, val name: String)

/**
 * La radio FM de fábrica de la radio del auto es una app de otro fabricante; no hay una API estándar para sintonizarla.
 * Lo que sí hacen muchas es publicar una sesión de medios con la emisora como "canción" (título, artista) y aceptar
 * siguiente, anterior y play/pausa. Aquí se reconoce esa app y se lee la frecuencia y el nombre de lo que dice.
 * Sin Android, para poder probarlo en cualquier PC.
 */
object FmRadio {
    const val MIN_MHZ = 87.5
    const val MAX_MHZ = 108.0

    /** Apps de radio FM de fábrica más comunes en radios de auto. */
    val PACKAGES = listOf(
        "com.nwd.radio", "com.nwd.android.radio", "com.syu.radio", "com.mediatek.fmradio",
        "com.android.fmradio", "com.caf.fmradio", "com.qualcomm.fmradio",
    )

    /** ¿Este paquete es una app de radio FM? Los conocidos, y los que se llaman "fmradio" o terminan en ".radio". */
    fun isRadioPackage(pkg: String): Boolean = pkg in PACKAGES || pkg.contains("fmradio") || pkg.endsWith(".radio")

    private val NUMBER = Regex("""(?<![\d.,])(\d{2,6})(?:[.,](\d{1,2}))?(?![\d])""")
    private val BAND_WORDS = Regex("""(?i)\b(fm\d?|am\d?|mhz|khz|radio fm)\b""")
    private val SEPARATORS = Regex("""^[\s\-–—·|:,/]+|[\s\-–—·|:,/]+$""")

    /** Frecuencia en MHz dentro de [texto], o null. Entiende "96.1", "96,1", "FM 96.1 MHz" y kHz ("96100"); descarta AM y números sueltos. */
    fun frequencyIn(text: String?): Double? {
        if (text.isNullOrBlank()) return null
        for (m in NUMBER.findAll(text)) {
            val whole = m.groupValues[1]
            val frac = m.groupValues[2]
            val value = when {
                frac.isNotEmpty() -> "$whole.$frac".toDoubleOrNull()
                whole.length == 5 || whole.length == 6 -> whole.toDoubleOrNull()?.div(1000.0) // 96100 kHz
                else -> whole.toDoubleOrNull()
            } ?: continue
            if (value in MIN_MHZ..MAX_MHZ) return value
        }
        return null
    }

    /** Emisora a partir del título y el artista que publica la radio (el nombre es lo que quede sin la frecuencia ni "FM"/"MHz"). */
    fun parse(title: String?, artist: String?): FmStation {
        val frequency = frequencyIn(title) ?: frequencyIn(artist)
        val name = listOf(title, artist)
            .map { clean(it) }
            .firstOrNull { it.isNotEmpty() }
            .orEmpty()
        return FmStation(frequency, name)
    }

    private fun clean(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        var s = NUMBER.replace(raw) { m -> if (frequencyIn(m.value) != null) "" else m.value }
        s = BAND_WORDS.replace(s, " ")
        return s.replace(Regex("""\s+"""), " ").replace(SEPARATORS, "").trim()
    }
}
