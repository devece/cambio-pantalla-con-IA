package cl.tablero.launcher

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.pm.PackageManager
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
import android.widget.ArrayAdapter
import android.widget.RadioGroup
import android.widget.Spinner
import android.view.ViewGroup
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import cl.tablero.core.LauncherConfig
import cl.tablero.core.Limits
import cl.tablero.core.Look
import cl.tablero.core.Maintenance
import cl.tablero.core.MaintenanceItem
import cl.tablero.core.ObdCheck
import cl.tablero.core.ObdLinkConfig
import cl.tablero.core.ObdLinkKind
import cl.tablero.core.RestrictionRules
import cl.tablero.core.WheelAction
import cl.tablero.core.WheelKeys

/**
 * Ajustes: lo mismo que el configurador del sitio, pero dentro de la radio y sin internet.
 * Lo que el nivel actual no permita aparece apagado con "(no disponible)". Hoy todo está permitido.
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
    private lateinit var photoStatus: TextView
    private lateinit var clock12: CheckBox
    private lateinit var lookTheme: Spinner
    private lateinit var lookLayout: Spinner
    private lateinit var lookScene: Spinner
    private lateinit var lookSceneTime: Spinner
    private lateinit var lookSeason: Spinner
    private var lookAccent = ""
    private val accentChips = linkedMapOf<String, View>()
    private val lookIds = HashMap<Spinner, List<String>>()
    private lateinit var oilOn: CheckBox
    private lateinit var odometer: EditText
    private lateinit var odometerAuto: CheckBox
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
    private lateinit var obdKind: RadioGroup
    private lateinit var obdBluetoothBox: LinearLayout
    private lateinit var obdDeviceText: TextView
    private lateinit var obdWifiBox: LinearLayout
    private lateinit var obdHost: EditText
    private lateinit var obdPort: EditText
    private lateinit var obdTest: Button
    private var obdName = ""
    private var obdAddress = ""
    private var afterBluetoothPermission: (() -> Unit)? = null
    private var afterScanPermission: (() -> Unit)? = null
    private var discovery: ObdDiscovery? = null
    private var scanDialog: AlertDialog? = null

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

        buildPhotoSection()
        buildLookSection(c.look, c.clock12)

        section("Aceite")
        oilOn = check("Aviso de cambio de aceite", c.oilOn, limits.oilOn)
        odometer = number("Kilometraje actual", c.odometer)
        odometerAuto = check("Sumar solos los km que recorro (usa el GPS; es una estimación)", c.odometerAuto, true)
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
                    text = label + if (allowed) "" else "  (no disponible)"
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

        buildObdSection(c)

        section("Apoyar")
        form.addView(Button(this).apply {
            text = "Invítame un café ☕"
            setOnClickListener { openCoffee() }
        })

        section("Actualizaciones")
        form.addView(TextView(this).apply {
            text = "Versión instalada: ${Updater.installedName()}. Tablero avisa solo cuando hay una nueva (necesita internet)."
            setTextColor(Ui.SUB)
        })
        form.addView(Button(this).apply {
            text = "Buscar actualización"
            setOnClickListener { Updater.checkAndOffer(this@SettingsActivity, manual = true) }
        })

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

    /** Abre el enlace de propina en el navegador. Muchas radios no traen uno: ahí se avisa en vez de no hacer nada. */
    private fun openCoffee() {
        try {
            startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(COFFEE_URL)))
        } catch (e: android.content.ActivityNotFoundException) {
            Toast.makeText(this, "Esta radio no tiene navegador. Puedes abrir ko-fi.com/daniexpress desde tu celular.", Toast.LENGTH_LONG).show()
        }
    }

    private fun save(old: LauncherConfig) {
        validateObd()?.let {
            Toast.makeText(this, it, Toast.LENGTH_LONG).show()
            return
        }
        val chosenApps = apps.filterValues { it.isChecked }.keys.toList()
        if (chosenApps.size > limits.maxApps) {
            Toast.makeText(this, "Puedes elegir hasta ${limits.maxApps} apps", Toast.LENGTH_LONG).show()
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
                odometerAuto = odometerAuto.isChecked,
                obd = readObd(),
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
                look = readLook(),
                clock12 = clock12.isChecked,
            ),
        )
        Toast.makeText(this, "Guardado", Toast.LENGTH_SHORT).show()
        finish()
    }

    /* ---------- foto del auto ---------- */

    private fun buildPhotoSection() {
        section("Foto de tu auto")
        photoStatus = TextView(this).apply {
            setTextColor(Ui.SUB)
            textSize = 14f
        }
        form.addView(photoStatus)
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(Button(this).apply {
            text = "Elegir foto"
            setOnClickListener { pickPhoto() }
        })
        row.addView(Button(this).apply {
            text = "Quitar foto"
            setOnClickListener {
                PhotoStore(this@SettingsActivity).clear()
                refreshPhotoStatus()
            }
        })
        form.addView(row)
        refreshPhotoStatus()
    }

    private fun refreshPhotoStatus() {
        val photos = PhotoStore(this)
        photoStatus.text = when {
            !photos.has() -> "Sin foto: se muestra un auto de ejemplo con el color que elegiste. Mejor una foto del auto completo, de lado y de día."
            photos.isCut() -> "Foto elegida (ya sin fondo). Se ve al volver al inicio."
            else -> "Foto elegida. Se ve al volver al inicio."
        }
    }

    /** Abre la galería o el explorador de archivos de la radio. Muchas radios no traen uno: ahí se avisa en vez de no hacer nada. */
    private fun pickPhoto() {
        val intent = android.content.Intent(android.content.Intent.ACTION_GET_CONTENT).apply {
            type = "image/*"
            addCategory(android.content.Intent.CATEGORY_OPENABLE)
        }
        try {
            startActivityForResult(intent, REQ_PHOTO)
        } catch (e: android.content.ActivityNotFoundException) {
            Toast.makeText(this, "Esta radio no tiene galería ni explorador de archivos para elegir la foto.", Toast.LENGTH_LONG).show()
        }
    }

    @Deprecated("Se usa la API clásica de Activity para funcionar en radios con Android antiguo")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_PHOTO || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        photoStatus.text = "Guardando la foto…"
        Thread {
            val problem = PhotoStore(this).save(uri)
            runOnUiThread {
                refreshPhotoStatus()
                if (problem != null) Toast.makeText(this, problem, Toast.LENGTH_LONG).show()
            }
        }.start()
    }

    /* ---------- apariencia ---------- */

    /**
     * Tema, distribución, fondo, hora del fondo, decoración y color de acento. La primera opción de cada lista es «Como viene»:
     * lo que trae la pantalla por defecto. Se ve al volver al inicio.
     */
    private fun buildLookSection(look: Look, clockOn: Boolean) {
        section("Apariencia")
        // Quien activó un código antes conserva el diseño del sitio, que manda sobre lo de abajo: se puede soltar para elegir aquí.
        if (DesignStore(this).has()) {
            form.addView(TextView(this).apply {
                text = "Esta radio usa un diseño que se activó con un código, y ese diseño manda sobre lo que elijas aquí."
                setTextColor(Ui.SUB)
                textSize = 14f
            })
            form.addView(Button(this).apply {
                text = "Dejar de usar ese diseño"
                setOnClickListener {
                    DesignStore(this@SettingsActivity).clear()
                    visibility = View.GONE
                    Toast.makeText(this@SettingsActivity, "Listo: ahora manda lo que elijas aquí.", Toast.LENGTH_LONG).show()
                }
            })
        }
        lookTheme = lookSpinner("Tema", Look.THEMES, look.theme)
        lookLayout = lookSpinner("Distribución", Look.LAYOUTS, look.layout)
        lookScene = lookSpinner("Fondo", Look.SCENES, look.scene)
        lookSceneTime = lookSpinner("Hora del fondo", Look.SCENE_TIMES, look.sceneTime)
        lookSeason = lookSpinner("Decoración de temporada", Look.SEASONS, look.season)
        form.addView(TextView(this).apply {
            text = "Color de acento"
            setTextColor(Ui.SUB)
            setPadding(0, dp(10), 0, 0)
        })
        lookAccent = look.accent
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        Look.ACCENTS.forEach { hex ->
            val chip = View(this)
            accentChips[hex] = chip
            chip.setOnClickListener {
                lookAccent = if (lookAccent == hex) "" else hex
                paintAccents()
            }
            row.addView(chip, LinearLayout.LayoutParams(dp(44), dp(44)).apply { setMargins(0, dp(8), dp(10), dp(4)) })
        }
        form.addView(row)
        form.addView(TextView(this).apply {
            text = "Toca el color elegido otra vez para volver al del tema."
            setTextColor(Ui.SUB)
            textSize = 13f
        })
        paintAccents()
        clock12 = check("Hora en formato de 12 horas (9:05 PM, en vez de 21:05)", clockOn, true)
    }

    private fun lookSpinner(label: String, options: Map<String, String>, current: String): Spinner {
        form.addView(TextView(this).apply {
            text = label
            setTextColor(Ui.SUB)
            setPadding(0, dp(10), 0, 0)
        })
        val ids = listOf("") + options.keys
        val names = listOf("Como viene") + options.values
        val spinner = Spinner(this)
        val adapter = object : ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, names) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View =
                (super.getView(position, convertView, parent) as TextView).apply { setTextColor(Ui.TEXT) }

            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View =
                (super.getDropDownView(position, convertView, parent) as TextView).apply {
                    setTextColor(Ui.TEXT)
                    setBackgroundColor(Color.parseColor("#0f3f2e"))
                    setPadding(dp(16), dp(12), dp(16), dp(12))
                }
        }
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter
        spinner.setSelection(ids.indexOf(current).coerceAtLeast(0))
        lookIds[spinner] = ids
        form.addView(spinner, LinearLayout.LayoutParams(dp(420), LinearLayout.LayoutParams.WRAP_CONTENT))
        return spinner
    }

    private fun readLook() = Look(
        theme = pickedId(lookTheme),
        layout = pickedId(lookLayout),
        scene = pickedId(lookScene),
        sceneTime = pickedId(lookSceneTime),
        season = pickedId(lookSeason),
        accent = lookAccent,
    ).cleaned()

    private fun pickedId(s: Spinner): String = lookIds[s]?.getOrNull(s.selectedItemPosition) ?: ""

    private fun paintAccents() {
        accentChips.forEach { (hex, chip) ->
            chip.background = GradientDrawable().apply {
                setColor(Color.parseColor(hex))
                cornerRadius = dp(10).toFloat()
                setStroke(dp(if (hex == lookAccent) 4 else 1), if (hex == lookAccent) Ui.ACCENT else Ui.LINE)
            }
        }
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
            text = label + if (enabled) "" else "  (no disponible)"
            isChecked = checked && enabled
            isEnabled = enabled
            setTextColor(if (enabled) Ui.TEXT else Ui.SUB)
        }
        form.addView(box)
        return box
    }

    /* ---------- scanner OBD ---------- */

    private fun buildObdSection(c: LauncherConfig) {
        section("Scanner OBD (opcional)")
        form.addView(TextView(this).apply {
            text = "Conecta un scanner OBD2 con chip ELM327 (por ejemplo, el «Scanner Automotriz OBD2 ELM327 Bluetooth V2.1») para que la app lea datos de tu auto. " +
                "Por ahora esta versión solo comprueba que se conecta: las pantallas con los datos llegan en una próxima versión."
            setTextColor(Ui.SUB)
        })
        obdAddress = if (ObdLinkConfig.isValidMac(c.obd.address)) c.obd.address else ""
        obdName = if (obdAddress.isNotEmpty()) c.obd.name else ""

        obdKind = RadioGroup(this).apply {
            orientation = RadioGroup.VERTICAL
            ObdLinkKind.entries.forEach { kind ->
                addView(RadioButton(this@SettingsActivity).apply {
                    id = View.generateViewId()
                    tag = kind.id
                    text = when (kind) {
                        ObdLinkKind.OFF -> "Sin scanner"
                        ObdLinkKind.BLUETOOTH -> "Bluetooth (el más común)"
                        ObdLinkKind.WIFI -> "Wi-Fi"
                    }
                    isChecked = c.obd.kind == kind
                    setTextColor(Ui.TEXT)
                })
            }
        }
        form.addView(obdKind)

        obdBluetoothBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        obdDeviceText = TextView(this).apply { setTextColor(Ui.TEXT) }
        obdBluetoothBox.addView(obdDeviceText)
        obdBluetoothBox.addView(Button(this).apply {
            text = "Buscar scanner cerca"
            setOnClickListener { searchScanner() }
        })
        obdBluetoothBox.addView(TextView(this).apply {
            text = "Con el scanner enchufado y el contacto puesto. Sirve aunque los ajustes de Bluetooth de la radio solo dejen emparejar teléfonos. Si Android pide una clave, es 1234 o 0000."
            setTextColor(Ui.SUB)
        })
        obdBluetoothBox.addView(Button(this).apply {
            text = "Elegir uno ya emparejado"
            setOnClickListener { pickBluetoothDevice() }
        })
        form.addView(obdBluetoothBox)

        obdWifiBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        obdWifiBox.addView(TextView(this).apply {
            text = "Conecta antes la radio a la red Wi-Fi del scanner. Mientras tanto la radio no tendrá internet por esa red."
            setTextColor(Ui.SUB)
        })
        val wifiHost = if (c.obd.kind == ObdLinkKind.WIFI) c.obd.address else ObdLinkConfig.DEFAULT_WIFI_HOST
        obdHost = EditText(this).apply {
            hint = "Dirección del scanner Wi-Fi"
            setText(wifiHost)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            setTextColor(Ui.TEXT)
            setHintTextColor(Ui.SUB)
        }
        obdPort = EditText(this).apply {
            hint = "Puerto"
            setText(c.obd.port.toString())
            inputType = InputType.TYPE_CLASS_NUMBER
            setTextColor(Ui.TEXT)
            setHintTextColor(Ui.SUB)
        }
        obdWifiBox.addView(obdHost, LinearLayout.LayoutParams(dp(420), LinearLayout.LayoutParams.WRAP_CONTENT))
        obdWifiBox.addView(obdPort, LinearLayout.LayoutParams(dp(420), LinearLayout.LayoutParams.WRAP_CONTENT))
        form.addView(obdWifiBox)

        obdTest = Button(this).apply {
            text = "Probar conexión"
            setOnClickListener { testObd() }
        }
        form.addView(obdTest)

        // Recién ahora (con todas las piezas creadas) se engancha el cambio de tipo.
        obdKind.setOnCheckedChangeListener { _, _ -> refreshObd() }
        refreshObd()
    }

    private fun selectedObdKind(): ObdLinkKind =
        ObdLinkKind.of(obdKind.findViewById<RadioButton>(obdKind.checkedRadioButtonId)?.tag as? String)

    private fun refreshObd() {
        val kind = selectedObdKind()
        obdBluetoothBox.visibility = if (kind == ObdLinkKind.BLUETOOTH) View.VISIBLE else View.GONE
        obdWifiBox.visibility = if (kind == ObdLinkKind.WIFI) View.VISIBLE else View.GONE
        obdTest.visibility = if (kind == ObdLinkKind.OFF) View.GONE else View.VISIBLE
        obdDeviceText.text = if (obdAddress.isEmpty()) "Scanner elegido: ninguno" else "Scanner elegido: " + obdName.ifBlank { obdAddress }
    }

    /** Lo que el usuario dejó en la pantalla, ya limpio. */
    private fun readObd(): ObdLinkConfig {
        val kind = selectedObdKind()
        val port = obdPort.text.toString().trim().toIntOrNull() ?: ObdLinkConfig.DEFAULT_WIFI_PORT
        return when (kind) {
            ObdLinkKind.WIFI -> ObdLinkConfig.fromStored(kind.id, obdHost.text.toString(), "", port)
            else -> ObdLinkConfig.fromStored(kind.id, obdAddress, obdName, port)
        }
    }

    /** Un mensaje si lo elegido no sirve para conectar; null si está bien. */
    private fun validateObd(): String? = when (selectedObdKind()) {
        ObdLinkKind.BLUETOOTH -> if (obdAddress.isEmpty()) "Elige primero el scanner Bluetooth, o marca «Sin scanner»." else null
        ObdLinkKind.WIFI ->
            if (!ObdLinkConfig.isValidHost(obdHost.text.toString())) "La dirección del scanner Wi-Fi no es válida (por ejemplo 192.168.0.10)."
            else if ((obdPort.text.toString().trim().toIntOrNull() ?: 0) !in 1..65535) "El puerto debe ser un número entre 1 y 65535."
            else null
        ObdLinkKind.OFF -> null
    }

    /** Desde Android 12 el permiso de Bluetooth se pide en pantalla; se sigue con [block] cuando el usuario lo da. */
    private fun withBluetoothPermission(block: () -> Unit) {
        if (ObdBluetooth.hasPermission(this)) {
            block()
            return
        }
        afterBluetoothPermission = block
        requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT), REQ_BLUETOOTH)
    }

    /** Pide los permisos para buscar scanners cerca (si faltan) y sigue con [block] cuando el usuario los da. */
    private fun withScanPermission(block: () -> Unit) {
        val missing = ObdBluetooth.missingScanPermissions(this)
        if (missing.isEmpty()) {
            block()
            return
        }
        afterScanPermission = block
        requestPermissions(missing, REQ_SCAN)
    }

    /** Busca scanners Bluetooth cerca y deja elegir uno, sin pasar por los ajustes de Bluetooth de la radio. */
    private fun searchScanner() = withScanPermission {
        discovery?.stop()
        val d = ObdDiscovery(this) { devices ->
            scanDialog?.dismiss()
            scanDialog = null
            if (!isFinishing) showScanResults(devices)
        }
        discovery = d
        if (!d.start()) {
            discovery = null
            Toast.makeText(this, "No se pudo buscar. Revisa que el Bluetooth de la radio esté encendido.", Toast.LENGTH_LONG).show()
            return@withScanPermission
        }
        scanDialog = AlertDialog.Builder(this)
            .setTitle("Buscando scanners cerca…")
            .setMessage("Unos 15 segundos. El scanner debe estar enchufado y con el contacto puesto, y sin otro aparato conectado a él.")
            .setNegativeButton("Terminar ya") { _, _ -> d.finish() }
            .setCancelable(false)
            .show()
    }

    private fun showScanResults(devices: List<PairedDevice>) {
        if (devices.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("No se encontró ninguno")
                .setMessage(
                    "Revisa que el scanner esté enchufado, con el contacto puesto, y que ningún teléfono esté conectado a él. " +
                        "Algunos scanners Bluetooth 4.0 (BLE) todavía no son compatibles: aparecen en el celular, pero no aquí.",
                )
                .setPositiveButton("Entendido", null)
                .show()
            return
        }
        val labels = devices.map { (if (it.looksLikeObd) "★ " else "") + it.name + "\n" + it.address }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Elige tu scanner OBD")
            .setItems(labels) { _, i ->
                obdName = devices[i].name
                obdAddress = devices[i].address
                refreshObd()
                Toast.makeText(this, "Listo. Pulsa «Probar conexión». Si Android pide una clave, es 1234 o 0000.", Toast.LENGTH_LONG).show()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    override fun onDestroy() {
        discovery?.stop()
        scanDialog?.dismiss()
        super.onDestroy()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_SCAN) {
            val next = afterScanPermission
            afterScanPermission = null
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                next?.invoke()
            } else {
                Toast.makeText(this, "Sin el permiso no se puede buscar el scanner.", Toast.LENGTH_LONG).show()
            }
            return
        }
        if (requestCode != REQ_BLUETOOTH) return
        val next = afterBluetoothPermission
        afterBluetoothPermission = null
        if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            next?.invoke()
        } else {
            Toast.makeText(this, "Sin el permiso de Bluetooth no se puede conectar el scanner.", Toast.LENGTH_LONG).show()
        }
    }

    private fun pickBluetoothDevice() = withBluetoothPermission {
        val paired = ObdBluetooth.paired(this)
        when {
            ObdBluetooth.adapter(this) == null -> Toast.makeText(this, "Esta radio no tiene Bluetooth.", Toast.LENGTH_LONG).show()
            paired == null -> Toast.makeText(this, "No se pudo leer la lista de Bluetooth. Revisa que esté encendido.", Toast.LENGTH_LONG).show()
            paired.isEmpty() -> AlertDialog.Builder(this)
                .setTitle("No hay dispositivos emparejados")
                .setMessage("Empareja primero el scanner desde los ajustes de Bluetooth de la radio (la clave suele ser 1234 o 0000) y vuelve aquí.")
                .setPositiveButton("Entendido", null)
                .show()
            else -> {
                val labels = paired.map { (if (it.looksLikeObd) "★ " else "") + it.name + "\n" + it.address }.toTypedArray()
                AlertDialog.Builder(this)
                    .setTitle("Elige tu scanner OBD")
                    .setItems(labels) { _, i ->
                        obdName = paired[i].name
                        obdAddress = paired[i].address
                        refreshObd()
                    }
                    .setNegativeButton("Cancelar", null)
                    .show()
            }
        }
    }

    private fun testObd() {
        validateObd()?.let {
            Toast.makeText(this, it, Toast.LENGTH_LONG).show()
            return
        }
        val config = readObd()
        if (config.kind == ObdLinkKind.BLUETOOTH) withBluetoothPermission { runObdTest(config) } else runObdTest(config)
    }

    private fun runObdTest(config: ObdLinkConfig) {
        val connector = ObdConnectors.create(this, config)
        if (connector == null) {
            Toast.makeText(this, "Elige primero un scanner.", Toast.LENGTH_LONG).show()
            return
        }
        val waiting = AlertDialog.Builder(this)
            .setTitle("Probando...")
            .setMessage("Conectando con ${config.display}. Puede tardar hasta unos 20 segundos.")
            .setCancelable(false)
            .show()
        Thread({
            val result = ObdCheck.run(connector) // fuera del hilo principal: espera al adaptador
            runOnUiThread {
                if (isFinishing) return@runOnUiThread
                waiting.dismiss()
                AlertDialog.Builder(this)
                    .setTitle(
                        when {
                            result.adapterOk && result.carOk -> "Todo bien"
                            result.adapterOk -> "El scanner responde, falta el auto"
                            else -> "No se pudo conectar"
                        },
                    )
                    .setMessage(result.report)
                    .setPositiveButton("Entendido", null)
                    .show()
            }
        }, "obd-prueba").start()
    }

    private companion object {
        const val REQ_BLUETOOTH = 71
        const val REQ_SCAN = 72
        const val COFFEE_URL = "https://ko-fi.com/daniexpress"
        const val REQ_PHOTO = 41
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
