package cl.tablero.launcher

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.view.View

/** Señal de límite de velocidad: círculo blanco con borde rojo y el número al centro. */
class LimitSignView(context: Context) : View(context) {
    /** null = no hay límite que mostrar. */
    var limit: Int? = null
        set(value) {
            field = value
            invalidate()
        }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val r = minOf(width, height) / 2f - 2f
        val active = limit != null

        paint.style = Paint.Style.FILL
        paint.color = if (active) Color.WHITE else Color.argb(40, 255, 255, 255)
        canvas.drawCircle(cx, cy, r, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = r * 0.2f
        paint.color = if (active) Ui.ALERT else Color.argb(90, 255, 255, 255)
        canvas.drawCircle(cx, cy, r - paint.strokeWidth / 2f, paint)

        paint.style = Paint.Style.FILL
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
        paint.color = if (active) Color.parseColor("#101418") else Ui.SUB
        val label = limit?.toString() ?: "—"
        paint.textSize = r * if (label.length >= 3) 0.78f else 0.95f
        canvas.drawText(label, cx, cy - (paint.ascent() + paint.descent()) / 2f, paint)
    }
}
