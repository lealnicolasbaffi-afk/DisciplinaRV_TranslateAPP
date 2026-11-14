package br.edu.tradutoroffline.ui

import android.content.Context
import android.graphics.Canvas
import android.widget.Button

/** Draw the symbol at the actual view center, independent of TextView font metrics. */
// The app uses the platform Material theme and native Activities, without AppCompat.
@android.annotation.SuppressLint("AppCompatCustomView")
internal class IconButton(context: Context) : Button(context) {
    var actionIcon: ActionIcon? = null
        set(value) { field = value; invalidate() }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        actionIcon?.let { icon ->
            val size = AppUi.dp(this, 24)
            val left = (width - size) / 2
            val top = (height - size) / 2
            icon.setBounds(left, top, left + size, top + size)
            icon.alpha = if (isEnabled) 255 else 95
            icon.draw(canvas)
        }
    }
}
