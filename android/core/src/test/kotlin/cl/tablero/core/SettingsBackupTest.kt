package cl.tablero.core

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SettingsBackupTest {
    private val data = BackupData(
        settings = mapOf("name" to "Ana", "odometer" to 123456, "oilOn" to true, "big" to 5_000_000_000L, "ratio" to 1.5f, "odometerCarryM" to 400),
        files = mapOf("mycar/car.png" to byteArrayOf(0, 1, 2, 3, 4, -1, -128, 127), "mybg/bg.photo.jpg" to ByteArray(0)),
        appVersion = "0.3.0",
        createdAtMillis = 1_700_000_000_000L,
    )

    private fun ok(text: String) = assertIs<BackupResult.Ok>(SettingsBackup.read(text)).data

    @Test
    fun `lo que se guarda vuelve igual, con sus tipos`() {
        val back = ok(SettingsBackup.write(data))
        assertEquals("Ana", back.settings["name"])
        assertEquals(123456, back.settings["odometer"])
        assertEquals(true, back.settings["oilOn"])
        assertEquals(5_000_000_000L, back.settings["big"])
        assertEquals(1.5f, back.settings["ratio"])
        assertContentEquals(data.files["mycar/car.png"], back.files["mycar/car.png"])
        assertEquals(0, back.files["mybg/bg.photo.jpg"]!!.size)
        assertEquals("0.3.0", back.appVersion)
        assertEquals(1_700_000_000_000L, back.createdAtMillis)
    }

    @Test
    fun `los metros sueltos del GPS no viajan`() {
        assertFalse("odometerCarryM" in ok(SettingsBackup.write(data)).settings)
    }

    @Test
    fun `base64 propio coincide con el estandar en todos los largos`() {
        for (n in 0..40) {
            val b = ByteArray(n) { (it * 37 + 11).toByte() }
            val enc = B64.encode(b)
            assertEquals(java.util.Base64.getEncoder().encodeToString(b), enc)
            assertContentEquals(b, B64.decode(enc))
        }
    }

    @Test
    fun `un archivo que no es una copia se rechaza con un motivo`() {
        assertEquals("El archivo no es una copia de Tablero.", (SettingsBackup.read("hola") as BackupResult.Fail).reason)
        assertEquals("El archivo no es una copia de Tablero.", (SettingsBackup.read("""{"app":"otra"}""") as BackupResult.Fail).reason)
        assertTrue((SettingsBackup.read("""{"app":"tablero","formato":99,"ajustes":{}}""") as BackupResult.Fail).reason.contains("más nueva"))
        assertTrue((SettingsBackup.read("""{"app":"tablero","formato":1}""") as BackupResult.Fail).reason.contains("dañada"))
    }

    @Test
    fun `rutas peligrosas de archivos se ignoran`() {
        val bad = listOf("../x", "mycar/../../etc/passwd", "/etc/passwd", "otra/car.png", "mycar/a/b.png", "mycar/..png", "mycar/")
        bad.forEach { assertFalse(SettingsBackup.isSafePath(it), it) }
        assertTrue(SettingsBackup.isSafePath("mycar/car.png"))
        assertTrue(SettingsBackup.isSafePath("mycar-rear/night.png"))
        val text = """{"app":"tablero","formato":1,"ajustes":{},"archivos":{"../x":"AAAA","mycar/car.png":"AAAA"}}"""
        assertEquals(setOf("mycar/car.png"), ok(text).files.keys)
    }

    @Test
    fun `entradas dañadas se saltan sin tumbar el resto`() {
        val text = """{"app":"tablero","formato":1,"ajustes":{"a":{"t":"i","v":3},"b":{"t":"x","v":1},"c":"suelto","d":{"t":"i"}},"archivos":{"mycar/car.png":"@@@"}}"""
        val r = ok(text)
        assertEquals(mapOf<String, Any>("a" to 3), r.settings)
        assertTrue(r.files.isEmpty())
    }

    @Test
    fun `una copia con archivos enormes se rechaza`() {
        val big = BackupData(emptyMap(), mapOf("mycar/car.png" to ByteArray(SettingsBackup.MAX_FILES_BYTES + 1)))
        assertTrue(SettingsBackup.read(SettingsBackup.write(big)) is BackupResult.Fail)
    }

    @Test
    fun `el nombre lleva la fecha`() {
        assertTrue(SettingsBackup.fileName(1_700_000_000_000L).matches(Regex("Tablero-copia-\\d{4}-\\d{2}-\\d{2}\\.json")))
    }
}
