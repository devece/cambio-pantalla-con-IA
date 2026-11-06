package cl.tablero.core

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CoreTest {
    // El archivo real que comparten el sitio y la app.
    private val editions = Editions.parse(File(System.getProperty("editions.path")).readText())
    private val free = editions.limits(Tier.FREE)
    private val esencial = editions.limits(Tier.ESENCIAL)
    private val completo = editions.limits(Tier.COMPLETO)

    @Test
    fun `lee los tres niveles del editions json real`() {
        assertEquals(setOf("ruta", "sobrio"), free.themes)
        assertEquals(2, free.maxApps)
        assertEquals(setOf("sobrio", "ruta", "papel", "cristal"), esencial.themes)
        assertNull(completo.themes, "Completo no tiene límite de temas")
        assertNull(completo.seasons)
    }

    @Test
    fun `gratis deja pasar lo basico sin cambios`() {
        val c = LauncherConfig()
        assertEquals(c, Gate.apply(c, free))
        assertTrue(Gate.locked(c, free).isEmpty())
    }

    @Test
    fun `gratis cambia un tema de pago por el permitido`() {
        val r = Gate.apply(LauncherConfig(theme = "patagonia", layout = "manejo", scene = "costa", season = "navidad"), free)
        assertEquals("ruta", r.theme)
        assertEquals("tarjetas", r.layout)
        assertEquals("tema", r.scene)
        assertEquals("ninguna", r.season)
    }

    @Test
    fun `gratis apaga las funciones de pago y limita las apps`() {
        val pedido = LauncherConfig(
            apps = listOf("maps", "waze", "spotify", "youtube"),
            speedAlert = "manual",
            otherMaintenance = listOf("filtro"),
            docsOn = true, fuelOn = true, restrOn = true, voice = true,
        )
        val r = Gate.apply(pedido, free)
        assertEquals(listOf("maps", "waze"), r.apps)
        assertEquals("auto", r.speedAlert)
        assertTrue(r.otherMaintenance.isEmpty())
        assertFalse(r.docsOn || r.fuelOn || r.restrOn || r.voice)
        assertEquals(listOf("docs", "fuel", "restr", "voice", "maintenance"), Gate.locked(pedido, free))
    }

    @Test
    fun `el candado no repite apps`() {
        assertEquals(listOf("maps"), Gate.apply(LauncherConfig(apps = listOf("maps", "maps")), free).apps)
    }

    @Test
    fun `esencial permite documentos pero no los temas ni temporadas de Completo`() {
        val pedido = LauncherConfig(theme = "cordillera", season = "patrias", docsOn = true, voice = true)
        val r = Gate.apply(pedido, esencial)
        assertEquals("sobrio", r.theme)
        assertEquals("ninguna", r.season)
        assertTrue(r.docsOn && r.voice)
        assertEquals(listOf("theme", "season"), Gate.locked(pedido, esencial))
    }

    @Test
    fun `completo no recorta nada`() {
        val pedido = LauncherConfig(
            theme = "patagonia", season = "navidad", docsOn = true, fuelOn = true, restrOn = true, voice = true,
            apps = listOf("maps", "waze", "spotify", "youtube"), speedAlert = "manual", otherMaintenance = listOf("filtro"),
        )
        assertEquals(pedido, Gate.apply(pedido, completo))
        assertTrue(Gate.locked(pedido, completo).isEmpty())
    }

    @Test
    fun `codigo de licencia acepta minusculas y sin guiones`() {
        assertEquals("TBL-ABCD-EFGH-JKMN", LicenseCode.normalize("tbl-abcd-efgh-jkmn"))
        assertEquals("TBL-ABCD-EFGH-JKMN", LicenseCode.normalize(" TBLABCDEFGHJKMN "))
        assertEquals("TBL-2345-6789-ABCD", LicenseCode.normalize("tbl 2345 6789 abcd"))
    }

    @Test
    fun `codigo de licencia rechaza lo que no es un codigo`() {
        assertNull(LicenseCode.normalize(null))
        assertNull(LicenseCode.normalize(""))
        assertNull(LicenseCode.normalize("TBL-ABCD-EFGH"))
        assertNull(LicenseCode.normalize("XYZ-ABCD-EFGH-JKMN"))
        assertNull(LicenseCode.normalize("TBL-0000-EFGH-JKMN"), "el 0 no existe en el alfabeto")
        assertNull(LicenseCode.normalize("TBL-ABCI-EFGH-JKMN"), "la I no existe en el alfabeto")
    }

    @Test
    fun `respuesta 200 con plan activa ese nivel`() {
        val r = ActivationResponse.parse(200, """{"ok":true,"status":"active","plan":"completo"}""")
        assertEquals(ActivationResult.Activated(Tier.COMPLETO), r)
    }

    @Test
    fun `respuesta 200 sin plan o con plan raro no activa nada`() {
        assertIs<ActivationResult.Unavailable>(ActivationResponse.parse(200, """{"ok":true,"status":"active"}"""))
        assertIs<ActivationResult.Unavailable>(ActivationResponse.parse(200, """{"ok":true,"plan":"gratis"}"""))
        assertIs<ActivationResult.Unavailable>(ActivationResponse.parse(200, """{"ok":true,"plan":"free"}"""))
    }

    @Test
    fun `codigo invalido o en otra radio se rechaza con el mensaje del servidor`() {
        val r = ActivationResponse.parse(409, """{"ok":false,"error":"Este código ya está activo en otra radio."}""")
        assertEquals(ActivationResult.Rejected("Este código ya está activo en otra radio."), r)
        assertIs<ActivationResult.Rejected>(ActivationResponse.parse(404, """{"ok":false,"error":"Código no válido."}"""))
        assertIs<ActivationResult.Rejected>(ActivationResponse.parse(400, null))
    }

    @Test
    fun `fallas temporales se pueden reintentar`() {
        assertIs<ActivationResult.Unavailable>(ActivationResponse.parse(429, """{"ok":false,"error":"Demasiados intentos."}"""))
        assertIs<ActivationResult.Unavailable>(ActivationResponse.parse(502, "<html>Bad gateway</html>"))
        val sinCuerpo = ActivationResponse.parse(503, null)
        assertNotNull(sinCuerpo)
        assertIs<ActivationResult.Unavailable>(sinCuerpo)
    }
}
