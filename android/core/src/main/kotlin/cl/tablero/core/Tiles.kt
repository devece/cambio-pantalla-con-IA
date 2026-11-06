package cl.tablero.core

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.tan

/** Un cuadro del mapa: estilo (claro u oscuro), zoom y posición. */
data class TileId(val style: String, val z: Int, val x: Int, val y: Int)

/**
 * Matemática de los cuadros de mapa (los mismos que dibuja la pantalla en js/preview.js) para guardarlos en la radio
 * y poder verlos sin internet. Solo lógica pura: no toca archivos ni red.
 */
object Tiles {
    const val ZOOM = 15
    val STYLES = listOf("dark_all", "light_all")

    private val PATH = Regex("^/(dark_all|light_all)/(\\d{1,2})/(\\d{1,8})/(\\d{1,8})\\.png$")

    fun tileX(lon: Double, z: Int = ZOOM): Int = floor((lon + 180.0) / 360.0 * (1 shl z)).toInt()

    fun tileY(lat: Double, z: Int = ZOOM): Int {
        val r = lat * PI / 180.0
        return floor((1.0 - ln(tan(r) + 1.0 / cos(r)) / PI) / 2.0 * (1 shl z)).toInt()
    }

    /** Los cuadros dentro de un cuadrado de (2 * radio + 1) por lado, centrado en el punto. */
    fun around(lat: Double, lon: Double, radius: Int, z: Int = ZOOM): List<Pair<Int, Int>> {
        val n = 1 shl z
        val cx = tileX(lon, z)
        val cy = tileY(lat, z)
        val out = ArrayList<Pair<Int, Int>>()
        for (dy in -radius..radius) {
            for (dx in -radius..radius) {
                val x = ((cx + dx) % n + n) % n
                val y = cy + dy
                if (y in 0 until n) out.add(Pair(x, y))
            }
        }
        return out
    }

    /** Nombre de archivo del cuadro dentro de la carpeta de mapas. */
    fun key(t: TileId): String = "${t.style}/${t.z}/${t.x}/${t.y}.png"

    fun url(t: TileId): String = "https://a.basemaps.cartocdn.com/${t.style}/${t.z}/${t.x}/${t.y}.png"

    /**
     * Interpreta la ruta de una petición de cuadro (por ejemplo /dark_all/15/9641/15960.png).
     * Devuelve null si no es exactamente eso: así nada raro puede convertirse en un nombre de archivo.
     */
    fun parse(path: String): TileId? {
        val m = PATH.matchEntire(path) ?: return null
        val z = m.groupValues[2].toInt()
        val x = m.groupValues[3].toLong()
        val y = m.groupValues[4].toLong()
        if (z !in 0..19) return null
        val n = 1L shl z
        if (x >= n || y >= n) return null
        return TileId(m.groupValues[1], z, x.toInt(), y.toInt())
    }
}
