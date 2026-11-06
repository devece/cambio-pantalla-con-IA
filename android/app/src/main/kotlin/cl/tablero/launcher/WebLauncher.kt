package cl.tablero.launcher

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import android.graphics.drawable.Drawable
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Base64
import cl.tablero.core.TileId
import cl.tablero.core.Tiles
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import android.provider.Settings
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import cl.tablero.core.FmRadio
import cl.tablero.core.LauncherConfig
import cl.tablero.core.Maintenance
import cl.tablero.core.Reminders
import org.json.JSONArray
import org.json.JSONObject

/**
 * La pantalla de inicio con el diseño del cliente. Usa el mismo motor del sitio (assets/web/), así se ve igual que
 * la vista previa donde eligió. Los datos reales (velocidad, ubicación, avisos) llegan desde la app.
 */
class WebLauncher(private val activity: Activity, private val designJson: String) {
    val view: WebView = WebView(activity)
    private var ready = false
    private val pending = ArrayList<String>()

    private val settings = SettingsStore(activity)
    private val maintenance by lazy {
        Maintenance.parse(activity.assets.open("maintenance.json").bufferedReader().use { it.readText() })
    }

    private val handler = Handler(Looper.getMainLooper())
    private var lastMusicSig = ""
    private val tiles = TileCache(activity)
    @Volatile private var saving = false
    @Volatile private var closed = false
    private var lastArt = ""
    private var lastRadioSig = ""
    private var lastMusicAt = 0L
    private val musicPoll = object : Runnable {
        override fun run() {
            try { pushMusic() } catch (e: Exception) { /* la música es un extra: nunca debe tumbar la pantalla */ }
            try { pushRadio() } catch (e: Exception) { /* la radio FM también */ }
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
            /** Los cuadros del mapa pasan por la copia guardada en la radio: se ven sin internet y no se bajan dos veces. */
            override fun shouldInterceptRequest(v: WebView, request: WebResourceRequest): WebResourceResponse? {
                val host = request.url.host ?: return null
                if (!host.endsWith("basemaps.cartocdn.com")) return null
                val tile = Tiles.parse(request.url.path ?: "") ?: return null
                val bytes = tiles.getOrFetch(tile)
                    ?: return WebResourceResponse("image/png", null, 404, "Sin mapa", emptyMap(), ByteArrayInputStream(ByteArray(0)))
                return WebResourceResponse("image/png", null, 200, "OK", mapOf("Cache-Control" to "max-age=86400"), ByteArrayInputStream(bytes))
            }

            override fun onPageFinished(v: WebView, url: String) {
                ready = true
                v.evaluateJavascript("TBApp.init(" + JSONObject.quote(designJson) + ")", null)
                progressJson(settings.load())?.let { v.evaluateJavascript("TBApp.setProgress(" + JSONObject.quote(it) + ")", null) }
                pending.forEach { v.evaluateJavascript(it, null) }
                pending.clear()
            }
        }
        view.loadUrl("file:///android_asset/web/launcher.html")
    }

    private fun js(code: String) {
        if (ready) view.evaluateJavascript(code, null) else pending.add(code)
    }

    /**
     * Lo que la radio ya anotó (kilometraje y último servicio de cada aviso), o null si no hay nada:
     * así no pisa lo que trae el diseño con ceros.
     */
    private fun progressJson(c: LauncherConfig): String? {
        if (c.lastOil <= 0 && c.maintDone.isEmpty() && c.odometer <= 0) return null
        val out = JSONObject()
        if (c.odometer > 0) out.put("odometer", c.odometer)
        if (c.lastOil > 0) out.put("lastOil", c.lastOil)
        out.put("maintDone", JSONObject().apply { c.maintDone.forEach { (id, km) -> put(id, km) } })
        return out.toString()
    }

    /** Le pasa a la pantalla el estado del scanner OBD y sus últimos valores (ver [ObdClient.toJson]). Solo en el hilo principal. */
    fun setObd(json: String) = js("TBApp.setObd(" + JSONObject.quote(json) + ")")

    /** Vuelve a mandar a la pantalla lo anotado en la radio (por ejemplo, el kilometraje que acaba de sumar el GPS). Solo en el hilo principal. */
    fun setProgress(c: LauncherConfig) {
        progressJson(c)?.let { js("TBApp.setProgress(" + JSONObject.quote(it) + ")") }
    }

    /** Controla la música que suena ("prev", "next" o "toggle"). Lo usan los botones de la pantalla y los del volante. Solo en el hilo principal. */
    fun media(cmd: String) {
        try {
            val c = currentController()
            if (c == null) {
                toast("No hay música sonando. Dale acceso a las notificaciones o abre tu app de música.")
                return
            }
            when (cmd) {
                "prev" -> c.transportControls.skipToPrevious()
                "next" -> c.transportControls.skipToNext()
                "toggle" -> if (c.playbackState?.state == PlaybackState.STATE_PLAYING) c.transportControls.pause() else c.transportControls.play()
            }
        } catch (e: Exception) {
            toast("No se pudo controlar la música.")
        }
    }

    /** Va a una pantalla de Tablero por su id ("inicio", "radio" o "mantenimiento"). */
    fun goTo(screen: String) = js("TBApp.goTo(" + JSONObject.quote(screen) + ")")

    /** Cambia entre el tema de día y el de noche (se queda así hasta que se reinicie la app). */
    fun toggleNight() = js("TBApp.toggleNight()")

    fun setSpeed(kmh: Int) = js("TBApp.setSpeed($kmh)")

    fun setLocation(lat: Double, lon: Double, bearing: Float) = js("TBApp.setLocation($lat,$lon,$bearing)")

    fun zoneNotice(text: String, warn: Boolean) = js("TBApp.zoneNotice(" + JSONObject.quote(text) + ",$warn)")

    fun destroy() {
        closed = true
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

        /** "Hecho" en un aviso por kilometraje: guarda el servicio al kilometraje de hoy y el aviso se reinicia solo. */
        @JavascriptInterface
        fun done(json: String) {
            activity.runOnUiThread {
                try {
                    val req = JSONObject(json)
                    val saved = settings.load()
                    val odometer = req.optInt("odometer", saved.odometer).coerceIn(0, 999_999)
                    val updated = Reminders.markDone(saved.copy(odometer = odometer), req.getString("id"), maintenance)
                    settings.save(updated)
                    progressJson(updated)?.let { js("TBApp.setProgress(" + JSONObject.quote(it) + ")") }
                    toast("Anotado. El próximo aviso toca más adelante.")
                } catch (e: Exception) {
                    toast("No se pudo anotar. Inténtalo de nuevo.")
                }
            }
        }

        /** Botones de la pantalla de Radio ("prev", "next" o "toggle"). */
        @JavascriptInterface
        fun radio(cmd: String) {
            activity.runOnUiThread { controlRadio(cmd) }
        }

        /** Baja de una vez los mapas alrededor de la posición (de día y de noche). Avisa el avance a la pantalla. */
        @JavascriptInterface
        fun saveMapArea(lat: Double, lon: Double) {
            if (saving || lat.isNaN() || lon.isNaN()) return
            saving = true
            Thread {
                var done = 0
                var failed = 0
                try {
                    val around = Tiles.around(lat, lon, MAP_RADIUS)
                    val all = ArrayList<TileId>()
                    Tiles.STYLES.forEach { st -> around.forEach { (x, y) -> all.add(TileId(st, Tiles.ZOOM, x, y)) } }
                    for (t in all) {
                        if (closed) return@Thread
                        if (!tiles.has(t)) {
                            if (tiles.fetch(t) == null) failed++
                            Thread.sleep(120) // sin apurar al servidor de mapas
                        }
                        done++
                        if (done % 15 == 0) progress(done, all.size, failed, false)
                    }
                    tiles.trim()
                    progress(done, all.size, failed, true)
                } finally {
                    saving = false
                }
            }.start()
        }

        @JavascriptInterface
        fun mapInfo(): String {
            val (n, bytes) = tiles.stats()
            return JSONObject().put("tiles", n).put("bytes", bytes).toString()
        }

        @JavascriptInterface
        fun clearMaps() {
            if (!saving) tiles.clear()
        }

        @JavascriptInterface
        fun launchPkg(pkg: String) {
            activity.runOnUiThread { launch(pkg) }
        }

        /** Lista de apps instaladas con su ícono pequeño, para el cajón de apps. Corre fuera del hilo principal. */
        @JavascriptInterface
        fun apps(): String {
            val pm = activity.packageManager
            val out = JSONArray()
            val list = Apps.launchables(pm, activity.packageName).sortedBy { it.loadLabel(pm).toString().lowercase() }
            for (ri in list) {
                try {
                    out.put(JSONObject().put("n", ri.loadLabel(pm).toString()).put("p", ri.activityInfo.packageName).put("i", iconDataUrl(ri.loadIcon(pm))))
                } catch (e: Exception) {
                    // una app con ícono raro no debe vaciar la lista
                }
            }
            return out.toString()
        }

        @JavascriptInterface
        fun media(cmd: String) {
            activity.runOnUiThread { this@WebLauncher.media(cmd) }
        }

        @JavascriptInterface
        fun open(kind: String) {
            activity.runOnUiThread {
                when (kind) {
                    "radio" -> openRadio()
                    "bluetooth" -> start(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                    "activate" -> start(Intent(activity, ActivationActivity::class.java))
                    "androidauto" -> openProjection("Android Auto", listOf("com.google.android.projection.gearhead"), listOf("android auto", "headunit", "zlink", "carlink", "autokit", "tlink", "autobox"))
                    "carplay" -> openProjection("CarPlay", emptyList(), listOf("carplay", "zlink", "carlink", "autokit", "tlink", "autobox"))
                    "camera" -> {
                        val pkg = Apps.findByLabel(activity.packageManager, activity.packageName, listOf("reversa", "retro", "backcar", "rear", "360", "avm", "cámara", "camara", "camera"))
                        if (pkg == null) toast("No encontramos la app de la cámara de tu radio.") else launch(pkg)
                    }
                    "musicaccess" -> start(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
                    "settings" -> start(Intent(activity, SettingsActivity::class.java))
                    "webview" -> openWebviewUpdate()
                    "music" -> toast("Agrega Spotify o YouTube Music a tu pantalla para abrir tu música desde aquí.")
                }
            }
        }
    }

    private fun openProjection(name: String, packages: List<String>, labels: List<String>) {
        val pm = activity.packageManager
        val pkg = packages.firstOrNull { Apps.isInstalled(pm, it) } ?: Apps.findByLabel(pm, activity.packageName, labels)
        if (pkg == null) toast("No encontramos $name en tu radio. Instala la app de tu pantalla (ZLink, CarLink…) o Open Headunit.") else launch(pkg)
    }

    private fun iconDataUrl(d: Drawable): String {
        val size = 64
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        d.setBounds(0, 0, size, size)
        d.draw(c)
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.PNG, 90, out)
        return "data:image/png;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    private fun progress(done: Int, total: Int, failed: Int, fin: Boolean) {
        activity.runOnUiThread { if (!closed) js("TBApp.mapProgress($done,$total,$failed,$fin)") }
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

    /** La sesión de medios de la app de radio FM de fábrica (si suena una, esa), o null si no hay o falta el acceso a las notificaciones. */
    private fun radioController(): MediaController? {
        if (!hasListenerAccess()) return null
        return try {
            val msm = activity.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            val radios = msm.getActiveSessions(ComponentName(activity, MediaListener::class.java)).filter { FmRadio.isRadioPackage(it.packageName) }
            radios.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: radios.firstOrNull()
        } catch (e: SecurityException) {
            null
        }
    }

    /**
     * Siguiente, anterior y play/pausa de la radio FM. Si la radio no entrega una sesión de medios (muchas no lo hacen),
     * no hay forma de manejarla desde aquí: se avisa y se abre su app, que sí la controla.
     */
    private fun controlRadio(cmd: String) {
        try {
            val c = radioController()
            if (c == null) {
                toast("Esta radio no se deja controlar desde aquí. Se abre su app de radio.")
                openRadio()
                return
            }
            when (cmd) {
                "prev" -> c.transportControls.skipToPrevious()
                "next" -> c.transportControls.skipToNext()
                "toggle" -> if (c.playbackState?.state == PlaybackState.STATE_PLAYING) c.transportControls.pause() else c.transportControls.play()
            }
        } catch (e: Exception) {
            toast("No se pudo controlar la radio.")
        }
    }

    /** Le dice a la pantalla qué emisora suena (frecuencia y nombre) según lo que publica la app de radio. Solo manda cuando cambia algo. */
    private fun pushRadio() {
        val access = hasListenerAccess()
        val c = if (access) radioController() else null
        val md = c?.metadata
        val json = JSONObject().put("access", access).put("found", c != null)
        if (c != null) {
            val title = md?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: md?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
            val artist = md?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: md?.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)
            val station = FmRadio.parse(title, artist)
            json.put("playing", c.playbackState?.state == PlaybackState.STATE_PLAYING)
                .put("freq", station.frequency ?: JSONObject.NULL)
                .put("name", station.name)
        }
        val sig = json.toString()
        if (sig == lastRadioSig) return
        lastRadioSig = sig
        js("TBApp.setRadio(" + JSONObject.quote(sig) + ")")
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
            val now = SystemClock.elapsedRealtime()
            // Se reenvía la posición cada 10 s mientras suena, para que la barra de progreso no se desfase.
            if (sig == lastMusicSig && (!playing || now - lastMusicAt < 10_000)) return
            lastMusicAt = now
            // La carátula solo se vuelve a codificar cuando cambia la canción.
            if (!lastMusicSig.startsWith(trackSig + "|") || lastArt.isEmpty()) lastArt = if (bmp != null) bitmapToDataUrl(bmp) else ""
            lastMusicSig = sig
            val st = c.playbackState
            var pos = st?.position ?: 0L
            if (playing && st != null) pos += ((SystemClock.elapsedRealtime() - st.lastPositionUpdateTime) * st.playbackSpeed).toLong()
            val dur = md.getLong(MediaMetadata.METADATA_KEY_DURATION)
            val src = try {
                val pm = activity.packageManager
                pm.getApplicationLabel(pm.getApplicationInfo(c.packageName, 0)).toString()
            } catch (e: Exception) { "" }
            json.put("active", true).put("playing", playing).put("title", title).put("artist", artist).put("art", lastArt)
                .put("pos", maxOf(0L, pos)).put("dur", maxOf(0L, dur)).put("speed", st?.playbackSpeed ?: 1f).put("src", src)
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
        val pkg = FmRadio.PACKAGES.firstOrNull { Apps.isInstalled(activity.packageManager, it) }
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
        // 15 x 15 cuadros por estilo (unos 15 km de lado): lo que cubre una ciudad sin pasarse con el servidor de mapas.
        const val MAP_RADIUS = 7
    }
}
