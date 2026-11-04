package cl.tablero.launcher

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.util.Base64
import java.io.ByteArrayOutputStream
import android.provider.Settings
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import org.json.JSONObject

/**
 * La pantalla de inicio con el diseño del cliente. Usa el mismo motor del sitio (assets/web/), así se ve igual que
 * la vista previa donde eligió. Los datos reales (velocidad, ubicación, avisos) llegan desde la app.
 */
class WebLauncher(private val activity: Activity, private val designJson: String) {
    val view: WebView = WebView(activity)
    private var ready = false
    private val pending = ArrayList<String>()

    private val handler = Handler(Looper.getMainLooper())
    private var lastMusicSig = ""
    private var lastArt = ""
    private val musicPoll = object : Runnable {
        override fun run() {
            try { pushMusic() } catch (e: Exception) { /* la música es un extra: nunca debe tumbar la pantalla */ }
            handler.postDelayed(this, 1500)
        }
    }

    init {
        setup()
        handler.postDelayed(musicPoll, 1500)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setup() {
        view.setBackgroundColor(Color.BLACK)
        view.isVerticalScrollBarEnabled = false
        view.isHorizontalScrollBarEnabled = false
        view.overScrollMode = View.OVER_SCROLL_NEVER
        view.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            // La pantalla solo lee sus propios archivos con <script> e <img>: no necesita que una página local lea otros archivos.
            @Suppress("DEPRECATION")
            allowFileAccessFromFileURLs = false
            @Suppress("DEPRECATION")
            allowUniversalAccessFromFileURLs = false
            cacheMode = WebSettings.LOAD_DEFAULT
        }
        view.addJavascriptInterface(Bridge(), "Android")
        view.webViewClient = object : WebViewClient() {
            override fun onPageFinished(v: WebView, url: String) {
                ready = true
                v.evaluateJavascript("TBApp.init(" + JSONObject.quote(designJson) + ")", null)
                pending.forEach { v.evaluateJavascript(it, null) }
                pending.clear()
            }
        }
        view.loadUrl("file:///android_asset/web/launcher.html")
    }

    private fun js(code: String) {
        if (ready) view.evaluateJavascript(code, null) else pending.add(code)
    }

    fun setSpeed(kmh: Int) = js("TBApp.setSpeed($kmh)")

    fun setLocation(lat: Double, lon: Double) = js("TBApp.setLocation($lat,$lon)")

    fun zoneNotice(text: String, warn: Boolean) = js("TBApp.zoneNotice(" + JSONObject.quote(text) + ",$warn)")

    fun destroy() {
        handler.removeCallbacks(musicPoll)
        view.removeJavascriptInterface("Android")
        view.destroy()
    }

    /** Lo que la pantalla le pide a la app. Llega en otro hilo: todo se hace en el principal. */
    inner class Bridge {
        @JavascriptInterface
        fun openApp(id: String) {
            activity.runOnUiThread {
                if (id == "telefono") {
                    start(Intent(Intent.ACTION_DIAL))
                    return@runOnUiThread
                }
                val app = Ui.KNOWN_APPS[id]
                if (app == null) toast("Ese atajo aún no está disponible en la radio.") else launch(app.pkg, app.label)
            }
        }

        @JavascriptInterface
        fun media(cmd: String) {
            activity.runOnUiThread {
                try {
                    val c = currentController() ?: return@runOnUiThread
                    when (cmd) {
                        "prev" -> c.transportControls.skipToPrevious()
                        "next" -> c.transportControls.skipToNext()
                        "toggle" -> if (c.playbackState?.state == PlaybackState.STATE_PLAYING) c.transportControls.pause() else c.transportControls.play()
                    }
                } catch (e: Exception) {
                    toast("No se pudo controlar la música.")
                }
            }
        }

        @JavascriptInterface
        fun open(kind: String) {
            activity.runOnUiThread {
                when (kind) {
                    "radio" -> openRadio()
                    "bluetooth" -> start(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                    "activate" -> start(Intent(activity, ActivationActivity::class.java))
                    "musicaccess" -> start(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
                    "settings" -> start(Intent(activity, SettingsActivity::class.java))
                    "webview" -> openWebviewUpdate()
                    "music" -> toast("Agrega Spotify o YouTube Music a tu pantalla para abrir tu música desde aquí.")
                }
            }
        }
    }

    /* ---------- música en vivo ---------- */

    private fun hasListenerAccess(): Boolean {
        val enabled = Settings.Secure.getString(activity.contentResolver, "enabled_notification_listeners") ?: return false
        return enabled.contains(activity.packageName)
    }

    private fun currentController(): MediaController? {
        if (!hasListenerAccess()) return null
        return try {
            val msm = activity.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            val all = msm.getActiveSessions(ComponentName(activity, MediaListener::class.java))
            all.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: all.firstOrNull()
        } catch (e: SecurityException) {
            null
        }
    }

    private fun bitmapToDataUrl(b: Bitmap): String {
        val max = 220
        val scale = max.toFloat() / maxOf(b.width, b.height)
        val small = if (scale < 1f) Bitmap.createScaledBitmap(b, (b.width * scale).toInt(), (b.height * scale).toInt(), true) else b
        val out = ByteArrayOutputStream()
        small.compress(Bitmap.CompressFormat.JPEG, 82, out)
        return "data:image/jpeg;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    private fun pushMusic() {
        val json = JSONObject()
        val access = hasListenerAccess()
        json.put("access", access)
        val c = if (access) currentController() else null
        val md = c?.metadata
        if (c != null && md != null) {
            val title = md.getString(MediaMetadata.METADATA_KEY_TITLE) ?: md.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE) ?: ""
            val artist = md.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: md.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
                ?: md.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE) ?: ""
            val playing = c.playbackState?.state == PlaybackState.STATE_PLAYING
            val trackSig = c.packageName + "|" + title + "|" + artist
            val bmp = md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) ?: md.getBitmap(MediaMetadata.METADATA_KEY_ART)
                ?: md.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
            val sig = trackSig + "|" + playing + "|" + (bmp != null)
            if (sig == lastMusicSig) return
            // La carátula solo se vuelve a codificar cuando cambia la canción.
            if (!lastMusicSig.startsWith(trackSig + "|") || lastArt.isEmpty()) lastArt = if (bmp != null) bitmapToDataUrl(bmp) else ""
            lastMusicSig = sig
            json.put("active", true).put("playing", playing).put("title", title).put("artist", artist).put("art", lastArt)
        } else {
            val sig = "none|$access"
            if (sig == lastMusicSig) return
            lastMusicSig = sig
            lastArt = ""
            json.put("active", false)
        }
        js("TBApp.setMusic(" + JSONObject.quote(json.toString()) + ")")
    }

    private fun openRadio() {
        val pkg = RADIO_PACKAGES.firstOrNull { Apps.isInstalled(activity.packageManager, it) }
        if (pkg == null) toast("No encontramos la app de radio de tu equipo.") else launch(pkg)
    }

    private fun launch(pkg: String, label: String? = null) {
        val intent = Apps.launchIntent(activity.packageManager, pkg)
        if (intent == null) toast(if (label != null) "$label no está instalada en la radio." else "Esa app no está instalada en la radio.") else start(intent)
    }

    /** Abre la ficha de Android System WebView en la tienda (o en el navegador si la radio no tiene Play Store). */
    private fun openWebviewUpdate() {
        val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.google.android.webview"))
        val web = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=com.google.android.webview"))
        try {
            activity.startActivity(market)
        } catch (e: Exception) {
            try {
                activity.startActivity(web)
            } catch (e2: Exception) {
                toast("Abre Play Store y actualiza «Android System WebView».")
            }
        }
    }

    private fun start(intent: Intent) {
        try {
            activity.startActivity(intent)
        } catch (e: Exception) {
            toast("No se pudo abrir.")
        }
    }

    private fun toast(text: String) = Toast.makeText(activity, text, Toast.LENGTH_SHORT).show()

    private companion object {
        // Apps de radio FM de fábrica más comunes en radios de auto chinas.
        val RADIO_PACKAGES = listOf(
            "com.nwd.radio", "com.nwd.android.radio", "com.syu.radio", "com.mediatek.fmradio",
            "com.android.fmradio", "com.caf.fmradio", "com.qualcomm.fmradio",
        )
    }
}
