package cl.tablero.launcher

import android.content.Context
import cl.tablero.core.TileId
import cl.tablero.core.Tiles
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Guarda en la radio los cuadros del mapa que se ven, para poder verlos sin internet: los de donde ya pasaste (se guardan solos
 * al mirarlos) y los de tu zona (botón «Guardar mapa»). Cuando se llena el espacio permitido se borran los que hace más
 * tiempo no se usan.
 */
class TileCache(context: Context) {
    private val root = File(context.applicationContext.filesDir, "tiles").apply { mkdirs() }

    private fun file(t: TileId) = File(root, Tiles.key(t))

    fun has(t: TileId): Boolean = file(t).isFile

    fun read(t: TileId): ByteArray? {
        val f = file(t)
        if (!f.isFile) return null
        return try {
            val bytes = f.readBytes()
            f.setLastModified(System.currentTimeMillis()) // lo usado hace poco se conserva más tiempo
            bytes
        } catch (e: Exception) {
            null
        }
    }

    /** Baja el cuadro de internet y lo guarda. Devuelve null si no hay conexión o la respuesta no es una imagen PNG. */
    fun fetch(t: TileId): ByteArray? {
        var conn: HttpURLConnection? = null
        return try {
            conn = URL(Tiles.url(t)).openConnection() as HttpURLConnection
            conn.connectTimeout = 6_000
            conn.readTimeout = 8_000
            conn.setRequestProperty("User-Agent", USER_AGENT)
            if (conn.responseCode != 200) return null
            val bytes = conn.inputStream.use { it.readBytes() }
            if (bytes.size < 100 || bytes.size > MAX_TILE_BYTES || !isPng(bytes)) return null
            write(t, bytes)
            bytes
        } catch (e: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }

    fun getOrFetch(t: TileId): ByteArray? = read(t) ?: fetch(t)

    private fun write(t: TileId, bytes: ByteArray) {
        try {
            val f = file(t)
            f.parentFile?.mkdirs()
            val tmp = File(f.parentFile, f.name + ".tmp")
            tmp.writeBytes(bytes)
            if (!tmp.renameTo(f)) tmp.delete()
        } catch (e: Exception) {
            // sin espacio o sin permiso: el mapa igual se ve, solo que no queda guardado
        }
    }

    private fun isPng(b: ByteArray): Boolean =
        b[0] == 0x89.toByte() && b[1] == 'P'.code.toByte() && b[2] == 'N'.code.toByte() && b[3] == 'G'.code.toByte()

    /** Cuántos cuadros hay guardados y cuánto pesan. */
    fun stats(): Pair<Int, Long> {
        var n = 0
        var size = 0L
        root.walkTopDown().filter { it.isFile && it.name.endsWith(".png") }.forEach { n++; size += it.length() }
        return Pair(n, size)
    }

    /** Si se pasa del espacio permitido, borra los menos usados hasta volver a caber. */
    fun trim(maxBytes: Long = MAX_BYTES) {
        val files = root.walkTopDown().filter { it.isFile && it.name.endsWith(".png") }.toMutableList()
        var total = files.sumOf { it.length() }
        if (total <= maxBytes) return
        files.sortBy { it.lastModified() }
        for (f in files) {
            if (total <= maxBytes) break
            total -= f.length()
            f.delete()
        }
    }

    fun clear() {
        root.deleteRecursively()
        root.mkdirs()
    }

    companion object {
        const val MAX_BYTES = 250L * 1024 * 1024
        const val MAX_TILE_BYTES = 200 * 1024
        const val USER_AGENT = "TableroLauncher/1.0 (+https://tablero-pearl.vercel.app)"
    }
}
