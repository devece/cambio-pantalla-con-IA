package cl.tablero.launcher

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.view.View
import cl.tablero.core.CarShapes
import cl.tablero.core.PathData

/** Dibuja el auto de perfil (las mismas formas del sitio) en el tipo y color elegidos. */
class CarView(context: Context) : View(context) {
    var carType: String = "auto"
        set(value) {
            field = value
            invalidate()
        }
    var carColor: Int = Color.parseColor("#b3261e")
        set(value) {
            field = value
            invalidate()
        }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bounds = RectF()
    private val cache = HashMap<String, Path>()

    override fun onDraw(canvas: Canvas) {
        val shape = CarShapes.of(carType)
        val scale = minOf(width / CarShapes.WIDTH, height / CarShapes.HEIGHT)
        canvas.save()
        canvas.translate((width - CarShapes.WIDTH * scale) / 2f, (height - CarShapes.HEIGHT * scale) / 2f)
        canvas.scale(scale, scale)

        // Sombra en el piso
        paint.style = Paint.Style.FILL
        paint.shader = null
        paint.color = Color.argb(97, 0, 0, 0)
        canvas.drawOval(RectF(10f, 109f, 390f, 127f), paint)

        // Carrocería con brillo arriba y sombra abajo
        val body = path(shape.body)
        paint.color = carColor
        canvas.drawPath(body, paint)
        body.computeBounds(bounds, true)
        paint.shader = LinearGradient(
            0f, bounds.top, 0f, bounds.bottom,
            intArrayOf(Color.argb(82, 255, 255, 255), Color.argb(0, 255, 255, 255), Color.argb(87, 0, 0, 0)),
            floatArrayOf(0f, 0.45f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawPath(body, paint)
        paint.shader = null

        shape.extraRect?.let { r ->
            paint.color = Color.argb(71, 0, 0, 0)
            canvas.drawRoundRect(RectF(r[0], r[1], r[0] + r[2], r[1] + r[3]), r[4], r[4], paint)
        }

        paint.color = Color.parseColor("#d4e8f4")
        shape.glass.forEach { canvas.drawPath(path(it), paint) }

        shape.wheels.forEach { cx ->
            val x = cx.toFloat()
            paint.color = Color.argb(140, 0, 0, 0)
            canvas.drawCircle(x, 108f, 29f, paint)
            paint.color = Color.parseColor("#101214")
            canvas.drawCircle(x, 108f, 25f, paint)
            paint.color = Color.parseColor("#a3acb3")
            canvas.drawCircle(x, 108f, 13f, paint)
            paint.color = Color.parseColor("#3a4047")
            canvas.drawCircle(x, 108f, 5f, paint)
        }

        // Faro y luz trasera
        paint.color = Color.parseColor("#e6edf0")
        canvas.drawPath(path("M368 88 L384 90 L384 98 L366 96 Z"), paint)
        paint.color = Color.parseColor("#9c2a24")
        canvas.drawRoundRect(RectF(14f, 88f, 20f, 98f), 1.5f, 1.5f, paint)

        canvas.restore()
    }

    private fun path(d: String): Path = cache.getOrPut(d) {
        Path().apply {
            PathData.parse(d).forEach { c ->
                val a = c.args
                when (c.op) {
                    'M' -> moveTo(a[0], a[1])
                    'L' -> lineTo(a[0], a[1])
                    'Q' -> quadTo(a[0], a[1], a[2], a[3])
                    'Z' -> close()
                }
            }
        }
    }
}
