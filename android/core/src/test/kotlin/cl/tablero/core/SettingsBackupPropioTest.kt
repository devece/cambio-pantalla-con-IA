package cl.tablero.core

import kotlin.random.Random
import kotlin.test.*

class SettingsBackupPropioTest {
    private fun ok(r: BackupResult) = (r as? BackupResult.Ok)?.data ?: fail("esperaba Ok: $r")
    private fun fail(r: BackupResult) = (r as? BackupResult.Fail)?.reason ?: fail("esperaba Fail: $r")

    @Test fun tiposVuelvenIgualesYConMismoTipo() {
        val s = linkedMapOf<String, Any>(
            "b" to true, "bf" to false, "i" to 5, "imax" to Int.MAX_VALUE, "imin" to Int.MIN_VALUE,
            "l" to 5L, "lmax" to Long.MAX_VALUE, "lmin" to Long.MIN_VALUE, "f" to 1.5f, "fnan" to Float.NaN,
            "finf" to Float.POSITIVE_INFINITY, "fmin" to Float.MIN_VALUE, "f0" to -0.0f,
            "s" to "hola ñandú \u0000 \"comillas\" \n 😀", "vacio" to "",
        )
        val r = ok(SettingsBackup.read(SettingsBackup.write(BackupData(s, appVersion = "v1", createdAtMillis = 123456789012L))))
        assertEquals(s.keys, r.settings.keys)
        for ((k, v) in s) {
            val g = r.settings[k]!!
            assertEquals(v::class, g::class, k)
            if (v is Float) assertEquals(v.toRawBits(), (g as Float).toRawBits(), k) else assertEquals(v, g, k)
        }
        assertEquals("v1", r.appVersion)
        assertEquals(123456789012L, r.createdAtMillis)
    }

    @Test fun tiposAjenosYClavesSeOmiten() {
        val s = mapOf<String, Any>("d" to 1.5, "lista" to listOf(1), "odometerCarryM" to 7, "ok" to 1,
            " " to 1, "" to 2, "x".repeat(65) to 3, "y".repeat(64) to 4)
        val r = ok(SettingsBackup.read(SettingsBackup.write(BackupData(s)))).settings
        assertEquals(setOf("ok", "y".repeat(64)), r.keys)
    }

    @Test fun odometerCarryYClavesMalasAlLeer() {
        val txt = """{"app":"Tablero","format":1,"settings":[
            {"k":"odometerCarryM","t":"i","v":1},{"k":"","t":"i","v":1},{"k":"${"z".repeat(65)}","t":"i","v":1},{"k":"bien","t":"i","v":1}]}"""
        assertEquals(setOf("bien"), ok(SettingsBackup.read(txt)).settings.keys)
    }

    @Test fun entradasMalFormadasSeSaltan() {
        val txt = """{"app":"Tablero","format":1,"settings":[
            {"k":"a","t":"zzz","v":1},{"k":"b","t":"i"},{"k":"c","t":"i","v":"texto"},{"k":"d","t":"b","v":1},
            {"k":"e","t":"i","v":99999999999},{"k":"f","t":"l","v":"abc"},5,"x",null,{"t":"i","v":1},
            {"k":"g","t":"s","v":3},{"k":"ok","t":"s","v":"si"},{"k":"ok2","t":"i","v":3}]}"""
        val r = ok(SettingsBackup.read(txt)).settings
        assertEquals(mapOf<String, Any>("ok" to "si", "ok2" to 3), r)
    }

    @Test fun archivosRoundTripYRutasPeligrosas() {
        val bytes = Random(1).nextBytes(1000)
        val files = mapOf(
            "mycar/a.png" to bytes, "mycar-rear/b_1-2.jpg" to ByteArray(0), "mybg/x" to byteArrayOf(1),
            "../etc/passwd" to byteArrayOf(1), "mycar/../x" to byteArrayOf(1), "otra/x" to byteArrayOf(1),
            "mycar/a/b" to byteArrayOf(1), "/mycar/a" to byteArrayOf(1), "mycar/" to byteArrayOf(1),
            "mycar/ñ.png" to byteArrayOf(1), "mycar/a b" to byteArrayOf(1), "mycar/..a" to byteArrayOf(1),
            "mycar/." to byteArrayOf(1), "mycar\\a" to byteArrayOf(1),
        )
        val r = ok(SettingsBackup.read(SettingsBackup.write(BackupData(mapOf("a" to 1), files)))).files
        assertEquals(setOf("mycar/a.png", "mycar-rear/b_1-2.jpg", "mybg/x"), r.keys)
        assertContentEquals(bytes, r["mycar/a.png"])
        assertEquals(0, r["mycar-rear/b_1-2.jpg"]!!.size)
    }

    @Test fun isSafePathLimites() {
        assertTrue(SettingsBackup.isSafePath("mycar/" + "a".repeat(64)))
        assertFalse(SettingsBackup.isSafePath("mycar/" + "a".repeat(65)))
        assertFalse(SettingsBackup.isSafePath(""))
        assertFalse(SettingsBackup.isSafePath("mycar"))
        assertTrue(SettingsBackup.isSafePath("mybg/a.b.c"))
    }

    @Test fun rutasInsegurasAlLeerSeIgnoran() {
        val txt = """{"app":"Tablero","format":1,"settings":[],"files":[{"p":"../x","d":"AAAA"},{"p":"mycar/ok","d":"AAAA"},{"p":"mycar/mal","d":"A"}]}"""
        assertEquals(setOf("mycar/ok"), ok(SettingsBackup.read(txt)).files.keys)
    }

    @Test fun errores() {
        val noEs = "El archivo no es una copia de Tablero."
        assertEquals(noEs, fail(SettingsBackup.read("")))
        assertEquals(noEs, fail(SettingsBackup.read("hola")))
        assertEquals(noEs, fail(SettingsBackup.read("[1,2]")))
        assertEquals(noEs, fail(SettingsBackup.read("{}")))
        assertEquals(noEs, fail(SettingsBackup.read("""{"app":"Otra","format":1,"settings":[]}""")))
        assertEquals("La copia viene de una versión más nueva de Tablero. Actualiza la app e inténtalo otra vez.",
            fail(SettingsBackup.read("""{"app":"Tablero","format":${SettingsBackup.FORMAT + 1},"settings":[]}""")))
        assertEquals("La copia viene de una versión más nueva de Tablero. Actualiza la app e inténtalo otra vez.",
            fail(SettingsBackup.read("""{"app":"Tablero","format":99}""")))
        val danada = "La copia está dañada."
        assertEquals(danada, fail(SettingsBackup.read("""{"app":"Tablero","format":1}""")))
        assertEquals(danada, fail(SettingsBackup.read("""{"app":"Tablero","format":1,"settings":{}}""")))
        assertEquals(danada, fail(SettingsBackup.read("""{"app":"Tablero","settings":[]}""")))
        assertEquals(danada, fail(SettingsBackup.read("""{"app":"Tablero","format":"x","settings":[]}""")))
        assertEquals(danada, fail(SettingsBackup.read("""{"app":"Tablero","format":0,"settings":[]}""")))
        assertEquals(danada, fail(SettingsBackup.read("""{"app":"Tablero","format":-3,"settings":[]}""")))
    }

    @Test fun truncadoEsNoEsDeTablero() {
        val t = SettingsBackup.write(BackupData(mapOf("a" to 1)))
        assertEquals("El archivo no es una copia de Tablero.", fail(SettingsBackup.read(t.substring(0, t.length / 2))))
    }

    @Test fun tamanoMaximoDeArchivos() {
        val mitad = ByteArray(SettingsBackup.MAX_FILES_BYTES / 2)
        val justo = BackupData(mapOf("a" to 1), mapOf("mycar/a" to mitad, "mybg/b" to mitad))
        assertEquals(2, ok(SettingsBackup.read(SettingsBackup.write(justo))).files.size)
        val pasado = BackupData(mapOf("a" to 1), mapOf("mycar/a" to mitad, "mybg/b" to mitad, "mycar-rear/c" to byteArrayOf(1)))
        assertEquals("La copia trae archivos demasiado grandes.", fail(SettingsBackup.read(SettingsBackup.write(pasado))))
    }

    @Test fun base64TodosLosLargosContraReferencia() {
        val rnd = Random(42)
        for (n in 0..300) {
            val b = rnd.nextBytes(n)
            val ref = java.util.Base64.getEncoder().encodeToString(b)
            assertEquals(ref, Base64Propio.encode(b), "encode $n")
            assertContentEquals(b, Base64Propio.decode(ref), "decode $n")
        }
        val todos = ByteArray(256) { it.toByte() }
        assertEquals(java.util.Base64.getEncoder().encodeToString(todos), Base64Propio.encode(todos))
    }

    @Test fun base64Invalido() {
        for (m in listOf("A", "AB", "ABC", "AB=A", "A===", "====", "AB==AAAA", "AA\nA", "AA A", "AAA*", "ñAAA", "=AAA"))
            assertNull(Base64Propio.decode(m), m)
        assertContentEquals(ByteArray(0), Base64Propio.decode(""))
    }

    @Test fun nombreDeArchivo() {
        assertEquals("Tablero-copia-2026-11-21.json", SettingsBackup.fileName(1_795_219_200_000L + 3600_000L))
        assertEquals("Tablero-copia-1970-01-01.json", SettingsBackup.fileName(0))
        assertTrue(SettingsBackup.fileName(5).startsWith(SettingsBackup.PREFIX) && SettingsBackup.fileName(5).endsWith(SettingsBackup.EXTENSION))
    }

    @Test fun ajustesVaciosYValoresRaros() {
        val r = ok(SettingsBackup.read(SettingsBackup.write(BackupData(emptyMap()))))
        assertTrue(r.settings.isEmpty() && r.files.isEmpty() && r.appVersion == "" && r.createdAtMillis == 0L)
    }
}
