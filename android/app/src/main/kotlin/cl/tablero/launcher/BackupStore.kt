package cl.tablero.launcher

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import cl.tablero.core.BackupData
import cl.tablero.core.BackupResult
import cl.tablero.core.SettingsBackup
import java.io.File

/**
 * Copia de seguridad y restauración de lo que el usuario configuró (ajustes, kilometraje, mantenciones, foto y fondo), en un archivo
 * de texto que se guarda en la memoria de la radio (y en Descargas/Tablero cuando Android lo permite) para pasarlo a una USB.
 * Las reglas del formato están en `SettingsBackup` (núcleo, con pruebas). Bloquea el hilo: usar fuera del hilo principal.
 */
class BackupStore(context: Context) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Dónde quedó una copia: la carpeta privada de la app y, si se pudo, la carpeta pública de Descargas. */
    class Saved(val name: String, val appFile: File?, val publicPath: String?)

    /** Carpeta de copias dentro de la app (no pide permisos). En la radio: Android/data/cl.tablero.launcher/files/copias. */
    fun appDir(): File? = app.getExternalFilesDir("copias")

    private fun publicDir(): File = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Tablero")

    /** Arma el texto de la copia con lo que hay ahora en la radio. */
    fun create(): String {
        val files = LinkedHashMap<String, ByteArray>()
        var total = 0
        for (dirName in SettingsBackup.FILE_DIRS) {
            File(app.filesDir, dirName).listFiles()?.filter { it.isFile }?.forEach { f ->
                val path = "$dirName/${f.name}"
                if (!SettingsBackup.isSafePath(path) || total + f.length() > SettingsBackup.MAX_FILES_BYTES) return@forEach
                runCatching { f.readBytes() }.getOrNull()?.let {
                    files[path] = it
                    total += it.size
                }
            }
        }
        val version = runCatching { app.packageManager.getPackageInfo(app.packageName, 0).versionName }.getOrNull() ?: ""
        return SettingsBackup.write(BackupData(prefs.all.filterValues { it != null }.mapValues { it.value!! }, files, version, System.currentTimeMillis()))
    }

    /** Guarda una copia nueva. Devuelve dónde quedó, o null si no se pudo escribir en ninguna parte. */
    fun save(): Saved? {
        val text = create()
        val name = SettingsBackup.fileName(System.currentTimeMillis())
        val inApp = appDir()?.let { dir ->
            runCatching {
                dir.mkdirs()
                File(dir, name).also { it.writeText(text) }
            }.getOrNull()
        }
        val publicPath = savePublic(name, text)
        return if (inApp == null && publicPath == null) null else Saved(name, inApp, publicPath)
    }

    private fun savePublic(name: String, text: String): String? = try {
        if (Build.VERSION.SDK_INT >= 29) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
                put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/Tablero")
            }
            val uri = app.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            if (uri == null) null else {
                app.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) }
                "Descargas/Tablero/$name"
            }
        } else {
            val dir = publicDir()
            dir.mkdirs()
            File(dir, name).writeText(text)
            "Descargas/Tablero/$name"
        }
    } catch (e: Exception) {
        null
    }

    /** Copias que se pueden restaurar, de la más nueva a la más vieja (carpeta de la app y Descargas/Tablero, si se pueden leer). */
    fun available(): List<File> {
        val dirs = listOfNotNull(appDir(), runCatching { publicDir() }.getOrNull())
        return dirs.flatMap { d ->
            runCatching {
                d.listFiles { f -> f.isFile && f.name.startsWith(SettingsBackup.PREFIX) && f.name.endsWith(SettingsBackup.EXTENSION) }?.toList()
            }.getOrNull().orEmpty()
        }.sortedByDescending { it.lastModified() }
    }

    /** Restaura desde el texto de una copia. Devuelve null si salió bien, o el motivo. Reemplaza los ajustes y los archivos de la copia. */
    fun restore(text: String): String? {
        val data = when (val r = SettingsBackup.read(text)) {
            is BackupResult.Fail -> return r.reason
            is BackupResult.Ok -> r.data
        }
        if (data.settings.isEmpty()) return "La copia no trae ajustes."
        return try {
            // Los archivos primero: si falla uno, los ajustes anteriores quedan como estaban.
            val folders = data.files.keys.map { it.substringBefore('/') }.toSet()
            folders.forEach { File(app.filesDir, it).deleteRecursively() }
            data.files.forEach { (path, bytes) ->
                val target = File(app.filesDir, path)
                target.parentFile?.mkdirs()
                target.writeBytes(bytes)
            }
            val e = prefs.edit().clear()
            data.settings.forEach { (k, v) ->
                when (v) {
                    is Boolean -> e.putBoolean(k, v)
                    is Int -> e.putInt(k, v)
                    is Long -> e.putLong(k, v)
                    is Float -> e.putFloat(k, v)
                    is String -> e.putString(k, v)
                }
            }
            // Los metros sueltos del GPS son de antes de la copia: empiezan de cero (clear() ya los borró).
            if (e.commit()) null else "No se pudo restaurar la copia."
        } catch (ex: Exception) {
            "No se pudo restaurar la copia."
        }
    }

    private companion object {
        const val PREFS = "tablero_settings"
    }
}
