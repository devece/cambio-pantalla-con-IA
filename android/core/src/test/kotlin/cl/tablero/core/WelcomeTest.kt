package cl.tablero.core

import org.json.JSONObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WelcomeTest {
    @Test
    fun `se muestra en la primera vez de quien no ha configurado nada`() {
        assertTrue(Welcome.shouldShow(LauncherConfig(), hasDesign = false))
    }

    @Test
    fun `no se muestra otra vez una vez terminado u omitido`() {
        assertFalse(Welcome.shouldShow(LauncherConfig(welcomeDone = true), hasDesign = false))
    }

    @Test
    fun `no molesta a quien ya activo un codigo ni a quien ya puso su nombre`() {
        assertFalse(Welcome.shouldShow(LauncherConfig(), hasDesign = true))
        assertFalse(Welcome.shouldShow(LauncherConfig(name = "Daniel"), hasDesign = false))
        assertTrue(Welcome.shouldShow(LauncherConfig(name = "   "), hasDesign = false), "un nombre en blanco no cuenta")
    }

    @Test
    fun `el nombre se recorta y no pasa del maximo`() {
        assertEquals("Daniel", Welcome.cleanName("  Daniel  "))
        assertEquals(Welcome.NAME_MAX, Welcome.cleanName("x".repeat(80)).length)
    }

    @Test
    fun `el perfil para la pantalla lleva nombre, saludo, frase, auto y color`() {
        val p = JSONObject(Welcome.profileJson(LauncherConfig(name = "Daniel \"Dani\"", greeting = "Wena", phrase = "Buen viaje", carType = "suv", carColor = "#1f5fa8")))
            .getJSONObject("profile")
        assertEquals("Daniel \"Dani\"", p.getString("name"))
        assertEquals("Wena", p.getString("greeting"))
        assertEquals("Buen viaje", p.getString("phrase"))
        assertEquals("suv", p.getString("type"))
        assertEquals("#1f5fa8", p.getString("color"))
    }

    @Test
    fun `el perfil no es un diseño y no trae config`() {
        assertFalse(JSONObject(Welcome.profileJson(LauncherConfig())).has("config"))
    }
}
