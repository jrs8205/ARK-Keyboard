package org.jarsi.ark.view

import android.content.Context
import android.os.Build
import android.view.Gravity
import android.view.WindowInsets
import android.widget.ImageView
import android.widget.LinearLayout
import org.jarsi.ark.R
import org.jarsi.ark.theme.KeyboardTheme
import kotlin.math.roundToInt

/** Yhden käden tilan sivupalsta: näppäimistö toiselle puolelle tai takaisin täyteen leveyteen. */
class OneHandSideView(context: Context) : LinearLayout(context) {

    interface Listener {
        fun onSwapSide()
        fun onExitOneHand()
    }

    var listener: Listener? = null

    private val swapButton = button(R.drawable.ic_swap, R.string.yksi_kasi_vaihda_puoli) {
        listener?.onSwapSide()
    }

    private val exitButton = button(R.drawable.ic_fullscreen, R.string.yksi_kasi_sulje) {
        listener?.onExitOneHand()
    }

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER
        val size = (BUTTON_DP * resources.displayMetrics.density).roundToInt()
        addView(swapButton, LayoutParams(size, size))
        addView(exitButton, LayoutParams(size, size))
    }

    private fun button(iconRes: Int, descriptionRes: Int, action: () -> Unit) =
        ImageView(context).apply {
            setImageResource(iconRes)
            scaleType = ImageView.ScaleType.CENTER
            contentDescription = context.getString(descriptionRes)
            setOnClickListener { action() }
        }

    fun applySettings(theme: KeyboardTheme) {
        setBackgroundColor(theme.background)
        swapButton.setColorFilter(theme.text)
        exitButton.setColorFilter(theme.text)
    }

    // Navigointipalkin varaus pitää napit näppäinrivien kohdalla.
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        requestApplyInsets()
    }

    override fun onApplyWindowInsets(insets: WindowInsets): WindowInsets {
        val bottom = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            insets.getInsets(WindowInsets.Type.navigationBars()).bottom
        } else {
            @Suppress("DEPRECATION")
            insets.systemWindowInsetBottom
        }
        if (bottom != paddingBottom) setPadding(0, 0, 0, bottom)
        return super.onApplyWindowInsets(insets)
    }

    private companion object {
        const val BUTTON_DP = 48
    }
}
