package cl.tablero.launcher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File

/**
 * El fondo de pantalla propio: un mosaico del auto, una foto o un logo. Viene dentro de la APK que se descarga del sitio, o se elige
 * en Ajustes. Se guarda dentro de la app (no sale de la radio). El modo («photo» llena la pantalla, «logo» va centrado y suave,
 * «badge» es un logo chico en la barra de arriba) viaja en el nombre del archivo: bg.photo.jpg, bg.logo.png…
 */
class BgStore(context: Context) {
    private val dir = File(context.applicationContext.filesDir, "mybg")
    private val appContext = context.applicationContext

    private fun file(): File? = dir.listFiles()?.firstOrNull { it.name.startsWith("bg.") }

    fun has(): Boolean = file() != null

    /** Dirección file:// del fondo para la pantalla de inicio, o null si no hay. */
    fun url(): String? = file()?.let { "file://" + it.absolutePath }

    /** «photo», «logo» o «badge». */
    fun mode(): String = file()?.name?.split('.')?.getOrNull(1)?.takeIf { it in MODES } ?: "photo"

    fun clear() {
        dir.deleteRecursively()
    }

    /** Guarda el fondo que ya viene listo (el de la APK descargada del sitio). Devuelve false si los bytes no son una imagen. */
    fun saveBytes(bytes: ByteArray, mode: String): Boolean {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return false
        val png = bytes.size > 4 && bytes[0] == 0x89.toByte() && bytes[1] == 'P'.code.toByte()
        return try {
            clear()
            dir.mkdirs()
            File(dir, "bg.${if (mode in MODES) mode else "photo"}.${if (png) "png" else "jpg"}").writeBytes(bytes)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Lee, achica y guarda una imagen elegida en Ajustes. Con transparencia (un logo) queda como «logo»; si no, como «photo».
     * Bloquea el hilo: usar fuera del hilo principal. Devuelve null si salió bien, o el motivo.
     */
    fun save(uri: Uri): String? {
        try {
            val resolver = appContext.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) } ?: return "No se pudo abrir la imagen."
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return "Ese archivo no es una imagen."
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) } ?: return "No se pudo leer la imagen."
            val longSide = maxOf(decoded.width, decoded.height)
            val bitmap = if (longSide > MAX_SIDE) {
                val scale = MAX_SIDE.toFloat() / longSide
                Bitmap.createScaledBitmap(decoded, (decoded.width * scale).toInt().coerceAtLeast(1), (decoded.height * scale).toInt().coerceAtLeast(1), true)
            } else {
                decoded
            }
            val alpha = bitmap.hasAlpha() && hasTransparency(bitmap)
            clear()
            dir.mkdirs()
            val target = File(dir, if (alpha) "bg.logo.png" else "bg.photo.jpg")
            target.outputStream().use { out ->
                if (alpha) bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) else bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
            }
            return null
        } catch (e: SecurityException) {
            return "La radio no dejó leer esa imagen."
        } catch (e: OutOfMemoryError) {
            return "La imagen es demasiado grande para esta radio."
        } catch (e: Exception) {
            return "No se pudo guardar la imagen."
        }
    }

    /** ¿Alguna esquina o el centro es transparente? (un logo sin fondo; basta con mirar unos puntos). */
    private fun hasTransparency(b: Bitmap): Boolean {
        val x = b.width - 1
        val y = b.height - 1
        return listOf(b.getPixel(0, 0), b.getPixel(x, 0), b.getPixel(0, y), b.getPixel(x, y)).any { (it ushr 24) < 250 }
    }

    private companion object {
        const val MAX_SIDE = 1280
        val MODES = setOf("photo", "logo", "badge")
    }
}
