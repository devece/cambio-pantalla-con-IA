package cl.tablero.launcher

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextClock
import android.widget.TextView
import android.widget.Toast
import cl.tablero.core.Gate
import cl.tablero.core.LauncherConfig
import cl.tablero.core.Maintenance
import cl.tablero.core.MaintenanceItem
import cl.tablero.core.Reminder
import cl.tablero.core.Reminders
import cl.tablero.core.Speed
import cl.tablero.core.Tier
import java.util.Calendar
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Pantalla de inicio de la radio (HOME): reloj, saludo, velocidad (GPS), avisos de mantención y
 * documentos, y atajos a apps. Todavía sin los temas visuales ni el clima/música/radio de la vista previa.
 */
class MainActivity : Activity() {
    private lateinit var entitlement: EntitlementStore
    private lateinit var settings: SettingsStore
    private lateinit var maintenance: List<MaintenanceItem>

    private var config = LauncherConfig()
    private var speedView: TextView? = null
    private var limitView: TextView? = null
    private var tts: TextToSpeech? = null
    private var askedLocation = false

    private val locationManager by lazy { getSystemService(Context.LOCATION_SERVICE) as LocationManager }

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            val kmh = if (location.hasSpeed()) (location.speed * 3.6f).roundToInt() else 0
            showSpeed(kmh)
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
        maintenance = Maintenance.parse(assets.open("maintenance.json").bufferedReader().use { it.readText() })
    }

    // Al volver de Ajustes o de Activar código hay que redibujar con lo nuevo.
    override fun onResume() {
        super.onResume()
        config = Gate.apply(settings.load(), entitlement.limits)
        setContentView(buildUi())
        startSpeed()
        greetOnce()
    }

    override fun onPause() {
        super.onPause()
        try {
            locationManager.removeUpdates(locationListener)
        } catch (e: SecurityException) {
            // sin permiso no había nada que quitar
        }
    }

    override fun onDestroy() {
        tts?.shutdown()
        super.onDestroy()
    }

    /* ---------- pantalla ---------- */

    private fun buildUi(): View {
        val tier = entitlement.tier
        val now = Calendar.getInstance()
        val reminders = Reminders.build(config, maintenance, now.get(Calendar.MONTH) + 1, now.get(Calendar.DAY_OF_WEEK))

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Ui.BG)
            setPadding(dp(40), dp(24), dp(40), dp(16))
        }

        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        top.addView(leftColumn(now), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))
        top.addView(rightColumn(reminders), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))
        root.addView(top, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        root.addView(appsRow())
        root.addView(footer(tier))
        return root
    }

    private fun leftColumn(now: Calendar): View {
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(TextClock(this).apply {
            format12Hour = "HH:mm"
            format24Hour = "HH:mm"
            textSize = 80f
            setTextColor(Ui.TEXT)
        })
        col.addView(TextClock(this).apply {
            format12Hour = "EEEE d 'de' MMMM"
            format24Hour = "EEEE d 'de' MMMM"
            textSize = 22f
            setTextColor(Ui.SUB)
        })
        col.addView(text(Reminders.hello(config), 28f, Ui.TEXT).apply { setPadding(0, dp(18), 0, 0) })
        col.addView(text(Reminders.greeting(now.get(Calendar.HOUR_OF_DAY)), 16f, Ui.SUB))

        if (config.speedShow) {
            val speed = text("— km/h", 56f, Ui.TEXT).apply { setPadding(0, dp(20), 0, 0) }
            val limit = text("", 18f, Ui.SUB)
            speedView = speed
            limitView = limit
            col.addView(speed)
            col.addView(limit)
        } else {
            speedView = null
            limitView = null
        }
        return col
    }

    private fun rightColumn(reminders: List<Reminder>): View {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), 0, 0, 0)
        }
        if (reminders.isEmpty()) {
            col.addView(text("Sin avisos. Actívalos en Ajustes.", 18f, Ui.SUB))
        }
        reminders.take(5).forEach { col.addView(reminderCard(it), cardParams()) }
        return col
    }

    private fun reminderCard(r: Reminder): View {
        val color = if (r.warn) Ui.WARN else Ui.OK
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Ui.CARD)
            setPadding(dp(14), dp(10), dp(14), dp(10))
        }
        card.addView(text(r.key, 18f, Ui.TEXT).apply { setTypeface(typeface, android.graphics.Typeface.BOLD) })
        card.addView(text(r.text, 16f, if (r.warn) Ui.WARN else Ui.SUB))
        if (!r.noBar) {
            val bar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            val filled = r.pct.toFloat().coerceIn(0.02f, 1f)
            bar.addView(View(this).apply { setBackgroundColor(color) }, LinearLayout.LayoutParams(0, dp(6), filled))
            bar.addView(View(this).apply { setBackgroundColor(Color.parseColor("#2C3642")) }, LinearLayout.LayoutParams(0, dp(6), 1f - filled + 0.0001f))
            card.addView(bar, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(6)).apply { topMargin = dp(8) })
        }
        return card
    }

    private fun cardParams() =
        LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(10) }

    private fun appsRow(): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        config.apps.mapNotNull { Ui.KNOWN_APPS[it] }.forEach { (label, pkg) ->
            row.addView(Button(this).apply {
                text = label
                textSize = 18f
                setOnClickListener { open(pkg) }
            }, LinearLayout.LayoutParams(dp(190), dp(84)).apply { setMargins(dp(10), 0, dp(10), 0) })
        }
        return row
    }

    private fun footer(tier: Tier): View {
        val f = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), 0, 0)
        }
        f.addView(
            text(
                when (tier) {
                    Tier.FREE -> "Edición gratuita"
                    Tier.ESENCIAL -> "Plan Esencial"
                    Tier.COMPLETO -> "Plan Completo"
                },
                16f, Ui.SUB,
            ),
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
        )
        f.addView(Button(this).apply {
            text = "Ajustes"
            setOnClickListener { startActivity(Intent(this@MainActivity, SettingsActivity::class.java)) }
        })
        if (tier == Tier.FREE) {
            f.addView(Button(this).apply {
                text = "Activar código"
                setOnClickListener { startActivity(Intent(this@MainActivity, ActivationActivity::class.java)) }
            })
        }
        return f
    }

    /* ---------- velocidad (GPS) ---------- */

    private fun startSpeed() {
        if (!config.speedShow) return
        if (!hasLocationPermission()) {
            if (!askedLocation && Build.VERSION.SDK_INT >= 23) {
                askedLocation = true
                requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), REQ_LOCATION)
            }
            return
        }
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

    private fun showSpeed(kmh: Int) {
        // Hoy no consultamos el límite de cada calle: en modo automático no hay límite que mostrar.
        val limit = Speed.limit(config, roadLimit = null)
        speedView?.text = "$kmh km/h"
        speedView?.setTextColor(if (Speed.isOver(kmh, limit)) Ui.WARN else Ui.TEXT)
        limitView?.text = when {
            limit != null -> "Límite $limit km/h"
            config.speedAlert == "auto" -> "Límite de la vía: aún no disponible"
            else -> ""
        }
    }

    /* ---------- saludo por voz ---------- */

    private fun greetOnce() {
        if (!config.voice || greeted) return
        greeted = true
        val phrase = "${Reminders.greeting(Calendar.getInstance().get(Calendar.HOUR_OF_DAY))}, ${config.name.ifBlank { "amigo" }}"
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale("es", "CL")
                tts?.speak(phrase, TextToSpeech.QUEUE_FLUSH, null, "saludo")
            }
        }
    }

    /* ---------- utilidades ---------- */

    private fun open(pkg: String) {
        val intent = packageManager.getLaunchIntentForPackage(pkg)
        if (intent == null) Toast.makeText(this, "Esa app no está instalada en la radio", Toast.LENGTH_SHORT).show()
        else startActivity(intent)
    }

    private fun text(s: String, size: Float, color: Int) = TextView(this).apply {
        text = s
        textSize = size
        setTextColor(color)
    }

    private fun dp(v: Int) = Ui.dp(this, v)

    companion object {
        private const val REQ_LOCATION = 1

        // Una vez por arranque de la app (no cada vez que se vuelve a la pantalla de inicio).
        private var greeted = false
    }
}
