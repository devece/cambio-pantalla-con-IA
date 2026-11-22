package cl.tablero.core

import java.net.URLEncoder

/**
 * Mapa de Google (imagen del mapa por posición): arma la dirección que se le pide a Google a partir de lo que pide la pantalla.
 * La pantalla nunca ve la llave: la pone la app al pedir la imagen. Solo lógica pura (no toca red ni archivos).
 *
 * Se aceptan únicamente los datos que la pantalla usa (centro, zoom, tamaño, escala y noche): nada más pasa a Google,
 * así nadie puede usar la llave de la app para pedir otra cosa.
 */
object GoogleMaps {
    const val HOST = "maps.googleapis.com"
    const val PATH = "/maps/api/staticmap"
    const val ZOOM = 15
    const val SIZE = 640

    /** Estilo oscuro (de noche): fondo gris azulado, calles más claras, agua oscura. */
    private val NIGHT_STYLES = listOf(
        "element:geometry|color:0x1d2c4d",
        "element:labels.text.fill|color:0x8ec3b9",
        "element:labels.text.stroke|color:0x1a3646",
        "feature:road|element:geometry|color:0x304a7d",
        "feature:road.highway|element:geometry|color:0x2c6675",
        "feature:water|element:geometry|color:0x0e1626",
        "feature:poi|visibility:off",
        "feature:transit|visibility:off",
    )

    /** Lo que se aprueba de la petición de la pantalla. */
    data class Request(val lat: Double, val lon: Double, val night: Boolean)

    /** Lee los datos de la petición (por ejemplo `center=-33.45,-70.66&zoom=15&size=640x640&scale=2&maptype=roadmap&night=1`) o devuelve null. */
    fun parse(params: Map<String, String>): Request? {
        val center = params["center"]?.split(",") ?: return null
        if (center.size != 2) return null
        val lat = center[0].toDoubleOrNull() ?: return null
        val lon = center[1].toDoubleOrNull() ?: return null
        if (lat.isNaN() || lon.isNaN() || lat !in -85.0..85.0 || lon !in -180.0..180.0) return null
        if (params["zoom"] != ZOOM.toString()) return null
        if (params["size"] != "${SIZE}x$SIZE") return null
        if (params["scale"] != "2") return null
        if (params["maptype"] != "roadmap") return null
        val night = when (params["night"]) { null -> false; "1" -> true; else -> return null }
        return Request(lat, lon, night)
    }

    /** La dirección que se le pide a Google, con la llave de la app. */
    fun url(r: Request, key: String): String {
        val sb = StringBuilder("https://$HOST$PATH")
        sb.append("?center=").append(String.format(java.util.Locale.US, "%.5f,%.5f", r.lat, r.lon))
        sb.append("&zoom=$ZOOM&size=${SIZE}x$SIZE&scale=2&maptype=roadmap&format=png")
        if (r.night) NIGHT_STYLES.forEach { sb.append("&style=").append(URLEncoder.encode(it, "UTF-8")) }
        sb.append("&key=").append(URLEncoder.encode(key, "UTF-8"))
        return sb.toString()
    }
}
