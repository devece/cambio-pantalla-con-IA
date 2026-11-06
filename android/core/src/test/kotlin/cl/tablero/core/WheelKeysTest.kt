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
        assertEquals(WheelAction.NEXT, WheelKeys.actionFor(mapOf(next to WheelAction.NEXT), next))
    }

    @Test
    fun `asignar reemplaza la accion anterior de esa tecla`() {
        val a = WheelKeys.assign(emptyMap(), next, WheelAction.NEXT)
        val b = WheelKeys.assign(a, next, WheelAction.MUTE)
        assertEquals(mapOf(next to WheelAction.MUTE), b)
    }

    @Test
    fun `no deja asignar inicio, atras, encendido ni apps recientes`() {
        listOf(3, 4, 26, 187, 0, -1).forEach {
            assertFalse(WheelKeys.isAssignable(it), "tecla $it")
            assertTrue(WheelKeys.assign(emptyMap(), it, WheelAction.NEXT).isEmpty())
        }
        assertTrue(WheelKeys.isAssignable(next))
    }

    @Test
    fun `hay un maximo de botones pero se puede cambiar uno ya asignado`() {
        var keys = emptyMap<Int, WheelAction>()
        (100 until 100 + WheelKeys.MAX).forEach { keys = WheelKeys.assign(keys, it, WheelAction.NEXT) }
        assertEquals(WheelKeys.MAX, keys.size)
        assertEquals(keys, WheelKeys.assign(keys, 500, WheelAction.NEXT), "uno más no entra")
        assertEquals(WheelAction.VOICE, WheelKeys.assign(keys, 100, WheelAction.VOICE)[100], "pero sí se cambia uno existente")
    }

    @Test
    fun `se guarda y se lee igual`() {
        val keys = linkedMapOf(next to WheelAction.NEXT, prev to WheelAction.PREVIOUS, 24 to WheelAction.VOLUME_UP)
        assertEquals("87:next,88:previous,24:volume_up", WheelKeys.serialize(keys))
        assertEquals(keys, WheelKeys.parse(WheelKeys.serialize(keys)))
    }

    @Test
    fun `lo dañado o desconocido se ignora sin fallar`() {
        assertEquals(emptyMap(), WheelKeys.parse(null))
        assertEquals(emptyMap(), WheelKeys.parse(""))
        val parsed = WheelKeys.parse("87:next,x:next,88:inventada,89,4:next,,90:mute:extra, 91 : play ")
        assertEquals(mapOf(87 to WheelAction.NEXT, 91 to WheelAction.PLAY_PAUSE), parsed)
    }

    @Test
    fun `todos los ids de accion son unicos y se pueden leer de vuelta`() {
        assertEquals(WheelAction.entries.size, WheelAction.entries.map { it.id }.toSet().size)
        WheelAction.entries.forEach { assertEquals(it, WheelAction.fromId(it.id)) }
        assertNull(WheelAction.fromId("no-existe"))
    }
}
