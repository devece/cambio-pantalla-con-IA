package cl.tablero.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TilesTest {
    // Valores calculados con la misma fórmula que usa la pantalla (js/preview.js, zoom 15).
    @Test
    fun `convierte coordenadas en cuadros como la pantalla`() {
        assertEquals(9641, Tiles.tileX(-74.08))
        assertEquals(15960, Tiles.tileY(4.65))      // Bogotá
        assertEquals(9952, Tiles.tileX(-70.66))
        assertEquals(19618, Tiles.tileY(-33.45))    // Santiago
        assertEquals(9505, Tiles.tileX(-75.57))
        assertEquals(15813, Tiles.tileY(6.25))      // Medellín
        assertEquals(9241, Tiles.tileX(-78.47))
        assertEquals(16400, Tiles.tileY(-0.18))     // Quito
    }

    @Test
    fun `los cuadros alrededor forman un cuadrado centrado en el punto`() {
        val t = Tiles.around(-33.45, -70.66, 7)
        assertEquals(15 * 15, t.size)
        assertTrue(Pair(9952, 19618) in t)
        assertTrue(Pair(9952 - 7, 19618 - 7) in t)
        assertTrue(Pair(9952 + 7, 19618 + 7) in t)
        assertEquals(t.size, t.toSet().size, "sin repetidos")
    }

    @Test
    fun `cerca de los polos y del borde no se salen del mapa`() {
        val n = 1 shl Tiles.ZOOM
        Tiles.around(84.9, 179.99, 7).forEach { (x, y) -> assertTrue(x in 0 until n && y in 0 until n) }
        Tiles.around(-84.9, -179.99, 7).forEach { (x, y) -> assertTrue(x in 0 until n && y in 0 until n) }
    }

    @Test
    fun `la ruta de una peticion se convierte en cuadro y de vuelta en archivo`() {
        val t = Tiles.parse("/dark_all/15/9952/19618.png")!!
        assertEquals(TileId("dark_all", 15, 9952, 19618), t)
        assertEquals("dark_all/15/9952/19618.png", Tiles.key(t))
        assertEquals("https://a.basemaps.cartocdn.com/dark_all/15/9952/19618.png", Tiles.url(t))
        assertEquals("light_all", Tiles.parse("/light_all/3/2/1.png")!!.style)
    }

    @Test
    fun `rechaza rutas raras para que nunca sean un nombre de archivo`() {
        listOf(
            "", "/", "/dark_all/15/9952/19618", "/dark_all/15/9952/19618.jpg", "/otro/15/1/1.png",
            "/dark_all/../15/1/1.png", "/dark_all/15/../1.png", "/dark_all/15/1/1.png/", "//dark_all/15/1/1.png",
            "/dark_all/15/-1/1.png", "/dark_all/99/1/1.png", "/dark_all/3/8/1.png", "/dark_all/3/1/8.png",
            "/dark_all/15/1/1.png?x=1", "/dark_all/15/1/1.png%00",
        ).forEach { assertNull(Tiles.parse(it), it) }
    }
}
