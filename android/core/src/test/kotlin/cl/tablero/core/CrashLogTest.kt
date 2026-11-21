package cl.tablero.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class CrashLogTest {
    private fun e(n: Int) = CrashLog.entry("Cierre inesperado", 1_700_000_000_000L + n * 1000L, "0.3.0", "main", "java.lang.Boom$n\n    at X.y(X.kt:1)")

    @Test
    fun `una entrada lleva fecha en UTC, version, hilo y pila`() {
        val t = e(0)
        assertTrue(t.startsWith("=== 2023-11-14 22:13:20 UTC · Cierre inesperado\n"))
        assertTrue("Versión: 0.3.0" in t && "Hilo: main" in t && "java.lang.Boom0" in t)
        assertTrue(t.endsWith("\n"))
    }

    @Test
    fun `el registro nunca pasa su tope y descarta las entradas mas viejas enteras`() {
        var log = ""
        for (i in 0 until 50) log = CrashLog.append(log, e(i), max = 600)
        assertTrue(log.length <= 600)
        assertTrue(log.startsWith(CrashLog.MARK), "no debe empezar a mitad de una entrada")
        assertTrue("Boom49" in log)
        assertFalse("Boom0\n" in log)
    }

    @Test
    fun `una entrada mas grande que el tope se recorta pero se guarda`() {
        val big = CrashLog.entry("Cierre", 0, "1", "main", "x".repeat(5000))
        val log = CrashLog.append("", big, max = 1000)
        assertEquals(1000, log.length)
    }

    @Test
    fun `el resumen muestra las ultimas entradas o avisa que no hay`() {
        assertEquals("Ninguno registrado.", CrashLog.summary(""))
        var log = ""
        for (i in 0 until 5) log = CrashLog.append(log, e(i))
        assertEquals(5, CrashLog.count(log))
        val s = CrashLog.summary(log, maxEntries = 2)
        assertTrue("Mostrando los últimos 2 de 5." in s)
        assertTrue("Boom4" in s && "Boom3" in s && "Boom2" !in s)
    }

    @Test
    fun `la pila de un error incluye su causa`() {
        val t = CrashLog.stackOf(RuntimeException("a", IllegalStateException("causa")))
        assertTrue("RuntimeException: a" in t && "Caused by: java.lang.IllegalStateException: causa" in t)
    }
}
