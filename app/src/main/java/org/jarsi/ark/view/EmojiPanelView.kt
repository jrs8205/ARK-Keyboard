package org.jarsi.ark.view

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.jarsi.ark.R
import org.jarsi.ark.emoji.EmojiEntry
import org.jarsi.ark.emoji.EmojiIndex
import org.jarsi.ark.emoji.EmojiRecents
import org.jarsi.ark.theme.KeyboardTheme
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Emojipaneeli näppäinalueen tilalle: yhtenäinen lista, jossa kategoria jatkuu
 * vierittäessä suoraan seuraavaan. Välilehti seuraa vieritystä ja hyppää
 * napautuksesta kategorian alkuun; sivuttaispyyhkäisy vaihtaa kategoriaa.
 * Ihonvärit pitkällä painalluksella, ja alarivillä paluu kirjaimiin, haku,
 * välilyönti ja poisto.
 */
class EmojiPanelView(context: Context) : LinearLayout(context) {

    interface Listener {
        fun onEmojiPicked(emoji: String)
        fun onBackspace()
        fun onSpace()
        fun onClose()
        fun onOpenSearch()
    }

    private sealed interface Row {
        data class Header(val title: String) : Row
        data object EmptyHint : Row
        data class Cell(val text: String, val entry: EmojiEntry?) : Row
    }

    var listener: Listener? = null

    /** Kentissä, jotka kieltävät oppimisen, ihonvärivalintaa ei muisteta. */
    var rememberChoices = true

    private var theme = KeyboardTheme.load(context)
    private val density = resources.displayMetrics.density
    private fun dp(value: Int) = (value * density).roundToInt()

    private var index: EmojiIndex? = null
    private var recents: EmojiRecents? = null
    private var rows: List<Row> = emptyList()
    private var sectionStarts = IntArray(SECTION_COUNT)
    private var currentSection = 0
    private var rowsDirty = true
    private var builtRevision = -1
    private var initialSectionChosen = false
    private var variantPopup: PopupWindow? = null

    private val gridAdapter = GridAdapter()
    private val gridLayout = GridLayoutManager(context, DEFAULT_COLUMNS).apply {
        spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int =
                if (rows.getOrNull(position) is Row.Cell) 1 else spanCount
        }
    }

    private val grid = RecyclerView(context).apply {
        layoutManager = gridLayout
        adapter = gridAdapter
        setHasFixedSize(true)
        addOnLayoutChangeListener { view, _, _, _, _, _, _, _, _ ->
            val columns = (view.width / dp(CELL_DP)).coerceAtLeast(MIN_COLUMNS)
            if (gridLayout.spanCount != columns) gridLayout.spanCount = columns
        }
        addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dy != 0) dismissVariants()
                val section = sectionAt(gridLayout.findFirstVisibleItemPosition())
                if (section != currentSection) {
                    currentSection = section
                    updateTabs()
                }
            }
        })
        addOnItemTouchListener(SwipeBetweenSections())
    }

    private val tabViews: List<View> = (0 until SECTION_COUNT).map { section ->
        val description = context.getString(TAB_NAMES[section])
        val tab: View = if (section == RECENT_SECTION) {
            ImageView(context).apply {
                setImageResource(R.drawable.ic_history)
                scaleType = ImageView.ScaleType.CENTER
                contentDescription = description
            }
        } else {
            TextView(context).apply {
                text = TAB_ICONS[section]
                textSize = 18f
                gravity = Gravity.CENTER
                contentDescription = description
            }
        }
        tab.setOnClickListener { jumpToSection(section) }
        tab
    }

    private val tabRow = LinearLayout(context).apply {
        orientation = HORIZONTAL
        tabViews.forEach { addView(it, LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)) }
    }

    private val abcButton = bottomLabel("ABC").apply {
        setOnClickListener { listener?.onClose() }
    }

    private val searchButton = ImageView(context).apply {
        setImageResource(R.drawable.ic_search)
        scaleType = ImageView.ScaleType.CENTER
        contentDescription = context.getString(R.string.emoji_hae)
        setOnClickListener { listener?.onOpenSearch() }
    }

    private val spaceButton = bottomLabel(context.getString(R.string.app_name)).apply {
        textSize = 12f
        maxLines = 1
        contentDescription = context.getString(R.string.cd_valilyonti)
        setOnClickListener { listener?.onSpace() }
    }

    // Vektori-ikoni tekstimerkin sijaan, jotta nappi näyttää samalta
    // kaikilla laitteilla emoji-fontista riippumatta.
    private val backspaceButton = ImageView(context).apply {
        setImageResource(R.drawable.ic_backspace)
        scaleType = ImageView.ScaleType.CENTER
        contentDescription = context.getString(R.string.cd_poista_merkki)
        setOnClickListener { listener?.onBackspace() }
        repeatWhilePressed { listener?.onBackspace() }
    }

    private val bottomRow = LinearLayout(context).apply {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        val pad = dp(3)
        fun add(view: View, weight: Float) {
            addView(
                view,
                LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, weight).apply {
                    setMargins(pad, pad, pad, pad)
                },
            )
        }
        add(abcButton, 1.3f)
        add(searchButton, 1f)
        add(spaceButton, 3.5f)
        add(backspaceButton, 1.3f)
    }

    init {
        orientation = VERTICAL
        addView(tabRow, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(TAB_ROW_DP)))
        addView(grid, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        addView(bottomRow, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(BOTTOM_ROW_DP)))
        applySettings(theme)
    }

    private fun bottomLabel(label: String) = TextView(context).apply {
        text = label
        textSize = 15f
        gravity = Gravity.CENTER
    }

    /** Pohjassa pidetty poisto toistuu kuten näppäimistön askelpalautin. */
    @SuppressLint("ClickableViewAccessibility")
    private fun View.repeatWhilePressed(action: () -> Unit) {
        val handler = Handler(Looper.getMainLooper())
        var repeated = false
        val repeater = object : Runnable {
            override fun run() {
                repeated = true
                action()
                handler.postDelayed(this, REPEAT_INTERVAL_MS)
            }
        }
        setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    repeated = false
                    view.isPressed = true
                    handler.postDelayed(repeater, REPEAT_DELAY_MS)
                }
                MotionEvent.ACTION_UP -> {
                    handler.removeCallbacks(repeater)
                    view.isPressed = false
                    if (!repeated) view.performClick()
                }
                MotionEvent.ACTION_CANCEL -> {
                    handler.removeCallbacks(repeater)
                    view.isPressed = false
                }
            }
            true
        }
    }

    /** Sivuttaispyyhkäisy listan päällä vaihtaa kategoriaa; pystyveto vierittää. */
    private inner class SwipeBetweenSections : RecyclerView.SimpleOnItemTouchListener() {
        private var downX = 0f
        private var downY = 0f

        override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.x
                    downY = e.y
                }
                MotionEvent.ACTION_UP -> {
                    val dx = e.x - downX
                    val dy = e.y - downY
                    if (abs(dx) > dp(SWIPE_MIN_DP) && abs(dx) > abs(dy) * 2) {
                        val next = if (dx < 0) currentSection + 1 else currentSection - 1
                        jumpToSection(next.coerceIn(0, SECTION_COUNT - 1))
                    }
                }
            }
            return false
        }
    }

    private fun sectionAt(position: Int): Int {
        if (position < 0) return currentSection
        var section = 0
        for (i in sectionStarts.indices) {
            if (sectionStarts[i] <= position) section = i else break
        }
        return section
    }

    private fun jumpToSection(section: Int) {
        dismissVariants()
        currentSection = section
        gridLayout.scrollToPositionWithOffset(sectionStarts[section], 0)
        updateTabs()
    }

    /** Asettaa aineiston; [newRecents] on palvelun jaettu tila. */
    fun setData(newIndex: EmojiIndex?, newRecents: EmojiRecents?) {
        if (newIndex !== index || newRecents !== recents) rowsDirty = true
        index = newIndex
        recents = newRecents
    }

    /** Päivittää listan avauksen yhteydessä: käytetyt ja ihonvärit muuttuvat käytössä. */
    fun refresh() {
        val data = index ?: return
        val memory = recents
        // Historia muuttuu myös haussa ja ehdotusriviltä, paneelin ohi.
        val revision = memory?.revision ?: 0
        if (rowsDirty || revision != builtRevision) {
            rebuildRows(data, memory)
            rowsDirty = false
            builtRevision = revision
        }
        if (!initialSectionChosen) {
            initialSectionChosen = true
            val hasRecents = memory?.recent.orEmpty().isNotEmpty()
            jumpToSection(if (hasRecents) RECENT_SECTION else RECENT_SECTION + 1)
        }
    }

    // Vierityskohta säilyy, vaikka historian kasvu siirtää alempia rivejä.
    private fun rebuildRows(data: EmojiIndex, memory: EmojiRecents?) {
        val first = gridLayout.findFirstVisibleItemPosition()
        val top = gridLayout.findViewByPosition(first)?.top ?: 0
        val oldSection = sectionAt(first)
        val oldOffset = if (first >= 0) first - sectionStarts[oldSection] else 0
        val built = ArrayList<Row>()
        val starts = IntArray(SECTION_COUNT)
        for (section in 0 until SECTION_COUNT) {
            starts[section] = built.size
            built += Row.Header(context.getString(TAB_NAMES[section]))
            if (section == RECENT_SECTION) {
                val used = memory?.recent.orEmpty()
                if (used.isEmpty()) built += Row.EmptyHint
                used.mapTo(built) { Row.Cell(it, data.entryFor(it)) }
            } else {
                data.category(section - 1).mapTo(built) { entry ->
                    Row.Cell(memory?.variantFor(entry.emoji) ?: entry.emoji, entry)
                }
            }
        }
        rows = built
        sectionStarts = starts
        gridAdapter.notifyDataSetChanged()
        if (first >= 0 && initialSectionChosen) {
            val end = if (oldSection + 1 < SECTION_COUNT) starts[oldSection + 1] else built.size
            val position = (starts[oldSection] + oldOffset).coerceAtMost(end - 1).coerceAtLeast(0)
            gridLayout.scrollToPositionWithOffset(position, top)
        }
    }

    private fun pick(emoji: String) {
        listener?.onEmojiPicked(emoji)
    }

    private fun updateTabs() {
        tabViews.forEachIndexed { section, tab ->
            val selected = section == currentSection
            tab.background = if (selected) {
                GradientDrawable().apply {
                    cornerRadius = dp(8).toFloat()
                    setColor(theme.accent)
                }
            } else {
                null
            }
            tab.isSelected = selected
            (tab as? ImageView)?.setColorFilter(if (selected) theme.accentText else theme.text)
        }
    }

    private fun cellBackground() = StateListDrawable().apply {
        addState(
            intArrayOf(android.R.attr.state_pressed),
            GradientDrawable().apply {
                cornerRadius = dp(8).toFloat()
                setColor(theme.keyPressed)
            },
        )
    }

    private fun showVariants(anchor: View, entry: EmojiEntry) {
        dismissVariants()
        val values = listOf(entry.emoji) + entry.variants
        val cellSize = minOf(dp(VARIANT_CELL_DP), (width - dp(8)) / values.size)
        val row = LinearLayout(context).apply {
            orientation = HORIZONTAL
            elevation = dp(6).toFloat()
            background = GradientDrawable().apply {
                cornerRadius = dp(8).toFloat()
                setColor(theme.specialKey)
            }
        }
        for (value in values) {
            row.addView(
                TextView(context).apply {
                    text = value
                    textSize = 24f
                    gravity = Gravity.CENTER
                    background = cellBackground()
                    setOnClickListener {
                        if (rememberChoices) recents?.rememberVariant(entry.emoji, value)
                        dismissVariants()
                        pick(value)
                    }
                },
                LayoutParams(cellSize, dp(VARIANT_CELL_DP)),
            )
        }
        val popupWidth = cellSize * values.size
        val anchorLocation = IntArray(2)
        anchor.getLocationInWindow(anchorLocation)
        val panelLocation = IntArray(2)
        getLocationInWindow(panelLocation)
        val minX = panelLocation[0] + dp(4)
        val maxX = (panelLocation[0] + width - popupWidth - dp(4)).coerceAtLeast(minX)
        val x = (anchorLocation[0] + anchor.width / 2 - popupWidth / 2).coerceIn(minX, maxX)
        val y = anchorLocation[1] - dp(VARIANT_CELL_DP) - dp(4)
        variantPopup = PopupWindow(row, popupWidth, dp(VARIANT_CELL_DP)).apply {
            isClippingEnabled = false
            isFocusable = false
            isOutsideTouchable = true
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setOnDismissListener { variantPopup = null }
            showAtLocation(this@EmojiPanelView, Gravity.NO_GRAVITY, x, y)
        }
    }

    fun dismissVariants() {
        variantPopup?.dismiss()
        variantPopup = null
    }

    override fun onDetachedFromWindow() {
        dismissVariants()
        super.onDetachedFromWindow()
    }

    // IME-ikkuna ulottuu navigointipalkin alle; palkin korkeus varataan
    // täytteenä samoin kuin muissa paneeleissa.
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

    fun applySettings(newTheme: KeyboardTheme) {
        theme = newTheme
        setBackgroundColor(theme.background)
        fun bottomKey(color: Int) = GradientDrawable().apply {
            cornerRadius = dp(8).toFloat()
            setColor(color)
        }
        abcButton.setTextColor(theme.text)
        abcButton.background = bottomKey(theme.specialKey)
        searchButton.setColorFilter(theme.text)
        searchButton.background = bottomKey(theme.specialKey)
        // Samaa pintaa kuin rivin muut napit: vaaleassa teemassa valkoinen
        // pelkkä näppäin näytti irralliselta.
        spaceButton.setTextColor(theme.text)
        spaceButton.background = bottomKey(theme.specialKey)
        backspaceButton.setColorFilter(theme.text)
        backspaceButton.background = bottomKey(theme.specialKey)
        updateTabs()
        gridAdapter.notifyDataSetChanged()
    }

    private inner class GridAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        override fun getItemCount() = rows.size

        override fun getItemViewType(position: Int) = when (rows[position]) {
            is Row.Header -> TYPE_HEADER
            Row.EmptyHint -> TYPE_HINT
            is Row.Cell -> TYPE_CELL
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val view = TextView(context).apply {
                includeFontPadding = false
                when (viewType) {
                    TYPE_HEADER -> {
                        textSize = 13f
                        gravity = Gravity.CENTER_VERTICAL
                        setPadding(dp(12), 0, dp(12), 0)
                        ViewCompat.setAccessibilityHeading(this, true)
                        layoutParams = RecyclerView.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            dp(HEADER_DP),
                        )
                    }
                    TYPE_HINT -> {
                        textSize = 14f
                        gravity = Gravity.CENTER
                        text = context.getString(R.string.emoji_ei_viimeisimpia)
                        layoutParams = RecyclerView.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            dp(HINT_DP),
                        )
                    }
                    else -> {
                        textSize = 26f
                        gravity = Gravity.CENTER
                        layoutParams = RecyclerView.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            dp(CELL_DP),
                        )
                    }
                }
            }
            return object : RecyclerView.ViewHolder(view) {}
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val view = holder.itemView as TextView
            when (val row = rows[position]) {
                is Row.Header -> {
                    view.text = row.title
                    view.setTextColor(theme.hint)
                }
                Row.EmptyHint -> view.setTextColor(theme.hint)
                is Row.Cell -> bindCell(view, row)
            }
        }

        private fun bindCell(view: TextView, cell: Row.Cell) {
            view.text = cell.text
            view.setTextColor(theme.text)
            view.background = cellBackground()
            // Suomenkielinen nimi ruudunlukijalle järjestelmän oman sijaan.
            view.contentDescription = cell.entry?.name
            view.setOnClickListener { pick(cell.text) }
            val entry = cell.entry
            if (entry != null && entry.variants.isNotEmpty()) {
                view.setOnLongClickListener {
                    showVariants(view, entry)
                    true
                }
            } else {
                view.setOnLongClickListener(null)
                view.isLongClickable = false
            }
        }
    }

    private companion object {
        const val RECENT_SECTION = 0
        const val SECTION_COUNT = EmojiIndex.CATEGORY_COUNT + 1
        const val TYPE_HEADER = 0
        const val TYPE_HINT = 1
        const val TYPE_CELL = 2
        const val TAB_ROW_DP = 44
        const val BOTTOM_ROW_DP = 48
        const val HEADER_DP = 28
        const val HINT_DP = 64
        const val CELL_DP = 46
        const val VARIANT_CELL_DP = 48
        const val DEFAULT_COLUMNS = 8
        const val MIN_COLUMNS = 6
        const val SWIPE_MIN_DP = 64
        const val REPEAT_DELAY_MS = 400L
        const val REPEAT_INTERVAL_MS = 60L

        val TAB_ICONS = listOf("", "😀", "👋", "🐻", "🍔", "🚗", "⚽", "💡", "🔣", "🚩")
        val TAB_NAMES = listOf(
            R.string.emoji_viimeisimmat,
            R.string.emoji_hymiot,
            R.string.emoji_ihmiset,
            R.string.emoji_luonto,
            R.string.emoji_ruoka,
            R.string.emoji_matkailu,
            R.string.emoji_toiminta,
            R.string.emoji_esineet,
            R.string.emoji_symbolit,
            R.string.emoji_liput,
        )
    }
}
