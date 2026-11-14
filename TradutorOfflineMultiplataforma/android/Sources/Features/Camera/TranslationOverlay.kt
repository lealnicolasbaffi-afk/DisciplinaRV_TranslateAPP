package br.edu.tradutoroffline.features.camera

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.view.View
import android.util.TypedValue
import kotlin.math.roundToInt

internal data class OverlayText(val id: Long, val quad: TextQuad, val translation: String?, val opacity: Float)

/** Fit text in a local rectangle, then project it onto the four corners of the physical text. */
internal class TranslationOverlay(context: Context) : View(context) {
    private var rows = emptyList<OverlayText>()
    private val density = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 1f, resources.displayMetrics)
    private val panel = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(248, 247, 239) }
    private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(77, 235, 188); style = Paint.Style.STROKE; strokeWidth = resources.displayMetrics.density
    }
    private data class Label(val layout: StaticLayout, val width: Int, val height: Int, val padding: Float)
    private val labels = object : LinkedHashMap<String, Label>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Label>?) = size > 64
    }

    fun show(value: List<OverlayText>) {
        rows = value
        val description = value.mapNotNull { it.translation }.distinct().joinToString(". ")
        if (contentDescription != description) contentDescription = description
        invalidate()
    }

    private fun label(value: String, quad: TextQuad): Label {
        // Quantized layout sizes avoid font reflow for every pixel of OCR jitter.
        val width = ((quad.width / 12).roundToInt() * 12).coerceIn(24, 1600)
        val height = ((quad.height / 8).roundToInt() * 8).coerceIn(16, 1200)
        val key = "$width:$height:$value"
        return labels.getOrPut(key) {
            val padding = (height * 0.06f).coerceIn(1f, 5 * density)
            val contentWidth = (width - padding * 2).toInt().coerceAtLeast(1)
            val contentHeight = (height - padding * 2).coerceAtLeast(1f)
            fun layout(size: Float, maxLines: Int = Int.MAX_VALUE): StaticLayout {
                val paint = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
                    color = Color.rgb(22, 29, 32); textSize = size
                }
                return StaticLayout.Builder.obtain(value, 0, value.length, paint, contentWidth)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL).setIncludePad(false)
                    .setMaxLines(maxLines).setEllipsize(TextUtils.TruncateAt.END).build()
            }
            var low = 4f; var high = (height * 0.8f).coerceAtMost(24 * density).coerceAtLeast(low)
            repeat(8) {
                val mid = (low + high) / 2
                if (layout(mid).height <= contentHeight) low = mid else high = mid
            }
            val full = layout(low)
            val fitted = if (full.height <= contentHeight) full else layout(low, 1)
            Label(fitted, width, height, padding)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        for (row in rows) {
            if (!row.quad.valid() || row.opacity <= 0) continue
            val points = row.quad.corners
            val path = Path().apply {
                moveTo(points[0].x, points[0].y)
                points.drop(1).forEach { lineTo(it.x, it.y) }; close()
            }
            val value = row.translation
            if (value == null) {
                outline.alpha = (row.opacity * 150).toInt()
                canvas.drawPath(path, outline)
                continue
            }
            val label = label(value, row.quad)
            val source = floatArrayOf(0f, 0f, label.width.toFloat(), 0f,
                label.width.toFloat(), label.height.toFloat(), 0f, label.height.toFloat())
            val destination = points.flatMap { listOf(it.x, it.y) }.toFloatArray()
            val projection = Matrix()
            if (!projection.setPolyToPoly(source, 0, destination, 0, 4)) continue
            canvas.save()
            canvas.clipPath(path)
            canvas.concat(projection)
            panel.alpha = (row.opacity * 250).toInt()
            canvas.drawRect(0f, 0f, label.width.toFloat(), label.height.toFloat(), panel)
            label.layout.paint.alpha = (row.opacity * 255).toInt()
            canvas.translate(label.padding, ((label.height - label.layout.height) / 2f).coerceAtLeast(label.padding))
            label.layout.draw(canvas)
            canvas.restore()
        }
    }
}
