package br.edu.tradutoroffline.features.camera

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.view.View

internal data class OverlayText(val box: RectF, val translation: String?)

/** Coordinates already include preview crop/rotation via CameraX MlKitAnalyzer. */
internal class TranslationOverlay(context: Context) : View(context) {
    private var rows = emptyList<OverlayText>()
    private val density = resources.displayMetrics.density
    private val background = Paint().apply { color = Color.argb(235, 14, 27, 35) }
    private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(77, 235, 188); style = Paint.Style.STROKE; strokeWidth = 2 * density
    }
    private val text = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }

    fun show(value: List<OverlayText>) {
        rows = value
        contentDescription = value.mapNotNull { it.translation }.joinToString(". ")
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0) return
        val padding = 5 * density
        for (row in rows) {
            val box = RectF(row.box)
            if (!box.intersect(0f, 0f, width.toFloat(), height.toFloat())) continue
            canvas.drawRoundRect(box, 4 * density, 4 * density, outline)
            val value = row.translation ?: continue
            val maxWidth = (width - 2 * padding).toInt().coerceAtLeast(1)
            val labelWidth = box.width().coerceAtLeast(140 * density).toInt().coerceAtMost(maxWidth)
            var fontSize = 19 * density
            fun layout(): StaticLayout {
                text.textSize = fontSize
                return StaticLayout.Builder.obtain(value, 0, value.length, text, labelWidth)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL).setIncludePad(false).build()
            }
            var label = layout()
            val availableHeight = (height - 2 * padding).coerceAtLeast(1f)
            val targetHeight = box.height().coerceAtLeast(48 * density).coerceAtMost(availableHeight)
            while (label.height > targetHeight && fontSize > 11 * density) {
                fontSize -= density; label = layout()
            }
            val left = box.left.coerceIn(0f, (width - labelWidth - 2 * padding).coerceAtLeast(0f))
            val top = box.top.coerceIn(0f, (height - label.height - 2 * padding).coerceAtLeast(0f))
            val card = RectF(left, top, left + labelWidth + 2 * padding, top + label.height + 2 * padding)
            canvas.drawRoundRect(card, 4 * density, 4 * density, background)
            canvas.save()
            canvas.translate(left + padding, top + padding)
            label.draw(canvas)
            canvas.restore()
        }
    }
}
