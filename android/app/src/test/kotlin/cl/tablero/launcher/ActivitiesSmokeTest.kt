package cl.tablero.launcher

import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertFalse

/**
 * Las pantallas de la app se abren, se muestran y se cierran sin errores en distintas versiones de Android
 * (5, 6, 8, 10, 13 y 14). Si se usa una función que no existe en una versión, aquí falla con su nombre.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [21, 23, 26, 29, 33, 34])
class ActivitiesSmokeTest {
    @Test fun `Ajustes se abre y se cierra`() {
        val c = Robolectric.buildActivity(SettingsActivity::class.java).create().start().resume()
        c.pause().stop().destroy()
        assertFalse(c.get() == null)
    }

    @Test fun `la bienvenida se abre y se cierra`() {
        val c = Robolectric.buildActivity(WelcomeActivity::class.java).create().start().resume()
        c.pause().stop().destroy()
    }

    @Test fun `el diagnostico de la radio se abre y se cierra`() {
        val c = Robolectric.buildActivity(DiagnosticActivity::class.java).create().start().resume()
        c.pause().stop().destroy()
    }
}
