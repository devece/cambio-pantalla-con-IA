package cl.tablero.launcher

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
import android.view.KeyEvent
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
import cl.tablero.core.RestrictionRules
import cl.tablero.core.WheelAction
import cl.tablero.core.WheelKeys

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
    private lateinit var phrase: EditText
    private lateinit var carTypeGroup: RadioGroup
    private var selectedColor = Ui.CAR_COLORS.first()
    private val colorChips = linkedMapOf<String, View>()
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
    private lateinit var restrType: RadioGroup
    private lateinit var restrDigit: EditText
    private lateinit var rules: RestrictionRules
    private lateinit var voice: CheckBox
    private val apps = linkedMapOf<String, CheckBox>()
    private var wheelKeys: Map<Int, WheelAction> = emptyMap()
    private lateinit var wheelList: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = SettingsStore(this)
        limits = EntitlementStore(this).limits
        maintenance = Maintenance.parse(assets.open("maintenance.json").bufferedReader().use { it.readText() })
        rules = RestrictionRules.parse(assets.open("restriction.json").bufferedReader().use { it.readText() })
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
        greeting = field("Saludo antes de tu nombre (Hola, Wena...)", c.greeting)
        phrase = field("Frase de abajo, en lugar de \"Buenas tardes\" (opcional)", c.phrase)

        section("Tu auto")
        carTypeGroup = RadioGroup(this).apply {
            orientation = RadioGroup.HORIZONTAL
            Ui.CAR_TYPES.forEach { (type, label) ->
                addView(RadioButton(this@SettingsActivity).apply {
                    id = View.generateViewId()
                    tag = type
                    text = label
                    isChecked = c.carType == type
                    setTextColor(Ui.TEXT)
                })
            }
        }
        form.addView(carTypeGroup)
        selectedColor = if (c.carColor in Ui.CAR_COLORS) c.carColor else Ui.CAR_COLORS.first()
        val swatches = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        Ui.CAR_COLORS.forEach { hex ->
            val chip = View(this)
            colorChips[hex] = chip
            chip.setOnClickListener {
                selectedColor = hex
                paintChips()
            }
            swatches.addView(chip, LinearLayout.LayoutParams(dp(44), dp(44)).apply { setMargins(0, dp(8), dp(10), dp(4)) })
        }
        form.addView(swatches)
        paintChips()

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
        restrOn = check("Avisarme al entrar a Santiago si hoy tengo restricción (usa el GPS)", c.restrOn, limits.restrOn)
        restrType = RadioGroup(this).apply {
            orientation = RadioGroup.VERTICAL
            Ui.RESTR_TYPES.forEach { (typeId, label) ->
                addView(RadioButton(this@SettingsActivity).apply {
                    id = View.generateViewId()
                    tag = typeId
                    text = label
                    isEnabled = limits.restrOn
                    isChecked = c.restrType == typeId
                    setTextColor(if (limits.restrOn) Ui.TEXT else Ui.SUB)
                })
            }
        }
        form.addView(restrType)
        restrDigit = field("Último dígito de tu patente (0 a 9)", c.restrDigit).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            filters = arrayOf(InputFilter.LengthFilter(1))
            isEnabled = limits.restrOn
        }

        section("Botones del volante")
        wheelKeys = c.wheelKeys
        form.addView(TextView(this).apply {
            text = "Elige qué hace cada botón de tu volante mientras Tablero está al frente. Los que no asignes hacen lo de siempre."
            setTextColor(Ui.SUB)
        })
        wheelList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        form.addView(wheelList)
        form.addView(Button(this).apply {
            text = "Aprender un botón"
            setOnClickListener { learnWheelKey() }
        })
        refreshWheelList()

        section("Apps en la barra (máximo ${limits.maxApps})")
        Ui.KNOWN_APPS.forEach { (id, app) -> apps[id] = check(app.label, id in c.apps, true) }

        section("Ayuda")
        form.addView(Button(this).apply {
            text = "Diagnóstico de la radio"
            setOnClickListener { startActivity(android.content.Intent(this@SettingsActivity, DiagnosticActivity::class.java)) }
        })

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
        val type = carTypeGroup.findViewById<RadioButton>(carTypeGroup.checkedRadioButtonId)?.tag as? String ?: old.carType
        val rType = restrType.findViewById<RadioButton>(restrType.checkedRadioButtonId)?.tag as? String ?: old.restrType
        val rDigit = restrDigit.text.toString().trim().take(1).filter { it.isDigit() }
        settings.save(
            old.copy(
                carType = type,
                carColor = selectedColor,
                name = name.text.toString().trim().take(24),
                greeting = greeting.text.toString().trim().take(16).ifBlank { "Hola" },
                phrase = phrase.text.toString().trim().take(28),
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
                restrType = rType,
                restrDigit = rDigit,
                // Los días se calculan con la misma tabla del sitio; aquí ya no se marcan a mano.
                restrDays = rules.daysFor(rType, rDigit),
                voice = voice.isChecked,
                wheelKeys = wheelKeys,
            ),
        )
        Toast.makeText(this, "Guardado", Toast.LENGTH_SHORT).show()
        finish()
    }

    /** Pinta cada muestra de color; la elegida lleva un borde amarillo. */
    private fun paintChips() {
        colorChips.forEach { (hex, chip) ->
            chip.background = GradientDrawable().apply {
                setColor(Color.parseColor(hex))
                cornerRadius = dp(10).toFloat()
                setStroke(dp(if (hex == selectedColor) 4 else 1), if (hex == selectedColor) Ui.ACCENT else Ui.LINE)
            }
        }
    }

    /* ---------- botones del volante ---------- */

    private fun keyName(keyCode: Int) = KeyEvent.keyCodeToString(keyCode).removePrefix("KEYCODE_").lowercase().replace('_', ' ')

    private fun refreshWheelList() {
        wheelList.removeAllViews()
        if (wheelKeys.isEmpty()) {
            wheelList.addView(TextView(this).apply {
                text = "Ningún botón asignado."
                setTextColor(Ui.SUB)
            })
            return
        }
        wheelKeys.forEach { (keyCode, action) ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
            }
            row.addView(TextView(this).apply {
                text = "Botón ${keyName(keyCode)}  →  ${action.label}"
                setTextColor(Ui.TEXT)
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(Button(this).apply {
                text = "Cambiar"
                setOnClickListener { pickWheelAction(keyCode) }
            })
            row.addView(Button(this).apply {
                text = "Quitar"
                setOnClickListener {
                    wheelKeys = wheelKeys - keyCode
                    refreshWheelList()
                }
            })
            wheelList.addView(row)
        }
    }

    /** Espera a que se apriete un botón del volante (llega como una tecla) y luego pregunta qué debe hacer. */
    private fun learnWheelKey() {
        if (wheelKeys.size >= WheelKeys.MAX) {
            Toast.makeText(this, "Ya asignaste el máximo de botones (${WheelKeys.MAX}).", Toast.LENGTH_LONG).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Aprieta el botón del volante")
            .setMessage("Aprieta ahora el botón que quieres asignar. Si no pasa nada, esa radio no manda ese botón como tecla.")
            .setNegativeButton("Cancelar", null)
            .setOnKeyListener { dialog, keyCode, event ->
                when {
                    keyCode == KeyEvent.KEYCODE_BACK -> false // Atrás cierra el diálogo
                    event.action != KeyEvent.ACTION_DOWN || event.repeatCount != 0 -> true
                    !WheelKeys.isAssignable(keyCode) -> {
                        Toast.makeText(this, "Esa tecla no se puede asignar.", Toast.LENGTH_SHORT).show()
                        true
                    }
                    else -> {
                        dialog.dismiss()
                        pickWheelAction(keyCode)
                        true
                    }
                }
            }
            .show()
    }

    private fun pickWheelAction(keyCode: Int) {
        val actions = WheelAction.entries
        AlertDialog.Builder(this)
            .setTitle("¿Qué hace el botón ${keyName(keyCode)}?")
            .setItems(actions.map { it.label }.toTypedArray()) { _, i ->
                wheelKeys = WheelKeys.assign(wheelKeys, keyCode, actions[i])
                refreshWheelList()
            }
            .setNegativeButton("Cancelar", null)
            .show()
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
