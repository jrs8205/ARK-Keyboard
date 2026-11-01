package org.jarsi.ark.view

import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.WindowInsets
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.customview.widget.ExploreByTouchHelper
import org.jarsi.ark.R
import org.jarsi.ark.keyboard.EditCommand
import org.jarsi.ark.keyboard.Key
import org.jarsi.ark.keyboard.KeyAction
import org.jarsi.ark.keyboard.KeySpeech
import org.jarsi.ark.keyboard.KeyboardHeight
import org.jarsi.ark.keyboard.KeyboardLayout
import org.jarsi.ark.keyboard.Layouts
import org.jarsi.ark.keyboard.ShiftState
import org.jarsi.ark.theme.KeyboardTheme
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Itse piirretty näppäimistönäkymä. Yksi View piirtää kaikki näppäimet Canvasille
 * ilman alinäkymiä, jotta kosketusviive ja muistinkulutus pysyvät pieninä.
 */
class KeyboardView(context: Context) : View(context) {

    interface Listener {
        fun onText(text: String)
        fun onKey(action: KeyAction)
        fun onSpaceSwipe(steps: Int)

        /** Pohjassa pidetyn näppäimen toistokerta; oletuksena kuin napautus. */
        fun onKeyRepeat(action: KeyAction) = onKey(action)

        /** Välilyönnin pitkä painallus: näppäimistön vaihto. */
        fun onSpaceLongPress()

        /** Liu'utus askelpalauttimesta vasemmalle: [words] sanaa valittuna poistoa varten. */
        fun onBackspaceSwipe(words: Int)

        /** Sormi nousi liu'utuksen lopuksi: valittu poistetaan. */
        fun onBackspaceSwipeEnd()

        /** Liu'utus keskeytyi: valinta perutaan eikä mitään poisteta. */
        fun onBackspaceSwipeCancel()
    }

    var listener: Listener? = null

    var shiftState: ShiftState = ShiftState.OFF
        set(value) {
            field = value
            invalidate()
            accessHelper.invalidateRoot()
        }

    /** Salasanakentässä ruudunlukija ei sano merkkejä ääneen. */
    var obscureKeyLabels = false
        set(value) {
            field = value
            accessHelper.invalidateRoot()
        }

    /** Korostaa Valitse-näppäimen, kun nuolet laajentavat valintaa. */
    var selectionActive = false
        set(value) {
            if (field == value) return
            field = value
            invalidate()
            accessHelper.invalidateRoot()
        }

    var enterText: String = "⏎"
        set(value) {
            field = value
            invalidate()
            accessHelper.invalidateRoot()
        }

    private var layout: KeyboardLayout = Layouts.letters()
    private var theme: KeyboardTheme = KeyboardTheme.load(context)
    private var heightScale = 1f
    private var previewEnabled = true
    private var hapticsEnabled = true

    private val fiLocale = Locale.forLanguageTag("fi")
    private val spaceLabel = context.getString(R.string.app_name)
    private val density = resources.displayMetrics.density
    private fun dp(value: Float) = value * density

    private val keyGap = dp(3f)
    private val keyCornerRadius = dp(8f)

    private val keyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT
    }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.RIGHT
    }

    // Erikoisnäppäinten vektorikuvakkeet; mutate erottaa sävytykset toisistaan.
    private val enterIcon = context.getDrawable(R.drawable.ic_enter)?.mutate()
    private val shiftIcon = context.getDrawable(R.drawable.ic_shift)?.mutate()
    private val shiftOnIcon = context.getDrawable(R.drawable.ic_shift_on)?.mutate()
    private val shiftCapsIcon = context.getDrawable(R.drawable.ic_shift_caps)?.mutate()
    private val arrowIcons = mapOf(
        KeyEvent.KEYCODE_DPAD_UP to R.drawable.ic_arrow_up,
        KeyEvent.KEYCODE_DPAD_DOWN to R.drawable.ic_arrow_down,
        KeyEvent.KEYCODE_DPAD_LEFT to R.drawable.ic_arrow_left,
        KeyEvent.KEYCODE_DPAD_RIGHT to R.drawable.ic_arrow_right,
    ).mapValues { context.getDrawable(it.value)?.mutate() }
    private val backspaceIcon = context.getDrawable(R.drawable.ic_backspace)?.mutate()

    private class BoundedKey(val key: Key, val rect: RectF, val row: Int)

    private var boundedKeys: List<BoundedKey> = emptyList()
    private var rowHeight = 0f

    private val accessHelper = KeyAccessHelper()

    init {
        ViewCompat.setAccessibilityDelegate(this, accessHelper)
    }

    override fun dispatchHoverEvent(event: MotionEvent): Boolean =
        accessHelper.dispatchHoverEvent(event) || super.dispatchHoverEvent(event)

    /**
     * Näppäimet ruudunlukijalle: yksi piirretty näkymä esitetään yksittäisinä
     * näppäiminä. Tekstinsyöttönäppäimen merkintä antaa ruudunlukijan toimia
     * käyttäjän asetuksen mukaan (nosto kirjoittaa tai kaksoisnapautus).
     */
    private inner class KeyAccessHelper : ExploreByTouchHelper(this) {

        override fun getVirtualViewAt(x: Float, y: Float): Int {
            val key = keyAt(x, y) ?: return INVALID_ID
            return boundedKeys.indexOf(key)
        }

        override fun getVisibleVirtualViews(virtualViewIds: MutableList<Int>) {
            boundedKeys.forEachIndexed { index, bounded ->
                if (bounded.key.action != KeyAction.None) virtualViewIds.add(index)
            }
        }

        override fun onPopulateNodeForVirtualView(
            virtualViewId: Int,
            node: AccessibilityNodeInfoCompat,
        ) {
            val bounded = boundedKeys.getOrNull(virtualViewId)
            if (bounded == null) {
                node.contentDescription = ""
                node.setBoundsInParent(Rect(0, 0, 1, 1))
                return
            }
            val key = bounded.key
            node.contentDescription = KeySpeech.describe(
                key, shiftState, enterText, selectionActive, obscureKeyLabels,
            )
            val rect = Rect()
            bounded.rect.roundOut(rect)
            node.setBoundsInParent(rect)
            node.isTextEntryKey = true
            node.isClickable = true
            node.addAction(AccessibilityNodeInfoCompat.ACTION_CLICK)
            if (key.longPress.isNotEmpty() || key.action == KeyAction.Space) {
                node.addAction(AccessibilityNodeInfoCompat.ACTION_LONG_CLICK)
            }
        }

        override fun onPerformActionForVirtualView(
            virtualViewId: Int,
            action: Int,
            arguments: Bundle?,
        ): Boolean {
            val bounded = boundedKeys.getOrNull(virtualViewId) ?: return false
            when (action) {
                AccessibilityNodeInfoCompat.ACTION_CLICK -> commit(bounded.key)
                AccessibilityNodeInfoCompat.ACTION_LONG_CLICK -> when {
                    bounded.key.action == KeyAction.Space -> listener?.onSpaceLongPress()
                    bounded.key.longPress.isNotEmpty() && alternatesPopup == null ->
                        showAlternates(bounded)
                    else -> return false
                }
                else -> return false
            }
            sendEventForVirtualView(
                virtualViewId,
                android.view.accessibility.AccessibilityEvent.TYPE_VIEW_CLICKED,
            )
            return true
        }
    }

    /** Navigointipalkin varaus näkymän alareunassa pikseleinä. */
    var bottomInset = 0
        private set

    /** Kutsutaan, kun navigointipalkin varaus muuttuu (korkeuskaton laskentaan). */
    var onBottomInsetChanged: (() -> Unit)? = null

    private inner class PressInfo(val bounded: BoundedKey) {
        var spaceAnchorX = 0f
        var spaceSwiping = false
        var backspaceAnchorX = 0f
        var backspaceSwiping = false
        var backspaceWords = 0
        var committed = false
        var alternatesOpen = false
        val longPressRunnable = Runnable { handleLongPress(this) }
        val repeatRunnable: Runnable = object : Runnable {
            override fun run() {
                val action = bounded.key.action
                if (action == KeyAction.Backspace) {
                    // Pohjassa pidetty askelpalautin siirtyy sanapoistoon.
                    committed = true
                    listener?.onKeyRepeat(action)
                    postDelayed(this, REPEAT_WORD_INTERVAL_MS)
                } else {
                    commit(bounded.key)
                    postDelayed(this, REPEAT_INTERVAL_MS)
                }
            }
        }
    }

    private val pressed = mutableMapOf<Int, PressInfo>()

    // Esikatselukupla
    private var previewPopup: PopupWindow? = null
    private var previewView: TextView? = null

    // Pitkän painalluksen vaihtoehtomerkit: valikko jää auki sormen
    // noustessa ja merkki valitaan napauttamalla. Liu'utusvalinta osui
    // helposti viereiseen merkkiin.
    private var alternatesPopup: PopupWindow? = null

    fun setKeyboardLayout(newLayout: KeyboardLayout) {
        if (newLayout == layout) return
        cancelAll()
        layout = newLayout
        requestLayout()
        computeBounds()
        invalidate()
        accessHelper.invalidateRoot()
    }

    fun applySettings(newTheme: KeyboardTheme, scale: Float, preview: Boolean, haptics: Boolean) {
        theme = newTheme
        previewEnabled = preview
        hapticsEnabled = haptics
        if (heightScale != scale) {
            heightScale = scale
            requestLayout()
        }
        invalidate()
    }

    // Näkymä voidaan rakentaa uudelleen esim. teeman vaihtuessa; insetit on
    // pyydettävä joka kiinnityksellä, ettei navigointipalkin varaus katoa.
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        requestApplyInsets()
    }

    // Uudemmissa Android-versioissa IME-ikkuna ulottuu navigointipalkin alle,
    // joten palkin korkeus varataan näkymän alareunasta tyhjänä tilana.
    override fun onApplyWindowInsets(insets: WindowInsets): WindowInsets {
        val bottom = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            insets.getInsets(WindowInsets.Type.navigationBars()).bottom
        } else {
            @Suppress("DEPRECATION")
            insets.systemWindowInsetBottom
        }
        if (bottom != bottomInset) {
            bottomInset = bottom
            requestLayout()
            onBottomInsetChanged?.invoke()
        }
        return super.onApplyWindowInsets(insets)
    }

    /**
     * Näppäimistön korkeus nykyisellä asettelulla. Rivimäärä vaihtelee
     * sivuittain (?123-sivulla on numerorivi), joten käännösnäkymän
     * mitoitus tarvitsee tämän jo ennen mittausta.
     */
    fun desiredHeight(): Int {
        val landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val baseRowHeight = dp(KeyboardHeight.baseRowDp(landscape))
        val rows = layout.rows.size.coerceAtLeast(1)
        return (baseRowHeight * rows * heightScale + dp(KeyboardHeight.BOTTOM_PADDING_DP)).roundToInt() +
            bottomInset
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), desiredHeight())
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        computeBounds()
    }

    private fun computeBounds() {
        val rows = layout.rows
        if (rows.isEmpty() || width == 0 || height == 0) {
            boundedKeys = emptyList()
            return
        }
        rowHeight = (height - dp(KeyboardHeight.BOTTOM_PADDING_DP) - bottomInset) / rows.size.toFloat()
        val result = mutableListOf<BoundedKey>()
        rows.forEachIndexed { rowIndex, row ->
            val totalWeight = row.sumOf { it.widthWeight.toDouble() }.toFloat()
            val rowWidth = width - dp(6f)
            var x = dp(3f)
            val top = rowIndex * rowHeight
            row.forEach { key ->
                val keyWidth = rowWidth * (key.widthWeight / totalWeight)
                result += BoundedKey(key, RectF(x, top, x + keyWidth, top + rowHeight), rowIndex)
                x += keyWidth
            }
        }
        boundedKeys = result
        accessHelper.invalidateRoot()
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(theme.background)
        val pressedKeys = pressed.values.mapTo(HashSet()) { it.bounded }
        for (bounded in boundedKeys) {
            if (bounded.key.action == KeyAction.None) continue
            val rect = RectF(
                bounded.rect.left + keyGap / 2f,
                bounded.rect.top + keyGap,
                bounded.rect.right - keyGap / 2f,
                bounded.rect.bottom - keyGap,
            )
            keyPaint.color = keyColor(bounded.key, bounded in pressedKeys)
            canvas.drawRoundRect(rect, keyCornerRadius, keyCornerRadius, keyPaint)
            drawLabel(canvas, bounded.key, rect)
        }
    }

    private fun isAccentKey(key: Key): Boolean = key.action == KeyAction.Enter ||
        (key.action == KeyAction.Shift && shiftState != ShiftState.OFF) ||
        (selectionActive && key.action == KeyAction.Edit(EditCommand.SELECT))

    private fun keyColor(key: Key, isPressed: Boolean): Int = when {
        isPressed -> theme.keyPressed
        isAccentKey(key) -> theme.accent
        key.action is KeyAction.Text || key.action == KeyAction.Space -> theme.key
        else -> theme.specialKey
    }

    private fun iconFor(key: Key): Drawable? = when (val action = key.action) {
        // Enter-kuvake vain kun näppäimellä ei ole toimintatekstiä (Hae, Lähetä…).
        KeyAction.Enter -> if (enterText == "⏎") enterIcon else null
        // Tila näkyy kuvakkeesta kuten Gboardissa: ääriviiva, täytetty, lukko.
        KeyAction.Shift -> when (shiftState) {
            ShiftState.OFF -> shiftIcon
            ShiftState.SHIFT -> shiftOnIcon
            ShiftState.CAPS -> shiftCapsIcon
        }
        KeyAction.Backspace -> backspaceIcon
        is KeyAction.Arrow -> arrowIcons[action.keyCode]
        else -> null
    }

    private fun drawLabel(canvas: Canvas, key: Key, rect: RectF) {
        val label = labelFor(key)
        val contentColor = when {
            key.action == KeyAction.Space -> theme.hint
            isAccentKey(key) -> theme.accentText
            else -> theme.text
        }
        val icon = iconFor(key)
        if (icon != null) {
            icon.setTint(contentColor)
            val size = (rowHeight * 0.42f).roundToInt()
            val cx = rect.centerX().roundToInt()
            val cy = rect.centerY().roundToInt()
            icon.setBounds(cx - size / 2, cy - size / 2, cx + size / 2, cy + size / 2)
            icon.draw(canvas)
            return
        }
        labelPaint.color = contentColor
        labelPaint.textSize = when {
            key.action == KeyAction.Space -> rowHeight * SPACE_LABEL_SCALE
            label.length > 2 -> rowHeight * 0.28f
            key.action is KeyAction.Text -> rowHeight * 0.42f
            else -> rowHeight * 0.38f
        }
        if (key.action == KeyAction.Space) {
            // Nimi mahtuu myös kapeaan välilyöntiin (yhden käden tila, lisämerkki).
            val available = rect.width() - dp(12f)
            val needed = labelPaint.measureText(label)
            if (needed > available) labelPaint.textSize *= available / needed
        }
        if (label.isNotEmpty()) {
            canvas.drawText(label, rect.centerX(), rect.centerY() + labelPaint.textSize * 0.35f, labelPaint)
        }
        val hint = key.longPress.firstOrNull()
        if (hint != null && key.action is KeyAction.Text) {
            hintPaint.color = theme.hint
            hintPaint.textSize = rowHeight * 0.2f
            canvas.drawText(hint, rect.right - dp(5f), rect.top + hintPaint.textSize + dp(3f), hintPaint)
        }
    }

    private fun labelFor(key: Key): String = when (key.action) {
        KeyAction.Enter -> enterText
        KeyAction.Shift -> if (shiftState == ShiftState.CAPS) "⇪" else "⇧"
        KeyAction.Space -> spaceLabel
        is KeyAction.Text ->
            if (shiftState != ShiftState.OFF && key.label.length == 1) {
                key.label.uppercase(fiLocale)
            } else {
                key.label
            }
        else -> key.label
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val index = event.actionIndex
                handleDown(event.getPointerId(index), event.getX(index), event.getY(index))
            }
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until event.pointerCount) {
                    handleMove(event.getPointerId(i), event.getX(i))
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                handleUp(event.getPointerId(event.actionIndex))
                if (event.actionMasked == MotionEvent.ACTION_UP) performClick()
            }
            MotionEvent.ACTION_CANCEL -> cancelAll()
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun handleDown(pointerId: Int, x: Float, y: Float) {
        // Valikon peittokuva ottaa näppäinalueen kosketukset; tänne asti
        // päätyy vain jo alkanut kosketus, joka ei saa kirjoittaa mitään.
        if (alternatesPopup != null) return
        val bounded = keyAt(x, y) ?: return
        val info = PressInfo(bounded)
        pressed[pointerId] = info
        val key = bounded.key
        if (key.action == KeyAction.Backspace) {
            // Poisto tapahtuu vasta sormen noustessa, koska painallus voi
            // olla liu'utuksen alku; pohjassa pito alkaa toistaa viiveellä.
            info.backspaceAnchorX = x
            postDelayed(info.repeatRunnable, REPEAT_DELAY_MS)
        } else if (key.repeatable) {
            // Toistuva näppäin toimii heti ja jatkaa pohjassa pidettäessä.
            info.committed = true
            commit(key)
            postDelayed(info.repeatRunnable, REPEAT_DELAY_MS)
        } else {
            if (key.longPress.isNotEmpty() || key.action == KeyAction.Space) {
                postDelayed(info.longPressRunnable, ViewConfiguration.getLongPressTimeout().toLong())
            }
            if (key.action == KeyAction.Space) {
                info.spaceAnchorX = x
            }
            if (previewEnabled && key.action is KeyAction.Text) {
                showPreview(bounded)
            }
        }
        invalidate()
    }

    private fun handleMove(pointerId: Int, x: Float) {
        val info = pressed[pointerId] ?: return
        if (info.alternatesOpen) return
        if (info.bounded.key.action == KeyAction.Space && !info.committed) {
            val threshold = dp(16f)
            var dx = x - info.spaceAnchorX
            while (abs(dx) >= threshold) {
                val step = if (dx > 0) 1 else -1
                listener?.onSpaceSwipe(step)
                info.spaceAnchorX += step * threshold
                info.spaceSwiping = true
                removeCallbacks(info.longPressRunnable)
                dx = x - info.spaceAnchorX
            }
        }
        if (info.bounded.key.action == KeyAction.Backspace && !info.committed) {
            handleBackspaceSwipe(info, x)
        }
    }

    /**
     * Gboardin tapaan: liu'utus vasemmalle valitsee sana kerrallaan, takaisin
     * vedetty valinta pienenee, ja poisto tapahtuu vasta sormen noustessa.
     */
    private fun handleBackspaceSwipe(info: PressInfo, x: Float) {
        val distance = info.backspaceAnchorX - x
        if (!info.backspaceSwiping) {
            if (distance < dp(BACKSPACE_SWIPE_START_DP)) return
            info.backspaceSwiping = true
            removeCallbacks(info.repeatRunnable)
        }
        val past = distance - dp(BACKSPACE_SWIPE_START_DP)
        val words = if (past < 0f) 0 else 1 + (past / dp(BACKSPACE_SWIPE_STEP_DP)).toInt()
        if (words != info.backspaceWords) {
            info.backspaceWords = words
            listener?.onBackspaceSwipe(words)
        }
    }

    private fun handleUp(pointerId: Int) {
        val info = pressed.remove(pointerId) ?: return
        removeCallbacks(info.longPressRunnable)
        removeCallbacks(info.repeatRunnable)
        hidePreview()
        when {
            // Valikko jää auki; merkki valitaan napauttamalla.
            info.alternatesOpen -> Unit
            info.backspaceSwiping -> listener?.onBackspaceSwipeEnd()
            info.spaceSwiping || info.committed -> Unit
            else -> commit(info.bounded.key)
        }
        invalidate()
    }

    /**
     * Sulkee kuplat ja pitkän painalluksen valikon. Valikko jää auki sormen
     * noustessa, joten se suljetaan näppäimistön piiloutuessa — muuten se
     * ilmestyisi seuraavalla avauksella uudelleen.
     */
    fun dismissPopups() = cancelAll()

    private fun cancelAll() {
        for (info in pressed.values) {
            removeCallbacks(info.longPressRunnable)
            removeCallbacks(info.repeatRunnable)
            if (info.backspaceSwiping) listener?.onBackspaceSwipeCancel()
        }
        pressed.clear()
        hidePreview()
        hideAlternates()
        invalidate()
    }

    private fun commit(key: Key) {
        when (val action = key.action) {
            is KeyAction.Text -> listener?.onText(action.text)
            else -> listener?.onKey(action)
        }
    }

    private fun keyAt(x: Float, y: Float): BoundedKey? {
        if (boundedKeys.isEmpty() || rowHeight <= 0f) return null
        // Navigointipalkille varattu tyhjä alue näkymän alareunassa ei saa
        // aktivoida alimman rivin näppäimiä.
        if (y >= layout.rows.size * rowHeight) return null
        val row = (y / rowHeight).toInt().coerceIn(0, layout.rows.size - 1)
        val rowKeys = boundedKeys.filter { it.row == row && it.key.action != KeyAction.None }
        return rowKeys.firstOrNull { x >= it.rect.left && x < it.rect.right }
            ?: rowKeys.minByOrNull { abs(it.rect.centerX() - x) }
    }

    private fun handleLongPress(info: PressInfo) {
        val key = info.bounded.key
        if (key.action == KeyAction.Space) {
            // Väli ei enää kirjoitu sormen noustessa.
            info.committed = true
            if (hapticsEnabled) performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            listener?.onSpaceLongPress()
            return
        }
        if (key.longPress.isEmpty() || alternatesPopup != null) return
        info.alternatesOpen = true
        // Valikon avautuminen tuntuu samoin kuin valinta siitä (vrt. Gboard).
        if (hapticsEnabled) {
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        }
        hidePreview()
        showAlternates(info.bounded)
    }

    /**
     * Avaa pitkän painalluksen vaihtoehdot napautettavana rivinä näppäimen
     * ylle. Läpinäkyvä peittokuva kattaa koko näppäinalueen: napautus
     * merkkiin valitsee sen, napautus muualle sulkee valikon kirjoittamatta
     * mitään. Kosketukset näppäimistön ulkopuolella sulkevat valikon myös.
     * Solut kapenevat, jotta koko rivi mahtuu kapeallekin näytölle.
     */
    private fun showAlternates(bounded: BoundedKey) {
        val base = (bounded.key.action as? KeyAction.Text)?.text
        val values = buildList {
            if (base != null) add(base)
            addAll(bounded.key.longPress)
        }.map { if (shiftState != ShiftState.OFF && it.length == 1) it.uppercase(fiLocale) else it }
        val cellWidth = minOf(dp(46f), (width - dp(8f)) / values.size)
        val cellHeight = dp(52f)

        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            background = GradientDrawable().apply {
                cornerRadius = keyCornerRadius
                setColor(theme.specialKey)
            }
            // Varjo erottaa valikon alla olevista samanvärisistä näppäimistä.
            elevation = dp(6f)
        }
        for (value in values) {
            row.addView(
                TextView(context).apply {
                    text = value
                    contentDescription = KeySpeech.symbol(value)
                    gravity = Gravity.CENTER
                    textSize = 20f
                    // Painettu solu korostuu kuten painettu näppäin.
                    setTextColor(
                        ColorStateList(
                            arrayOf(intArrayOf(android.R.attr.state_pressed), intArrayOf()),
                            intArrayOf(theme.accentText, theme.text),
                        )
                    )
                    background = StateListDrawable().apply {
                        addState(
                            intArrayOf(android.R.attr.state_pressed),
                            GradientDrawable().apply {
                                cornerRadius = keyCornerRadius
                                setColor(theme.accent)
                            },
                        )
                    }
                    setOnClickListener {
                        hideAlternates()
                        listener?.onText(value)
                    }
                },
                LinearLayout.LayoutParams(cellWidth.roundToInt(), cellHeight.roundToInt()),
            )
        }

        val totalWidth = cellWidth * values.size
        val left = (bounded.rect.centerX() - totalWidth / 2f)
            .coerceIn(dp(4f), (width - totalWidth - dp(4f)).coerceAtLeast(dp(4f)))
        // Rivin yläreuna näkymän koordinaateissa; ylimmällä rivillä se on
        // näppäimistön yläpuolella, ja peittokuva alkaa siitä.
        val rowTop = bounded.rect.top - cellHeight - dp(8f)
        val overlayTop = minOf(rowTop, 0f)
        val overlay = FrameLayout(context).apply {
            setOnClickListener { hideAlternates() }
            addView(
                row,
                FrameLayout.LayoutParams(totalWidth.roundToInt(), cellHeight.roundToInt()).apply {
                    leftMargin = left.roundToInt()
                    topMargin = (rowTop - overlayTop).roundToInt()
                },
            )
        }
        val location = IntArray(2)
        getLocationInWindow(location)
        alternatesPopup = PopupWindow(overlay, width, (height - overlayTop).roundToInt()).apply {
            isClippingEnabled = false
            isFocusable = false
            // Napautus työkaluriville tai sovellukseen sulkee valikon.
            isOutsideTouchable = true
            setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
            setOnDismissListener { alternatesPopup = null }
            showAtLocation(
                this@KeyboardView,
                Gravity.NO_GRAVITY,
                location[0],
                location[1] + overlayTop.roundToInt(),
            )
        }
    }

    private fun hideAlternates() {
        alternatesPopup?.dismiss()
        alternatesPopup = null
    }

    private fun showPreview(bounded: BoundedKey) {
        val popupWidth = (bounded.rect.width() * 1.2f).roundToInt()
        val popupHeight = (rowHeight * 1.1f).roundToInt()
        val location = IntArray(2)
        getLocationInWindow(location)
        val x = location[0] + (bounded.rect.centerX() - popupWidth / 2f).roundToInt()
        val y = location[1] + (bounded.rect.top - popupHeight - dp(6f)).roundToInt()

        val view = previewView ?: TextView(context).apply {
            gravity = Gravity.CENTER
            textSize = 26f
        }.also { previewView = it }
        view.text = labelFor(bounded.key)
        view.setTextColor(theme.text)
        view.background = GradientDrawable().apply {
            cornerRadius = keyCornerRadius
            setColor(theme.keyPressed)
        }

        val popup = previewPopup ?: PopupWindow(view, popupWidth, popupHeight).apply {
            isTouchable = false
            isClippingEnabled = false
        }.also { previewPopup = it }
        popup.width = popupWidth
        popup.height = popupHeight
        if (popup.isShowing) {
            popup.update(x, y, popupWidth, popupHeight)
        } else {
            popup.showAtLocation(this, Gravity.NO_GRAVITY, x, y)
        }
    }

    private fun hidePreview() {
        previewPopup?.dismiss()
    }

    override fun onDetachedFromWindow() {
        cancelAll()
        super.onDetachedFromWindow()
    }

    private companion object {
        const val REPEAT_DELAY_MS = 400L
        const val REPEAT_INTERVAL_MS = 50L

        // Sanapoisto etenee harvemmin kuin merkkipoisto, jotta pitkän
        // painalluksen voi lopettaa halutun sanan kohdalle.
        const val REPEAT_WORD_INTERVAL_MS = 250L

        const val SPACE_LABEL_SCALE = 0.22f
        const val BACKSPACE_SWIPE_START_DP = 20f
        const val BACKSPACE_SWIPE_STEP_DP = 28f
    }
}
