package br.edu.tradutoroffline.ui

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.Button
import android.widget.TextView

/** Shared native visual language; no extra UI dependency. */
internal object AppUi {
    val background = Color.rgb(243, 246, 245)
    val ink = Color.rgb(22, 43, 42)
    val muted = Color.rgb(80, 103, 99)
    val accent = Color.rgb(15, 106, 91)
    val pale = Color.rgb(225, 240, 234)
    val night = Color.rgb(16, 29, 29)
    fun dp(view: View, value: Int) = (value * view.resources.displayMetrics.density).toInt()
    fun shape(view: View, color: Int, radius: Int = 20): GradientDrawable = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(view, radius).toFloat()
    }
    fun surface(view: View, color: Int = Color.WHITE) {
        view.background = shape(view, color)
        view.setPadding(dp(view, 16), dp(view, 14), dp(view, 16), dp(view, 14))
    }
    fun button(view: Button, primary: Boolean = false, dark: Boolean = false) {
        val fill = if (primary) accent else if (dark) Color.rgb(39, 58, 57) else pale
        val foreground = if (primary || dark) Color.WHITE else accent
        view.background = android.graphics.drawable.RippleDrawable(
            ColorStateList.valueOf(Color.argb(35, 120, 170, 150)), shape(view, fill, 16), null)
        view.setTextColor(ColorStateList(arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf()),
            intArrayOf(if (dark) Color.rgb(137, 153, 149) else Color.rgb(117, 136, 130), foreground)))
        view.elevation = 0f; view.stateListAnimator = null
        view.isAllCaps = false; view.textSize = 15f
        view.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        view.minHeight = dp(view, 52); view.minimumHeight = dp(view, 52)
        view.setPadding(dp(view, 16), dp(view, 8), dp(view, 16), dp(view, 8))
    }
    fun icon(view: Button, kind: String, label: String, primary: Boolean = false, dark: Boolean = false) {
        button(view, primary, dark)
        view.text = ""; view.contentDescription = label; view.tooltipText = label
        view.setCompoundDrawables(null, null, null, null)
        (view as IconButton).actionIcon = ActionIcon(kind, if (primary || dark) Color.WHITE else accent)
        view.background = android.graphics.drawable.RippleDrawable(
            ColorStateList.valueOf(Color.argb(45, 140, 190, 170)),
            shape(view, if (primary) accent else if (dark) Color.rgb(39, 58, 57) else pale, if (primary) 32 else 16), null)
        view.gravity = android.view.Gravity.CENTER
        view.minWidth = 0; view.minimumWidth = 0
        view.minHeight = dp(view, 48); view.minimumHeight = dp(view, 48)
        view.setPadding(dp(view, 12), dp(view, 12), dp(view, 12), dp(view, 12))
        view.elevation = 0f; view.stateListAnimator = null
    }
    fun title(view: TextView) {
        view.setTextColor(ink); view.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
}
