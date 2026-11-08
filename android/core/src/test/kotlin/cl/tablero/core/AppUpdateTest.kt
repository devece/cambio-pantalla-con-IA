package cl.tablero.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppUpdateTest {
    @Test fun `lee la version publicada`() {
        assertEquals(UpdateInfo(121, "1.0.121"), AppUpdate.parse("""{"versionCode":121,"versionName":"1.0.121"}"""))
    }

    @Test fun `un archivo roto o sin version no avisa`() {
        assertNull(AppUpdate.parse(null))
        assertNull(AppUpdate.parse(""))
        assertNull(AppUpdate.parse("<html>404</html>"))
        assertNull(AppUpdate.parse("""{"versionName":"1.0"}"""))
        assertNull(AppUpdate.parse("""{"versionCode":0}"""))
    }

    @Test fun `avisa solo si la publicada es mas nueva`() {
        val nueva = UpdateInfo(121, "1.0.121")
        assertTrue(AppUpdate.shouldOffer(120, nueva, 0))
        assertFalse(AppUpdate.shouldOffer(121, nueva, 0))
        assertFalse(AppUpdate.shouldOffer(122, nueva, 0))
        assertFalse(AppUpdate.shouldOffer(120, null, 0))
    }

    @Test fun `no insiste con una version que el usuario dejo para despues`() {
        val nueva = UpdateInfo(121, "1.0.121")
        assertFalse(AppUpdate.shouldOffer(120, nueva, 121))
        assertTrue(AppUpdate.shouldOffer(120, UpdateInfo(122, "1.0.122"), 121))
    }
}
