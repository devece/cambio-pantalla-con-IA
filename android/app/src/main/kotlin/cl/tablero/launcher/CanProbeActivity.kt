package cl.tablero.launcher

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import cl.tablero.core.CanCandidate
import cl.tablero.core.CanProbe
import cl.tablero.core.CanProbeInfo
import cl.tablero.core.CapturedBroadcast
import cl.tablero.core.SerialPortInfo
import java.io.File
import java.util.zip.ZipFile

/**
 * Prueba si la radio comparte los datos del auto (RPM, clima, puertas) con otras apps. Busca las apps de CanBus, lista
 * lo que ofrecen y escucha por unos minutos los avisos que mandan. Solo lee: no guarda nada y no manda nada solo.
 */
class CanProbeActivity : Activity() {
    private lateinit var out: TextView
    private var candidates: List<CanCandidate> = emptyList()
    private var ports: List<SerialPortInfo> = emptyList()
    private var listening = 0
    private val counts = LinkedHashMap<String, Int>()
    private val lastExtras = HashMap<String, List<String>>()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action ?: return
            counts[action] = (counts[action] ?: 0) + 1
            lastExtras[action] = extrasOf(intent)
            out.removeCallbacks(refreshTask)
            out.postDelayed(refreshTask, 300)
        }
    }
    private val refreshTask = Runnable { refresh() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Ui.BG)
            setPadding(dp(48), dp(24), dp(48), dp(24))
        }
        root.addView(TextView(this).apply {
            text = "Datos del auto (CAN bus)"
            textSize = 30f
            setTextColor(Ui.TEXT)
            typeface = Ui.DISPLAY
        })
        root.addView(TextView(this).apply {
            text = "Deja esta pantalla abierta, sube las RPM, cambia el clima y abre una puerta. Después toca Copiar o Compartir y mándaselo a quien te lo pidió."
            textSize = 16f
            setTextColor(Ui.SUB)
            setPadding(0, dp(4), 0, dp(12))
        })
        out = TextView(this).apply {
            typeface = android.graphics.Typeface.MONOSPACE
            textSize = 15f
            setTextColor(Ui.TEXT)
            setTextIsSelectable(true)
            text = "Buscando…"
        }
        root.addView(ScrollView(this).apply { addView(out) }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        val buttons = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        fun button(label: String, onClick: () -> Unit) = buttons.addView(Button(this).apply {
            text = label
            setOnClickListener { onClick() }
        })
        button("Actualizar") { refresh() }
        button("Copiar") { copy() }
        button("Compartir") { share() }
        button("Volver") { finish() }
        root.addView(buttons)
        setContentView(root)

        // Buscar apps y leer manifiestos puede tardar en una radio lenta: se hace aparte para no congelar la pantalla.
        Thread {
            val found = runCatching { findCandidates() }.getOrDefault(emptyList())
            val serial = runCatching { findPorts() }.getOrDefault(emptyList())
            runOnUiThread {
                if (isFinishing) return@runOnUiThread
                candidates = found
                ports = serial
                startListening()
                refresh()
            }
        }.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        runCatching { unregisterReceiver(receiver) }
    }

    private fun startListening() {
        val actions = candidates.flatMap { it.actions }.distinct().take(MAX_LISTEN)
        if (actions.isEmpty()) return
        val filter = IntentFilter().apply { actions.forEach { addAction(it) } }
        try {
            // Android 14 exige decir si el receptor acepta avisos de otras apps; aquí es justo lo que se quiere.
            if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED) else registerReceiver(receiver, filter)
            listening = actions.size
        } catch (e: Exception) {
            listening = 0
        }
    }

    private fun findCandidates(): List<CanCandidate> {
        val pm = packageManager
        return pm.getInstalledApplications(0)
            .filter { it.packageName != packageName && CanProbe.isCandidate(it.packageName, pm.getApplicationLabel(it).toString()) }
            .map { app ->
                val info = runCatching {
                    pm.getPackageInfo(app.packageName, PackageManager.GET_SERVICES or PackageManager.GET_RECEIVERS or PackageManager.GET_PROVIDERS)
                }.getOrNull()
                CanCandidate(
                    pkg = app.packageName,
                    label = pm.getApplicationLabel(app).toString(),
                    system = app.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0,
                    exportedServices = info?.services?.filter { it.exported }?.map { it.name }.orEmpty(),
                    exportedReceivers = info?.receivers?.filter { it.exported }?.map { it.name }.orEmpty(),
                    providers = info?.providers?.filter { it.exported }?.mapNotNull { it.authority }.orEmpty(),
                    actions = manifestActions(app.sourceDir),
                )
            }
            .sortedBy { it.label.lowercase() }
    }

    private fun manifestActions(apk: String?): List<String> = try {
        if (apk == null) emptyList() else ZipFile(apk).use { zip ->
            val entry = zip.getEntry("AndroidManifest.xml") ?: return emptyList()
            CanProbe.manifestActions(zip.getInputStream(entry).use { it.readBytes() })
        }
    } catch (e: Exception) {
        emptyList()
    }

    private fun findPorts(): List<SerialPortInfo> =
        File("/dev").listFiles().orEmpty()
            .filter { CanProbe.isSerialName(it.name) }
            .sortedBy { it.name }
            .map { SerialPortInfo(it.path, it.canRead(), it.canWrite()) }

    private fun extrasOf(intent: Intent): List<String> {
        val e = intent.extras ?: return emptyList()
        return e.keySet().take(MAX_EXTRAS).map { k ->
            @Suppress("DEPRECATION")
            val v = e.get(k)
            val text = when (v) {
                is IntArray -> v.joinToString(",", "[", "]")
                is ByteArray -> v.joinToString(" ") { "%02X".format(it) }
                is FloatArray -> v.joinToString(",", "[", "]")
                else -> v.toString()
            }
            "$k=${text.take(MAX_VALUE)}"
        }
    }

    private fun refresh() {
        out.text = CanProbe.report(
            CanProbeInfo(
                candidates = candidates,
                serialPorts = ports,
                captured = counts.map { (a, n) -> CapturedBroadcast(a, n, lastExtras[a].orEmpty()) },
                listening = listening,
            ),
        )
    }

    private fun copy() {
        try {
            (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Datos del auto de Tablero", out.text))
            Toast.makeText(this, "Copiado.", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "No se pudo copiar.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun share() {
        try {
            val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, out.text.toString())
            startActivity(Intent.createChooser(send, "Compartir el resultado"))
        } catch (e: Exception) {
            Toast.makeText(this, "Esta radio no tiene cómo compartir. Usa Copiar o toma una captura.", Toast.LENGTH_LONG).show()
        }
    }

    private fun dp(v: Int) = Ui.dp(this, v)

    private companion object {
        const val MAX_LISTEN = 200
        const val MAX_EXTRAS = 12
        const val MAX_VALUE = 60
    }
}
