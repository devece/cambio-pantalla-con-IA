package cl.tablero.launcher

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import cl.tablero.core.ActivationResult
import cl.tablero.core.LicenseCode

/** Pide el código TBL-XXXX-XXXX-XXXX, lo valida con el servidor y guarda el nivel desbloqueado. */
class ActivationActivity : Activity() {
    private lateinit var input: EditText
    private lateinit var status: TextView
    private lateinit var button: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#101418"))
            setPadding(dp(64), dp(32), dp(64), dp(32))
        }
        root.addView(TextView(this).apply {
            text = "Activar código"
            textSize = 28f
            setTextColor(Color.WHITE)
        })
        input = EditText(this).apply {
            hint = "TBL-XXXX-XXXX-XXXX"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#6B7480"))
            textSize = 24f
        }
        root.addView(input, LinearLayout.LayoutParams(dp(420), LinearLayout.LayoutParams.WRAP_CONTENT))
        status = TextView(this).apply {
            textSize = 16f
            setTextColor(Color.parseColor("#F2B8B5"))
        }
        root.addView(status)
        button = Button(this).apply {
            text = "Activar"
            setOnClickListener { submit() }
        }
        root.addView(button)
        setContentView(root)
    }

    private fun submit() {
        val code = LicenseCode.normalize(input.text.toString())
        if (code == null) {
            status.text = "Ese código no tiene el formato correcto (TBL-XXXX-XXXX-XXXX)."
            return
        }
        status.text = "Verificando…"
        button.isEnabled = false
        val deviceId = LicenseClient.deviceId(this)

        Thread {
            val result = LicenseClient.activate(code, deviceId)
            // Baja el diseño del cliente (y su foto) ahora, mientras hay internet; después todo funciona sin conexión.
            if (result is ActivationResult.Activated) {
                try {
                    val store = DesignStore(this)
                    if (result.design != null) store.save(result.design) else store.clear()
                } catch (e: Exception) {
                    // sin diseño la app igual queda activada, con su pantalla de siempre
                }
            }
            runOnUiThread {
                if (isFinishing) return@runOnUiThread
                button.isEnabled = true
                when (result) {
                    is ActivationResult.Activated -> {
                        EntitlementStore(this).activate(result.tier, code)
                        Toast.makeText(this, "¡Listo! Código activado.", Toast.LENGTH_LONG).show()
                        finish()
                    }
                    is ActivationResult.Rejected -> status.text = result.message
                    is ActivationResult.Unavailable -> status.text = result.message
                }
            }
        }.start()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
