package br.edu.tradutoroffline.ui

import android.graphics.*
import android.graphics.drawable.Drawable

/** Consistent 24-unit outline icons, independent of emoji fonts. */
internal class ActionIcon(private val kind: String, private val tint: Int) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 1.8f
        strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND; color = tint
    }
    override fun draw(canvas: Canvas) {
        canvas.save(); canvas.translate(bounds.left.toFloat(), bounds.top.toFloat())
        canvas.scale(bounds.width() / 24f, bounds.height() / 24f)
        fun line(x: Float, y: Float, xx: Float, yy: Float) = canvas.drawLine(x, y, xx, yy, paint)
        fun path(vararg xy: Float) { val p = Path(); p.moveTo(xy[0], xy[1]); for (i in 2 until xy.size step 2) p.lineTo(xy[i], xy[i+1]); canvas.drawPath(p, paint) }
        when (kind) {
            "back" -> { path(14f,5f,7f,12f,14f,19f); line(7f,12f,21f,12f) }
            "camera" -> { canvas.drawRoundRect(3f,6f,21f,20f,3f,3f,paint); path(7f,6f,9f,3f,15f,3f,17f,6f); canvas.drawCircle(12f,13f,4f,paint) }
            "mic" -> { canvas.drawRoundRect(9f,3f,15f,15f,3f,3f,paint); canvas.drawArc(6f,7f,18f,19f,0f,180f,false,paint); line(12f,19f,12f,22f); line(9f,22f,15f,22f) }
            "stop" -> canvas.drawRoundRect(6f,6f,18f,18f,2f,2f,paint)
            "play" -> path(8f,4f,20f,12f,8f,20f,8f,4f)
            "pause" -> { line(8f,5f,8f,19f); line(16f,5f,16f,19f) }
            "speaker" -> { path(3f,9f,7f,9f,12f,5f,12f,19f,7f,15f,3f,15f,3f,9f); canvas.drawArc(10f,4f,22f,20f,-55f,110f,false,paint) }
            "plus" -> { line(12f,5f,12f,19f); line(5f,12f,19f,12f) }
            "download" -> { path(7f,10f,12f,15f,17f,10f); line(12f,3f,12f,15f); path(4f,16f,4f,21f,20f,21f,20f,16f) }
            "check" -> path(5f,12f,10f,17f,20f,6f)
            "folder" -> { path(3f,19f,3f,5f,9f,5f,12f,8f,21f,8f,21f,19f,3f,19f) }
            "settings" -> { for (y in listOf(6f,12f,18f)) line(3f,y,21f,y); canvas.drawCircle(8f,6f,2f,paint); canvas.drawCircle(16f,12f,2f,paint); canvas.drawCircle(9f,18f,2f,paint) }
            else -> { line(5f,12f,19f,12f); path(13f,6f,19f,12f,13f,18f) }
        }
        canvas.restore()
    }
    override fun setAlpha(alpha: Int) { paint.alpha = alpha }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter }
    @Deprecated("Drawable opacity") override fun getOpacity() = PixelFormat.TRANSLUCENT
}
