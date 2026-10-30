package cl.tablero.launcher

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextClock
import android.widget.TextView
import android.widget.Toast
import android.app.Activity
import cl.tablero.core.Gate
import cl.tablero.core.LauncherConfig
import cl.tablero.core.Tier

/**
 * Pantalla de inicio de la radio (HOME).
 * Esqueleto: reloj, saludo, atajos a apps y estado de la edición. Aún NO están los temas visuales ni las
 * pantallas de mantención, documentos, velocidad y combustible de docs/ANDROID_LAUNCHER.md.
 */
class MainActivity : Activity() {
    private lateinit var store: EntitlementStore

    /** id de app (catalog.js) -> nombre en pantalla y paquete de Android. */
    private val knownApps = mapOf(
        "maps" to ("Mapas" to "com.google.android.apps.maps"),
        "waze" to ("Waze" to "com.waze"),
        "spotify" to ("Spotify" to "com.spotify.music"),
        "ytmusic" to ("YouTube Music" to "com.google.android.apps.youtube.music"),
        "youtube" to ("YouTube" to "com.google.android.youtube"),
        "whatsapp" to ("WhatsApp" to "com.whatsapp"),
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = EntitlementStore(this)
    }

    // Al volver de la pantalla de activación hay que redibujar con el nivel nuevo.
    override fun onResume() {
        super.onResume()
        setContentView(buildUi())
    }

    private fun buildUi(): View {
        val tier = store.tier
        // Por ahora la configuración es la de fábrica; después vendrá de una pantalla de ajustes.
        val config = Gate.apply(LauncherConfig(), store.limits)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#101418"))
            setPadding(dp(48), dp(32), dp(48), dp(24))
        }

        root.addView(TextClock(this).apply {
            format12Hour = "HH:mm"
            format24Hour = "HH:mm"
            textSize = 72f
            setTextColor(Color.WHITE)
        })
        root.addView(TextClock(this).apply {
            format12Hour = "EEEE d 'de' MMMM"
            format24Hour = "EEEE d 'de' MMMM"
            textSize = 22f
            setTextColor(Color.parseColor("#B8C0CC"))
        })

        val apps = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        config.apps.mapNotNull { knownApps[it] }.forEach { (label, pkg) ->
            apps.addView(Button(this).apply {
                text = label
                textSize = 20f
                setOnClickListener { open(pkg) }
            }, LinearLayout.LayoutParams(dp(200), dp(110)).apply { setMargins(dp(12), 0, dp(12), 0) })
        }
        root.addView(apps, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        val footer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        footer.addView(TextView(this).apply {
            text = when (tier) {
                Tier.FREE -> "Edición gratuita"
                Tier.ESENCIAL -> "Plan Esencial"
                Tier.COMPLETO -> "Plan Completo"
            }
            textSize = 16f
            setTextColor(Color.parseColor("#B8C0CC"))
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        if (tier == Tier.FREE) {
            footer.addView(Button(this).apply {
                text = "Activar código"
                setOnClickListener { startActivity(Intent(this@MainActivity, ActivationActivity::class.java)) }
            })
        }
        root.addView(footer)
        return root
    }

    private fun open(pkg: String) {
        val intent = packageManager.getLaunchIntentForPackage(pkg)
        if (intent == null) Toast.makeText(this, "Esa app no está instalada en la radio", Toast.LENGTH_SHORT).show()
        else startActivity(intent)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
