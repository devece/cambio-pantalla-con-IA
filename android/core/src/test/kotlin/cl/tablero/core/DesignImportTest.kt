package cl.tablero.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesignImportTest {
    private val sample = """
        {"v":1,
         "car":{"type":"4x4","color":"#1F5FA8","name":"  Dani  ","greeting":"Wena","phrase":"Buen viaje"},
         "style":{"theme":"costa","layout":"minimal","scene":"nieve","sceneTime":"noche","season":"navidad","accent":null,"apps":["maps","waze","maps","spotify","youtube","whatsapp"]},
         "features":{"oilOn":true,"odometer":52000,"lastOil":50000,"oilEvery":7500,"others":["filtro","bujias"],"docsOn":true,"rtMonth":6,
           "fuelOn":true,"tankL":55,"kmPerL":11,"speedShow":true,"speedAlert":"manual","manualLimit":50,"restrOn":true,"restrType":"sello",
           "restrDigit":"7","restrDays":["lun","jue","sab"],"voice":false}}
    """.trimIndent()

    @Test
    fun `aplica el diseno a la configuracion`() {
        val c = assertNotNull(DesignImport.apply(LauncherConfig(), sample))
        assertEquals("Dani", c.name)
        assertEquals("Wena", c.greeting)
        assertEquals("Buen viaje", c.phrase)
        assertEquals("camioneta", c.carType, "la 4x4 todavía no existe en la app")
        assertEquals("#1f5fa8", c.carColor)
        assertEquals("costa", c.look.theme)
        assertEquals("minimal", c.look.layout)
        assertEquals("noche", c.look.sceneTime)
        assertEquals("navidad", c.look.season)
        assertEquals("", c.look.accent, "un acento null no se aplica")
        assertEquals("", c.look.scene, "«nieve» todavía no se ofrece en la app")
        assertEquals(listOf("maps", "waze", "spotify", "youtube"), c.apps, "sin repetidos y como máximo 4")
        assertEquals(52_000, c.odometer)
        assertEquals(7_500, c.oilEvery)
        assertEquals(listOf("filtro", "bujias"), c.otherMaintenance)
        assertEquals("manual", c.speedAlert)
        assertEquals("7", c.restrDigit)
        assertEquals(listOf("lun", "jue"), c.restrDays, "un día que no existe se descarta")
        assertEquals(false, c.voice)
    }

    @Test
    fun `lo invalido se ignora y queda lo que ya habia`() {
        val base = LauncherConfig(name = "Antes", carColor = "#b3261e", speedAlert = "auto")
        val c = assertNotNull(DesignImport.apply(base, """{"v":1,"car":{"color":"rojo","type":"moto"},"features":{"speedAlert":"raro","oilEvery":1,"tankL":9999}}"""))
        assertEquals("#b3261e", c.carColor)
        assertEquals("auto", c.speedAlert)
        assertEquals("suv", c.carType)
        assertEquals(1_000, c.oilEvery, "se acota al rango permitido")
        assertEquals(150, c.tankL)
        assertEquals("Hola", c.greeting, "un saludo en blanco vuelve al de siempre")
    }

    @Test
    fun `un texto que no es un diseno no cambia nada`() {
        assertNull(DesignImport.apply(LauncherConfig(), "no es json"))
        assertNull(DesignImport.apply(LauncherConfig(), """{"v":2}"""))
        assertNull(DesignImport.apply(LauncherConfig(), "{}"))
    }

    @Test
    fun `sin secciones deja la configuracion igual`() {
        val base = LauncherConfig(name = "Daniel")
        assertEquals(base, DesignImport.apply(base, """{"v":1}"""))
        assertTrue(DesignImport.apply(base, """{"v":1}""") == base)
    }

    @Test
    fun `dice si el diseno trae foto y si viene recortada o con version de noche`() {
        assertEquals(Pair(true, true), DesignImport.photoInfo("""{"v":1,"photo":{"cut":true,"night":true}}"""))
        assertEquals(Pair(false, false), DesignImport.photoInfo("""{"v":1,"photo":{"cut":false,"night":false}}"""))
        assertNull(DesignImport.photoInfo("""{"v":1}"""))
        assertNull(DesignImport.photoInfo("no es json"))
    }

    @Test
    fun `la hora de 12 horas del diseno se aplica y si no viene queda como estaba`() {
        assertEquals(true, assertNotNull(DesignImport.apply(LauncherConfig(), """{"v":1,"features":{"clock12":true}}""")).clock12)
        assertEquals(false, assertNotNull(DesignImport.apply(LauncherConfig(), """{"v":1,"features":{"clock12":false}}""")).clock12)
        assertEquals(true, assertNotNull(DesignImport.apply(LauncherConfig(clock12 = true), """{"v":1,"features":{"oilOn":true}}""")).clock12, "sin el dato, no se pisa")
    }

    @Test
    fun `el movimiento del diseno se aplica solo si es valido y si no viene queda como estaba`() {
        assertEquals("completo", assertNotNull(DesignImport.apply(LauncherConfig(), """{"v":1,"features":{"motion":"completo"}}""")).motion)
        assertEquals("off", assertNotNull(DesignImport.apply(LauncherConfig(), """{"v":1,"features":{"motion":"off"}}""")).motion)
        assertEquals("vivo", assertNotNull(DesignImport.apply(LauncherConfig(), """{"v":1,"features":{"motion":"siempre"}}""")).motion, "un valor inválido se ignora")
        assertEquals("off", assertNotNull(DesignImport.apply(LauncherConfig(motion = "off"), """{"v":1,"features":{"oilOn":true}}""")).motion, "sin el dato, no se pisa")
    }

    @Test
    fun `el pais Otro apaga cualquier restriccion aunque el diseno la traiga encendida`() {
        val c = assertNotNull(DesignImport.apply(LauncherConfig(), """{"v":1,"features":{"country":"OTRO","restrOn":true,"restrDigit":"8","restrDays":["lun","mie"]}}"""))
        assertEquals("OTRO", c.country)
        assertEquals(false, c.restrOn)
        assertEquals("", c.restrDigit)
        assertEquals(emptyList(), c.restrDays)
        val co = assertNotNull(DesignImport.apply(LauncherConfig(), """{"v":1,"features":{"country":"CO","restrOn":true,"restrDigit":"8"}}"""))
        assertEquals(true, co.restrOn, "otro pais conserva su restriccion")
    }

    @Test
    fun `el diseno dice si trae la foto trasera`() {
        assertEquals(true, DesignImport.hasRearPhoto("""{"v":1,"photoRear":true}"""))
        assertEquals(false, DesignImport.hasRearPhoto("""{"v":1,"photoRear":false}"""))
        assertEquals(false, DesignImport.hasRearPhoto("""{"v":1}"""))
        assertEquals(false, DesignImport.hasRearPhoto("no es json"))
    }
}
