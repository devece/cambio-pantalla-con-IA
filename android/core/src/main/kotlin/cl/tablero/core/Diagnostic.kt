package cl.tablero.core

/** Una sesión de medios activa en la radio (lo que Android sabe que está sonando o listo para sonar). */
data class MediaSessionInfo(val pkg: String, val playing: Boolean, val title: String?, val artist: String?)

/** Lo que se junta de la radio para el diagnóstico. No incluye nada personal: ni nombre, ni ubicación, ni patente. */
data class DiagnosticInfo(
    val appVersion: String,
    val androidRelease: String,
    val sdk: Int,
    val device: String,
    val screenWidthPx: Int,
    val screenHeightPx: Int,
    val densityDpi: Int,
    val webView: String?,
    val notificationAccess: Boolean,
    val locationPermission: Boolean,
    val isHomeApp: Boolean?,
    val sessions: List<MediaSessionInfo>,
    val assignedWheelKeys: Int,
    /** Códigos de tecla que llegaron mientras la pantalla de diagnóstico estaba abierta, del más viejo al más nuevo. */
    val keysSeen: List<Int>,
)

/**
 * El reporte de diagnóstico: un texto corto que se lee en la pantalla de la radio o se comparte, para saber qué hace
 * (y qué no) cada radio sin necesitar adb ni Android Studio. Sin Android, para poder probarlo en cualquier PC.
 */
object Diagnostic {
    fun report(info: DiagnosticInfo, keyName: (Int) -> String = { it.toString() }): String = buildString {
        fun line(label: String, value: String) = append(label).append(": ").append(value).append('\n')
        fun yesNo(b: Boolean) = if (b) "sí" else "no"

        appendLine("Diagnóstico de la radio")
        line("App", info.appVersion)
        line("Radio", info.device.ifBlank { "desconocida" })
        line("Android", "${info.androidRelease} (API ${info.sdk})")
        line("Pantalla", "${info.screenWidthPx} × ${info.screenHeightPx} px, ${info.densityDpi} dpi")
        line("WebView", info.webView ?: "no se pudo leer")
        line("Pantalla de inicio de Android", when (info.isHomeApp) { true -> "es Tablero"; false -> "es otra app"; null -> "no se pudo saber" })
        line("Permiso de ubicación", yesNo(info.locationPermission))
        line("Acceso a las notificaciones", yesNo(info.notificationAccess))

        appendLine()
        appendLine("Radio FM")
        if (!info.notificationAccess) {
            appendLine("No se puede saber: falta el acceso a las notificaciones (Ajustes de Android).")
        } else {
            val radios = info.sessions.filter { FmRadio.isRadioPackage(it.pkg) }
            if (radios.isEmpty()) {
                appendLine("Ninguna app de radio FM publica una sesión de medios. Con la radio FM sonando, vuelve a actualizar.")
                appendLine("Si sigue igual, esta radio no se puede controlar desde Tablero (solo abrir su app).")
            } else {
                radios.forEach {
                    val st = FmRadio.parse(it.title, it.artist)
                    val freq = st.frequency?.let { f -> "%.1f MHz".format(java.util.Locale.ROOT, f) } ?: "frecuencia no reconocida"
                    line("Reconocida", "${it.pkg} (${if (it.playing) "sonando" else "en pausa"}) · $freq" + if (st.name.isNotBlank()) " · ${st.name}" else "")
                }
            }
            appendLine("Sesiones de medios activas: ${info.sessions.size}")
            info.sessions.forEach {
                val what = listOfNotNull(it.title?.takeIf { t -> t.isNotBlank() }, it.artist?.takeIf { a -> a.isNotBlank() }).joinToString(" — ")
                appendLine("  · ${it.pkg} (${if (it.playing) "sonando" else "en pausa"})" + if (what.isNotEmpty()) " «$what»" else "")
            }
        }

        appendLine()
        appendLine("Botones del volante")
        line("Asignados en Ajustes", info.assignedWheelKeys.toString())
        if (info.keysSeen.isEmpty()) {
            appendLine("No llegó ninguna tecla. Aprieta un botón del volante con esta pantalla abierta: si no aparece, esa radio no lo manda como tecla.")
        } else {
            line("Teclas recibidas", info.keysSeen.joinToString(", ") { "${keyName(it)} ($it)" })
        }
    }.trimEnd()
}
