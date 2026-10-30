package cl.tablero.launcher

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.view.View

/** Fondo del tema Ruta: degradado verde, cordillera lejana y una ruta con línea central. */
class BackgroundView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var gradient: Shader? = null
    private var ridges: List<Path> = emptyList()
    private val road = Path()
    private val dash = Path()

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        gradient = LinearGradient(0f, 0f, 0f, h.toFloat(), Ui.BG1, Ui.BG2, Shader.TileMode.CLAMP)
        ridges = listOf(
            ridge(11L, w, h, 0.50f, 0.26f, 12),
            ridge(23L, w, h, 0.64f, 0.18f, 14),
            ridge(37L, w, h, 0.76f, 0.10f, 16),
        )

        // Ruta en perspectiva: angosta en el horizonte, ancha abajo.
        val cx = w / 2f
        val top = h * 0.55f
        road.reset()
        road.moveTo(cx - w * 0.015f, top)
        road.lineTo(cx + w * 0.015f, top)
        road.lineTo(cx + w * 0.16f, h.toFloat())
        road.lineTo(cx - w * 0.16f, h.toFloat())
        road.close()

        // Línea central discontinua: los trazos crecen al acercarse.
        dash.reset()
        val n = 8
        for (i in 0 until n) {
            val a = i / n.toFloat()
            val b = a + 0.5f / n
            val ya = top + (h - top) * a * a
            val yb = top + (h - top) * b * b
            val wa = w * (0.002f + 0.010f * a)
            val wb = w * (0.002f + 0.010f * b)
            dash.moveTo(cx - wa, ya)
            dash.lineTo(cx + wa, ya)
            dash.lineTo(cx + wb, yb)
            dash.lineTo(cx - wb, yb)
            dash.close()
        }
    }

    override fun onDraw(canvas: Canvas) {
        paint.shader = gradient
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        paint.shader = null

        val alphas = intArrayOf(14, 22, 34)
        ridges.forEachIndexed { i, p ->
            paint.color = Color.argb(alphas[i], 255, 255, 255)
            canvas.drawPath(p, paint)
        }
        paint.color = Color.argb(60, 0, 0, 0)
        canvas.drawPath(road, paint)
        paint.color = Color.argb(190, 245, 196, 0)
        canvas.drawPath(dash, paint)
    }

    /** Cerro aleatorio pero siempre igual (misma semilla), como en public/js/art.js. */
    private fun ridge(seed: Long, w: Int, h: Int, base: Float, amp: Float, steps: Int): Path {
        var s = seed
        val p = Path()
        p.moveTo(0f, h.toFloat())
        for (i in 0..steps) {
            s = (s * 16807L) % 2147483647L
            val r = s / 2147483647.0
            val x = w * i / steps.toFloat()
            val y = h * base - (r * amp * h).toFloat() - (if (i % 2 == 1) amp * h * 0.35f else 0f)
            p.lineTo(x, y)
        }
        p.lineTo(w.toFloat(), h.toFloat())
        p.close()
        return p
    }
}
