package cl.tablero.launcher

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.KeyEvent
import android.webkit.WebView
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import cl.tablero.core.Diagnostic
import cl.tablero.core.DiagnosticInfo
import cl.tablero.core.MediaSessionInfo

/**
 * Diagnóstico de la radio: qué Android y qué pantalla tiene, si su radio FM publica la emisora y qué teclas manda cada botón del
 * volante. Se lee en la pantalla o se copia y se comparte, sin adb ni Android Studio. No guarda nada y no manda nada solo.
 */
class DiagnosticActivity : Activity() {
    private lateinit var out: TextView
    private val keysSeen = ArrayList<Int>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Ui.BG)
            setPadding(dp(48), dp(24), dp(48), dp(24))
        }
        root.addView(TextView(this).apply {
            text = "Diagnóstico"
            textSize = 30f
            setTextColor(Ui.TEXT)
            typeface = Ui.DISPLAY
        })
        root.addView(TextView(this).apply {
            text = "Para saber qué hace esta radio. Aprieta un botón del volante y su código aparece abajo; con la radio FM sonando, toca Actualizar."
            textSize = 16f
            setTextColor(Ui.SUB)
            setPadding(0, dp(4), 0, dp(12))
        })
        out = TextView(this).apply {
            typeface = android.graphics.Typeface.MONOSPACE
            textSize = 15f
            setTextColor(Ui.TEXT)
            setTextIsSelectable(true)
        }
        root.addView(ScrollView(this).apply { addView(out) }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        val buttons = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        fun button(label: String, onClick: () -> Unit) = buttons.addView(Button(this).apply {
            text = label
            setOnClickListener { onClick() }
        })
        button("Actualizar") { refresh() }
        button("Copiar") { copy() }
        button("Compartir") { share() }
        button("Notificaciones de Android") { openNotificationAccess() }
        button("Volver") { finish() }
        root.addView(buttons)

        setContentView(root)
        refresh()
    }

    // Todo botón del volante llega como una tecla: se anota su código. Atrás sigue saliendo de la pantalla.
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK) return super.dispatchKeyEvent(event)
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            keysSeen.add(event.keyCode)
            while (keysSeen.size > MAX_KEYS) keysSeen.removeAt(0)
            refresh()
        }
        return true
    }

    private fun refresh() {
        out.text = Diagnostic.report(collect()) { KeyEvent.keyCodeToString(it).removePrefix("KEYCODE_") }
    }

    private fun collect(): DiagnosticInfo {
        val access = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")?.contains(packageName) == true
        val metrics = resources.displayMetrics
        return DiagnosticInfo(
            appVersion = runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull() ?: "?",
            androidRelease = Build.VERSION.RELEASE ?: "?",
            sdk = Build.VERSION.SDK_INT,
            device = "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
            screenWidthPx = metrics.widthPixels,
            screenHeightPx = metrics.heightPixels,
            densityDpi = metrics.densityDpi,
            webView = if (Build.VERSION.SDK_INT >= 26) runCatching { WebView.getCurrentWebViewPackage()?.let { "${it.packageName} ${it.versionName}" } }.getOrNull() else null,
            notificationAccess = access,
            locationPermission = Build.VERSION.SDK_INT < 23 || checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED,
            isHomeApp = runCatching { isHomeApp(this) }.getOrNull(),
            sessions = if (access) mediaSessions() else emptyList(),
            assignedWheelKeys = SettingsStore(this).load().wheelKeys.size,
            keysSeen = keysSeen.toList(),
        )
    }

    private fun mediaSessions(): List<MediaSessionInfo> = try {
        val msm = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        msm.getActiveSessions(ComponentName(this, MediaListener::class.java)).map {
            val md = it.metadata
            MediaSessionInfo(
                pkg = it.packageName,
                playing = it.playbackState?.state == PlaybackState.STATE_PLAYING,
                title = md?.getString(android.media.MediaMetadata.METADATA_KEY_TITLE) ?: md?.getString(android.media.MediaMetadata.METADATA_KEY_DISPLAY_TITLE),
                artist = md?.getString(android.media.MediaMetadata.METADATA_KEY_ARTIST) ?: md?.getString(android.media.MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE),
            )
        }
    } catch (e: Exception) {
        emptyList()
    }

    private fun copy() {
        try {
            (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Diagnóstico de Tablero", out.text))
            Toast.makeText(this, "Copiado.", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "No se pudo copiar.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun share() {
        try {
            val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, out.text.toString())
            startActivity(Intent.createChooser(send, "Compartir el diagnóstico"))
        } catch (e: Exception) {
            Toast.makeText(this, "Esta radio no tiene cómo compartir. Usa Copiar o toma una captura.", Toast.LENGTH_LONG).show()
        }
    }

    private fun openNotificationAccess() {
        try {
            startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
        } catch (e: Exception) {
            Toast.makeText(this, "En Ajustes de Android busca \"Acceso a las notificaciones\".", Toast.LENGTH_LONG).show()
        }
    }

    private fun dp(v: Int) = Ui.dp(this, v)

    private companion object {
        const val MAX_KEYS = 12
    }
}
