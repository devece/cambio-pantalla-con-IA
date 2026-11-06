package cl.tablero.launcher

import android.app.Activity
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import cl.tablero.core.LauncherConfig
import cl.tablero.core.Welcome

/**
 * Primer arranque, en tres pasos y todo opcional: 1) tu auto (nombre, tipo y color), 2) tu aceite (kilometraje y último cambio)
 * y 3) hacer de LiteDash la pantalla de inicio de la radio. Se puede omitir en cualquier momento y todo se cambia después en Ajustes.
 */
class WelcomeActivity : Activity() {
    private lateinit var settings: SettingsStore
    private lateinit var config: LauncherConfig
    private lateinit var body: LinearLayout
    private lateinit var back: Button
    private lateinit var next: Button
    private lateinit var dots: TextView

    private var step = 0
    private var carType = ""
    private var carColor = ""
    private lateinit var nameField: EditText
    private lateinit var odometerField: EditText
    private lateinit var lastOilField: EditText
    private val colorChips = linkedMapOf<String, View>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = SettingsStore(this)
        config = settings.load()
        carType = config.carType
        carColor = if (config.carColor in Ui.CAR_COLORS) config.carColor else Ui.CAR_COLORS.first()
        step = savedInstanceState?.getInt(KEY_STEP, 0)?.coerceIn(0, LAST) ?: 0

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Ui.BG)
            setPadding(dp(48), dp(24), dp(48), dp(24))
        }
        val top = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        top.addView(TextView(this).apply {
            text = "Bienvenido"
            textSize = 30f
            setTextColor(Ui.TEXT)
            typeface = Ui.DISPLAY
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        dots = TextView(this).apply {
            textSize = 16f
            setTextColor(Ui.SUB)
        }
        top.addView(dots)
        root.addView(top)

        body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(ScrollView(this).apply { addView(body) }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        val buttons = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        buttons.addView(Button(this).apply {
            text = "Omitir"
            setOnClickListener { finishWelcome(save = false) }
        })
        buttons.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        back = Button(this).apply {
            text = "Atrás"
            setOnClickListener { go(step - 1) }
        }
        buttons.addView(back)
        next = Button(this).apply { setOnClickListener { if (step == LAST) finishWelcome(save = true) else go(step + 1) } }
        buttons.addView(next)
        root.addView(buttons)

        setContentView(root)
        show()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_STEP, step)
    }

    /** Atrás vuelve un paso; en el primero es lo mismo que omitir. */
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (step > 0) go(step - 1) else finishWelcome(save = false)
    }

    // Al volver del diálogo del sistema para elegir la pantalla de inicio, se vuelve a mirar si ya quedó.
    override fun onResume() {
        super.onResume()
        if (::body.isInitialized && step == LAST) show()
    }

    private fun go(to: Int) {
        keepFields()
        step = to.coerceIn(0, LAST)
        show()
    }

    /** Guarda en memoria lo escrito en este paso, para que no se pierda al ir y volver. */
    private fun keepFields() {
        if (step == 0 && ::nameField.isInitialized) config = config.copy(name = Welcome.cleanName(nameField.text.toString()))
        if (step == 1 && ::odometerField.isInitialized) {
            config = config.copy(odometer = number(odometerField, config.odometer), lastOil = number(lastOilField, config.lastOil))
        }
    }

    private fun number(e: EditText, default: Int) = e.text.toString().trim().toIntOrNull()?.coerceIn(0, 999_999) ?: default

    private fun show() {
        body.removeAllViews()
        dots.text = "Paso ${step + 1} de ${LAST + 1}"
        back.visibility = if (step == 0) View.INVISIBLE else View.VISIBLE
        next.text = if (step == LAST) "Listo" else "Siguiente"
        when (step) {
            0 -> stepCar()
            1 -> stepOil()
            else -> stepHome()
        }
    }

    private fun stepCar() {
        heading("Tu auto")
        hint("Así te saluda la pantalla y se ve tu auto. Todo se puede cambiar después en Ajustes.")
        nameField = EditText(this).apply {
            hint = "Tu nombre"
            setText(config.name)
            setTextColor(Ui.TEXT)
            setHintTextColor(Ui.SUB)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
            textSize = 22f
        }
        body.addView(nameField)
        val types = RadioGroup(this).apply {
            orientation = RadioGroup.HORIZONTAL
            Ui.CAR_TYPES.forEach { (type, label) ->
                addView(RadioButton(this@WelcomeActivity).apply {
                    id = View.generateViewId()
                    text = label
                    isChecked = carType == type
                    setTextColor(Ui.TEXT)
                    setOnClickListener { carType = type }
                })
            }
        }
        body.addView(types, margins(top = 16))
        val swatches = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        colorChips.clear()
        Ui.CAR_COLORS.forEach { hex ->
            val chip = View(this)
            colorChips[hex] = chip
            chip.setOnClickListener {
                carColor = hex
                paintChips()
            }
            swatches.addView(chip, LinearLayout.LayoutParams(dp(48), dp(48)).apply { setMargins(0, dp(8), dp(12), dp(4)) })
        }
        body.addView(swatches)
        paintChips()
    }

    private fun paintChips() {
        colorChips.forEach { (hex, chip) ->
            chip.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(android.graphics.Color.parseColor(hex))
                setStroke(dp(if (hex == carColor) 4 else 1), if (hex == carColor) Ui.ACCENT else Ui.LINE)
            }
        }
    }

    private fun stepOil() {
        heading("Tu aceite")
        hint("Para avisarte cuándo toca el cambio. Puedes dejarlo en blanco y completarlo después.")
        odometerField = numberField("Kilometraje actual (km)", config.odometer)
        lastOilField = numberField("Último cambio de aceite a los (km)", config.lastOil)
        body.addView(odometerField)
        body.addView(lastOilField)
    }

    private fun stepHome() {
        heading("Tu pantalla de inicio")
        val isHome = isHomeApp()
        hint(
            if (isHome) "Listo: LiteDash ya es la pantalla de inicio de tu radio."
            else "Para que LiteDash abra al encender la radio, elígelo como pantalla de inicio. En algunas radios hay que marcar \"Siempre\".",
        )
        if (!isHome) {
            body.addView(Button(this).apply {
                text = "Elegir pantalla de inicio"
                setOnClickListener { chooseHome() }
            }, margins(top = 16))
        }
    }

    /** ¿LiteDash ya es la pantalla de inicio? Desde Android 10 se pregunta por el rol; antes, quién resuelve la acción HOME. */
    private fun isHomeApp(): Boolean {
        if (Build.VERSION.SDK_INT >= 29) {
            runCatching { return getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_HOME) }
        }
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolved = packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        return resolved?.activityInfo?.packageName == packageName
    }

    private fun chooseHome() {
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                startActivity(getSystemService(RoleManager::class.java).createRequestRoleIntent(RoleManager.ROLE_HOME))
            } else {
                startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
            }
        } catch (e: Exception) {
            // Muchas radios no traen ese diálogo: se abre Ajustes de Android o, si tampoco, se explica el camino.
            try {
                startActivity(Intent(Settings.ACTION_SETTINGS))
            } catch (e2: Exception) {
                Toast.makeText(this, "En Ajustes de la radio busca \"Aplicaciones\" y \"Pantalla de inicio\".", Toast.LENGTH_LONG).show()
            }
        }
    }

    /** Guarda lo contado (si no se omite) y marca el asistente como hecho para que no vuelva a salir. */
    private fun finishWelcome(save: Boolean) {
        keepFields()
        val out = if (save) config.copy(carType = carType, carColor = carColor) else settings.load()
        settings.save(out.copy(welcomeDone = true))
        finish()
    }

    /* ---------- piezas ---------- */

    private fun heading(s: String) = body.addView(TextView(this).apply {
        text = s
        textSize = 24f
        setTextColor(Ui.ACCENT)
        typeface = Ui.DISPLAY
        setPadding(0, dp(20), 0, dp(4))
    })

    private fun hint(s: String) = body.addView(TextView(this).apply {
        text = s
        textSize = 17f
        setTextColor(Ui.SUB)
        setPadding(0, 0, 0, dp(12))
    })

    private fun numberField(hint: String, value: Int) = EditText(this).apply {
        this.hint = hint
        if (value > 0) setText(value.toString())
        setTextColor(Ui.TEXT)
        setHintTextColor(Ui.SUB)
        inputType = InputType.TYPE_CLASS_NUMBER
        textSize = 20f
    }

    private fun margins(top: Int) = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(top) }

    private fun dp(v: Int) = Ui.dp(this, v)

    private companion object {
        const val LAST = 2
        const val KEY_STEP = "step"
    }
}
