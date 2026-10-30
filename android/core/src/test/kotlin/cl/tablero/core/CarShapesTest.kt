package cl.tablero.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

class CarShapesTest {
    @Test
    fun `lee un trazo con lineas curvas y cierre`() {
        val cmds = PathData.parse("M14 106 L14 90 Q14 82 26 80 Z")
        assertEquals(
            listOf(
                PathCmd('M', listOf(14f, 106f)),
                PathCmd('L', listOf(14f, 90f)),
                PathCmd('Q', listOf(14f, 82f, 26f, 80f)),
                PathCmd('Z', emptyList()),
            ),
            cmds,
        )
    }

    @Test
    fun `pares extra despues de M son lineas`() {
        val cmds = PathData.parse("M0 0 10 10 20 0Z")
        assertEquals(listOf('M', 'L', 'L', 'Z'), cmds.map { it.op })
        assertEquals(listOf(20f, 0f), cmds[2].args)
    }

    @Test
    fun `acepta negativos y decimales`() {
        val cmds = PathData.parse("M-1.5 2.25 L3,-4")
        assertEquals(listOf(-1.5f, 2.25f), cmds[0].args)
        assertEquals(listOf(3f, -4f), cmds[1].args)
    }

    @Test
    fun `rechaza comandos que no soporta`() {
        assertFailsWith<IllegalArgumentException> { PathData.parse("M0 0 C1 1 2 2 3 3") }
        assertFailsWith<IllegalArgumentException> { PathData.parse("m0 0 l5 5") }
    }

    @Test
    fun `los tres autos tienen trazos validos y cerrados`() {
        assertEquals(setOf("auto", "suv", "camioneta"), CarShapes.ALL.keys)
        CarShapes.ALL.forEach { (name, shape) ->
            val body = PathData.parse(shape.body)
            assertEquals('M', body.first().op, name)
            assertEquals('Z', body.last().op, name)
            assertTrue(shape.glass.size == 2, name)
            shape.glass.forEach { g -> assertEquals('Z', PathData.parse(g).last().op, name) }
            assertTrue(shape.wheels.size == 2 && shape.wheels.all { it in 0..400 }, name)
        }
    }

    @Test
    fun `todas las coordenadas caben en el lienzo de 400 por 150`() {
        CarShapes.ALL.forEach { (name, shape) ->
            (listOf(shape.body) + shape.glass).flatMap { PathData.parse(it) }.forEach { cmd ->
                cmd.args.forEachIndexed { i, v ->
                    val max = if (i % 2 == 0) CarShapes.WIDTH else CarShapes.HEIGHT
                    assertTrue(v in 0f..max, "$name: $cmd")
                }
            }
        }
    }

    @Test
    fun `un tipo desconocido usa el auto`() {
        assertSame(CarShapes.ALL.getValue("auto"), CarShapes.of("nave"))
        assertSame(CarShapes.ALL.getValue("suv"), CarShapes.of("suv"))
    }

    @Test
    fun `solo la camioneta tiene detalle extra`() {
        assertEquals(5, CarShapes.of("camioneta").extraRect?.size)
        assertEquals(null, CarShapes.of("auto").extraRect)
    }
}
