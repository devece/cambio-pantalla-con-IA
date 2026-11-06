package cl.tablero.core

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DiagnosticTest {
    private val base = DiagnosticInfo(
        appVersion = "1.2.3", androidRelease = "13", sdk = 33, device = "Unisoc YT7311B",
        screenWidthPx = 1280, screenHeightPx = 720, densityDpi = 240, webView = "120.0.6099.43",
        notificationAccess = true, locationPermission = true, isHomeApp = true,
        sessions = emptyList(), assignedWheelKeys = 0, keysSeen = emptyList(),
    )

    @Test
    fun `incluye los datos de la radio`() {
        val r = Diagnostic.report(base)
        listOf("Diagnóstico de la radio", "App: 1.2.3", "Radio: Unisoc YT7311B", "Android: 13 (API 33)", "1280 × 720 px, 240 dpi", "WebView: 120.0.6099.43", "es Tablero").forEach {
            assertTrue(it in r, "falta «$it»")
        }
    }

    @Test
    fun `sin acceso a las notificaciones dice que no se puede saber la radio FM`() {
        val r = Diagnostic.report(base.copy(notificationAccess = false))
        assertTrue("No se puede saber: falta el acceso a las notificaciones" in r)
        assertFalse("Sesiones de medios activas" in r)
    }

    @Test
    fun `sin sesion de radio FM explica que solo se puede abrir su app`() {
        val r = Diagnostic.report(base.copy(sessions = listOf(MediaSessionInfo("com.spotify.music", true, "Canción", "Artista"))))
        assertTrue("Ninguna app de radio FM publica una sesión" in r)
        assertTrue("solo abrir su app" in r)
        assertTrue("com.spotify.music (sonando) «Canción — Artista»" in r)
    }

    @Test
    fun `reconoce la radio FM y su emisora`() {
        val r = Diagnostic.report(base.copy(sessions = listOf(MediaSessionInfo("com.android.fmradio", true, "FM 96.1 MHz", "Radio Futuro"))))
        assertTrue("Reconocida: com.android.fmradio (sonando) · 96.1 MHz · Radio Futuro" in r, r)
        assertFalse("Ninguna app de radio FM" in r)
    }

    @Test
    fun `una radio FM sin frecuencia legible lo dice`() {
        val r = Diagnostic.report(base.copy(sessions = listOf(MediaSessionInfo("com.nwd.radio", false, "Sintonizando", null))))
        assertTrue("frecuencia no reconocida" in r)
        assertTrue("en pausa" in r)
    }

    @Test
    fun `lista las teclas que llegaron con su nombre y su codigo`() {
        val names = mapOf(87 to "MEDIA_NEXT", 88 to "MEDIA_PREVIOUS")
        val r = Diagnostic.report(base.copy(keysSeen = listOf(87, 88)), keyName = { names[it] ?: "?" })
        assertTrue("Teclas recibidas: MEDIA_NEXT (87), MEDIA_PREVIOUS (88)" in r)
    }

    @Test
    fun `sin teclas explica que la radio puede no mandarlas`() {
        assertTrue("esa radio no lo manda como tecla" in Diagnostic.report(base))
    }

    @Test
    fun `los datos que faltan no rompen el reporte`() {
        val r = Diagnostic.report(base.copy(device = "", webView = null, isHomeApp = null))
        assertTrue("Radio: desconocida" in r)
        assertTrue("WebView: no se pudo leer" in r)
        assertTrue("no se pudo saber" in r)
    }

    @Test
    fun `no lleva datos personales`() {
        val r = Diagnostic.report(base.copy(assignedWheelKeys = 3))
        listOf("nombre", "patente", "latitud", "longitud").forEach { assertFalse(it in r.lowercase(), it) }
        assertTrue("Asignados en Ajustes: 3" in r)
    }
}
