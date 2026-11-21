package cl.tablero.core

import kotlin.test.*

class CrashLogPropioTest {
    private fun e(n: Int, stack: String = "boom") = CrashLog.entry("Excepción", 1_700_000_000_000L + n * 1000L, "1.0-abc", "hilo-$n", stack)

    @Test fun entradaLegible() {
        val s = e(0)
        assertTrue(s.endsWith("\n"))
        assertTrue(s.startsWith("##### 2023-11-14 22:13:20 UTC | Excepción\n"))
        assertTrue(s.contains("Versión: 1.0-abc"))
        assertTrue(s.contains("Hilo: hilo-0"))
        assertEquals(1, CrashLog.count(s))
    }

    @Test fun fechaEpocaYAnioBisiesto() {
        assertTrue(CrashLog.entry("k", 0, "v", "t", "s").startsWith("##### 1970-01-01 00:00:00 UTC"))
        assertTrue(CrashLog.entry("k", 951_782_400_000L, "v", "t", "s").contains("2000-02-29 00:00:00"))
        assertEquals("Tablero-copia-2024-12-31.json", SettingsBackup.fileName(1_735_689_599_000L))
    }

    @Test fun pilaConMarcaFalsaNoCuentaComoRegistro() {
        val s = e(0, "##### falso\nlinea\n#####otro")
        assertEquals(1, CrashLog.count(s))
        assertEquals(2, CrashLog.count(CrashLog.append(s, e(1))))
    }

    @Test fun saltosEnCamposNoRompen() {
        val s = CrashLog.entry("a\n##### b", 0, "v\r\n#####", "t\n", "x")
        assertEquals(1, CrashLog.count(s))
    }

    @Test fun pilaVacia() {
        assertEquals(1, CrashLog.count(e(0, "")))
        assertTrue(CrashLog.stackOf(emptyArray()).isNotBlank())
        assertTrue(CrashLog.entry("k", 0, "v", "t", "").contains("sin pila"))
    }

    @Test fun stackOfConCausasYCiclos() {
        val a = RuntimeException("a", IllegalStateException("causa"))
        val s = CrashLog.stackOf(a)
        assertTrue(s.contains("RuntimeException: a"))
        assertTrue(s.contains("Caused by: java.lang.IllegalStateException: causa"))
        val x = RuntimeException("x"); val y = RuntimeException("y", x); x.initCause(y)
        assertTrue(CrashLog.stackOf(x).contains("Caused by"))
        val frames = Throwable().stackTrace
        assertEquals(frames.size, CrashLog.stackOf(frames).lines().count { it.isNotEmpty() })
    }

    @Test fun appendRespetaTopeYDescartaViejosEnteros() {
        val uno = e(0, "x".repeat(100)).length
        val max = uno * 3 + 10
        var log = ""
        for (i in 0 until 20) {
            log = CrashLog.append(log, e(i, "x".repeat(100)), max)
            assertTrue(log.length <= max)
            assertTrue(log.startsWith("##### "))
            assertTrue(log.endsWith("\n"))
        }
        assertEquals(3, CrashLog.count(log))
        assertTrue(log.contains("hilo-19"))
        assertFalse(log.contains("hilo-16\n".replace("16", "10")))
        // cada registro entero
        assertEquals(3, log.split("##### ").size - 1)
    }

    @Test fun appendSinRecorteSiCabe() {
        val log = CrashLog.append(CrashLog.append("", e(0)), e(1))
        assertEquals(e(0) + e(1), log)
    }

    @Test fun registroEnormeSeRecorta() {
        val grande = e(0, "y".repeat(200_000))
        val r = CrashLog.append(e(1), grande)
        assertTrue(r.length <= CrashLog.MAX_CHARS)
        assertTrue(r.startsWith("##### "))
        assertEquals(1, CrashLog.count(r))
        assertFalse(r.contains("hilo-1\n"))
        assertTrue(r.endsWith("\n"))
    }

    @Test fun topeExactoYMinimos() {
        val x = e(0)
        assertEquals(x, CrashLog.append("", x, x.length))
        assertEquals(x.length, CrashLog.append(e(1), x, x.length).length)
        assertEquals("", CrashLog.append("abc", x, 0))
        assertEquals(1, CrashLog.append("", x, 1).length)
        assertTrue(CrashLog.append("", x, 5).length <= 5)
    }

    @Test fun existenteConBasuraInicialOSinSaltoFinal() {
        val r = CrashLog.append("basura\n" + e(0).trimEnd('\n'), e(1))
        assertTrue(r.startsWith("##### "))
        assertEquals(2, CrashLog.count(r))
        assertEquals("", CrashLog.append("", "").let { if (it.isEmpty()) "" else it })
    }

    @Test fun tamanoDeMaxChars() {
        assertTrue(CrashLog.MAX_CHARS in 20_000..60_000 && CrashLog.MAX_CHARS != 65_536)
    }

    @Test fun summaryVacioYBlanco() {
        assertEquals("Ninguno registrado.", CrashLog.summary(""))
        assertEquals("Ninguno registrado.", CrashLog.summary(" \n\t"))
        assertEquals(0, CrashLog.count(""))
    }

    @Test fun summaryUltimos() {
        var log = ""
        for (i in 0 until 5) log = CrashLog.append(log, e(i))
        assertEquals(5, CrashLog.count(log))
        val s = CrashLog.summary(log)
        assertTrue(s.startsWith("Mostrando los últimos 3 de 5.\n##### "))
        assertTrue(s.contains("hilo-2") && s.contains("hilo-4") && !s.contains("hilo-1"))
        val todos = CrashLog.summary(log, 5)
        assertFalse(todos.contains("Mostrando"))
        assertEquals(log, todos)
        assertFalse(CrashLog.summary(log, 10).contains("Mostrando"))
        assertEquals(1, CrashLog.count(CrashLog.summary(log, 1).substringAfter('\n')))
    }
}
