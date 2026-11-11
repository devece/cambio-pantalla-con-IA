package cl.tablero.launcher

import android.app.Activity
import android.app.AlertDialog
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import cl.tablero.core.AppUpdate
import cl.tablero.core.DownloadCheck
import cl.tablero.core.UpdateInfo
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Aviso de actualización: mira cuál es la última versión publicada, la descarga y la instala con la confirmación del usuario.
 * Solo funciona porque todas las compilaciones se firman con la misma clave: Android instala encima y conserva los ajustes.
 * Las direcciones están fijas aquí; del version.json solo se lee el número de versión, nunca una dirección.
 */
object Updater {
    private const val BASE = "https://github.com/devece/cambio-pantalla-con-IA/releases/download/app-latest/"
    private const val ACTION_RESULT = "cl.tablero.launcher.UPDATE_RESULT"
    private const val CHECK_EVERY_MS = 6L * 60 * 60 * 1000
    private const val MIN_APK_BYTES = 500_000L

    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences("tablero_update", Context.MODE_PRIVATE)

    fun installedCode(): Int = BuildConfig.VERSION_CODE

    fun installedName(): String = BuildConfig.VERSION_NAME

    /**
     * Revisa y, si hay una versión nueva, ofrece instalarla.
     * [manual] = el usuario pulsó «Buscar actualización»: no respeta el «Después» ni el intervalo, y siempre responde algo.
     */
    fun checkAndOffer(activity: Activity, manual: Boolean) {
        // La variante de Play Store no se actualiza por su cuenta: la actualiza Play.
        if (!BuildConfig.IN_APP_UPDATE) return
        val p = prefs(activity)
        if (!manual && System.currentTimeMillis() - p.getLong("lastCheck", 0L) < CHECK_EVERY_MS) return
        Thread {
            val latest = fetchLatest()
            if (latest != null || manual) p.edit().putLong("lastCheck", System.currentTimeMillis()).apply()
            activity.runOnUiThread {
                if (activity.isFinishing || activity.isDestroyed) return@runOnUiThread
                when {
                    latest == null -> if (manual) toast(activity, "No se pudo revisar. ¿La radio tiene internet?")
                    AppUpdate.shouldOffer(installedCode(), latest, if (manual) 0 else p.getInt("postponed", 0)) ->
                        // Solo se ofrece lo que se puede verificar (el version.json trae la huella SHA-256 del APK).
                        if (AppUpdate.isVerifiable(latest)) offer(activity, latest)
                        else if (manual) toast(activity, "Hay una versión nueva, pero no se pudo verificar. Descárgala desde tablero-pearl.vercel.app/app.")
                    manual -> toast(activity, "Ya tienes la última versión (${installedName()}).")
                }
            }
        }.start()
    }

    private fun fetchLatest(): UpdateInfo? = try {
        val conn = URL(BASE + "version.json").openConnection() as HttpURLConnection
        conn.connectTimeout = 8000
        conn.readTimeout = 8000
        try {
            if (conn.responseCode != 200) null else AppUpdate.parse(conn.inputStream.bufferedReader().use { it.readText().take(2000) })
        } finally {
            conn.disconnect()
        }
    } catch (e: Exception) {
        null
    }

    private fun offer(activity: Activity, latest: UpdateInfo) {
        AlertDialog.Builder(activity)
            .setTitle("Hay una versión nueva de Tablero")
            .setMessage(
                "Versión ${latest.versionName.ifBlank { latest.versionCode.toString() }} (tienes la ${installedName()}).\n\n" +
                    "Se descarga ahora (unos 2 MB) y Android te pedirá confirmar la instalación. Tus ajustes y tu foto se conservan."
            )
            .setPositiveButton("Actualizar ahora") { _, _ -> download(activity, latest) }
            .setNegativeButton("Después") { _, _ -> prefs(activity).edit().putInt("postponed", latest.versionCode).apply() }
            .show()
    }

    private fun download(activity: Activity, latest: UpdateInfo) {
        val dialog = AlertDialog.Builder(activity).setTitle("Descargando la versión nueva").setMessage("Un momento…").setCancelable(false).show()
        Thread {
            val file = try {
                val dir = File(activity.cacheDir, "update").apply { mkdirs() }
                val out = File(dir, "tablero.apk")
                val conn = URL(BASE + "tablero.apk").openConnection() as HttpURLConnection
                conn.connectTimeout = 10000
                conn.readTimeout = 20000
                try {
                    if (conn.responseCode != 200) throw IllegalStateException("http ${conn.responseCode}")
                    val total = conn.contentLength.toLong()
                    var done = 0L
                    var lastShown = -1
                    conn.inputStream.use { input ->
                        out.outputStream().use { output ->
                            val buf = ByteArray(32 * 1024)
                            while (true) {
                                val n = input.read(buf)
                                if (n < 0) break
                                output.write(buf, 0, n)
                                done += n
                                if (total > 0) {
                                    val pct = (done * 100 / total).toInt()
                                    if (pct != lastShown) {
                                        lastShown = pct
                                        activity.runOnUiThread { dialog.setMessage("$pct %") }
                                    }
                                }
                            }
                        }
                    }
                } finally {
                    conn.disconnect()
                }
                if (out.length() < MIN_APK_BYTES) throw IllegalStateException("archivo incompleto")
                out
            } catch (e: Exception) {
                null
            }
            activity.runOnUiThread {
                runCatching { dialog.dismiss() }
                if (file == null) {
                    toast(activity, "No se pudo descargar la actualización. Intenta de nuevo más tarde.")
                } else if (!activity.isFinishing) {
                    // Antes de entregarla al instalador: que sea exactamente la publicada y que la firme la misma clave de esta app.
                    val problema = verify(activity, file, latest)
                    if (problema != null) {
                        file.delete()
                        toast(activity, problema)
                    } else {
                        install(activity, file, latest)
                    }
                }
            }
        }.start()
    }

    /** Un mensaje si el APK descargado no es de fiar, o null si todo cuadra. */
    private fun verify(context: Context, file: File, info: UpdateInfo): String? {
        val sha = try {
            file.inputStream().use { AppUpdate.sha256Hex(it) }
        } catch (e: Exception) {
            return "No se pudo revisar la descarga. No se instaló nada."
        }
        when (AppUpdate.checkDownload(info, file.length(), sha)) {
            DownloadCheck.OK -> Unit
            DownloadCheck.SIN_HUELLA -> return "No se puede verificar esta actualización. No se instaló nada."
            DownloadCheck.TAMANO, DownloadCheck.HUELLA -> return "La descarga no coincide con la versión publicada. No se instaló nada."
        }
        // En Android 9 o más nuevo se compara además la clave con la que está firmado. En versiones anteriores esa lectura no es
        // fiable: ahí lo comprueba el instalador de Android, que no deja actualizar una app con otra firma.
        if (Build.VERSION.SDK_INT >= 28 && !sameSigner(context, file)) {
            return "La actualización no está firmada con la misma clave de Tablero. No se instaló nada."
        }
        return null
    }

    @android.annotation.TargetApi(28)
    private fun signerDigests(info: PackageInfo?): Set<String> {
        val signing = info?.signingInfo ?: return emptySet()
        val certs = if (signing.hasMultipleSigners()) signing.apkContentsSigners else signing.signingCertificateHistory
        return certs.orEmpty().map { AppUpdate.sha256Hex(it.toByteArray()) }.toSet()
    }

    @android.annotation.TargetApi(28)
    private fun sameSigner(context: Context, file: File): Boolean = try {
        val pm = context.packageManager
        val flags = PackageManager.GET_SIGNING_CERTIFICATES
        val mine = signerDigests(pm.getPackageInfo(context.packageName, flags))
        val theirs = signerDigests(pm.getPackageArchiveInfo(file.absolutePath, flags))
        mine.isNotEmpty() && theirs.isNotEmpty() && mine.any { it in theirs }
    } catch (e: Exception) {
        false
    }

    /** Entrega el APK al instalador de Android. Él pide la confirmación y, si falta, el permiso de instalar desde esta app. */
    private fun install(activity: Activity, file: File, latest: UpdateInfo) {
        try {
            val installer = activity.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            val id = installer.createSession(params)
            installer.openSession(id).use { session ->
                file.inputStream().use { input ->
                    session.openWrite("tablero", 0, file.length()).use { out ->
                        input.copyTo(out)
                        session.fsync(out)
                    }
                }
                val intent = Intent(activity, UpdateReceiver::class.java).setAction(ACTION_RESULT)
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
                session.commit(PendingIntent.getBroadcast(activity, id, intent, flags).intentSender)
            }
            // Si el usuario cancela o falla, se vuelve a ofrecer en la próxima revisión.
            prefs(activity).edit().remove("postponed").apply()
        } catch (e: Exception) {
            toast(activity, "No se pudo abrir el instalador de la radio.")
        }
    }

    private fun toast(c: Context, s: String) = Toast.makeText(c, s, Toast.LENGTH_LONG).show()

    /** Respuesta del instalador de Android: pide la confirmación al usuario o cuenta por qué no se pudo. */
    class UpdateReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
                PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                    @Suppress("DEPRECATION")
                    val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                    confirm?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    try {
                        confirm?.let { context.startActivity(it) }
                    } catch (e: Exception) {
                        toast(context, "Android no dejó abrir la confirmación de la instalación.")
                    }
                }
                PackageInstaller.STATUS_SUCCESS -> Unit
                PackageInstaller.STATUS_FAILURE_CONFLICT, PackageInstaller.STATUS_FAILURE_INCOMPATIBLE ->
                    toast(context, "No se pudo actualizar encima: esta versión instalada tiene otra firma. Desinstala Tablero e instala la nueva una vez.")
                PackageInstaller.STATUS_FAILURE_ABORTED -> Unit
                else -> toast(context, "No se pudo instalar la actualización.")
            }
        }
    }
}
