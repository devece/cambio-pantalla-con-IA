package cl.tablero.core

import java.io.File
import java.util.Calendar
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RemindersTest {
    private val maintenance = Maintenance.parse(File(System.getProperty("maintenance.path")).readText())

    private fun build(c: LauncherConfig, month: Int = 6, dow: Int = Calendar.WEDNESDAY) =
        Reminders.build(c, maintenance, month, dow)

    @Test
    fun `km usa punto como separador de miles`() {
        assertEquals("0", Reminders.km(0))
        assertEquals("500", Reminders.km(500))
        assertEquals("5.000", Reminders.km(5000))
        assertEquals("15.000", Reminders.km(15000))
        assertEquals("1.234.567", Reminders.km(1234567))
        assertEquals("-1.200", Reminders.km(-1200))
    }

    @Test
    fun `lee las 6 mantenciones del json compartido`() {
        assertEquals(6, maintenance.size)
        assertEquals(MaintenanceItem("filtro", "Filtro de aire", 15000), maintenance.first())
    }

    @Test
    fun `aceite faltan km cuando aun no toca`() {
        // 58.000 + 5.000 - 60.000 = faltan 3.000 km; 1 - 3000/5000 = 0,4
        val r = build(LauncherConfig(odometer = 60000, lastOil = 58000, oilEvery = 5000)).single()
        assertEquals("Aceite", r.key)
        assertEquals("faltan 3.000 km", r.text)
        assertEquals(0.4, r.pct, 1e-9)
        assertFalse(r.warn)
    }

    @Test
    fun `aceite avisa cuando faltan 500 km o menos y cuando se paso`() {
        val cerca = build(LauncherConfig(odometer = 62500, lastOil = 58000, oilEvery = 5000)).single()
        assertEquals("faltan 500 km", cerca.text)
        assertTrue(cerca.warn)

        val pasado = build(LauncherConfig(odometer = 64000, lastOil = 58000, oilEvery = 5000)).single()
        assertEquals("pasado por 1.000 km", pasado.text)
        assertTrue(pasado.warn)
        assertEquals(1.0, pasado.pct, 1e-9, "la barra no pasa de llena")
    }

    @Test
    fun `sin aceite activado no hay aviso de aceite`() {
        assertTrue(build(LauncherConfig(oilOn = false)).isEmpty())
    }

    @Test
    fun `otras mantenciones solo recuerdan cada cuanto toca`() {
        val r = build(LauncherConfig(oilOn = false, otherMaintenance = listOf("filtro", "correa")))
        assertEquals(listOf("Filtro de aire", "Correa de distribución"), r.map { it.key })
        assertEquals("cada 15.000 km (revisa tu manual)", r[0].text)
        assertTrue(r.all { it.noBar && !it.warn })
    }

    @Test
    fun `revision tecnica segun el mes`() {
        val c = LauncherConfig(oilOn = false, docsOn = true, rtMonth = 9)
        val lejos = build(c, month = 3)[0]
        assertEquals("vence en septiembre", lejos.text)
        assertFalse(lejos.warn)

        val proximo = build(c, month = 8)[0]
        assertTrue(proximo.warn, "falta 1 mes")

        val esteMes = build(c, month = 9)[0]
        assertEquals("vence este mes", esteMes.text)
        assertTrue(esteMes.warn)
        assertEquals(1.0, esteMes.pct, 1e-9)

        val pasoElAno = build(LauncherConfig(oilOn = false, docsOn = true, rtMonth = 1), month = 12)[0]
        assertTrue(pasoElAno.warn, "de diciembre a enero falta 1 mes")
    }

    @Test
    fun `permiso de circulacion apunta al proximo pago`() {
        val c = LauncherConfig(oilOn = false, docsOn = true)
        fun permiso(m: Int) = build(c, month = m).first { it.key == "Permiso de circulación" }.text
        assertEquals("próximo pago en marzo", permiso(1))
        assertEquals("próximo pago en marzo", permiso(3))
        assertEquals("próximo pago en agosto", permiso(4))
        assertEquals("próximo pago en agosto", permiso(8))
        assertEquals("próximo pago en marzo", permiso(9))
        assertEquals("vence el 31 de marzo", build(c).first { it.key.startsWith("Seguro") }.text)
    }

    @Test
    fun `combustible calcula km con el tanque lleno`() {
        val c = LauncherConfig(oilOn = false, fuelOn = true, tankL = 45, kmPerL = 12)
        assertEquals(540, Reminders.fuelRange(c))
        assertEquals("~540 km con el tanque lleno", build(c).single().text)
    }

    @Test
    fun `restriccion avisa el dia que toca y lista los dias el resto`() {
        val c = LauncherConfig(oilOn = false, restrOn = true, restrDays = listOf("lun", "mie"))
        assertEquals("HOY tienes restricción (lunes, miércoles)", build(c, dow = Calendar.WEDNESDAY).single().text)
        assertTrue(build(c, dow = Calendar.WEDNESDAY).single().warn)
        assertEquals("lunes, miércoles", build(c, dow = Calendar.TUESDAY).single().text)
        assertFalse(build(c, dow = Calendar.SUNDAY).single().warn)
        assertTrue(build(c.copy(restrDays = emptyList())).isEmpty())
    }

    @Test
    fun `dia de la semana de Calendar a id del sitio`() {
        assertEquals("lun", Reminders.weekdayId(Calendar.MONDAY))
        assertEquals("vie", Reminders.weekdayId(Calendar.FRIDAY))
        assertNull(Reminders.weekdayId(Calendar.SATURDAY))
        assertNull(Reminders.weekdayId(Calendar.SUNDAY))
    }

    @Test
    fun `saludo segun la hora y nombre`() {
        assertEquals("Buenos días", Reminders.greeting(6))
        assertEquals("Buenas tardes", Reminders.greeting(12))
        assertEquals("Buenas tardes", Reminders.greeting(19))
        assertEquals("Buenas noches", Reminders.greeting(20))
        assertEquals("Hola, Daniel", Reminders.hello(LauncherConfig(name = "Daniel")))
        assertEquals("Hola, amigo", Reminders.hello(LauncherConfig(name = "  ")))
    }

    @Test
    fun `alerta de velocidad`() {
        assertEquals(60, Speed.limit(LauncherConfig(speedAlert = "manual", manualLimit = 60), roadLimit = 100))
        assertEquals(100, Speed.limit(LauncherConfig(speedAlert = "auto"), roadLimit = 100))
        assertNull(Speed.limit(LauncherConfig(speedAlert = "auto"), roadLimit = null), "sin dato de la vía no inventamos un límite")
        assertNull(Speed.limit(LauncherConfig(speedAlert = "off"), roadLimit = 100))

        assertFalse(Speed.isOver(65, 60), "dentro de la tolerancia de 5 km/h")
        assertTrue(Speed.isOver(66, 60))
        assertFalse(Speed.isOver(200, null))
    }

    @Test
    fun `la frase del usuario reemplaza al saludo del momento del dia`() {
        val sin = LauncherConfig(name = "Daniel", greeting = "Wena")
        assertEquals("Wena, Daniel", Reminders.hello(sin))
        assertEquals("Buenas tardes", Reminders.below(sin, hour = 15))
        assertEquals("Wena, Daniel. Buenas tardes", Reminders.voiceLine(sin, hour = 15))
        val con = sin.copy(phrase = "  Dios te bendiga ")
        assertEquals("Dios te bendiga", Reminders.below(con, hour = 15))
        assertEquals("Wena, Daniel. Dios te bendiga", Reminders.voiceLine(con, hour = 15))
        assertEquals("Hola, amigo. Buenos días", Reminders.voiceLine(LauncherConfig(), hour = 8))
    }
}
