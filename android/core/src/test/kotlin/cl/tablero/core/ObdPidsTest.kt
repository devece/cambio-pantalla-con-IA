package cl.tablero.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ObdPidsTest {
    private fun value(pid: Int, vararg bytes: Int): Double = ObdPids.byPid(pid)!!.decode(bytes)

    @Test
    fun `revoluciones`() {
        assertEquals(1726.0, value(0x0C, 0x1A, 0xF8), 0.001) // (26*256 + 248) / 4
        assertEquals(0.0, value(0x0C, 0, 0), 0.001)
        assertEquals(16383.75, value(0x0C, 0xFF, 0xFF), 0.001)
    }

    @Test
    fun `velocidad`() {
        assertEquals(60.0, value(0x0D, 0x3C), 0.001)
        assertEquals(255.0, value(0x0D, 0xFF), 0.001)
    }

    @Test
    fun `temperaturas parten en menos 40`() {
        assertEquals(-40.0, value(0x05, 0), 0.001)
        assertEquals(83.0, value(0x05, 0x7B), 0.001)
        assertEquals(215.0, value(0x05, 0xFF), 0.001)
        assertEquals(-15.0, value(0x0F, 25), 0.001)
    }

    @Test
    fun `porcentajes van de 0 a 100`() {
        assertEquals(0.0, value(0x04, 0), 0.001)
        assertEquals(100.0, value(0x04, 0xFF), 0.001)
        assertEquals(49.8, value(0x2F, 127), 0.05) // 127 de 255
        assertEquals(100.0, value(0x11, 0xFF), 0.001)
        assertEquals(100.0, value(0x2F, 0xFF), 0.001)
    }

    @Test
    fun `voltaje en volts`() {
        assertEquals(13.8, value(0x42, 0x35, 0xE8), 0.0001) // 13800 mV
        assertEquals(65.535, value(0x42, 0xFF, 0xFF), 0.0001)
    }

    @Test
    fun `flujo de aire y gasto de combustible`() {
        assertEquals(10.0, value(0x10, 0x03, 0xE8), 0.0001) // 1000 / 100 g/s
        assertEquals(6.0, value(0x5E, 0x00, 0x78), 0.0001) // 120 / 20 L/h
        assertEquals(3276.75, value(0x5E, 0xFF, 0xFF), 0.0001)
    }

    @Test
    fun `presion de admision`() {
        assertEquals(101.0, value(0x0B, 101), 0.001)
    }

    @Test
    fun `kilometraje usa cuatro bytes en decimas de kilometro`() {
        assertEquals(123456.7, value(0xA6, 0x00, 0x12, 0xD6, 0x87), 0.0001) // 1.234.567 décimas
        assertEquals(0.0, value(0xA6, 0, 0, 0, 0), 0.0001)
        assertEquals(429496729.5, value(0xA6, 0xFF, 0xFF, 0xFF, 0xFF), 0.0001) // sin desbordar
    }

    @Test
    fun `cada dato declara cuantos bytes usa y su decodificador los respeta`() {
        for (spec in ObdPids.all) {
            val bytes = IntArray(spec.bytes) { 0x10 }
            assertTrue(spec.decode(bytes).isFinite(), spec.name)
            assertTrue(spec.name.isNotBlank() && spec.unit.isNotBlank() && spec.key.isNotBlank())
        }
        assertEquals(ObdPids.all.size, ObdPids.all.map { it.pid }.toSet().size, "PIDs repetidos")
        assertEquals(ObdPids.all.size, ObdPids.all.map { it.key }.toSet().size, "claves repetidas")
    }

    @Test
    fun `buscar un dato por su PID`() {
        assertNotNull(ObdPids.byPid(0x0C))
        assertNull(ObdPids.byPid(0x99))
    }

    @Test
    fun `la mascara de soportados es la del ejemplo clasico BE 1F A8 13`() {
        val got = ObdPids.supportedFrom(0x00, intArrayOf(0xBE, 0x1F, 0xA8, 0x13))
        // 0xBE = 1011 1110 -> 01,03,04,05,06,07 ; 0x1F -> 0C..10 ; 0xA8 -> 11,13,15 ; 0x13 -> 1C,1F y 20 (hay otro bloque)
        val esperado = setOf(0x01, 0x03, 0x04, 0x05, 0x06, 0x07, 0x0C, 0x0D, 0x0E, 0x0F, 0x10, 0x11, 0x13, 0x15, 0x1C, 0x1F, 0x20)
        assertEquals(esperado, got)
    }

    @Test
    fun `la mascara de un bloque posterior suma su base`() {
        // Bloque 0xA0: el bit más alto del primer byte es A1; 0x04 es el sexto bit -> A6 (kilometraje).
        assertEquals(setOf(0xA6), ObdPids.supportedFrom(0xA0, intArrayOf(0x04, 0, 0, 0)))
        assertEquals(emptySet(), ObdPids.supportedFrom(0x20, intArrayOf(0, 0, 0, 0)))
        // Una máscara corta no rompe nada.
        assertEquals(setOf(0x21), ObdPids.supportedFrom(0x20, intArrayOf(0x80)))
    }
}
