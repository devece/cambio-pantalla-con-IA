package cl.tablero.core

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/** Un polígono ya proyectado a la pantalla, con su color. `alpha` 1 = opaco. */
class Poly(val pts: FloatArray, val r: Int, val g: Int, val b: Int, val alpha: Float, internal val depth: Double)

/** Lo que hay que dibujar, de atrás hacia adelante. */
class CarScene(val polys: List<Poly>)

/**
 * Auto en 3D dibujado por código: un modelo simple (carrocería y cabina por secciones, ruedas, faros) que se
 * proyecta en perspectiva y se sombrea con una luz. Sirve para auto, SUV y camioneta, en cualquier color y ángulo.
 * Se ve igual en Android que en la maqueta del sitio (mismas cuentas).
 */
object Car3D {
    /** Ángulo con que se ve el auto: 0 = de frente, 90 = de lado. */
    const val DEFAULT_PHI = 34.0
    const val DEFAULT_PITCH = 11.0

    private const val DISTANCE = 11.0
    private const val FOCAL = 800.0

    /** x = largo (frente +x), y = alto, z = ancho. Cada sección: x, piso, techo, semiancho. */
    private class Model(
        val lower: List<DoubleArray>,
        val cabin: List<DoubleArray>,
        val wheelX: DoubleArray,
        val wheelR: Double,
        val front: Double,
        val pillarX: Double,
        val seams: DoubleArray,
        val mirrorX: Double,
        val mirrorY: Double,
        val bed: DoubleArray? = null,
    )

    private fun s(vararg v: Double) = v

    private val MODELS: Map<String, Model> = mapOf(
        "suv" to Model(
            lower = listOf(
                s(-2.30, 0.50, 1.00, 0.80), s(-2.22, 0.38, 1.16, 0.88), s(-2.05, 0.32, 1.24, 0.92), s(-1.0, 0.32, 1.20, 0.93),
                s(0.4, 0.32, 1.16, 0.93), s(1.25, 0.32, 1.08, 0.92), s(1.85, 0.32, 1.0, 0.90), s(2.12, 0.34, 0.94, 0.87), s(2.24, 0.42, 0.84, 0.79),
            ),
            cabin = listOf(
                s(-2.0, 1.12, 1.30, 0.66), s(-1.92, 1.12, 1.70, 0.70), s(-1.6, 1.12, 1.84, 0.73), s(0.0, 1.12, 1.86, 0.73),
                s(0.5, 1.12, 1.74, 0.72), s(1.1, 1.12, 1.22, 0.67),
            ),
            wheelX = s(1.5, -1.45), wheelR = 0.42, front = 2.24, pillarX = -0.55, seams = s(0.45, -0.55, -1.5), mirrorX = 1.02, mirrorY = 1.16,
        ),
        "auto" to Model(
            lower = listOf(
                s(-2.2, 0.46, 0.92, 0.76), s(-2.12, 0.34, 1.0, 0.84), s(-1.9, 0.28, 1.0, 0.88), s(-0.8, 0.26, 0.98, 0.9),
                s(0.8, 0.26, 0.96, 0.9), s(1.5, 0.26, 0.9, 0.88), s(2.0, 0.27, 0.82, 0.85), s(2.14, 0.30, 0.76, 0.82), s(2.22, 0.36, 0.68, 0.74),
            ),
            cabin = listOf(
                s(-1.6, 0.96, 1.02, 0.60), s(-1.2, 0.96, 1.36, 0.66), s(-0.8, 0.96, 1.46, 0.69),
                s(0.15, 0.96, 1.46, 0.69), s(0.65, 0.96, 1.30, 0.66), s(1.2, 0.96, 1.0, 0.62),
            ),
            wheelX = s(1.45, -1.35), wheelR = 0.34, front = 2.22, pillarX = -0.3, seams = s(0.4, -0.45, -1.3), mirrorX = 0.98, mirrorY = 1.0,
        ),
        "camioneta" to Model(
            lower = listOf(
                s(-2.5, 0.50, 0.98, 0.84), s(-2.42, 0.38, 1.04, 0.9), s(-2.2, 0.32, 1.06, 0.93), s(-0.3, 0.32, 1.06, 0.93),
                s(-0.25, 0.32, 1.12, 0.93), s(1.4, 0.32, 1.06, 0.92), s(2.0, 0.32, 0.98, 0.89), s(2.3, 0.34, 0.93, 0.86), s(2.42, 0.42, 0.84, 0.78),
            ),
            cabin = listOf(
                s(-0.3, 1.1, 1.22, 0.72), s(-0.2, 1.1, 1.74, 0.73), s(0.15, 1.1, 1.82, 0.73),
                s(0.75, 1.1, 1.8, 0.73), s(1.2, 1.1, 1.56, 0.70), s(1.6, 1.1, 1.14, 0.66),
            ),
            wheelX = s(1.65, -1.6), wheelR = 0.43, front = 2.42, pillarX = 0.5, seams = s(0.85, -0.1), mirrorX = 1.45, mirrorY = 1.18,
            bed = s(-2.38, -0.4, 0.74, 1.03),
        ),
    )

    val TYPES: Set<String> get() = MODELS.keys

    /** Construye la escena. `rgb` es el color de la carrocería (el alfa se ignora). */
    fun build(type: String, rgb: Int, phiDeg: Double = DEFAULT_PHI, pitchDeg: Double = DEFAULT_PITCH): CarScene =
        Builder(MODELS[type] ?: MODELS.getValue("auto"), rgb, phiDeg, pitchDeg).run()

    /** Caja (minX, minY, maxX, maxY) que ocupa el auto al girar entre `phiMin` y `phiMax`, para encuadrarlo siempre igual. */
    fun bounds(type: String, rgb: Int, phiMin: Double, phiMax: Double, pitchDeg: Double = DEFAULT_PITCH): FloatArray {
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        for (phi in listOf(phiMin, (phiMin + phiMax) / 2, phiMax)) {
            for (p in build(type, rgb, phi, pitchDeg).polys) {
                var i = 0
                while (i < p.pts.size) {
                    minX = min(minX, p.pts[i]); maxX = max(maxX, p.pts[i])
                    minY = min(minY, p.pts[i + 1]); maxY = max(maxY, p.pts[i + 1])
                    i += 2
                }
            }
        }
        return floatArrayOf(minX, minY, maxX, maxY)
    }

    /* ---------- vectores ---------- */

    private fun sub(a: DoubleArray, b: DoubleArray) = doubleArrayOf(a[0] - b[0], a[1] - b[1], a[2] - b[2])
    private fun dot(a: DoubleArray, b: DoubleArray) = a[0] * b[0] + a[1] * b[1] + a[2] * b[2]
    private fun cross(a: DoubleArray, b: DoubleArray) =
        doubleArrayOf(a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0])

    private fun norm(a: DoubleArray): DoubleArray {
        val l = hypot(hypot(a[0], a[1]), a[2]).let { if (it == 0.0) 1.0 else it }
        return doubleArrayOf(a[0] / l, a[1] / l, a[2] / l)
    }

    private fun neg(a: DoubleArray) = doubleArrayOf(-a[0], -a[1], -a[2])

    private fun mean(q: List<DoubleArray>): DoubleArray {
        val m = DoubleArray(3)
        for (v in q) for (i in 0..2) m[i] += v[i]
        for (i in 0..2) m[i] /= q.size
        return m
    }

    private fun densify(st: List<DoubleArray>, m: Int): List<DoubleArray> {
        val out = mutableListOf<DoubleArray>()
        for (i in 0 until st.size - 1) {
            for (k in 0 until m) {
                val t = k.toDouble() / m
                out += DoubleArray(4) { j -> st[i][j] + (st[i + 1][j] - st[i][j]) * t }
            }
        }
        out += st.last()
        return out
    }

    /** Sección transversal redondeada (superelipse) de una estación. */
    private fun ring(st: DoubleArray, n: Double, seg: Int, zOff: Double): List<DoubleArray> {
        val cy = (st[1] + st[2]) / 2
        val ry = (st[2] - st[1]) / 2
        return List(seg) { k ->
            val t = k.toDouble() / seg * PI * 2
            val c = cos(t)
            val sn = sin(t)
            val z = Math.signum(c) * abs(c).pow(2 / n) * st[3]
            val y = cy + Math.signum(sn) * abs(sn).pow(2 / n) * ry
            doubleArrayOf(st[0], y, z + zOff)
        }
    }

    /* ---------- construcción ---------- */

    private class Builder(val m: Model, rgb: Int, phiDeg: Double, pitchDeg: Double) {
        private val base = doubleArrayOf(((rgb shr 16) and 255).toDouble(), ((rgb shr 8) and 255).toDouble(), (rgb and 255).toDouble())
        private val a = (-90 - phiDeg) * PI / 180
        private val p = pitchDeg * PI / 180
        private val light = norm(doubleArrayOf(0.0, 0.72, 0.7))
        private val polys = mutableListOf<Poly>()
        private val details = mutableListOf<Poly>()
        private val wheels = mutableListOf<Poly>()

        private fun cam(v: DoubleArray): DoubleArray {
            val x1 = v[0] * cos(a) + v[2] * sin(a)
            val z1 = -v[0] * sin(a) + v[2] * cos(a)
            val y2 = v[1] * cos(p) - z1 * sin(p)
            val z2 = v[1] * sin(p) + z1 * cos(p)
            return doubleArrayOf(x1, y2 - 0.9, z2 - DISTANCE)
        }

        private fun proj(q: DoubleArray) = doubleArrayOf(FOCAL * q[0] / -q[2], -FOCAL * q[1] / -q[2])

        private fun poly(pts: List<DoubleArray>, color: DoubleArray, depth: Double, alpha: Float = 1f): Poly {
            val f = FloatArray(pts.size * 2)
            pts.forEachIndexed { i, q ->
                val pr = proj(q)
                f[i * 2] = pr[0].toFloat()
                f[i * 2 + 1] = pr[1].toFloat()
            }
            fun c(v: Double) = min(255, max(0, v.roundToInt()))
            return Poly(f, c(color[0]), c(color[1]), c(color[2]), alpha, depth)
        }

        private fun shade(n: DoubleArray, mid: DoubleArray, kind: String): DoubleArray {
            val lit = max(0.0, dot(n, light))
            val v = norm(neg(mid))
            val h = norm(doubleArrayOf(light[0] + v[0], light[1] + v[1], light[2] + v[2]))
            val spec = max(0.0, dot(n, h)).pow(38.0)
            return when (kind) {
                "glass" -> {
                    val k = 0.35 + 0.9 * lit
                    doubleArrayOf(28 * k + 18 + spec * 120, 44 * k + 26 + spec * 120, 62 * k + 36 + spec * 120)
                }
                "dark" -> {
                    val k = 0.5 + 0.5 * lit
                    doubleArrayOf(22 * k, 25 * k, 28 * k)
                }
                else -> {
                    val k = 0.6 + 0.62 * lit
                    doubleArrayOf(base[0] * k + spec * 120, base[1] * k + spec * 120, base[2] * k + spec * 120)
                }
            }
        }

        private fun addQuad(world: List<DoubleArray>, kind: String, centerRef: DoubleArray) {
            val q = world.map(::cam)
            val n = norm(cross(sub(q[1], q[0]), sub(q[3], q[0])))
            val mid = mean(q)
            val nn = if (dot(n, sub(mid, cam(centerRef))) < 0) neg(n) else n
            if (dot(nn, neg(mid)) <= 0) return
            polys += poly(q, shade(nn, mid, kind), mid[2])
        }

        private fun loft(
            stations: List<DoubleArray>,
            n: Double,
            seg: Int,
            glassRule: ((Double, Double, Boolean) -> Boolean)?,
            zOff: Double = 0.0,
        ) {
            val rings = stations.map { ring(it, n, seg, zOff) }
            for (i in 0 until rings.size - 1) {
                val flat = abs(stations[i][2] - stations[i + 1][2]) < 0.035
                val center = doubleArrayOf((stations[i][0] + stations[i + 1][0]) / 2, (stations[i][1] + stations[i][2]) / 2, zOff)
                for (k in 0 until seg) {
                    val k2 = (k + 1) % seg
                    val quad = listOf(rings[i][k], rings[i][k2], rings[i + 1][k2], rings[i + 1][k])
                    val t = (k + 0.5) / seg * PI * 2
                    val phi = atan2(sin(t), abs(cos(t)))
                    val x = (stations[i][0] + stations[i + 1][0]) / 2
                    addQuad(quad, if (glassRule != null && glassRule(x, phi, flat)) "glass" else "body", center)
                }
            }
            for (i in listOf(0, stations.size - 1)) {
                val c = doubleArrayOf(stations[i][0], (stations[i][1] + stations[i][2]) / 2, zOff)
                val outward = doubleArrayOf(c[0] + (if (i == 0) -1 else 1), c[1], c[2])
                val q = rings[i].map(::cam)
                val n2 = norm(cross(sub(q[1], q[0]), sub(q[2], q[0])))
                val mid = mean(q)
                val nn = if (dot(n2, sub(cam(outward), cam(c))) < 0) neg(n2) else n2
                if (dot(nn, neg(mid)) <= 0) continue
                polys += poly(q, shade(nn, mid, "body"), mid[2] - 0.02)
            }
        }

        private fun frontPlane(y0: Double, y1: Double, z0: Double, z1: Double, rgb: DoubleArray, bias: Double) {
            val fx = m.front + 0.004
            val q = listOf(
                doubleArrayOf(fx, y0, z0), doubleArrayOf(fx, y0, z1), doubleArrayOf(fx, y1, z1), doubleArrayOf(fx, y1, z0),
            ).map(::cam)
            val mid = mean(q)
            val n = norm(cross(sub(q[1], q[0]), sub(q[3], q[0])))
            val nn = if (dot(n, sub(cam(doubleArrayOf(fx + 1, y0, 0.0)), cam(doubleArrayOf(fx, y0, 0.0)))) < 0) neg(n) else n
            if (dot(nn, neg(mid)) <= 0) return
            details += poly(q, rgb, mid[2] + 0.2 + bias)
        }

        fun run(): CarScene {
            // carrocería baja
            loft(densify(m.lower, 2), 4.2, 30, null)

            // hueco de la caja de la camioneta
            m.bed?.let { bd ->
                val q = listOf(
                    doubleArrayOf(bd[0], bd[3] + 0.002, -bd[2]), doubleArrayOf(bd[1], bd[3] + 0.002, -bd[2]),
                    doubleArrayOf(bd[1], bd[3] + 0.002, bd[2]), doubleArrayOf(bd[0], bd[3] + 0.002, bd[2]),
                ).map(::cam)
                polys += poly(q, doubleArrayOf(30.0, 33.0, 37.0), mean(q)[2] + 0.12)
            }

            // cabina con pilares
            val glassRule = { x: Double, phi: Double, flat: Boolean ->
                when {
                    phi < 0 -> false
                    flat -> phi < 0.62 && abs(x - m.pillarX) > 0.05
                    else -> !(phi > 0.6 && phi < 0.72)
                }
            }
            loft(densify(m.cabin, 4), 3.2, 28, glassRule)

            // espejo del lado visible
            val mx = m.mirrorX
            val my = m.mirrorY
            loft(
                densify(listOf(s(mx - 0.1, my - 0.04, my + 0.09, 0.05), s(mx + 0.06, my - 0.04, my + 0.09, 0.06), s(mx + 0.12, my - 0.02, my + 0.07, 0.05)), 2),
                3.0, 12, null, -(m.lower[3][3] + 0.03),
            )

            // frente: faros, parrilla, moldura y toma de aire
            val nose = m.lower.last()
            val lampTop = nose[2] - 0.04
            val lamp = doubleArrayOf(255.0, 246.0, 205.0)
            val darkC = doubleArrayOf(18.0, 20.0, 23.0)
            frontPlane(lampTop - 0.23, lampTop - 0.04, -0.34, 0.34, darkC, 0.01)
            frontPlane(lampTop - 0.14, lampTop - 0.12, -0.34, 0.34, doubleArrayOf(205.0, 212.0, 218.0), 0.03)
            frontPlane(nose[1] + 0.01, nose[1] + 0.13, -0.52, 0.52, darkC, 0.01)
            frontPlane(lampTop - 0.17, lampTop, 0.40, 0.69, lamp, 0.02)
            frontPlane(lampTop - 0.17, lampTop, -0.69, -0.40, lamp, 0.02)

            // líneas de puertas
            val sideHW = m.lower[3][3]
            for (sx in m.seams) {
                val lo = m.lower.minByOrNull { abs(it[0] - sx) }!!
                val y0 = lo[1] + 0.18
                val y1 = lo[2] - 0.16
                val zz = -(lo[3] + 0.004)
                val q = listOf(
                    doubleArrayOf(sx, y0, zz), doubleArrayOf(sx + 0.014, y0, zz), doubleArrayOf(sx + 0.014, y1, zz), doubleArrayOf(sx, y1, zz),
                ).map(::cam)
                details += poly(q, doubleArrayOf(20.0, 22.0, 25.0), mean(q)[2] + 0.1)
            }

            // ruedas del lado visible
            val zOut = -sideHW
            for (wx in m.wheelX) {
                val r = m.wheelR
                fun disc(rad: Double, zz: Double, col: DoubleArray, bias: Double) {
                    val pts = List(30) { k ->
                        val t = k / 30.0 * PI * 2
                        cam(doubleArrayOf(wx + cos(t) * rad, r + sin(t) * rad, zz))
                    }
                    wheels += poly(pts, col, cam(doubleArrayOf(wx, r, zz))[2] + bias)
                }
                disc(r + 0.075, zOut, doubleArrayOf(10.0, 12.0, 14.0), 0.30)
                disc(r, zOut + 0.02, doubleArrayOf(22.0, 23.0, 25.0), 0.31)
                disc(r * 0.66, zOut + 0.03, doubleArrayOf(176.0, 184.0, 190.0), 0.32)
                disc(r * 0.52, zOut + 0.035, doubleArrayOf(70.0, 77.0, 83.0), 0.325)
                for (sp in 0 until 5) {
                    val t0 = sp / 5.0 * PI * 2
                    val pts = listOf(0.12 to -0.1, 0.12 to 0.1, 0.62 to 0.07, 0.62 to -0.07).map { (rr, da) ->
                        val t = t0 + da
                        cam(doubleArrayOf(wx + cos(t) * r * rr, r + sin(t) * r * rr, zOut + 0.04))
                    }
                    wheels += poly(pts, doubleArrayOf(190.0, 197.0, 203.0), cam(doubleArrayOf(wx, r, zOut + 0.04))[2] + 0.33)
                }
                disc(r * 0.14, zOut + 0.045, doubleArrayOf(215.0, 220.0, 224.0), 0.335)
            }

            polys.sortBy { it.depth }
            wheels.sortBy { it.depth }

            // sombra en el piso
            val sx = (m.lower.first()[0] + m.lower.last()[0]) / 2
            val shadowPts = List(40) { k ->
                val t = k / 40.0 * PI * 2
                cam(doubleArrayOf(sx + cos(t) * 2.5, 0.0, 0.1 + sin(t) * 1.1))
            }
            val shadow = poly(shadowPts, doubleArrayOf(0.0, 0.0, 0.0), -99.0, 0.38f)
            // Los detalles (faros, parrilla, puertas) van encima de la carrocería, en el orden en que se agregaron.
            return CarScene(listOf(shadow) + polys + details + wheels)
        }
    }
}
