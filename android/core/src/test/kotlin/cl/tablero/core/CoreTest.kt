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

    // Un nivel intermedio de ejemplo (lo que era el plan Esencial): no tiene todos los temas ni temporadas.
    private val intermedio = Limits(
        themes = linkedSetOf("sobrio", "ruta", "papel", "cristal"),
        layouts = null,
        scenes = null,
        seasons = linkedSetOf("ninguna"),
        maxApps = 4,
        oilOn = true,
        speedShow = true,
        speedAlerts = null,
        othersMaintenance = true,
        docsOn = true,
        fuelOn = true,
        restrOn = true,
        voice = true,
    )

    // Un nivel restringido, como lo era la edición gratis antes de que todo fuera gratis. Hoy ninguna edición lo usa, pero el
    // candado (Gate) sigue siendo la lógica que hay que tener probada por si algún día vuelve a haber un plan de pago.
    private val restringido = Limits(
        themes = linkedSetOf("ruta", "sobrio"),
        layouts = linkedSetOf("tarjetas", "minimal", "consola"),
        scenes = linkedSetOf("tema", "color"),
        seasons = linkedSetOf("ninguna"),
        maxApps = 2,
        oilOn = true,
        speedShow = true,
        speedAlerts = linkedSetOf("auto", "off"),
        othersMaintenance = false,
        docsOn = false,
        fuelOn = false,
        restrOn = false,
        voice = false,
    )

    @Test
    fun `lee los tres niveles del editions json real`() {
        // No hay planes: los tres niveles que la app reconoce no tienen límites y son iguales.
        // (Esencial y Completo existen solo por los códigos ya emitidos y las radios que ya activaron uno.)
        assertNull(free.themes, "gratis no tiene límite de temas")
        assertNull(free.speedAlerts)
        assertEquals(4, free.maxApps)
        assertEquals(free, esencial)
        assertEquals(free, completo)
    }

    @Test
    fun `gratis deja pasar todo sin cambios`() {
        val c = LauncherConfig()
        assertEquals(c, Gate.apply(c, free))
        assertTrue(Gate.locked(c, free).isEmpty())

        val todo = LauncherConfig(
            theme = "patagonia", layout = "manejo", scene = "costa", season = "navidad",
            apps = listOf("maps", "waze", "spotify", "youtube"),
            speedAlert = "manual", otherMaintenance = listOf("filtro"),
            docsOn = true, fuelOn = true, restrOn = true, voice = true,
        )
        assertEquals(todo, Gate.apply(todo, free), "nada de lo que elija el usuario se bloquea")
        assertTrue(Gate.locked(todo, free).isEmpty())
    }

    @Test
    fun `un nivel restringido cambia un tema no permitido por el permitido`() {
        val r = Gate.apply(LauncherConfig(theme = "patagonia", layout = "manejo", scene = "costa", season = "navidad"), restringido)
        assertEquals("ruta", r.theme)
        assertEquals("tarjetas", r.layout)
        assertEquals("tema", r.scene)
        assertEquals("ninguna", r.season)
    }

    @Test
    fun `un nivel restringido apaga las funciones no permitidas y limita las apps`() {
        val pedido = LauncherConfig(
            apps = listOf("maps", "waze", "spotify", "youtube"),
            speedAlert = "manual",
            otherMaintenance = listOf("filtro"),
            docsOn = true, fuelOn = true, restrOn = true, voice = true,
        )
        val r = Gate.apply(pedido, restringido)
        assertEquals(listOf("maps", "waze"), r.apps)
        assertEquals("auto", r.speedAlert)
        assertTrue(r.otherMaintenance.isEmpty())
        assertFalse(r.docsOn || r.fuelOn || r.restrOn || r.voice)
        assertEquals(listOf("docs", "fuel", "restr", "voice", "maintenance"), Gate.locked(pedido, restringido))
    }

    @Test
    fun `un codigo nunca da menos que la edicion gratis`() {
        // Con los datos reales (todo igual) activar un código no cambia nada ni quita nada.
        val efectivo = editions.effective(Tier.ESENCIAL)
        assertEquals(free, efectivo)
        val todo = LauncherConfig(theme = "patagonia", season = "navidad", apps = listOf("maps", "waze", "spotify", "youtube"), docsOn = true, voice = true)
        assertEquals(todo, Gate.apply(todo, efectivo))
        assertEquals(free, editions.effective(Tier.FREE))
    }

    @Test
    fun `aunque algun dia la edicion gratis fuera mas amplia que un plan, el plan no da menos`() {
        fun level(themes: String, docs: Boolean) =
            """{"themes":$themes,"layouts":"*","scenes":"*","seasons":["ninguna"],"maxApps":4,"features":{"oilOn":true,"speedShow":true,"speedAlerts":"*","othersMaintenance":true,"docsOn":$docs,"fuelOn":true,"restrOn":true,"voice":true}}"""
        val hipotetico = Editions.parse(
            """{"free":${level("[\"ruta\",\"papel\"]", false)},"esencial":${level("[\"sobrio\"]", true)},"completo":${level("\"*\"", true)}}""",
        )
        val e = hipotetico.effective(Tier.ESENCIAL)
        assertEquals(listOf("sobrio", "ruta", "papel"), e.themes!!.toList(), "los de su plan primero y los de gratis después")
        assertTrue(e.docsOn, "lo que el plan permite sigue permitido")
        assertEquals(hipotetico.limits(Tier.FREE), hipotetico.effective(Tier.FREE))
    }

    @Test
    fun `orMoreOf toma lo mas amplio de cada cosa y conserva el orden`() {
        val r = restringido.orMoreOf(intermedio)
        assertEquals(listOf("ruta", "sobrio", "papel", "cristal"), r.themes!!.toList(), "primero los de este nivel")
        assertNull(r.layouts, "sin límite en uno de los dos = sin límite")
        assertEquals(4, r.maxApps)
        assertTrue(r.docsOn && r.voice, "una función permitida en cualquiera queda permitida")
        // Un nivel restringido combinado con otro igual no cambia nada.
        assertEquals(restringido, restringido.orMoreOf(restringido))
        // Si los dos limitan, queda la unión.
        val otro = restringido.copy(themes = linkedSetOf("papel"), maxApps = 3, docsOn = true)
        val u = restringido.orMoreOf(otro)
        assertEquals(setOf("ruta", "sobrio", "papel"), u.themes)
        assertEquals(3, u.maxApps)
        assertTrue(u.docsOn)
        assertFalse(u.fuelOn)
    }

    @Test
    fun `el candado no repite apps`() {
        assertEquals(listOf("maps"), Gate.apply(LauncherConfig(apps = listOf("maps", "maps")), restringido).apps)
    }

    @Test
    fun `un nivel intermedio permite documentos pero no los temas ni temporadas extra`() {
        val pedido = LauncherConfig(theme = "cordillera", season = "patrias", docsOn = true, voice = true)
        val r = Gate.apply(pedido, intermedio)
        assertEquals("sobrio", r.theme)
        assertEquals("ninguna", r.season)
        assertTrue(r.docsOn && r.voice)
        assertEquals(listOf("theme", "season"), Gate.locked(pedido, intermedio))
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
}
