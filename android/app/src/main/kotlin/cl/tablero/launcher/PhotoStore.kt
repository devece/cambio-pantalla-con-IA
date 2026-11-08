package cl.tablero.launcher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File

/**
 * La foto del auto que el usuario eligió en Ajustes. Se achica y se guarda dentro de la app (no sale de la radio).
 * Si es un PNG ya sin fondo (esquinas transparentes) se muestra recortada; si no, como foto normal.
 */
class PhotoStore(context: Context) {
    private val appContext = context.applicationContext
    private val dir = File(appContext.filesDir, "mycar")

    private fun file(): File? = listOf("car.png", "car.jpg").map { File(dir, it) }.firstOrNull { it.exists() }

    fun has(): Boolean = file() != null

    /** Dirección file:// de la foto para la pantalla de inicio, o null si no hay. */
    fun url(): String? = file()?.let { "file://" + it.absolutePath }

    /** ¿La foto ya viene sin fondo? */
    fun isCut(): Boolean = file()?.name == "car.png"

    fun clear() {
        dir.deleteRecursively()
    }

    /** Lee, achica y guarda la foto. Bloquea el hilo: usar fuera del hilo principal. Devuelve null si salió bien, o el motivo. */
    fun save(uri: Uri): String? {
        try {
            val resolver = appContext.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) } ?: return "No se pudo abrir la foto."
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return "Ese archivo no es una imagen."
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) } ?: return "No se pudo leer la foto."
            val longSide = maxOf(decoded.width, decoded.height)
            val bitmap = if (longSide > MAX_SIDE) {
                val scale = MAX_SIDE.toFloat() / longSide
                Bitmap.createScaledBitmap(decoded, (decoded.width * scale).toInt().coerceAtLeast(1), (decoded.height * scale).toInt().coerceAtLeast(1), true)
            } else {
                decoded
            }
            val cut = looksCut(bitmap)
            clear()
            dir.mkdirs()
            val target = File(dir, if (cut) "car.png" else "car.jpg")
            target.outputStream().use { out ->
                if (cut) bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) else bitmap.compress(Bitmap.CompressFormat.JPEG, 88, out)
            }
            return null
        } catch (e: SecurityException) {
            return "La radio no dejó leer esa foto."
        } catch (e: OutOfMemoryError) {
            return "La foto es demasiado grande para esta radio."
        } catch (e: Exception) {
            return "No se pudo guardar la foto."
        }
    }

    /** Un PNG sin fondo tiene transparentes las cuatro esquinas. */
    private fun looksCut(b: Bitmap): Boolean {
        if (!b.hasAlpha()) return false
        val x = b.width - 1
        val y = b.height - 1
        return listOf(b.getPixel(0, 0), b.getPixel(x, 0), b.getPixel(0, y), b.getPixel(x, y)).all { (it ushr 24) < 16 }
    }

    private companion object {
        const val MAX_SIDE = 1600
    }
}
