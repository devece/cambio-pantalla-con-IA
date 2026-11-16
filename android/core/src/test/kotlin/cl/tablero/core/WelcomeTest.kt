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

    @Test
    fun `sin apariencia elegida el perfil no lleva estilo`() {
        assertFalse(JSONObject(Welcome.profileJson(LauncherConfig())).getJSONObject("profile").has("style"))
    }

    @Test
    fun `la apariencia elegida viaja en el perfil y lo invalido se descarta`() {
        val look = Look(theme = "costa", layout = "minimal", scene = "nieve", sceneTime = "noche", season = "navidad", accent = "#4cc9f0")
        val style = JSONObject(Welcome.profileJson(LauncherConfig(look = look))).getJSONObject("profile").getJSONObject("style")
        assertEquals("costa", style.getString("theme"))
        assertEquals("minimal", style.getString("layout"))
        assertEquals("noche", style.getString("sceneTime"))
        assertEquals("navidad", style.getString("season"))
        assertEquals("#4cc9f0", style.getString("accent"))
        assertFalse(style.has("scene"), "«nieve» todavía no se ofrece en la app y se descarta")
    }

    @Test
    fun `un color de acento que no es de la lista se descarta`() {
        assertEquals("", Look(accent = "#123456").cleaned().accent)
        assertEquals("#06d6a0", Look(accent = "#06d6a0").cleaned().accent)
    }

    @Test
    fun `la foto elegida viaja aparte del perfil y sin foto no hay clave`() {
        val con = JSONObject(Welcome.profileJson(LauncherConfig(), "file:///x/car.png", true)).getJSONObject("photo")
        assertEquals("file:///x/car.png", con.getString("day"))
        assertTrue(con.getBoolean("cut"))
        assertFalse(JSONObject(Welcome.profileJson(LauncherConfig())).has("photo"))
        assertFalse(JSONObject(Welcome.profileJson(LauncherConfig(), "")).has("photo"), "una dirección vacía no cuenta")
    }

    @Test
    fun `la version de noche solo viaja si la foto esta recortada`() {
        val con = JSONObject(Welcome.profileJson(LauncherConfig(), "file:///x/car.png", true, "file:///x/night.png")).getJSONObject("photo")
        assertEquals("file:///x/night.png", con.getString("night"))
        val sin = JSONObject(Welcome.profileJson(LauncherConfig(), "file:///x/car.jpg", false, "file:///x/night.png")).getJSONObject("photo")
        assertFalse(sin.has("night"))
    }

    @Test
    fun `la hora de 12 horas viaja en el perfil solo si se eligio`() {
        assertFalse(JSONObject(Welcome.profileJson(LauncherConfig())).getJSONObject("profile").has("clock12"))
        assertTrue(JSONObject(Welcome.profileJson(LauncherConfig(clock12 = true))).getJSONObject("profile").getBoolean("clock12"))
    }

    @Test
    fun `lo de Ajustes manda sobre los valores del diseno bajado del sitio`() {
        val diseno = """{"config":{"car":{"name":"Ana"},"features":{"oilEvery":5000,"speedShow":false,"restrDigit":"3"}}}"""
        val c = LauncherConfig(oilEvery = 10000, lastOil = 180000, odometer = 186000, otherMaintenance = listOf("filtro"))
        val f = JSONObject(Welcome.withFeatures(diseno, c)).getJSONObject("config")
        assertEquals(10000, f.getJSONObject("features").getInt("oilEvery"))
        assertEquals(listOf("filtro"), (0 until f.getJSONObject("features").getJSONArray("others").length()).map { f.getJSONObject("features").getJSONArray("others").getString(it) })
        assertEquals("Ana", f.getJSONObject("car").getString("name"), "lo demás del diseño no se toca")
    }

    @Test
    fun `un diseno sin features o ilegible no rompe`() {
        assertEquals(10000, JSONObject(Welcome.withFeatures("{}", LauncherConfig(oilEvery = 10000))).getJSONObject("config").getJSONObject("features").getInt("oilEvery"))
        assertEquals("esto no es json", Welcome.withFeatures("esto no es json", LauncherConfig()))
    }

    @Test
    fun `el movimiento elegido viaja en las opciones y un valor invalido vuelve al de fabrica`() {
        assertEquals("completo", Welcome.featuresJson(LauncherConfig(motion = "completo")).getString("motion"))
        assertEquals("vivo", Welcome.featuresJson(LauncherConfig()).getString("motion"))
        assertEquals("vivo", Motion.clean("x"))
        assertEquals("vivo", Motion.clean(null))
        assertEquals("off", Motion.clean("off"))
    }

    @Test
    fun `con el pais Otro nunca viaja una restriccion hacia la pantalla`() {
        val otro = Welcome.featuresJson(LauncherConfig(country = "OTRO", restrOn = true, restrDigit = "8", restrDays = listOf("lun")))
        assertFalse(otro.getBoolean("restrOn"))
        assertEquals("", otro.getString("restrDigit"))
        assertEquals(0, otro.getJSONArray("restrDays").length())
        val cl = Welcome.featuresJson(LauncherConfig(country = "CL", restrOn = true, restrDigit = "8", restrDays = listOf("lun")))
        assertTrue(cl.getBoolean("restrOn"))
        assertEquals("8", cl.getString("restrDigit"))
    }

    @Test
    fun `la foto trasera viaja a la pantalla solo si existe`() {
        val con = JSONObject(Welcome.profileJson(LauncherConfig(), rearPhotoUrl = "file:///x/atras.png"))
        assertEquals("file:///x/atras.png", con.getJSONObject("photoRear").getString("day"))
        assertFalse(JSONObject(Welcome.profileJson(LauncherConfig())).has("photoRear"))
        assertFalse(JSONObject(Welcome.profileJson(LauncherConfig(), rearPhotoUrl = "")).has("photoRear"), "una dirección vacía no cuenta")
        // También con un diseño bajado del sitio: la foto de la radio se suma sin tocar el resto.
        val diseno = """{"v":1,"config":{"car":{"name":"Ana"}},"photo":{"day":"file:///x/lado.png","cut":true}}"""
        val out = JSONObject(Welcome.withFeatures(diseno, LauncherConfig(), "file:///x/atras.png"))
        assertEquals("file:///x/atras.png", out.getJSONObject("photoRear").getString("day"))
        assertEquals("file:///x/lado.png", out.getJSONObject("photo").getString("day"))
        assertFalse(JSONObject(Welcome.withFeatures(diseno, LauncherConfig())).has("photoRear"))
    }
}
