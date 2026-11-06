package cl.tablero.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WheelKeysTest {
    private val next = 87
    private val prev = 88

    @Test
    fun `una tecla sin asignar no tiene accion y sigue su camino normal`() {
        assertNull(WheelKeys.actionFor(emptyMap(), next))
        assertEquals(WheelAction.SONG_NEXT, WheelKeys.actionFor(mapOf(next to WheelAction.SONG_NEXT), next))
    }

    @Test
    fun `asignar reemplaza la accion anterior de esa tecla`() {
        val a = WheelKeys.assign(emptyMap(), next, WheelAction.SONG_NEXT)
        val b = WheelKeys.assign(a, next, WheelAction.SILENCE)
        assertEquals(mapOf(next to WheelAction.SILENCE), b)
    }

    @Test
    fun `no deja asignar inicio, atras, encendido ni apps recientes`() {
        listOf(3, 4, 26, 187, 0, -1).forEach {
            assertFalse(WheelKeys.isAssignable(it), "tecla $it")
            assertTrue(WheelKeys.assign(emptyMap(), it, WheelAction.SONG_NEXT).isEmpty())
        }
        assertTrue(WheelKeys.isAssignable(next))
    }

    @Test
    fun `hay un maximo de botones pero se puede cambiar uno ya asignado`() {
        var keys = emptyMap<Int, WheelAction>()
        (100 until 100 + WheelKeys.MAX).forEach { keys = WheelKeys.assign(keys, it, WheelAction.SONG_NEXT) }
        assertEquals(WheelKeys.MAX, keys.size)
        assertEquals(keys, WheelKeys.assign(keys, 500, WheelAction.SONG_NEXT), "uno más no entra")
        assertEquals(WheelAction.ASSISTANT, WheelKeys.assign(keys, 100, WheelAction.ASSISTANT)[100], "pero sí se cambia uno existente")
    }

    @Test
    fun `se guarda y se lee igual`() {
        val keys = linkedMapOf(next to WheelAction.SONG_NEXT, prev to WheelAction.SONG_PREV, 24 to WheelAction.LOUDER)
        assertEquals("87:song_next,88:song_prev,24:louder", WheelKeys.serialize(keys))
        assertEquals(keys, WheelKeys.parse(WheelKeys.serialize(keys)))
    }

    @Test
    fun `lo dañado o desconocido se ignora sin fallar`() {
        assertEquals(emptyMap(), WheelKeys.parse(null))
        assertEquals(emptyMap(), WheelKeys.parse(""))
        val parsed = WheelKeys.parse("87:song_next,x:song_next,88:inventada,89,4:song_next,,90:silence:extra, 91 : playback ")
        assertEquals(mapOf(87 to WheelAction.SONG_NEXT, 91 to WheelAction.PLAYBACK), parsed)
    }

    @Test
    fun `todos los ids de accion son unicos y se pueden leer de vuelta`() {
        assertEquals(WheelAction.entries.size, WheelAction.entries.map { it.id }.toSet().size)
        WheelAction.entries.forEach { assertEquals(it, WheelAction.fromId(it.id)) }
        assertNull(WheelAction.fromId("no-existe"))
    }

    @Test
    fun `los atajos y las pantallas se resuelven desde la accion`() {
        assertEquals(0, WheelAction.SHORTCUT_1.shortcutIndex)
        assertEquals(1, WheelAction.SHORTCUT_2.shortcutIndex)
        assertNull(WheelAction.PLAYBACK.shortcutIndex)
        assertEquals("inicio", WheelAction.GO_HOME.screen)
        assertEquals("radio", WheelAction.GO_RADIO.screen)
        assertEquals("mantenimiento", WheelAction.GO_MAINTENANCE.screen)
        assertNull(WheelAction.SWAP_THEME.screen)
        // Toda acción "Ir a…" lleva a una pantalla y solo ellas.
        assertEquals(
            setOf(WheelAction.GO_HOME, WheelAction.GO_RADIO, WheelAction.GO_MAINTENANCE),
            WheelAction.entries.filter { it.screen != null }.toSet(),
        )
    }

    @Test
    fun `solo el volumen se repite al mantener apretado`() {
        assertEquals(setOf(WheelAction.LOUDER, WheelAction.QUIETER), WheelAction.entries.filter { it.repeatsWhileHeld }.toSet())
    }
}
