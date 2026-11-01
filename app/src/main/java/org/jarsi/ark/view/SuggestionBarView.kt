package org.jarsi.ark.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.customview.widget.ExploreByTouchHelper
import org.jarsi.ark.R
import org.jarsi.ark.keyboard.KeyboardHeight
import org.jarsi.ark.keyboard.SuggestionItem
import org.jarsi.ark.theme.KeyboardTheme
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Vaakasuunnassa sormella vieritettävä ehdotusrivi. Piirretään Canvasille
 * ilman alinäkymiä kuten näppäimistökin. Pitkä painallus avaa sanan
 * poisto-/estovalikon.
 */
class SuggestionBarView(context: Context) : View(context) {

    interface MenuListener {
        fun isOwnWord(word: String): Boolean
        fun isPinned(word: String): Boolean
        fun onTogglePin(word: String)
        fun onDeleteLearned(word: String)
        fun onBlockWord(word: String)
    }

    var listener: ((SuggestionItem) -> Unit)? = null
    var menuListener: MenuListener? = null

    private var theme = KeyboardTheme.load(context)
    private var heightScale = 1f
    private var suggestions: List<SuggestionItem> = emptyList()
    private var slotWidths = FloatArray(0)
    private var contentWidth = 0f
    private var scrollOffset = 0f

    private var downX = 0f
    private var downOffset = 0f
    private var dragging = false
    private var pressedIndex = -1
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    // Pitkän painalluksen valikko
    private enum class MenuAction { PIN, DELETE, BLOCK }

    private var menuPopup: PopupWindow? = null
    private var menuActions: List<MenuAction> = emptyList()
    private var menuViews: List<TextView> = emptyList()
    private var menuSelected = -1
    private var menuLeft = 0f
    private var menuCellWidths = FloatArray(0)
    private var menuWord: String? = null
    // Valinta aktivoituu vasta kun sormi on liikahtanut selvästi avauskohdasta,
    // ettei irrotus heti avauksen jälkeen suorita mitään vahingossa.
    private var menuAnchorX = 0f
    private var menuArmed = false
    private val longPressRunnable = Runnable { openMenu() }

    private val density = resources.displayMetrics.density
    private fun dp(value: Float) = value * density

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }
    private val dividerPaint = Paint()
    private val pressedPaint = Paint()
    private val pinPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val clipIcon = context.getDrawable(R.drawable.ic_clipboard)?.mutate()

    // Tuoreen kopion kohdassa teksti siirtyy kuvakkeen verran oikealle.
    private fun clipIconSize() = height * 0.42f

    fun applySettings(newTheme: KeyboardTheme, scale: Float) {
        theme = newTheme
        if (heightScale != scale) {
            heightScale = scale
            requestLayout()
        }
        invalidate()
    }

    fun setSuggestions(items: List<SuggestionItem>) {
        if (items == suggestions) return
        closeMenu()
        suggestions = items
        scrollOffset = 0f
        pressedIndex = -1
        computeSlots()
        invalidate()
        accessHelper.invalidateRoot()
    }

    private val accessHelper = SuggestionAccessHelper()

    init {
        ViewCompat.setAccessibilityDelegate(this, accessHelper)
    }

    override fun dispatchHoverEvent(event: MotionEvent): Boolean =
        accessHelper.dispatchHoverEvent(event) || super.dispatchHoverEvent(event)

    private fun slotLeft(index: Int): Float {
        var left = -scrollOffset
        for (i in 0 until index) left += slotWidths[i]
        return left
    }

    private fun maxScroll() = max(0f, contentWidth - width)

    /**
     * Ehdotukset ruudunlukijalle: kiinnitys, poisto ja esto ovat sanan
     * toimintoja, ja rivi vieritetään ruudunlukijan vieritystoiminnolla.
     */
    private inner class SuggestionAccessHelper : ExploreByTouchHelper(this) {

        override fun getVirtualViewAt(x: Float, y: Float): Int {
            val index = indexAt(x)
            return if (index >= 0) index else INVALID_ID
        }

        override fun getVisibleVirtualViews(virtualViewIds: MutableList<Int>) {
            virtualViewIds.addAll(suggestions.indices)
        }

        override fun onPopulateNodeForVirtualView(
            virtualViewId: Int,
            node: AccessibilityNodeInfoCompat,
        ) {
            val item = suggestions.getOrNull(virtualViewId)
            if (item == null || virtualViewId >= slotWidths.size) {
                node.contentDescription = ""
                node.setBoundsInParent(Rect(0, 0, 1, 1))
                return
            }
            node.contentDescription = when (item) {
                is SuggestionItem.RecentClip ->
                    context.getString(R.string.cd_liita_kopio, item.text)
                else -> item.text
            }
            val left = slotLeft(virtualViewId)
            val right = (left + slotWidths[virtualViewId]).toInt()
            node.setBoundsInParent(Rect(left.toInt(), 0, right, height.coerceAtLeast(1)))
            node.isClickable = true
            node.addAction(AccessibilityNodeInfoCompat.ACTION_CLICK)
            val menu = menuListener
            if (item is SuggestionItem.Word && menu != null) {
                val pinLabel = if (menu.isPinned(item.text)) {
                    R.string.ehdotus_irrota
                } else {
                    R.string.ehdotus_kiinnita
                }
                node.addAction(action(R.id.toiminto_kiinnita, pinLabel))
                if (menu.isOwnWord(item.text)) {
                    node.addAction(action(R.id.toiminto_poista, R.string.ehdotus_poista))
                }
                node.addAction(action(R.id.toiminto_esta, R.string.ehdotus_esta))
            }
        }

        private fun action(id: Int, labelRes: Int) =
            AccessibilityNodeInfoCompat.AccessibilityActionCompat(id, context.getString(labelRes))

        override fun onPerformActionForVirtualView(
            virtualViewId: Int,
            action: Int,
            arguments: Bundle?,
        ): Boolean {
            val item = suggestions.getOrNull(virtualViewId) ?: return false
            val word = (item as? SuggestionItem.Word)?.text
            when {
                action == AccessibilityNodeInfoCompat.ACTION_CLICK -> listener?.invoke(item)
                word == null -> return false
                action == R.id.toiminto_kiinnita -> menuListener?.onTogglePin(word)
                action == R.id.toiminto_poista -> menuListener?.onDeleteLearned(word)
                action == R.id.toiminto_esta -> menuListener?.onBlockWord(word)
                else -> return false
            }
            return true
        }

        override fun onPopulateNodeForHost(node: AccessibilityNodeInfoCompat) {
            if (maxScroll() <= 0f) return
            node.isScrollable = true
            if (scrollOffset > 0f) {
                node.addAction(AccessibilityNodeInfoCompat.ACTION_SCROLL_BACKWARD)
            }
            if (scrollOffset < maxScroll()) {
                node.addAction(AccessibilityNodeInfoCompat.ACTION_SCROLL_FORWARD)
            }
        }

    }

    // Ruudunlukijan vieritys kohdistuu itse näkymään, ei yksittäiseen ehdotukseen.
    override fun performAccessibilityAction(action: Int, arguments: Bundle?): Boolean {
        val step = width * 0.8f
        scrollOffset = when (action) {
            AccessibilityNodeInfoCompat.ACTION_SCROLL_FORWARD -> scrollOffset + step
            AccessibilityNodeInfoCompat.ACTION_SCROLL_BACKWARD -> scrollOffset - step
            else -> return super.performAccessibilityAction(action, arguments)
        }.coerceIn(0f, maxScroll())
        invalidate()
        accessHelper.invalidateRoot()
        return true
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        setMeasuredDimension(width, (dp(KeyboardHeight.SUGGESTION_BAR_DP) * heightScale).roundToInt())
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        computeSlots()
    }

    private fun computeSlots() {
        textPaint.textSize = height * 0.38f
        slotWidths = FloatArray(suggestions.size)
        var total = 0f
        suggestions.forEachIndexed { i, item ->
            val iconWidth = if (item is SuggestionItem.RecentClip) clipIconSize() + dp(6f) else 0f
            slotWidths[i] = max(textPaint.measureText(item.text) + iconWidth + dp(28f), dp(60f))
            total += slotWidths[i]
        }
        // Jos ehdotukset eivät täytä riviä, ne levitetään koko leveydelle.
        if (total < width && suggestions.isNotEmpty()) {
            val extra = (width - total) / suggestions.size
            for (i in slotWidths.indices) slotWidths[i] += extra
            total = width.toFloat()
        }
        contentWidth = total
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(theme.background)
        if (suggestions.isEmpty()) return
        textPaint.textSize = height * 0.38f
        dividerPaint.color = theme.hint and 0x60FFFFFF.toInt()
        var x = -scrollOffset
        suggestions.forEachIndexed { i, item ->
            val slot = slotWidths[i]
            if (x + slot > 0 && x < width) {
                if (i == pressedIndex) {
                    pressedPaint.color = theme.keyPressed
                    canvas.drawRect(x, 0f, x + slot, height.toFloat(), pressedPaint)
                }
                textPaint.color = theme.text
                var textCenter = x + slot / 2f
                if (item is SuggestionItem.RecentClip) {
                    val size = clipIconSize()
                    val total = size + dp(6f) + textPaint.measureText(item.text)
                    val left = x + (slot - total) / 2f
                    clipIcon?.setTint(theme.text)
                    clipIcon?.setBounds(
                        left.roundToInt(),
                        ((height - size) / 2f).roundToInt(),
                        (left + size).roundToInt(),
                        ((height + size) / 2f).roundToInt(),
                    )
                    clipIcon?.let { it.draw(canvas) }
                    textCenter = left + size + dp(6f) + textPaint.measureText(item.text) / 2f
                }
                canvas.drawText(
                    item.text,
                    textCenter,
                    height / 2f + textPaint.textSize * 0.35f,
                    textPaint,
                )
                // Kiinnitetty sana merkitään pienellä korostuspisteellä.
                if (item is SuggestionItem.Word && menuListener?.isPinned(item.text) == true) {
                    pinPaint.color = theme.accent
                    canvas.drawCircle(x + slot - dp(9f), dp(9f), dp(3f), pinPaint)
                }
                if (i > 0) {
                    canvas.drawRect(x, height * 0.25f, x + dp(1f), height * 0.75f, dividerPaint)
                }
            }
            x += slot
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downOffset = scrollOffset
                dragging = false
                pressedIndex = indexAt(event.x)
                // Valikko koskee vain sanoja.
                if (suggestions.getOrNull(pressedIndex) is SuggestionItem.Word &&
                    menuListener != null
                ) {
                    postDelayed(
                        longPressRunnable,
                        ViewConfiguration.getLongPressTimeout().toLong(),
                    )
                }
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                if (menuPopup != null) {
                    updateMenuSelection(event.x)
                    return true
                }
                val dx = event.x - downX
                if (!dragging && abs(dx) > touchSlop) {
                    dragging = true
                    pressedIndex = -1
                    removeCallbacks(longPressRunnable)
                }
                if (dragging) {
                    val maxOffset = max(0f, contentWidth - width)
                    scrollOffset = (downOffset - dx).coerceIn(0f, maxOffset)
                    invalidate()
                }
            }
            MotionEvent.ACTION_UP -> {
                removeCallbacks(longPressRunnable)
                if (menuPopup != null) {
                    commitMenu()
                } else if (!dragging) {
                    val index = indexAt(event.x)
                    suggestions.getOrNull(index)?.let { listener?.invoke(it) }
                }
                pressedIndex = -1
                invalidate()
                performClick()
            }
            MotionEvent.ACTION_CANCEL -> {
                removeCallbacks(longPressRunnable)
                closeMenu()
                pressedIndex = -1
                invalidate()
            }
        }
        return true
    }

    private fun openMenu() {
        val menu = menuListener ?: return
        val word = (suggestions.getOrNull(pressedIndex) as? SuggestionItem.Word)?.text ?: return
        menuWord = word
        menuActions = buildList {
            add(MenuAction.PIN)
            if (menu.isOwnWord(word)) add(MenuAction.DELETE)
            add(MenuAction.BLOCK)
        }
        menuSelected = -1
        menuAnchorX = downX
        menuArmed = false
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)

        val cellHeight = dp(48f)
        val labelPadding = dp(10f)
        textPaint.textSize = dp(13f)
        val labels = menuActions.map { action ->
            when (action) {
                MenuAction.PIN -> context.getString(
                    if (menu.isPinned(word)) R.string.ehdotus_irrota else R.string.ehdotus_kiinnita
                )
                MenuAction.DELETE -> context.getString(R.string.ehdotus_poista)
                MenuAction.BLOCK -> context.getString(R.string.ehdotus_esta)
            }
        }
        menuCellWidths = FloatArray(labels.size)
        labels.forEachIndexed { i, label ->
            menuCellWidths[i] = textPaint.measureText(label) + labelPadding * 2
        }
        // Valikko ei saa ylittää näkymän leveyttä millään näytöllä:
        // ylileveä valikko kutistetaan suhteessa ja teksti pienenee hieman.
        var menuTextSize = 13f
        val available = width - dp(8f)
        val naturalWidth = menuCellWidths.sum()
        if (naturalWidth > available) {
            val factor = available / naturalWidth
            for (i in menuCellWidths.indices) menuCellWidths[i] *= factor
            menuTextSize = 12f
        }

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            background = GradientDrawable().apply {
                cornerRadius = dp(8f)
                setColor(theme.specialKey)
            }
        }
        menuViews = labels.mapIndexed { i, label ->
            TextView(context).apply {
                text = label
                gravity = Gravity.CENTER
                textSize = menuTextSize
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                setTextColor(theme.text)
                layoutParams = LinearLayout.LayoutParams(
                    menuCellWidths[i].roundToInt(),
                    cellHeight.roundToInt(),
                )
                container.addView(this)
            }
        }

        // Valikko avautuu painetun ehdotuksen ylle; pysyy näytön reunojen sisällä.
        var slotLeft = -scrollOffset
        for (i in 0 until pressedIndex) slotLeft += slotWidths[i]
        val slotCenter = slotLeft + slotWidths[pressedIndex] / 2f
        val totalWidth = menuCellWidths.sum()
        menuLeft = (slotCenter - totalWidth / 2f)
            .coerceIn(dp(4f), (width - totalWidth - dp(4f)).coerceAtLeast(dp(4f)))
        val location = IntArray(2)
        getLocationInWindow(location)
        menuPopup = PopupWindow(container, totalWidth.roundToInt(), cellHeight.roundToInt()).apply {
            isTouchable = false
            isClippingEnabled = false
            showAtLocation(
                this@SuggestionBarView,
                Gravity.NO_GRAVITY,
                location[0] + menuLeft.roundToInt(),
                location[1] - cellHeight.roundToInt() - dp(6f).roundToInt(),
            )
        }
    }

    private fun updateMenuSelection(x: Float) {
        if (menuActions.isEmpty()) return
        if (!menuArmed) {
            if (abs(x - menuAnchorX) < dp(8f)) return
            menuArmed = true
        }
        var index = -1
        var pos = menuLeft
        menuCellWidths.forEachIndexed { i, cellWidth ->
            if (x >= pos && x < pos + cellWidth) index = i
            pos += cellWidth
        }
        if (index != menuSelected) {
            menuSelected = index
            menuViews.forEachIndexed { i, view ->
                if (i == menuSelected) {
                    view.setBackgroundColor(theme.accent)
                    view.setTextColor(theme.accentText)
                } else {
                    view.setBackgroundColor(0)
                    view.setTextColor(theme.text)
                }
            }
            if (index >= 0) {
                performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            }
        }
    }

    private fun commitMenu() {
        val action = menuActions.getOrNull(menuSelected)
        val word = menuWord
        closeMenu()
        if (action != null && word != null) {
            when (action) {
                MenuAction.PIN -> menuListener?.onTogglePin(word)
                MenuAction.DELETE -> menuListener?.onDeleteLearned(word)
                MenuAction.BLOCK -> menuListener?.onBlockWord(word)
            }
        }
    }

    private fun closeMenu() {
        menuPopup?.dismiss()
        menuPopup = null
        menuViews = emptyList()
        menuActions = emptyList()
        menuSelected = -1
        menuWord = null
        menuArmed = false
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(longPressRunnable)
        closeMenu()
        super.onDetachedFromWindow()
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun indexAt(x: Float): Int {
        var pos = -scrollOffset
        slotWidths.forEachIndexed { i, slot ->
            if (x >= pos && x < pos + slot) return i
            pos += slot
        }
        return -1
    }
}
