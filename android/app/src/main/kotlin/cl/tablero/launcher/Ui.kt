package cl.tablero.launcher

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable

/** Un atajo a una app instalada en la radio. */
data class AppInfo(val label: String, val pkg: String, val icon: Int)

/** Colores y piezas comunes a las pantallas. Paleta del tema "Ruta" (verde de señalética vial). */
object Ui {
    val BG = Color.parseColor("#0a3324")
    val BG1 = Color.parseColor("#12694a")
    val BG2 = Color.parseColor("#08301f")
    val CARD = Color.argb(26, 255, 255, 255)
    val LINE = Color.argb(51, 255, 255, 255)
    val TEXT = Color.parseColor("#f5f7f2")
    val SUB = Color.parseColor("#b9d3c3")
    val ACCENT = Color.parseColor("#f5c400")
    val ACCENT_INK = Color.parseColor("#231c00")
    val ALERT = Color.parseColor("#d2352b")
    val OK = Color.parseColor("#3fb68b")
    val WARN = Color.parseColor("#ff8a7a")
    val TRACK = Color.argb(46, 255, 255, 255)

    /** Tipografía de números y títulos: condensada, como en el sitio. */
    val DISPLAY: Typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)

    /** id de app (catalog.js) -> nombre, paquete de Android e ícono. */
    val KNOWN_APPS: Map<String, AppInfo> = linkedMapOf(
        "maps" to AppInfo("Mapas", "com.google.android.apps.maps", R.drawable.ic_maps),
        "waze" to AppInfo("Waze", "com.waze", R.drawable.ic_waze),
        "spotify" to AppInfo("Spotify", "com.spotify.music", R.drawable.ic_spotify),
        "ytmusic" to AppInfo("YouTube Music", "com.google.android.apps.youtube.music", R.drawable.ic_ytmusic),
        "youtube" to AppInfo("YouTube", "com.google.android.youtube", R.drawable.ic_youtube),
        "whatsapp" to AppInfo("WhatsApp", "com.whatsapp", R.drawable.ic_whatsapp),
    )

    val WEEKDAYS: List<Pair<String, String>> = listOf(
        "lun" to "Lunes", "mar" to "Martes", "mie" to "Miércoles", "jue" to "Jueves", "vie" to "Viernes",
    )

    val CAR_TYPES: List<Pair<String, String>> = listOf("auto" to "Auto", "suv" to "SUV", "camioneta" to "Camioneta")

    val CAR_COLORS: List<String> = listOf(
        "#b3261e", "#1f5fa8", "#1e7a4f", "#e6e6e6", "#2b2b2b", "#f5c400", "#7a3fb0", "#e8742a",
    )

    fun dp(context: Context, v: Int): Int = (v * context.resources.displayMetrics.density).toInt()

    /** Tarjeta de vidrio: relleno translúcido y borde fino. */
    fun glass(context: Context, radiusDp: Int = 22): GradientDrawable = GradientDrawable().apply {
        setColor(CARD)
        cornerRadius = dp(context, radiusDp).toFloat()
        setStroke(dp(context, 1), LINE)
    }

    fun rounded(context: Context, color: Int, radiusDp: Int): GradientDrawable = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(context, radiusDp).toFloat()
    }
}
