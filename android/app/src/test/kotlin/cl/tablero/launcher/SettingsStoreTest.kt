package cl.tablero.launcher

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import cl.tablero.core.LauncherConfig
import cl.tablero.core.Look
import cl.tablero.core.ObdLinkConfig
import cl.tablero.core.ObdLinkKind
import cl.tablero.core.WheelAction
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/**
 * Lo que el usuario elige en Ajustes se guarda en la radio y vuelve igual: ninguna opción se pierde al guardar y leer.
 * Corre en Android 5 (la mínima), 8, 10 y 14.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [21, 26, 29, 34])
class SettingsStoreTest {
    private fun store() = SettingsStore(ApplicationProvider.getApplicationContext<Application>())

    /** Compara campo por campo para que, si algo no vuelve igual, el mensaje diga cuál. */
    private fun assertSame(expected: LauncherConfig, actual: LauncherConfig) {
        for (f in LauncherConfig::class.java.declaredFields) {
            if (java.lang.reflect.Modifier.isStatic(f.modifiers)) continue
            f.isAccessible = true
            assertEquals(f.get(expected), f.get(actual), "el campo «${f.name}» no volvio igual")
        }
    }

    @Test
    fun `sin nada guardado se lee la configuracion de fabrica`() {
        assertEquals(LauncherConfig(), store().load())
    }

    @Test
    fun `cada opcion guardada vuelve igual`() {
        val c = LauncherConfig(
            name = "Ana", greeting = "Wena", phrase = "Buen viaje", carType = "camioneta", carColor = "#1f5fa8",
            apps = listOf("maps", "waze", "spotify"),
            oilOn = false, odometer = 45_000, lastOil = 42_000, oilEvery = 8_000,
            otherMaintenance = listOf("frenos", "filtro"), maintDone = mapOf("frenos" to 40_000),
            docsOn = true, rtMonth = 6, fuelOn = true, tankL = 60, kmPerL = 15,
            speedShow = false, speedAlert = "manual", manualLimit = 90,
            restrOn = true, restrType = "sin", restrDigit = "8", restrDays = listOf("lun", "mie"),
            voice = true, clock12 = true,
            wheelKeys = mapOf(24 to WheelAction.entries.first()),
            welcomeDone = true, odometerAuto = false,
            obd = ObdLinkConfig(kind = ObdLinkKind.WIFI, address = "192.168.0.10", name = "OBDII", port = 35_000),
            look = Look(theme = "costa", layout = "minimal", scene = "costa", sceneTime = "noche", season = "navidad", accent = "#4cc9f0"),
        )
        val s = store()
        s.save(c)
        assertSame(c, s.load())
        // Lo guardado sobrevive a una instancia nueva (como al reiniciar la radio).
        assertSame(s.load(), store().load())
    }

    @Test
    fun `guardar de nuevo cambia solo lo que cambio`() {
        val s = store()
        s.save(LauncherConfig(name = "Ana", odometer = 1_000))
        s.save(s.load().copy(odometer = 2_000))
        val back = s.load()
        assertEquals("Ana", back.name)
        assertEquals(2_000, back.odometer)
    }
}
