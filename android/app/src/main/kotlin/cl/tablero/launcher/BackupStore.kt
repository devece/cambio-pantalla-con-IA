package cl.tablero.launcher

import android.content.ContentValues
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import cl.tablero.core.BackupData
import cl.tablero.core.BackupResult
import cl.tablero.core.SettingsBackup
import java.io.File

/** Crea, guarda, lista y restaura copias de seguridad. Bloquea el hilo: llamar desde un hilo aparte. */
class BackupStore(context: Context) {
    class Saved(val name: String, val appFile: File?, val publicPath: String?)

    private val ctx: Context = context.applicationContext ?: context

    private companion object {
        const val PREFS = "tablero_settings"
        const val SUBCARPETA_PUBLICA = "Tablero"
        const val SUBCARPETA_APP = "copias"
        const val MSG_FALLO = "No se pudo restaurar la copia."
    }

    private fun prefs(): SharedPreferences = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun appDir(): File? = try {
        ctx.getExternalFilesDir(null)?.let { File(it, SUBCARPETA_APP) }
    } catch (_: Throwable) {
        null
    }

    private fun carpetaPublicaAntigua(): File? = try {
        File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), SUBCARPETA_PUBLICA)
    } catch (_: Throwable) {
        null
    }

    private fun versionApp(): String {
        val nombre = try {
            ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName
        } catch (_: Throwable) {
            null
        }
        return "${nombre ?: "?"} (${BuildConfig.GIT_COMMIT})"
    }

    fun create(): String {
        val ajustes = LinkedHashMap<String, Any>()
        try {
            for ((k, v) in prefs().all) if (v != null) ajustes[k] = v
        } catch (_: Throwable) {
        }
        val archivos = LinkedHashMap<String, ByteArray>()
        var usado = 0L
        try {
            for (dir in SettingsBackup.FILE_DIRS) {
                val lista = File(ctx.filesDir, dir).listFiles() ?: continue
                for (f in lista.sortedBy { it.name }) {
                    try {
                        val ruta = "$dir/${f.name}"
                        if (!f.isFile || !SettingsBackup.isSafePath(ruta)) continue
                        if (usado + f.length() > SettingsBackup.MAX_FILES_BYTES) continue
                        val bytes = f.readBytes()
                        if (usado + bytes.size > SettingsBackup.MAX_FILES_BYTES) continue
                        usado += bytes.size
                        archivos[ruta] = bytes
                    } catch (_: Throwable) {
                    }
                }
            }
        } catch (_: Throwable) {
        }
        return SettingsBackup.write(BackupData(ajustes, archivos, versionApp(), System.currentTimeMillis()))
    }

    fun save(): Saved? {
        return try {
            val ahora = System.currentTimeMillis()
            val nombre = SettingsBackup.fileName(ahora)
            val texto = create()
            val bytes = texto.toByteArray(Charsets.UTF_8)

            var archivoApp: File? = null
            try {
                val dir = appDir()
                if (dir != null && (dir.isDirectory || dir.mkdirs())) {
                    val f = File(dir, nombre)
                    f.writeBytes(bytes)
                    archivoApp = f
                }
            } catch (_: Throwable) {
            }

            val rutaPublica = try {
                guardarPublico(nombre, bytes)
            } catch (_: Throwable) {
                null
            }

            if (archivoApp == null && rutaPublica == null) null else Saved(nombre, archivoApp, rutaPublica)
        } catch (_: Throwable) {
            null
        }
    }

    private fun guardarPublico(nombre: String, bytes: ByteArray): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val relativa = Environment.DIRECTORY_DOWNLOADS + "/" + SUBCARPETA_PUBLICA
            val valores = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, nombre)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativa)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val resolver = ctx.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, valores) ?: return null
            try {
                val salida = resolver.openOutputStream(uri) ?: throw IllegalStateException("sin flujo")
                salida.use { it.write(bytes) }
                val listo = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
                resolver.update(uri, listo, null, null)
                return "$relativa/$nombre"
            } catch (e: Throwable) {
                try {
                    resolver.delete(uri, null, null)
                } catch (_: Throwable) {
                }
                return null
            }
        }
        val dir = carpetaPublicaAntigua() ?: return null
        if (!dir.isDirectory && !dir.mkdirs()) return null
        val f = File(dir, nombre)
        f.writeBytes(bytes)
        return f.absolutePath
    }

    fun available(): List<File> {
        val res = ArrayList<File>()
        for (dir in listOf(appDir(), carpetaPublicaAntigua())) {
            try {
                val lista = dir?.listFiles() ?: continue
                for (f in lista) {
                    if (f.isFile && f.name.startsWith(SettingsBackup.PREFIX) && f.name.endsWith(SettingsBackup.EXTENSION)) res.add(f)
                }
            } catch (_: Throwable) {
            }
        }
        return try {
            res.sortedByDescending { it.lastModified() }
        } catch (_: Throwable) {
            res
        }
    }

    fun restore(text: String): String? {
        try {
            val datos = when (val r = SettingsBackup.read(text)) {
                is BackupResult.Fail -> return r.reason
                is BackupResult.Ok -> r.data
            }
            if (datos.settings.isEmpty()) return "La copia no trae ajustes."

            // 1) Archivos: se reemplaza cada carpeta que la copia incluye.
            for (dir in SettingsBackup.FILE_DIRS) {
                val prefijo = "$dir/"
                val delaCarpeta = datos.files.filterKeys { it.startsWith(prefijo) }
                if (delaCarpeta.isEmpty()) continue
                if (!reemplazarCarpeta(dir, delaCarpeta)) return MSG_FALLO
            }

            // 2) Preferencias: se borran todas y se escriben las de la copia.
            val ed = prefs().edit()
            ed.clear()
            for ((k, v) in datos.settings) {
                when (v) {
                    is Boolean -> ed.putBoolean(k, v)
                    is Int -> ed.putInt(k, v)
                    is Long -> ed.putLong(k, v)
                    is Float -> ed.putFloat(k, v)
                    is String -> ed.putString(k, v)
                }
            }
            return if (ed.commit()) null else MSG_FALLO
        } catch (_: Throwable) {
            return MSG_FALLO
        }
    }

    /** Escribe en una carpeta temporal y solo si todo salió bien sustituye la carpeta original. */
    private fun reemplazarCarpeta(dir: String, archivos: Map<String, ByteArray>): Boolean {
        val destino = File(ctx.filesDir, dir)
        val temporal = File(ctx.filesDir, "$dir.restaurando")
        return try {
            temporal.deleteRecursively()
            if (!temporal.mkdirs()) return false
            for ((ruta, bytes) in archivos) {
                File(temporal, ruta.substringAfter('/')).writeBytes(bytes)
            }
            destino.deleteRecursively()
            if (temporal.renameTo(destino)) {
                true
            } else {
                temporal.deleteRecursively()
                false
            }
        } catch (_: Throwable) {
            try {
                temporal.deleteRecursively()
            } catch (_: Throwable) {
            }
            false
        }
    }
}
