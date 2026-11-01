package org.jarsi.ark.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.accessibility.AccessibilityManager
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.customview.widget.ExploreByTouchHelper
import org.jarsi.ark.R
import org.jarsi.ark.keyboard.KeyboardHeight
import org.jarsi.ark.keyboard.ToolbarOrder
import org.jarsi.ark.keyboard.ToolbarTool
import org.jarsi.ark.theme.KeyboardTheme
import kotlin.math.roundToInt

/** Työkalun nimi asetuksissa ja ruudunlukijalle. */
fun ToolbarTool.labelRes(): Int = when (this) {
    ToolbarTool.ARROWS -> R.string.tyokalu_nuolet
    ToolbarTool.WEB -> R.string.tyokalu_www
    ToolbarTool.MIC -> R.string.tyokalu_mikrofoni
    ToolbarTool.EMOJI -> R.string.tyokalu_emojit
    ToolbarTool.CLIPBOARD -> R.string.tyokalu_leikepoyta
    ToolbarTool.TRANSLATE -> R.string.tyokalu_kaannos
    ToolbarTool.UNDO -> R.string.tyokalu_peruutus
    ToolbarTool.ONE_HAND -> R.string.tyokalu_yksi_kasi
    ToolbarTool.SETTINGS -> R.string.tyokalu_asetukset
}

fun ToolbarTool.iconRes(): Int = when (this) {
    ToolbarTool.ARROWS -> R.drawable.ic_cursor_move
    ToolbarTool.WEB -> R.drawable.ic_link
    ToolbarTool.MIC -> R.drawable.ic_mic
    ToolbarTool.EMOJI -> R.drawable.ic_emoji
    ToolbarTool.CLIPBOARD -> R.drawable.ic_clipboard
    ToolbarTool.TRANSLATE -> R.drawable.ic_translate
    ToolbarTool.UNDO -> R.drawable.ic_undo
    ToolbarTool.ONE_HAND -> R.drawable.ic_dock_right
    ToolbarTool.SETTINGS -> R.drawable.ic_settings
}

/** Työkalurivi ehdotusrivin yläpuolella: kursorinsiirtotila, verkko-osoitteet ja asetukset. */
class ToolbarView(context: Context) : View(context) {

    interface Listener {
        fun onToggleArrows()
        fun onToggleWeb()
        fun onToggleDictation()
        fun onToggleEmoji()
        fun onToggleClipboard()
        fun onToggleTranslation()
        fun onUndo()
        fun onToggleOneHand()
        fun onOpenSettings()
    }

    var listener: Listener? = null

    /** Korostaa kursorinapin, kun nuolitila on päällä. */
    var arrowsActive = false
        set(value) {
            field = value
            invalidate()
        }

    /** Korostaa verkkonapin, kun osoitesivu on auki. */
    var webActive = false
        set(value) {
            field = value
            invalidate()
        }

    /** Korostaa mikrofoninapin sanelun ajaksi. */
    var micActive = false
        set(value) {
            field = value
            if (value) {
                // Aloitussykäys kertoo, että nyt voi alkaa puhua.
                micLevel = 1f
            } else {
                micShownLevel = 0f
            }
            invalidate()
        }

    /** Puheen voimakkuus 0–1; mikrofonin kehä sykkii sen mukana. */
    var micLevel = 0f
        set(value) {
            field = value.coerceIn(0f, 1f)
            if (micActive) invalidate()
        }

    private var micShownLevel = 0f
    private val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    /** Korostaa leikepöytänapin, kun näkymä on auki. */
    var clipboardActive = false
        set(value) {
            field = value
            invalidate()
        }

    /** Korostaa emojinapin, kun emojipaneeli on auki. */
    var emojiActive = false
        set(value) {
            field = value
            invalidate()
        }

    /** Korostaa käännösnapin, kun käännösnäkymä on auki. */
    var translationActive = false
        set(value) {
            field = value
            invalidate()
        }

    /** Korostaa yhden käden napin, kun tila on päällä. */
    var oneHandActive = false
        set(value) {
            field = value
            invalidate()
        }

    private var theme = KeyboardTheme.load(context)
    private var pressedIndex = -1

    private val density = resources.displayMetrics.density
    private fun dp(value: Float) = value * density

    private val buttonPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    /** Näkyvät napit järjestyksessä; asetuksista muokattavissa. */
    var tools: List<ToolbarTool> = ToolbarOrder.default
        set(value) {
            field = value
            invalidate()
            accessHelper.invalidateRoot()
        }

    private val icons = ToolbarTool.entries.associateWith {
        context.getDrawable(it.iconRes())?.mutate()
    }

    private val accessHelper = ToolAccessHelper()
    private val accessibility = context.getSystemService(AccessibilityManager::class.java)

    init {
        ViewCompat.setAccessibilityDelegate(this, accessHelper)
    }

    override fun dispatchHoverEvent(event: MotionEvent): Boolean =
        accessHelper.dispatchHoverEvent(event) || super.dispatchHoverEvent(event)

    private fun isActive(tool: ToolbarTool): Boolean = when (tool) {
        ToolbarTool.ARROWS -> arrowsActive
        ToolbarTool.WEB -> webActive
        ToolbarTool.MIC -> micActive
        ToolbarTool.EMOJI -> emojiActive
        ToolbarTool.CLIPBOARD -> clipboardActive
        ToolbarTool.TRANSLATE -> translationActive
        ToolbarTool.ONE_HAND -> oneHandActive
        else -> false
    }

    private fun perform(tool: ToolbarTool) {
        when (tool) {
            ToolbarTool.ARROWS -> listener?.onToggleArrows()
            ToolbarTool.WEB -> listener?.onToggleWeb()
            ToolbarTool.MIC -> listener?.onToggleDictation()
            ToolbarTool.EMOJI -> listener?.onToggleEmoji()
            ToolbarTool.CLIPBOARD -> listener?.onToggleClipboard()
            ToolbarTool.TRANSLATE -> listener?.onToggleTranslation()
            ToolbarTool.UNDO -> listener?.onUndo()
            ToolbarTool.ONE_HAND -> listener?.onToggleOneHand()
            ToolbarTool.SETTINGS -> listener?.onOpenSettings()
        }
    }

    /** Napit ruudunlukijalle; rivi piirretään yhdeksi näkymäksi. */
    private inner class ToolAccessHelper : ExploreByTouchHelper(this) {
        override fun getVirtualViewAt(x: Float, y: Float): Int {
            val index = indexAt(x, y)
            return if (index >= 0) index else INVALID_ID
        }

        override fun getVisibleVirtualViews(virtualViewIds: MutableList<Int>) {
            virtualViewIds.addAll(tools.indices)
        }

        override fun onPopulateNodeForVirtualView(
            virtualViewId: Int,
            node: AccessibilityNodeInfoCompat,
        ) {
            val tool = tools.getOrNull(virtualViewId)
            if (tool == null) {
                node.contentDescription = ""
                node.setBoundsInParent(Rect(0, 0, 1, 1))
                return
            }
            node.contentDescription = context.getString(tool.labelRes())
            val rect = buttonRect(virtualViewId)
            node.setBoundsInParent(
                Rect(rect.left.toInt(), 0, rect.right.toInt(), height.coerceAtLeast(1))
            )
            node.className = "android.widget.Button"
            node.isClickable = true
            node.addAction(AccessibilityNodeInfoCompat.ACTION_CLICK)
            node.isCheckable = tool != ToolbarTool.UNDO && tool != ToolbarTool.SETTINGS
            node.isChecked = isActive(tool)
        }

        override fun onPerformActionForVirtualView(
            virtualViewId: Int,
            action: Int,
            arguments: Bundle?,
        ): Boolean {
            if (action != AccessibilityNodeInfoCompat.ACTION_CLICK) return false
            val tool = tools.getOrNull(virtualViewId) ?: return false
            perform(tool)
            sendEventForVirtualView(
                virtualViewId,
                android.view.accessibility.AccessibilityEvent.TYPE_VIEW_CLICKED,
            )
            invalidateRoot()
            return true
        }
    }

    fun applySettings(newTheme: KeyboardTheme) {
        theme = newTheme
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // Ruudunlukijan kanssa rivi on Androidin suosittamaa 48 dp:n korkeutta.
        val rowDp = if (accessibility?.isTouchExplorationEnabled == true) {
            ACCESSIBLE_ROW_DP
        } else {
            KeyboardHeight.TOOLBAR_DP
        }
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), dp(rowDp).roundToInt())
    }

    // Kapealla näytöllä nappien leveydet kutistuvat suhteessa, jotta koko
    // rivi mahtuu aina näkyviin.
    private fun widthScale(): Float {
        val size = height - dp(8f)
        var natural = dp(6f)
        repeat(tools.size) { natural += size + dp(6f) }
        return if (natural > width && natural > 0f) width / natural else 1f
    }

    private fun buttonRect(index: Int): RectF {
        val size = height - dp(8f)
        val scale = widthScale()
        var left = dp(6f) * scale
        left += index * (size + dp(6f)) * scale
        return RectF(left, dp(4f), left + size * scale, dp(4f) + size)
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(theme.background)
        tools.forEachIndexed { i, tool ->
            val rect = buttonRect(i)
            val active = isActive(tool)
            if (tool == ToolbarTool.MIC && micActive) {
                // Kehä sykkii puheen tahdissa ja laskee itsestään hiljaisuudessa.
                micShownLevel += (micLevel - micShownLevel) * 0.3f
                micLevel *= 0.94f
                val alpha = (60 + 140 * micShownLevel).toInt().coerceIn(0, 255)
                haloPaint.color = (theme.accent and 0x00FFFFFF) or (alpha shl 24)
                val radius = rect.height() / 2f + dp(2f) + dp(6f) * micShownLevel
                canvas.drawCircle(rect.centerX(), rect.centerY(), radius, haloPaint)
                postInvalidateOnAnimation()
            }
            buttonPaint.color = when {
                i == pressedIndex -> theme.keyPressed
                active -> theme.accent
                else -> theme.specialKey
            }
            canvas.drawRoundRect(rect, dp(8f), dp(8f), buttonPaint)
            val contentColor = if (active) theme.accentText else theme.text
            val icon = icons[tool] ?: return@forEachIndexed
            icon.setTint(contentColor)
            val iconSize = (minOf(rect.height(), rect.width()) * 0.62f).roundToInt()
            val cx = rect.centerX().roundToInt()
            val cy = rect.centerY().roundToInt()
            icon.setBounds(cx - iconSize / 2, cy - iconSize / 2, cx + iconSize / 2, cy + iconSize / 2)
            icon.draw(canvas)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pressedIndex = indexAt(event.x, event.y)
                invalidate()
            }
            MotionEvent.ACTION_UP -> {
                val index = indexAt(event.x, event.y)
                if (index >= 0 && index == pressedIndex) perform(tools[index])
                pressedIndex = -1
                invalidate()
                performClick()
            }
            MotionEvent.ACTION_CANCEL -> {
                pressedIndex = -1
                invalidate()
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    // Napit piirretään 32 dp:n kokoisina, mutta osuma-alue kattaa rivin
    // koko korkeuden ja puolet nappien välistä, jotta sormi osuu varmasti.
    private fun indexAt(x: Float, y: Float): Int {
        if (y < 0f || y >= height) return -1
        val halfGap = dp(3f) * widthScale()
        return tools.indices.firstOrNull {
            val rect = buttonRect(it)
            x >= rect.left - halfGap && x < rect.right + halfGap
        } ?: -1
    }

    private companion object {
        const val ACCESSIBLE_ROW_DP = 48f
    }
}
