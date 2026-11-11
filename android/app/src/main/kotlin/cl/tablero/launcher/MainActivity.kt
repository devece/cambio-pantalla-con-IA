package cl.tablero.launcher

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.media.AudioManager
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextClock
import android.widget.TextView
import android.widget.Toast
import cl.tablero.core.Gate
import cl.tablero.core.LauncherConfig
import cl.tablero.core.Maintenance
import cl.tablero.core.MaintenanceItem
import cl.tablero.core.ObdCodes
import cl.tablero.core.ObdState
import cl.tablero.core.Reminder
import cl.tablero.core.Reminders
import cl.tablero.core.RestrictionNotice
import cl.tablero.core.RestrictionRules
import cl.tablero.core.Speed
import cl.tablero.core.ApkBlock
import cl.tablero.core.DesignImport
import cl.tablero.core.Tier
import cl.tablero.core.Welcome
import cl.tablero.core.WheelAction
import cl.tablero.core.WheelKeys
import java.util.Calendar
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Pantalla de inicio de la radio (HOME), tema Ruta: tu auto, reloj y velocidad, avisos de mantención y
 * documentos, y accesos a tus apps. Sin clima, música ni radio FM todavía.
 */
class MainActivity : Activity() {
    private lateinit var entitlement: EntitlementStore
    private lateinit var settings: SettingsStore
    private lateinit var maintenance: List<MaintenanceItem>

    private var config = LauncherConfig()
    private var speedView: TextView? = null
    private var signView: LimitSignView? = null
    private var limitText: TextView? = null
    private var alertView: TextView? = null
    private var tts: TextToSpeech? = null
    private var askedLocation = false
    private var askedWelcome = false
    private lateinit var restriction: RestrictionRules
    private var zoneView: TextView? = null
    private var web: WebLauncher? = null
    private var odometerListener: SharedPreferences.OnSharedPreferenceChangeListener? = null
    private var obdClient: ObdClient? = null
    /** Explicaciones de los códigos de falla; null si el catálogo no se pudo leer (se muestran sin explicación). */
    private val obdCodes: ObdCodes? by lazy {
        try {
            ObdCodes.parse(assets.open("obd-codes.json").bufferedReader().use { it.readText() })
        } catch (e: Exception) {
            null
        }
    }
    private var obdState = ObdState.IDLE
    private var obdDetail = ""
    private var insideZone = false
    private val handler = Handler(Looper.getMainLooper())
    private val hideZone = Runnable { zoneView?.visibility = View.GONE }

    private val locationManager by lazy { getSystemService(Context.LOCATION_SERVICE) as LocationManager }

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            val kmh = if (location.hasSpeed()) (location.speed * 3.6f).roundToInt() else 0
            showSpeed(kmh)
            web?.setSpeed(kmh)
            web?.setLocation(location.latitude, location.longitude, if (location.hasBearing()) location.bearing else -1f)
            checkZone(location.latitude, location.longitude)
        }

        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}

        @Deprecated("Solo se necesita en Android antiguos")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        entitlement = EntitlementStore(this)
        settings = SettingsStore(this)
        importApkDesign()
        Updater.checkAndOffer(this, manual = false)
        maintenance = Maintenance.parse(assets.open("maintenance.json").bufferedReader().use { it.readText() })
        restriction = RestrictionRules.parse(assets.open("restriction.json").bufferedReader().use { it.readText() })
    }

    /**
     * Si esta APK se descargó del sitio con un diseño adentro, lo aplica a la configuración (una sola vez por diseño: si el
     * usuario después cambia algo en Ajustes, no se pisa al volver a abrir la app). Una APK normal no trae nada y no pasa nada.
     */
    private fun importApkDesign() {
        try {
            val apk = java.io.File(applicationInfo.sourceDir)
            val bytes = ApkBlock.read(apk) ?: return
            val json = String(bytes, Charsets.UTF_8)
            val photo = DesignImport.photoInfo(json)
            val day = if (photo != null) ApkBlock.read(apk, ApkBlock.PHOTO_DAY_ID) else null
            val night = if (photo != null && photo.second) ApkBlock.read(apk, ApkBlock.PHOTO_NIGHT_ID) else null
            val digest = java.security.MessageDigest.getInstance("SHA-256")
            digest.update(bytes)
            day?.let { digest.update(it) }
            night?.let { digest.update(it) }
            val hash = digest.digest().joinToString("") { "%02x".format(it) }
            if (hash == settings.apkDesignHash()) return
            // Se anota primero: si algo falla a medias, no se reintenta en cada arranque.
            settings.setApkDesignHash(hash)
            val known = Maintenance.parse(assets.open("maintenance.json").bufferedReader().use { it.readText() }).map { it.id }.toSet()
            val merged = DesignImport.apply(settings.load(), json, known) ?: return
            settings.save(merged)
            // La foto que vino en la APK (si vino): reemplaza a la que hubiera.
            if (photo != null && day != null) PhotoStore(this).saveBytes(day, photo.first, night)
        } catch (e: Exception) {
            // Sin diseño o archivo raro: la app sigue como siempre.
        }
    }

    // Al volver de Ajustes hay que redibujar con lo nuevo.
    override fun onResume() {
        super.onResume()
        config = Gate.apply(settings.load(), entitlement.limits)
        web?.destroy()
        web = null
        val designs = DesignStore(this)
        val saved = if (designs.has()) designs.loadText() else null
        if (saved != null) config = designs.applyTo(config)
        // Primera vez: el asistente de bienvenida (una sola vez por arranque de la app; al terminar vuelve aquí y ya no sale).
        if (!askedWelcome && Welcome.shouldShow(config, hasDesign = saved != null)) {
            askedWelcome = true
            startActivity(Intent(this, WelcomeActivity::class.java))
        }
        // Con o sin código se dibuja con el motor del sitio; sin código, con el diseño base de la edición gratuita
        // y lo que el usuario contó (nombre, auto y color).
        val launcher = WebLauncher(this, saved ?: PhotoStore(this).let { Welcome.profileJson(config, it.url(), it.isCut(), it.nightUrl()) })
        web = launcher
        setContentView(launcher.view)
        startSpeed()
        // Mientras el inicio está a la vista, los avisos ("faltan X km") siguen el kilometraje que va sumando el GPS.
        odometerListener?.let { settings.removeOdometerListener(it) }
        odometerListener = settings.onOdometerChange { km ->
            runOnUiThread {
                config = config.copy(odometer = km)
                web?.setProgress(config)
            }
        }
        // Scanner OBD (si el usuario eligió uno en Ajustes): se conecta mientras el inicio está a la vista y descansa al salir.
        // Apagado mientras ninguna pantalla muestre sus datos: leer algo que nadie ve solo cansa al adaptador y la Bluetooth de la radio.
        obdClient?.stop()
        obdClient = null
        obdState = ObdState.IDLE
        obdDetail = ""
        if (OBD_LIVE_DATA) obdClient = ObdClient(
            this,
            onState = { state, detail ->
                obdState = state
                obdDetail = detail
                web?.setObd(ObdClient.toJson(state, detail, null, obdCodes))
            },
            onSnapshot = { snapshot ->
                web?.setObd(ObdClient.toJson(obdState, obdDetail, snapshot, obdCodes))
                // Si el auto informa su kilometraje real, manda sobre el que suma el GPS (y los avisos de mantención lo siguen).
                if (config.odometerAuto) snapshot.values["odometer"]?.let { km -> settings.setOdometerFromCar(Math.round(km).toInt()) }
            },
        ).also { it.start(config.obd) }
        greetOnce()
    }

    override fun onPause() {
        super.onPause()
        obdClient?.stop()
        obdClient = null
        odometerListener?.let { settings.removeOdometerListener(it) }
        odometerListener = null
        handler.removeCallbacks(hideZone)
        try {
            locationManager.removeUpdates(locationListener)
        } catch (e: SecurityException) {
            // sin permiso no había nada que quitar
        }
    }

    /* ---------- botones del volante ---------- */

    /**
     * Los botones del volante llegan como teclas normales de Android. Si el usuario asignó este botón se hace su acción
     * (y la tecla no sigue); si no, se deja pasar para que Android haga lo de siempre. Solo con el launcher al frente.
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val action = WheelKeys.actionFor(config.wheelKeys, event.keyCode) ?: return super.dispatchKeyEvent(event)
        // Una acción por pulsación (mantener apretado no la repite), salvo el volumen, que sube o baja mientras se mantiene.
        if (event.action == KeyEvent.ACTION_DOWN && (event.repeatCount == 0 || action.repeatsWhileHeld)) runWheelAction(action)
        return true
    }

    private fun runWheelAction(action: WheelAction) {
        action.shortcutIndex?.let { openShortcut(it); return }
        action.screen?.let { web?.goTo(it); return }
        when (action) {
            WheelAction.PLAYBACK -> web?.media("toggle")
            WheelAction.SONG_NEXT -> web?.media("next")
            WheelAction.SONG_PREV -> web?.media("prev")
            WheelAction.LOUDER -> adjustVolume(AudioManager.ADJUST_RAISE)
            WheelAction.QUIETER -> adjustVolume(AudioManager.ADJUST_LOWER)
            WheelAction.SILENCE -> adjustVolume(if (Build.VERSION.SDK_INT >= 23) AudioManager.ADJUST_TOGGLE_MUTE else AudioManager.ADJUST_MUTE)
            WheelAction.PHONE -> openIntent(Intent(Intent.ACTION_DIAL))
            WheelAction.ASSISTANT -> openIntent(Intent(Intent.ACTION_VOICE_COMMAND), "Esta radio no tiene asistente de voz.")
            WheelAction.SWAP_THEME -> web?.toggleNight()
            // Los atajos y las pantallas ya se atendieron arriba.
            else -> {}
        }
    }

    private fun adjustVolume(direction: Int) {
        try {
            (getSystemService(Context.AUDIO_SERVICE) as AudioManager).adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
        } catch (e: Exception) {
            // Algunas radios no dejan cambiar el volumen desde una app: ahí el botón no hace nada, pero no tumba la pantalla.
        }
    }

    /** Abre la app que el usuario puso en esa posición de la barra de abajo (0 es la primera). */
    private fun openShortcut(index: Int) {
        val id = config.apps.getOrNull(index)
        if (id == null) {
            Toast.makeText(this, "Aún no hay un atajo ${index + 1} en tu barra. Agrégalo en Ajustes.", Toast.LENGTH_SHORT).show()
            return
        }
        if (id == "telefono") return openIntent(Intent(Intent.ACTION_DIAL))
        val app = Ui.KNOWN_APPS[id]
        val intent = app?.let { Apps.launchIntent(packageManager, it.pkg) }
        if (intent == null) {
            Toast.makeText(this, "${app?.label ?: "Esa app"} no está instalada en la radio.", Toast.LENGTH_SHORT).show()
            return
        }
        openIntent(intent)
    }

    private fun openIntent(intent: Intent, missing: String = "No se pudo abrir.") {
        try {
            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            Toast.makeText(this, missing, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        web?.destroy()
        web = null
        tts?.shutdown()
        super.onDestroy()
    }

    /* ---------- pantalla ---------- */

    private fun buildUi(): View {
        val tier = entitlement.tier
        val now = Calendar.getInstance()
        val reminders = Reminders.build(config, maintenance, now.get(Calendar.MONTH) + 1, now.get(Calendar.DAY_OF_WEEK))

        val frame = FrameLayout(this)
        frame.addView(BackgroundView(this), FrameLayout.LayoutParams(MATCH, MATCH))

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(14), dp(14), dp(14), dp(14))
        }
        root.addView(sidebar(tier), lp(dp(84), MATCH, 0f, r = 12))

        val main = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val alert = TextView(this).apply {
            typeface = Ui.DISPLAY
            textSize = 20f
            setTextColor(Color.WHITE)
            background = Ui.rounded(this@MainActivity, Ui.ALERT, 22)
            setPadding(dp(24), dp(6), dp(24), dp(6))
            visibility = View.INVISIBLE
            text = " "
        }
        val zone = TextView(this).apply {
            typeface = Ui.DISPLAY
            textSize = 20f
            setTextColor(Color.BLACK)
            background = Ui.rounded(this@MainActivity, Ui.ACCENT, 22)
            setPadding(dp(24), dp(8), dp(24), dp(8))
            gravity = Gravity.CENTER
            visibility = View.GONE
        }
        zoneView = zone
        main.addView(zone, LinearLayout.LayoutParams(WRAP, WRAP).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            bottomMargin = dp(8)
        })
        alertView = alert
        main.addView(alert, LinearLayout.LayoutParams(WRAP, WRAP).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            bottomMargin = dp(8)
        })

        val columns = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        columns.addView(carCard(now), lp(0, MATCH, 1.25f, r = 12))
        columns.addView(centerColumn(now, reminders), lp(0, MATCH, 1f, r = 12))
        columns.addView(appsColumn(), lp(0, MATCH, 0.8f))
        main.addView(columns, LinearLayout.LayoutParams(MATCH, 0, 1f))

        root.addView(main, LinearLayout.LayoutParams(0, MATCH, 1f))
        frame.addView(root, FrameLayout.LayoutParams(MATCH, MATCH))
        return frame
    }

    /** Barra lateral: Inicio y Ajustes. */
    private fun sidebar(tier: Tier): View {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = Ui.glass(this@MainActivity)
            setPadding(0, dp(14), 0, dp(14))
        }
        bar.addView(navButton(R.drawable.ic_home, "Inicio", true) {}, lp(dp(56), dp(56), 0f, b = 10))
        bar.addView(View(this), LinearLayout.LayoutParams(1, 0, 1f))
        // Ya no se piden códigos: no hay botón para activarlos.
        bar.addView(
            navButton(R.drawable.ic_settings, "Ajustes", false) {
                startActivity(Intent(this, SettingsActivity::class.java))
            },
            lp(dp(56), dp(56), 0f),
        )
        return bar
    }

    private fun navButton(icon: Int, description: String, selected: Boolean, onClick: () -> Unit): View =
        ImageView(this).apply {
            setImageResource(icon)
            contentDescription = description
            setPadding(dp(14), dp(14), dp(14), dp(14))
            background = if (selected) Ui.rounded(this@MainActivity, Ui.ACCENT, 16) else null
            setColorFilter(if (selected) Ui.ACCENT_INK else Ui.TEXT)
            setOnClickListener { onClick() }
        }

    /** Tarjeta grande: saludo, el auto y a qué edición pertenece. */
    private fun carCard(now: Calendar): View {
        val card = card()
        card.addView(text(Reminders.hello(config), 40f, Ui.TEXT).apply { typeface = Ui.DISPLAY })
        val odometer = if (config.odometer > 0) " · ${Reminders.km(config.odometer)} km" else ""
        card.addView(text(Reminders.below(config, now.get(Calendar.HOUR_OF_DAY)) + odometer, 17f, Ui.SUB))

        val car = CarView(this).apply {
            carType = config.carType
            carColor = parseColor(config.carColor)
        }
        card.addView(car, LinearLayout.LayoutParams(MATCH, 0, 1f))

        card.addView(
            text(
                when (entitlement.tier) {
                    Tier.FREE -> "Gratis"
                    Tier.ESENCIAL -> "Activada con tu código"
                    Tier.COMPLETO -> "Activada con tu código"
                },
                14f, Ui.SUB,
            ),
        )
        return card
    }

    /** Columna central: reloj y velocidad arriba, avisos abajo. */
    private fun centerColumn(now: Calendar, reminders: List<Reminder>): View {
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        val clock = card()
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val time = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        time.addView(TextClock(this).apply {
            format12Hour = "HH:mm"
            format24Hour = "HH:mm"
            typeface = Ui.DISPLAY
            textSize = 72f
            setTextColor(Ui.TEXT)
        })
        time.addView(TextClock(this).apply {
            format12Hour = "EEEE d 'de' MMMM"
            format24Hour = "EEEE d 'de' MMMM"
            textSize = 16f
            setTextColor(Ui.SUB)
        })
        top.addView(time, LinearLayout.LayoutParams(0, WRAP, 1f))

        if (config.speedShow) {
            val speed = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
            }
            val number = text("—", 60f, Ui.ACCENT).apply { typeface = Ui.DISPLAY }
            speedView = number
            speed.addView(number)
            speed.addView(text("km/h", 14f, Ui.SUB))
            top.addView(speed)
        } else {
            speedView = null
        }
        clock.addView(top)

        if (config.speedShow) {
            clock.addView(View(this), LinearLayout.LayoutParams(1, 0, 1f))
            val limitRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            val sign = LimitSignView(this)
            signView = sign
            limitRow.addView(sign, LinearLayout.LayoutParams(dp(60), dp(60)))
            val info = text("", 16f, Ui.SUB).apply { setPadding(dp(14), 0, 0, 0) }
            limitText = info
            limitRow.addView(info)
            clock.addView(limitRow)
            showSpeed(null)
        } else {
            signView = null
            limitText = null
        }
        col.addView(clock, LinearLayout.LayoutParams(MATCH, 0, 1f).apply { bottomMargin = dp(12) })

        col.addView(remindersCard(reminders), LinearLayout.LayoutParams(MATCH, 0, 1.25f))
        return col
    }

    private fun remindersCard(reminders: List<Reminder>): View {
        val card = card()
        card.addView(text("Avisos", 20f, Ui.ACCENT).apply {
            typeface = Ui.DISPLAY
            setPadding(0, 0, 0, dp(6))
        })
        if (reminders.isEmpty()) {
            card.addView(text("Sin avisos. Actívalos en Ajustes.", 16f, Ui.SUB))
        }
        reminders.take(4).forEach { card.addView(reminderRow(it), lp(MATCH, WRAP, 0f, b = 8)) }
        return card
    }

    private fun reminderRow(r: Reminder): View {
        val color = if (r.warn) Ui.WARN else Ui.OK
        val row = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        row.addView(text(r.key, 17f, Ui.TEXT).apply { typeface = Ui.DISPLAY })
        row.addView(text(r.text, 15f, if (r.warn) Ui.WARN else Ui.SUB))
        if (!r.noBar) {
            val filled = r.pct.toFloat().coerceIn(0.03f, 1f)
            val bar = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                background = Ui.rounded(this@MainActivity, Ui.TRACK, 3)
            }
            bar.addView(View(this).apply { background = Ui.rounded(this@MainActivity, color, 3) }, LinearLayout.LayoutParams(0, dp(6), filled))
            bar.addView(View(this), LinearLayout.LayoutParams(0, dp(6), 1f - filled + 0.0001f))
            row.addView(bar, lp(MATCH, dp(6), 0f, t = 6))
        }
        return row
    }

    /** Columna de la derecha: tus apps como tarjetas grandes. */
    private fun appsColumn(): View {
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val apps = config.apps.mapNotNull { Ui.KNOWN_APPS[it] }
        apps.forEachIndexed { i, app ->
            col.addView(appCard(app), LinearLayout.LayoutParams(MATCH, 0, 1f).apply {
                if (i < apps.size - 1) bottomMargin = dp(10)
            })
        }
        if (apps.isEmpty()) {
            col.addView(card().apply { addView(text("Elige tus apps en Ajustes.", 16f, Ui.SUB)) }, LinearLayout.LayoutParams(MATCH, MATCH))
        }
        return col
    }

    private fun appCard(app: AppInfo): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = Ui.glass(this@MainActivity)
            setPadding(dp(16), dp(10), dp(16), dp(10))
            isClickable = true
            setOnClickListener { open(app.pkg) }
        }
        card.addView(ImageView(this).apply {
            setImageResource(app.icon)
            setColorFilter(Ui.ACCENT)
            setPadding(dp(11), dp(11), dp(11), dp(11))
            background = Ui.rounded(this@MainActivity, Color.argb(36, 255, 255, 255), 26)
        }, LinearLayout.LayoutParams(dp(52), dp(52)))

        val labels = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), 0, 0, 0)
        }
        labels.addView(text(app.label, 20f, Ui.TEXT).apply { typeface = Ui.DISPLAY })
        labels.addView(text(if (isInstalled(app.pkg)) "Abrir" else "No instalada", 14f, Ui.SUB))
        card.addView(labels, LinearLayout.LayoutParams(0, WRAP, 1f))
        card.addView(text("›", 30f, Ui.SUB))
        return card
    }

    /* ---------- velocidad (GPS) ---------- */

    private fun startSpeed() {
        val wantsSpeed = config.speedShow || config.restrOn
        // Kilometraje automático: solo si el usuario ya anotó un kilometraje (sin punto de partida no hay qué sumar).
        val wantsOdometer = config.odometerAuto && config.odometer > 0
        if (!wantsOdometer) OdometerService.stop(this)
        if (!wantsSpeed && !wantsOdometer) return
        if (!hasLocationPermission()) {
            if (!askedLocation && Build.VERSION.SDK_INT >= 23) {
                askedLocation = true
                requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), REQ_LOCATION)
            }
            return
        }
        // El servicio sigue contando km aunque la pantalla de inicio pase a segundo plano (Waze, Maps...).
        if (wantsOdometer) OdometerService.startIfNeeded(this)
        if (!wantsSpeed) return
        try {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, locationListener)
        } catch (e: SecurityException) {
            // el permiso se retiró mientras tanto
        } catch (e: IllegalArgumentException) {
            // esta radio no tiene GPS
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_LOCATION && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) startSpeed()
    }

    private fun hasLocationPermission(): Boolean =
        Build.VERSION.SDK_INT < 23 || checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** `kmh` null = todavía no hay señal de GPS. */
    private fun showSpeed(kmh: Int?) {
        // Hoy no consultamos el límite de cada calle: en modo automático no hay límite que mostrar.
        val limit = Speed.limit(config, roadLimit = null)
        val over = kmh != null && Speed.isOver(kmh, limit)

        speedView?.text = kmh?.toString() ?: "—"
        speedView?.setTextColor(if (over) Ui.WARN else Ui.ACCENT)
        signView?.limit = limit
        limitText?.text = when {
            limit != null -> "Límite fijo de $limit km/h"
            config.speedAlert == "auto" -> "Límite de la vía:\naún no disponible"
            else -> "Sin aviso de velocidad"
        }
        alertView?.apply {
            visibility = if (over) View.VISIBLE else View.INVISIBLE
            if (over) text = "Baja la velocidad · $kmh km/h · límite ${limit ?: ""}"
        }
    }

    /* ---------- restricción vehicular (GPS) ---------- */

    /** Avisa una sola vez, al pasar de "afuera" a "adentro" de la zona de Santiago. */
    private fun checkZone(lat: Double, lon: Double) {
        // Las reglas de este aviso son las de Santiago: en otro país (pico y placa de Colombia, Ecuador, México) las avisa la pantalla.
        if (!config.restrOn || config.country != "CL") return
        val inside = restriction.inZone(lat, lon)
        if (inside && !insideZone) {
            val cal = Calendar.getInstance()
            val minutes = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
            restriction.notice(config, Reminders.weekdayId(cal.get(Calendar.DAY_OF_WEEK)), cal.get(Calendar.MONTH) + 1, minutes, cal.get(Calendar.YEAR))
                ?.let { showZoneNotice(it) }
        }
        insideZone = inside
    }

    private fun showZoneNotice(n: RestrictionNotice) {
        web?.let {
            it.zoneNotice(n.text, n.warn)
            speak(n.text)
            return
        }
        zoneView?.apply {
            text = n.text
            visibility = View.VISIBLE
        }
        handler.removeCallbacks(hideZone)
        handler.postDelayed(hideZone, 15_000L)
        speak(n.text)
    }

    private fun speak(phrase: String) {
        if (!config.voice) return
        val engine = tts
        if (engine != null) {
            engine.speak(phrase, TextToSpeech.QUEUE_ADD, null, "zona")
            return
        }
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale("es", "CL")
                tts?.speak(phrase, TextToSpeech.QUEUE_ADD, null, "zona")
            }
        }
    }

    /* ---------- saludo por voz ---------- */

    private fun greetOnce() {
        if (!config.voice || greeted) return
        greeted = true
        val phrase = Reminders.voiceLine(config, Calendar.getInstance().get(Calendar.HOUR_OF_DAY))
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale("es", "CL")
                tts?.speak(phrase, TextToSpeech.QUEUE_FLUSH, null, "saludo")
            }
        }
    }

    /* ---------- utilidades ---------- */

    private fun open(pkg: String) {
        val intent = Apps.launchIntent(packageManager, pkg)
        if (intent == null) Toast.makeText(this, "Esa app no está instalada en la radio", Toast.LENGTH_SHORT).show()
        else startActivity(intent)
    }

    private fun isInstalled(pkg: String): Boolean = Apps.isInstalled(packageManager, pkg)

    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = Ui.glass(this@MainActivity)
        setPadding(dp(18), dp(14), dp(18), dp(14))
    }

    private fun text(s: String, size: Float, color: Int) = TextView(this).apply {
        text = s
        textSize = size
        setTextColor(color)
    }

    private fun parseColor(hex: String): Int = try {
        Color.parseColor(hex)
    } catch (e: IllegalArgumentException) {
        Color.parseColor("#b3261e")
    }

    /** Parámetros de diseño; los márgenes (l, t, r, b) van en dp. */
    private fun lp(w: Int, h: Int, weight: Float, l: Int = 0, t: Int = 0, r: Int = 0, b: Int = 0) =
        LinearLayout.LayoutParams(w, h, weight).apply { setMargins(dp(l), dp(t), dp(r), dp(b)) }

    private fun dp(v: Int) = Ui.dp(this, v)

    companion object {
        /** La lectura en vivo del scanner: ya hay una pantalla («Auto») que muestra sus datos. */
        private const val OBD_LIVE_DATA = true

        private const val REQ_LOCATION = 1
        private const val MATCH = LinearLayout.LayoutParams.MATCH_PARENT
        private const val WRAP = LinearLayout.LayoutParams.WRAP_CONTENT

        // Una vez por arranque de la app (no cada vez que se vuelve a la pantalla de inicio).
        private var greeted = false
    }
}
