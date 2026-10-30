package cl.tablero.launcher

import android.content.Context
import android.graphics.Color

/** Colores y datos comunes a las pantallas. */
object Ui {
    val BG = Color.parseColor("#101418")
    val CARD = Color.parseColor("#1B222B")
    val TEXT = Color.WHITE
    val SUB = Color.parseColor("#B8C0CC")
    val ACCENT = Color.parseColor("#F5C400")
    val OK = Color.parseColor("#3FB68B")
    val WARN = Color.parseColor("#F26B5E")

    /** id de app (catalog.js) -> nombre en pantalla y paquete de Android. */
    val KNOWN_APPS: Map<String, Pair<String, String>> = linkedMapOf(
        "maps" to ("Mapas" to "com.google.android.apps.maps"),
        "waze" to ("Waze" to "com.waze"),
        "spotify" to ("Spotify" to "com.spotify.music"),
        "ytmusic" to ("YouTube Music" to "com.google.android.apps.youtube.music"),
        "youtube" to ("YouTube" to "com.google.android.youtube"),
        "whatsapp" to ("WhatsApp" to "com.whatsapp"),
    )

    val WEEKDAYS: List<Pair<String, String>> = listOf(
        "lun" to "Lunes", "mar" to "Martes", "mie" to "Miércoles", "jue" to "Jueves", "vie" to "Viernes",
    )

    fun dp(context: Context, v: Int): Int = (v * context.resources.displayMetrics.density).toInt()
}
