package com.zirakusa.stone

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ClipDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ProgressBar
import android.widget.TextView
import kotlin.math.roundToInt

object StoneVolumeOverlay {
  private const val ACCENT = "#F5C518"
  private var popup: PopupWindow? = null
  private var valueText: TextView? = null
  private var modeText: TextView? = null
  private var progressBar: ProgressBar? = null
  private var dismiss: Runnable? = null
  private val handler = android.os.Handler(android.os.Looper.getMainLooper())

  fun show(activity: Activity?, percent: Int) {
    activity ?: return
    activity.runOnUiThread {
      if (popup?.isShowing != true) create(activity)
      update(percent.coerceIn(0, 300))
      dismiss?.let(handler::removeCallbacks)
      dismiss = Runnable { popup?.dismiss() }.also { handler.postDelayed(it, 1400) }
    }
  }

  private fun create(activity: Activity) {
    val width = activity.resources.displayMetrics.widthPixels - dp(activity, 24)
    val content = LinearLayout(activity).apply {
      orientation = LinearLayout.VERTICAL
      setPadding(dp(activity, 20), dp(activity, 14), dp(activity, 20), dp(activity, 16))
      background = rounded("#17151C", activity, 22, "#3A343F")
    }
    content.addView(createHeader(activity))
    content.addView(createTrack(activity, width - dp(activity, 40)))
    popup = PopupWindow(content, width, WindowManager.LayoutParams.WRAP_CONTENT, false).apply {
      setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
      isOutsideTouchable = false
      isFocusable = false
      elevation = dp(activity, 18).toFloat()
      showAtLocation(activity.window.decorView, Gravity.TOP or Gravity.CENTER_HORIZONTAL, 0, dp(activity, 18))
    }
  }

  private fun createHeader(activity: Activity) = LinearLayout(activity).apply {
    gravity = Gravity.CENTER_VERTICAL
    addView(ImageView(activity).apply {
      setImageResource(android.R.drawable.ic_lock_silent_mode_off)
      setColorFilter(Color.parseColor(ACCENT))
    }, LinearLayout.LayoutParams(dp(activity, 28), dp(activity, 28)))
    addView(label(activity, "Volume", 20f, Color.WHITE).apply {
      setTypeface(Typeface.DEFAULT, Typeface.BOLD)
    }, LinearLayout.LayoutParams(0, dp(activity, 36), 1f).apply { marginStart = dp(activity, 10) })
    valueText = label(activity, "0%", 20f, Color.parseColor(ACCENT)).apply {
      setTypeface(Typeface.DEFAULT, Typeface.BOLD)
    }.also { addView(it) }
    modeText = label(activity, "NATIVO", 10f, Color.parseColor("#B8B5C0")).apply {
      gravity = Gravity.CENTER
      setPadding(dp(activity, 8), dp(activity, 4), dp(activity, 8), dp(activity, 4))
      background = rounded("#2A2730", activity, 10)
    }.also { addView(it, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(activity, 10) }) }
  }

  private fun createTrack(activity: Activity, width: Int) = FrameLayout(activity).apply {
    layoutParams = LinearLayout.LayoutParams(-1, dp(activity, 16)).apply { topMargin = dp(activity, 10) }
    progressBar = ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal).apply {
      max = 300
      progressDrawable = LayerDrawable(arrayOf(
        rounded("#302C36", activity, 8),
        ClipDrawable(rounded(ACCENT, activity, 8), Gravity.START, ClipDrawable.HORIZONTAL)
      )).apply { setId(0, android.R.id.background); setId(1, android.R.id.progress) }
    }.also { addView(it, FrameLayout.LayoutParams(-1, dp(activity, 12), Gravity.CENTER_VERTICAL)) }
    addView(View(activity).apply { setBackgroundColor(Color.WHITE); alpha = 0.85f },
      FrameLayout.LayoutParams(dp(activity, 2), dp(activity, 16)).apply { leftMargin = width / 3 })
  }

  private fun update(percent: Int) {
    valueText?.text = "$percent%"
    modeText?.text = if (percent > 100) "BOOST" else "NATIVO"
    modeText?.setTextColor(Color.parseColor(if (percent > 100) ACCENT else "#B8B5C0"))
    progressBar?.progress = percent
  }

  private fun label(activity: Activity, text: String, size: Float, color: Int) =
    TextView(activity).apply { this.text = text; textSize = size; setTextColor(color); gravity = Gravity.CENTER_VERTICAL }

  private fun rounded(color: String, activity: Activity, radius: Int, stroke: String? = null) =
    GradientDrawable().apply {
      setColor(Color.parseColor(color)); cornerRadius = dp(activity, radius).toFloat()
      stroke?.let { setStroke(dp(activity, 1), Color.parseColor(it)) }
    }

  private fun dp(activity: Activity, value: Int) =
    (value * activity.resources.displayMetrics.density).roundToInt()
}
