package cl.tablero.launcher

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.Color
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

    init {
        setup()
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
            @Suppress("DEPRECATION")
            allowFileAccessFromFileURLs = true
            @Suppress("DEPRECATION")
            allowUniversalAccessFromFileURLs = true
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
        view.removeJavascriptInterface("Android")
        view.destroy()
    }

    /** Lo que la pantalla le pide a la app. Llega en otro hilo: todo se hace en el principal. */
    inner class Bridge {
        @JavascriptInterface
        fun openApp(id: String) {
            activity.runOnUiThread {
                val app = Ui.KNOWN_APPS[id]
                if (app == null) toast("Ese atajo aún no está disponible en la radio.") else launch(app.pkg)
            }
        }

        @JavascriptInterface
        fun open(kind: String) {
            activity.runOnUiThread {
                when (kind) {
                    "radio" -> openRadio()
                    "bluetooth" -> start(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                    "settings" -> start(Intent(activity, SettingsActivity::class.java))
                    "music" -> toast("Agrega Spotify o YouTube Music a tu pantalla para abrir tu música desde aquí.")
                }
            }
        }
    }

    private fun openRadio() {
        val pkg = RADIO_PACKAGES.firstOrNull { activity.packageManager.getLaunchIntentForPackage(it) != null }
        if (pkg == null) toast("No encontramos la app de radio de tu equipo.") else launch(pkg)
    }

    private fun launch(pkg: String) {
        val intent = activity.packageManager.getLaunchIntentForPackage(pkg)
        if (intent == null) toast("Esa app no está instalada en la radio.") else start(intent)
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
