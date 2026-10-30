package cl.tablero.core

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class Car3DTest {
    private val red = 0xb3261e
    private val lamp = Triple(255, 246, 205)

    private fun lampCount(scene: CarScene) = scene.polys.count { Triple(it.r, it.g, it.b) == lamp }

    @Test
    fun `hay tres tipos de auto y cada uno genera muchos poligonos`() {
        assertEquals(setOf("suv", "auto", "camioneta"), Car3D.TYPES)
        for (type in Car3D.TYPES) {
            val scene = Car3D.build(type, red)
            assertTrue(scene.polys.size > 300, "$type: ${scene.polys.size}")
        }
    }

    @Test
    fun `todas las coordenadas y colores son validos`() {
        for (type in Car3D.TYPES) for (phi in listOf(0.0, 20.0, 34.0, 60.0)) {
            for (p in Car3D.build(type, red, phi).polys) {
                assertTrue(p.pts.size >= 6 && p.pts.size % 2 == 0, "$type $phi")
                assertTrue(p.pts.all { it.isFinite() }, "$type $phi")
                assertTrue(p.r in 0..255 && p.g in 0..255 && p.b in 0..255)
                assertTrue(p.alpha in 0f..1f)
            }
        }
    }

    @Test
    fun `se ven los dos faros de frente`() {
        for (type in Car3D.TYPES) assertEquals(2, lampCount(Car3D.build(type, red, 34.0)), type)
    }

    @Test
    fun `visto casi de lado ya no se ve el frente`() {
        assertEquals(0, lampCount(Car3D.build("suv", red, 88.0)))
    }

    @Test
    fun `el color cambia la carroceria pero no los faros ni las ruedas`() {
        val a = Car3D.build("suv", 0xb3261e, 34.0)
        val b = Car3D.build("suv", 0x1f5fa8, 34.0)
        assertEquals(a.polys.size, b.polys.size)
        assertEquals(lampCount(a), lampCount(b))
        val differing = a.polys.indices.count { a.polys[it].r != b.polys[it].r || a.polys[it].b != b.polys[it].b }
        assertTrue(differing > 100, "la carrocería cambia de color: $differing")
        val same = a.polys.indices.count { a.polys[it].r == b.polys[it].r && a.polys[it].g == b.polys[it].g && a.polys[it].b == b.polys[it].b }
        assertTrue(same > 20, "faros, parrilla y ruedas no cambian: $same")
    }

    @Test
    fun `girar el auto cambia lo que se ve`() {
        val a = Car3D.build("auto", red, 20.0)
        val b = Car3D.build("auto", red, 40.0)
        assertNotEquals(a.polys.first().pts.toList(), b.polys.first().pts.toList())
    }

    @Test
    fun `es determinista`() {
        val a = Car3D.build("camioneta", red)
        val b = Car3D.build("camioneta", red)
        assertEquals(a.polys.size, b.polys.size)
        assertTrue(a.polys.indices.all { a.polys[it].pts.contentEquals(b.polys[it].pts) && a.polys[it].r == b.polys[it].r })
    }

    @Test
    fun `un tipo desconocido se dibuja como auto`() {
        assertEquals(Car3D.build("auto", red).polys.size, Car3D.build("nave", red).polys.size)
    }

    @Test
    fun `la sombra va primero y las ruedas al final`() {
        val polys = Car3D.build("suv", red).polys
        assertTrue(polys.first().alpha < 1f)
        assertTrue(polys.drop(1).all { it.alpha == 1f })
        val last = polys.last()
        assertEquals(Triple(215, 220, 224), Triple(last.r, last.g, last.b), "el centro de la última rueda")
    }

    @Test
    fun `el encuadre es estable al balancearse`() {
        val (minX, minY, maxX, maxY) = Car3D.bounds("suv", red, 27.0, 41.0).toList()
        assertTrue(maxX - minX > 200 && maxY - minY > 100)
        val single = Car3D.bounds("suv", red, 34.0, 34.0)
        assertTrue(minX <= single[0] && maxX >= single[2], "el rango incluye el ángulo central")
    }

    /** Si se pasa -Dcar3d.out=carpeta, deja un SVG por tipo para revisarlos a ojo. */
    @Test
    fun `exporta svg para revision visual`() {
        val dir = System.getProperty("car3d.out") ?: return
        val colors = mapOf("suv" to 0xb3261e, "auto" to 0x1f5fa8, "camioneta" to 0xe6e6e6)
        for ((type, color) in colors) {
            val scene = Car3D.build(type, color)
            val b = Car3D.bounds(type, color, 34.0, 34.0)
            val w = 440f
            val h = 240f
            val s = minOf(w * 0.94f / (b[2] - b[0]), h * 0.94f / (b[3] - b[1]))
            val ox = (w - (b[2] - b[0]) * s) / 2 - b[0] * s
            val oy = (h - (b[3] - b[1]) * s) / 2 - b[1] * s
            val body = scene.polys.joinToString("\n") { p ->
                val pts = p.pts.toList().chunked(2).joinToString(" ") { (x, y) -> "%.1f,%.1f".format(java.util.Locale.ROOT, x * s + ox, y * s + oy) }
                val c = "rgb(${p.r},${p.g},${p.b})"
                val stroke = if (p.alpha == 1f) "stroke=\"$c\" stroke-width=\"0.7\" stroke-linejoin=\"round\"" else ""
                "<polygon points=\"$pts\" fill=\"$c\" fill-opacity=\"${p.alpha}\" $stroke/>"
            }
            File(dir, "kotlin_$type.svg").writeText(
                "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 ${w.toInt()} ${h.toInt()}\" width=\"${(w * 2).toInt()}\" height=\"${(h * 2).toInt()}\">$body</svg>",
            )
        }
    }
}
