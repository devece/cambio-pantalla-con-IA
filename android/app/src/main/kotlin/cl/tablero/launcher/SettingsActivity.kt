package cl.tablero.launcher

import android.app.Activity
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import cl.tablero.core.LauncherConfig
import cl.tablero.core.Limits
import cl.tablero.core.Maintenance
import cl.tablero.core.MaintenanceItem

/**
 * Ajustes: lo mismo que el configurador del sitio, pero dentro de la radio y sin internet.
 * Lo que el plan no incluye aparece apagado con "(requiere código)".
 */
class SettingsActivity : Activity() {
    private lateinit var settings: SettingsStore
    private lateinit var limits: Limits
    private lateinit var maintenance: List<MaintenanceItem>
    private lateinit var form: LinearLayout

    private lateinit var name: EditText
    private lateinit var greeting: EditText
    private lateinit var oilOn: CheckBox
    private lateinit var odometer: EditText
    private lateinit var lastOil: EditText
    private lateinit var oilEvery: EditText
    private val others = linkedMapOf<String, CheckBox>()
    private lateinit var docsOn: CheckBox
    private lateinit var rtMonth: EditText
    private lateinit var fuelOn: CheckBox
    private lateinit var tankL: EditText
    private lateinit var kmPerL: EditText
    private lateinit var speedShow: CheckBox
    private lateinit var speedMode: RadioGroup
    private lateinit var manualLimit: EditText
    private lateinit var restrOn: CheckBox
    private val restrDays = linkedMapOf<String, CheckBox>()
    private lateinit var voice: CheckBox
    private val apps = linkedMapOf<String, CheckBox>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = SettingsStore(this)
        limits = EntitlementStore(this).limits
        maintenance = Maintenance.parse(assets.open("maintenance.json").bufferedReader().use { it.readText() })
        val c = settings.load()

        form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(48), dp(24), dp(48), dp(24))
        }
        val scroll = ScrollView(this).apply {
            setBackgroundColor(Ui.BG)
            addView(form)
        }

        title("Ajustes")

        section("Tu saludo")
        name = field("Tu nombre", c.name)
        greeting = field("Saludo (Hola, Buenas...)", c.greeting)

        section("Aceite")
        oilOn = check("Aviso de cambio de aceite", c.oilOn, limits.oilOn)
        odometer = number("Kilometraje actual", c.odometer)
        lastOil = number("Último cambio de aceite a los (km)", c.lastOil)
        oilEvery = number("Cambio cada (km)", c.oilEvery)

        section("Otros mantenimientos")
        maintenance.forEach {
            others[it.id] = check(it.name, it.id in c.otherMaintenance, limits.othersMaintenance)
        }

        section("Documentos")
        docsOn = check("Revisión técnica, permiso de circulación y SOAP", c.docsOn, limits.docsOn)
        rtMonth = number("Mes de vencimiento de la revisión técnica (1 a 12)", c.rtMonth)

        section("Combustible")
        fuelOn = check("Calcular km con el tanque lleno (aproximado)", c.fuelOn, limits.fuelOn)
        tankL = number("Tanque (litros)", c.tankL)
        kmPerL = number("Rendimiento (km por litro)", c.kmPerL)

        section("Al manejar")
        speedShow = check("Mostrar mi velocidad (usa el GPS)", c.speedShow, limits.speedShow)
        speedMode = RadioGroup(this).apply {
            orientation = RadioGroup.VERTICAL
            val allowedModes = limits.speedAlerts
            listOf("auto" to "Límite automático (aún no disponible)", "manual" to "Límite fijo", "off" to "Sin aviso").forEach { (mode, label) ->
                val allowed = allowedModes == null || mode in allowedModes
                addView(RadioButton(this@SettingsActivity).apply {
                    id = View.generateViewId()
                    tag = mode
                    text = label + if (allowed) "" else "  (requiere código)"
                    isEnabled = allowed
                    isChecked = c.speedAlert == mode
                    setTextColor(if (allowed) Ui.TEXT else Ui.SUB)
                })
            }
        }
        form.addView(speedMode)
        manualLimit = number("Límite fijo (km/h)", c.manualLimit)
        voice = check("Saludarme por voz al encender", c.voice, limits.voice)

        section("Restricción vehicular")
        restrOn = check("Recordar mi día de restricción", c.restrOn, limits.restrOn)
        Ui.WEEKDAYS.forEach { (id, label) -> restrDays[id] = check(label, id in c.restrDays, limits.restrOn) }

        section("Apps en la barra (máximo ${limits.maxApps})")
        Ui.KNOWN_APPS.forEach { (id, pair) -> apps[id] = check(pair.first, id in c.apps, true) }

        form.addView(Button(this).apply {
            text = "Guardar"
            setOnClickListener { save(c) }
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(24) })

        setContentView(scroll)
    }

    private fun save(old: LauncherConfig) {
        val chosenApps = apps.filterValues { it.isChecked }.keys.toList()
        if (chosenApps.size > limits.maxApps) {
            Toast.makeText(this, "Tu plan permite hasta ${limits.maxApps} apps", Toast.LENGTH_LONG).show()
            return
        }
        val mode = speedMode.findViewById<RadioButton>(speedMode.checkedRadioButtonId)?.tag as? String ?: old.speedAlert
        settings.save(
            old.copy(
                name = name.text.toString().trim().take(24),
                greeting = greeting.text.toString().trim().take(24).ifBlank { "Hola" },
                apps = chosenApps,
                oilOn = oilOn.isChecked,
                odometer = int(odometer, old.odometer, 0..999_999),
                lastOil = int(lastOil, old.lastOil, 0..999_999),
                oilEvery = int(oilEvery, old.oilEvery, 1_000..30_000),
                otherMaintenance = others.filterValues { it.isChecked }.keys.toList(),
                docsOn = docsOn.isChecked,
                rtMonth = int(rtMonth, old.rtMonth, 1..12),
                fuelOn = fuelOn.isChecked,
                tankL = int(tankL, old.tankL, 10..150),
                kmPerL = int(kmPerL, old.kmPerL, 3..40),
                speedShow = speedShow.isChecked,
                speedAlert = mode,
                manualLimit = int(manualLimit, old.manualLimit, 20..140),
                restrOn = restrOn.isChecked,
                restrDays = restrDays.filterValues { it.isChecked }.keys.toList(),
                voice = voice.isChecked,
            ),
        )
        Toast.makeText(this, "Guardado", Toast.LENGTH_SHORT).show()
        finish()
    }

    /* ---------- piezas del formulario ---------- */

    private fun title(s: String) {
        form.addView(TextView(this).apply {
            text = s
            textSize = 30f
            setTextColor(Ui.TEXT)
        })
    }

    private fun section(s: String) {
        form.addView(TextView(this).apply {
            text = s
            textSize = 20f
            setTextColor(Ui.ACCENT)
            setPadding(0, dp(22), 0, dp(4))
        })
    }

    private fun check(label: String, checked: Boolean, enabled: Boolean): CheckBox {
        val box = CheckBox(this).apply {
            text = label + if (enabled) "" else "  (requiere código)"
            isChecked = checked && enabled
            isEnabled = enabled
            setTextColor(if (enabled) Ui.TEXT else Ui.SUB)
        }
        form.addView(box)
        return box
    }

    private fun field(hint: String, value: String): EditText {
        val e = EditText(this).apply {
            this.hint = hint
            setText(value)
            inputType = InputType.TYPE_CLASS_TEXT
            setTextColor(Ui.TEXT)
            setHintTextColor(Ui.SUB)
        }
        form.addView(e, LinearLayout.LayoutParams(dp(420), LinearLayout.LayoutParams.WRAP_CONTENT))
        return e
    }

    private fun number(hint: String, value: Int): EditText {
        val e = field(hint, value.toString())
        e.inputType = InputType.TYPE_CLASS_NUMBER
        return e
    }

    private fun int(e: EditText, default: Int, range: IntRange): Int =
        e.text.toString().trim().toIntOrNull()?.coerceIn(range.first, range.last) ?: default

    private fun dp(v: Int) = Ui.dp(this, v)
}
