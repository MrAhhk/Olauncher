package app.subconsciously.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.util.AttributeSet
import android.view.View
import app.subconsciously.data.RadarSnapshot
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

class PentagonRadarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var current: RadarSnapshot? = null
        set(value) { field = value; invalidate() }
    var week: RadarSnapshot? = null
        set(value) { field = value; invalidate() }
    var month: RadarSnapshot? = null
        set(value) { field = value; invalidate() }
    private val labels = arrayOf("Aware", "Control", "Intention", "Time", "Energy")

    private val colorCurrent = Color.parseColor("#4ADE80")
    private val colorWeek    = Color.parseColor("#FACC15")
    private val colorMonth   = Color.parseColor("#F87171")
    private val colorGrid    = Color.argb(20, 255, 255, 255)
    private val colorAxis    = Color.argb(30, 255, 255, 255)
    private val colorLabel   = Color.parseColor("#38BDF8")

    private val paintFill   = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val paintStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.5f }
    private val paintGrid   = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 0.8f; color = colorGrid }
    private val paintAxis   = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 0.8f; color = colorAxis }
    private val paintLabel  = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colorLabel; textAlign = Paint.Align.CENTER }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val r = min(cx, cy) * 0.86f
        val labelR = r + paintLabel.textSize * 1.6f

        val sp = resources.displayMetrics.scaledDensity
        paintLabel.textSize = 12f * sp

        drawGrid(canvas, cx, cy, r)
        drawAxes(canvas, cx, cy, r)
        month?.let   { drawPolygon(canvas, cx, cy, r, it, colorMonth, 0.18f) }
        week?.let    { drawPolygon(canvas, cx, cy, r, it, colorWeek, 0.30f) }
        current?.let { drawPolygon(canvas, cx, cy, r, it, colorCurrent, 0.22f) }
        drawLabels(canvas, cx, cy, labelR)
    }

    private fun vertex(cx: Float, cy: Float, r: Float, i: Int, v: Float = 1f): PointF {
        val a = Math.toRadians(-90.0 + 72.0 * i)
        return PointF(cx + (r * v * cos(a)).toFloat(), cy + (r * v * sin(a)).toFloat())
    }

    private fun drawGrid(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        repeat(10) { ring ->
            val rr = r * (ring + 1) / 10f
            val path = Path()
            for (i in 0 until 5) {
                val p = vertex(cx, cy, rr, i)
                if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
            }
            path.close()
            canvas.drawPath(path, paintGrid)
        }
    }

    private fun drawAxes(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        for (i in 0 until 5) {
            val p = vertex(cx, cy, r, i)
            canvas.drawLine(cx, cy, p.x, p.y, paintAxis)
        }
    }

    private fun drawPolygon(canvas: Canvas, cx: Float, cy: Float, r: Float, snap: RadarSnapshot, color: Int, fillAlpha: Float) {
        val values = floatArrayOf(snap.awareness, snap.control, snap.intention, snap.time, snap.energy)
        val path = Path()
        for (i in 0 until 5) {
            val p = vertex(cx, cy, r, i, values[i].coerceIn(0.05f, 1f))
            if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
        }
        path.close()
        paintFill.color = color
        paintFill.alpha = (fillAlpha * 255).toInt()
        canvas.drawPath(path, paintFill)
        paintStroke.color = color
        paintStroke.alpha = (fillAlpha * 2f * 255).toInt().coerceAtMost(255)
        canvas.drawPath(path, paintStroke)
    }

    private fun drawLabels(canvas: Canvas, cx: Float, cy: Float, labelR: Float) {
        for (i in 0 until 5) {
            val p = vertex(cx, cy, labelR, i)
            canvas.drawText(labels[i], p.x, p.y + paintLabel.textSize / 3f, paintLabel)
        }
    }

}
