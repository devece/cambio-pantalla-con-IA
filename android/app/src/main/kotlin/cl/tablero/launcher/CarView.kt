package cl.tablero.launcher

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.os.SystemClock
import android.view.View
import cl.tablero.core.Car3D
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

/** Dibuja el auto en 3D (auto, SUV o camioneta) en el color elegido, balanceándose despacio para dar profundidad. */
class CarView(context: Context) : View(context) {
    var carType: String = "suv"
        set(value) {
            field = value
            bounds = null
            invalidate()
        }
    var carColor: Int = Color.parseColor("#b3261e")
        set(value) {
            field = value
            invalidate()
        }

    /** false = el auto queda quieto (por ejemplo, si la radio va justa de rendimiento). */
    var animate: Boolean = true

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private var bounds: FloatArray? = null

    private val tick = object : Runnable {
        override fun run() {
            invalidate()
            postDelayed(this, FRAME_MS)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (animate) postDelayed(tick, FRAME_MS)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(tick)
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        if (width == 0 || height == 0) return
        val rgb = carColor and 0xFFFFFF
        val seconds = SystemClock.uptimeMillis() / 1000.0
        val phi = if (animate) BASE_PHI + SWAY * sin(seconds * 2 * PI / PERIOD_S) else BASE_PHI

        // El encuadre se calcula una vez (con todo el vaivén) para que el auto no "salte" al balancearse.
        val b = bounds ?: Car3D.bounds(carType, rgb, BASE_PHI - SWAY, BASE_PHI + SWAY).also { bounds = it }
        val w = b[2] - b[0]
        val h = b[3] - b[1]
        val scale = min(width * 0.97f / w, height * 0.97f / h)

        canvas.save()
        canvas.translate((width - w * scale) / 2f - b[0] * scale, (height - h * scale) / 2f - b[1] * scale)
        canvas.scale(scale, scale)
        paint.strokeJoin = Paint.Join.ROUND
        paint.strokeWidth = 1.2f / scale

        for (p in Car3D.build(carType, rgb, phi).polys) {
            path.reset()
            var i = 0
            while (i < p.pts.size) {
                if (i == 0) path.moveTo(p.pts[0], p.pts[1]) else path.lineTo(p.pts[i], p.pts[i + 1])
                i += 2
            }
            path.close()
            paint.color = Color.argb((p.alpha * 255).toInt(), p.r, p.g, p.b)
            // El borde del mismo color tapa las costuras finas entre polígonos vecinos.
            paint.style = if (p.alpha >= 1f) Paint.Style.FILL_AND_STROKE else Paint.Style.FILL
            canvas.drawPath(path, paint)
        }
        canvas.restore()
    }

    private companion object {
        const val BASE_PHI = Car3D.DEFAULT_PHI
        const val SWAY = 7.0
        const val PERIOD_S = 9.0
        const val FRAME_MS = 33L
    }
}
