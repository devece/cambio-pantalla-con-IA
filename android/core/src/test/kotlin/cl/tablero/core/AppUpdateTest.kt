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

    private val sha = "a".repeat(64)

    @Test fun `lee tambien el commit, la huella y el tamano, y los formatos viejos siguen valiendo`() {
        val i = AppUpdate.parse("""{"versionCode":1155,"versionName":"0.2.0+155","commit":"ee17774da76f","sha256":"$sha","size":2000000}""")!!
        assertEquals(1155, i.versionCode)
        assertEquals("ee17774da76f", i.commit)
        assertEquals(sha, i.sha256)
        assertEquals(2_000_000L, i.size)
        // El archivo que publicaban las versiones 122 a 138 solo traia versionCode y versionName.
        val viejo = AppUpdate.parse("""{"versionCode":138,"versionName":"1.0.138"}""")!!
        assertEquals("", viejo.sha256)
        assertFalse(AppUpdate.isVerifiable(viejo))
    }

    @Test fun `una huella o un commit mal formados se descartan`() {
        val i = AppUpdate.parse("""{"versionCode":5,"commit":"no es un commit","sha256":"corta","size":-3}""")!!
        assertEquals("", i.commit)
        assertEquals("", i.sha256)
        assertEquals(0L, i.size)
    }

    @Test fun `solo se ofrece lo que se puede verificar`() {
        assertTrue(AppUpdate.isVerifiable(UpdateInfo(1, "x", sha256 = sha)))
        assertFalse(AppUpdate.isVerifiable(UpdateInfo(1, "x")))
        assertFalse(AppUpdate.isVerifiable(null))
    }

    @Test fun `comprueba tamano y huella del archivo descargado`() {
        val i = UpdateInfo(2, "x", sha256 = sha, size = 100)
        assertEquals(DownloadCheck.OK, AppUpdate.checkDownload(i, 100, sha))
        assertEquals(DownloadCheck.OK, AppUpdate.checkDownload(i, 100, sha.uppercase()))
        assertEquals(DownloadCheck.TAMANO, AppUpdate.checkDownload(i, 99, sha))
        assertEquals(DownloadCheck.HUELLA, AppUpdate.checkDownload(i, 100, "b".repeat(64)))
        assertEquals(DownloadCheck.SIN_HUELLA, AppUpdate.checkDownload(UpdateInfo(2, "x", size = 100), 100, sha))
        // Sin tamano publicado no se exige el tamano, pero la huella siempre.
        assertEquals(DownloadCheck.OK, AppUpdate.checkDownload(UpdateInfo(2, "x", sha256 = sha), 5, sha))
    }

    @Test fun `la huella SHA-256 coincide con el valor conocido`() {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", AppUpdate.sha256Hex("abc".toByteArray()))
        assertEquals(AppUpdate.sha256Hex("abc".toByteArray()), AppUpdate.sha256Hex("abc".toByteArray().inputStream()))
    }
}
