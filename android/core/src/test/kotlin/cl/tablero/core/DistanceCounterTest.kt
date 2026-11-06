package cl.tablero.core

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DistanceCounterTest {
    private val lat0 = -33.45
    private val lon0 = -70.66
    private val metersPerDegree = 111_195.0

    /** Un punto a [northM] metros al norte del origen. */
    private fun fix(northM: Double, timeMs: Long, accuracyM: Float? = 5f, speedKmh: Double? = null, eastM: Double = 0.0) =
        GpsFix(
            lat = lat0 + northM / metersPerDegree,
            lon = lon0 + eastM / (metersPerDegree * Math.cos(Math.toRadians(lat0))),
            timeMs = timeMs,
            accuracyM = accuracyM,
            speedMps = speedKmh?.let { (it / 3.6).toFloat() },
        )

    /** Maneja [totalM] metros al norte a [kmh], un punto por segundo. Devuelve los km enteros que se contaron. */
    private fun drive(c: DistanceCounter, totalM: Double, kmh: Double, startM: Double = 0.0, startT: Long = 0L, reportSpeed: Boolean = true): Int {
        val stepM = kmh / 3.6
        var km = 0
        var m = startM
        var t = startT
        km += c.onFix(fix(m, t, speedKmh = if (reportSpeed) kmh else null))
        while (m < startM + totalM - 1e-6) {
            m += stepM
            t += 1000
            km += c.onFix(fix(m, t, speedKmh = if (reportSpeed) kmh else null))
        }
        return km
    }

    private fun total(km: Int, c: DistanceCounter) = km + c.carryMeters / 1000.0

    @Test
    fun `la distancia entre dos puntos es la real`() {
        // Un grado de latitud son unos 111,2 km.
        val d = DistanceCounter.distanceMeters(0.0, 0.0, 1.0, 0.0)
        assertTrue(abs(d - 111_195.0) < 100.0, "dio $d")
        assertEquals(0.0, DistanceCounter.distanceMeters(lat0, lon0, lat0, lon0), 0.001)
    }

    @Test
    fun `diez kilometros a 60 con velocidad del GPS suman diez`() {
        val c = DistanceCounter()
        val km = drive(c, 10_000.0, 60.0)
        assertTrue(abs(total(km, c) - 10.0) < 0.05, "dio ${total(km, c)}")
        assertTrue(km in 9..10)
    }

    @Test
    fun `sin velocidad del GPS tambien suma bien, a paso normal y a paso lento`() {
        for (kmh in listOf(50.0, 20.0)) {
            val c = DistanceCounter()
            val km = drive(c, 10_000.0, kmh, reportSpeed = false)
            assertTrue(abs(total(km, c) - 10.0) < 0.1, "a $kmh km/h dio ${total(km, c)}")
        }
    }

    @Test
    fun `auto detenido con el GPS bailando no suma, con velocidad informada`() {
        val c = DistanceCounter()
        var km = 0
        for (i in 0 until 1200) { // 20 minutos, el punto salta ±5 m
            val jitter = if (i % 2 == 0) 5.5 else -5.5
            km += c.onFix(fix(jitter, i * 1000L, accuracyM = 8f, speedKmh = 0.4))
        }
        assertEquals(0, km)
        assertEquals(0, c.carryMeters)
    }

    @Test
    fun `auto detenido con el GPS bailando no suma, sin velocidad informada`() {
        val c = DistanceCounter()
        var km = 0
        for (i in 0 until 1200) {
            val jitter = if (i % 2 == 0) 5.5 else -5.5
            km += c.onFix(fix(jitter, i * 1000L, accuracyM = 8f))
        }
        assertEquals(0, km)
        assertEquals(0, c.carryMeters)
    }

    @Test
    fun `un salto imposible no suma pero el viaje sigue desde el nuevo punto`() {
        val c = DistanceCounter()
        c.onFix(fix(0.0, 0, speedKmh = 50.0))
        // 5 km en 10 segundos: 1800 km/h, es un error del GPS.
        assertEquals(0, c.onFix(fix(5_000.0, 10_000, speedKmh = 50.0)))
        assertEquals(0, c.carryMeters)
        val km = drive(c, 2_000.0, 50.0, startM = 5_000.0, startT = 10_000)
        assertTrue(abs(total(km, c) - 2.0) < 0.05, "dio ${total(km, c)}")
    }

    @Test
    fun `un punto con mucho error se ignora`() {
        val c = DistanceCounter()
        c.onFix(fix(0.0, 0, speedKmh = 40.0))
        // Punto malo (error de 80 m) 3 km más allá: no debe contar ni mover la partida.
        assertEquals(0, c.onFix(fix(3_000.0, 5_000, accuracyM = 80f, speedKmh = 40.0)))
        // El siguiente punto bueno está 100 m más allá del primero.
        c.onFix(fix(100.0, 9_000, speedKmh = 40.0))
        assertTrue(abs(c.carryMeters - 100) <= 2, "dio ${c.carryMeters}")
    }

    @Test
    fun `un punto sin error informado se acepta`() {
        val c = DistanceCounter()
        val km = drive(c, 3_000.0, 50.0).let { it } // con accuracy 5 por defecto del helper
        assertTrue(km >= 2)
        val c2 = DistanceCounter()
        c2.onFix(fix(0.0, 0, accuracyM = null, speedKmh = 50.0))
        c2.onFix(fix(14.0, 1000, accuracyM = null, speedKmh = 50.0))
        assertTrue(c2.carryMeters in 13..15)
    }

    @Test
    fun `un hueco largo de tiempo no cuenta la linea recta entre los dos puntos`() {
        val c = DistanceCounter()
        c.onFix(fix(0.0, 0, speedKmh = 50.0))
        // 20 minutos después, 30 km más allá (el auto estuvo apagado o en un túnel largo).
        assertEquals(0, c.onFix(fix(30_000.0, 20 * 60_000L, speedKmh = 50.0)))
        assertEquals(0, c.carryMeters)
        // Y desde ahí se sigue contando normal.
        val km = drive(c, 1_000.0, 50.0, startM = 30_000.0, startT = 20 * 60_000L)
        assertTrue(abs(total(km, c) - 1.0) < 0.05)
    }

    @Test
    fun `los puntos repetidos o fuera de orden se ignoran`() {
        val c = DistanceCounter()
        c.onFix(fix(0.0, 10_000, speedKmh = 50.0))
        assertEquals(0, c.onFix(fix(100.0, 10_000, speedKmh = 50.0)))
        assertEquals(0, c.onFix(fix(100.0, 5_000, speedKmh = 50.0)))
        assertEquals(0, c.carryMeters)
    }

    @Test
    fun `los metros acumulados se guardan y se retoman sin perderse`() {
        // Quedaban 900 m para el siguiente kilómetro: 200 m más lo completan y sobran unos 100.
        val c = DistanceCounter(carryMeters = 900)
        val km = drive(c, 200.0, 36.0)
        assertEquals(1, km)
        assertTrue(abs(c.carryMeters - 100) <= 3, "dio ${c.carryMeters}")
        // Un valor guardado raro no rompe nada.
        assertEquals(0, DistanceCounter(carryMeters = -5).carryMeters)
        assertEquals(999, DistanceCounter(carryMeters = 5000).carryMeters)
    }

    @Test
    fun `resetTrack conserva lo acumulado y reset lo borra`() {
        val c = DistanceCounter(carryMeters = 400)
        c.resetTrack()
        assertEquals(400, c.carryMeters)
        // Tras resetTrack el primer punto solo marca la partida: no suma.
        assertEquals(0, c.onFix(fix(0.0, 0, speedKmh = 50.0)))
        assertEquals(400, c.carryMeters)
        c.reset()
        assertEquals(0, c.carryMeters)
    }
}
