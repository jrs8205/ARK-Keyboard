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
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import org.jarsi.ark.R
import org.jarsi.ark.emoji.EmojiEntry
import org.jarsi.ark.emoji.EmojiIndex
import org.jarsi.ark.emoji.EmojiRecents
import org.jarsi.ark.theme.KeyboardTheme
import kotlin.math.roundToInt

/**
 * Emojipaneeli näppäinalueen tilalle: oma sivu kullekin kategorialle
 * SwiftKeyn tapaan, viimeksi käytetyt ensimmäisenä, ihonvärit pitkällä
 * painalluksella ja alarivillä paluu kirjaimiin, haku, välilyönti ja poisto.
 */
class EmojiPanelView(context: Context) : LinearLayout(context) {

    interface Listener {
        fun onEmojiPicked(emoji: String)
        fun onBackspace()
        fun onSpace()
        fun onClose()
        fun onOpenSearch()
    }

    private data class Cell(val text: String, val entry: EmojiEntry?)

    var listener: Listener? = null

    private var theme = KeyboardTheme.load(context)
    private val density = resources.displayMetrics.density
    private fun dp(value: Int) = (value * density).roundToInt()

    private var index: EmojiIndex? = null
    private var recents: EmojiRecents? = null
    private var pages: List<List<Cell>> = List(PAGE_COUNT) { emptyList() }
    private var pagesDirty = true
    private var initialPageChosen = false
    private var variantPopup: PopupWindow? = null

    private val cellPool = RecyclerView.RecycledViewPool().apply { setMaxRecycledViews(0, 96) }
    private val pageAdapter = PageAdapter()
    private val pager = ViewPager2(context).apply {
        adapter = pageAdapter
        offscreenPageLimit = 1
        registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                dismissVariants()
                updateTabs(position)
            }
        })
    }

    private val tabViews: List<View> = (0 until PAGE_COUNT).map { page ->
        val description = context.getString(TAB_NAMES[page])
        val tab: View = if (page == RECENT_PAGE) {
            ImageView(context).apply {
                setImageResource(R.drawable.ic_history)
                scaleType = ImageView.ScaleType.CENTER
                contentDescription = description
            }
        } else {
            TextView(context).apply {
                text = TAB_ICONS[page]
                textSize = 18f
                gravity = Gravity.CENTER
                contentDescription = description
            }
        }
        tab.setOnClickListener { pager.setCurrentItem(page, false) }
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

    private val spaceButton = bottomLabel("").apply {
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
        addView(pager, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
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

    /** Asettaa aineiston; [recents] on palvelun jaettu tila. */
    fun setData(newIndex: EmojiIndex?, newRecents: EmojiRecents?) {
        if (newIndex !== index || newRecents !== recents) pagesDirty = true
        index = newIndex
        recents = newRecents
    }

    /** Päivittää sivut avauksen yhteydessä: käytetyt ja ihonvärit muuttuvat käytössä. */
    fun refresh() {
        val data = index ?: return
        if (pagesDirty) {
            val memory = recents
            pages = List(PAGE_COUNT) { page ->
                if (page == RECENT_PAGE) {
                    memory?.recent.orEmpty().map { Cell(it, data.entryFor(it)) }
                } else {
                    data.category(page - 1).map { entry ->
                        Cell(memory?.variantFor(entry.emoji) ?: entry.emoji, entry)
                    }
                }
            }
            pagesDirty = false
            pageAdapter.notifyDataSetChanged()
        }
        if (!initialPageChosen) {
            initialPageChosen = true
            val first = if (pages[RECENT_PAGE].isEmpty()) RECENT_PAGE + 1 else RECENT_PAGE
            pager.setCurrentItem(first, false)
            updateTabs(first)
        }
    }

    private fun pick(emoji: String) {
        pagesDirty = true
        listener?.onEmojiPicked(emoji)
    }

    private fun updateTabs(selected: Int) {
        tabViews.forEachIndexed { page, tab ->
            tab.background = if (page == selected) {
                GradientDrawable().apply {
                    cornerRadius = dp(8).toFloat()
                    setColor(theme.accent)
                }
            } else {
                null
            }
            tab.isSelected = page == selected
            (tab as? ImageView)?.setColorFilter(
                if (page == selected) theme.accentText else theme.text
            )
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
                        recents?.rememberVariant(entry.emoji, value)
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
        spaceButton.background = bottomKey(theme.key)
        backspaceButton.setColorFilter(theme.text)
        backspaceButton.background = bottomKey(theme.specialKey)
        updateTabs(pager.currentItem)
        pageAdapter.notifyDataSetChanged()
    }

    private inner class PageAdapter : RecyclerView.Adapter<PageAdapter.PageHolder>() {

        inner class PageHolder(
            root: FrameLayout,
            val grid: RecyclerView,
            val hint: TextView,
        ) : RecyclerView.ViewHolder(root)

        override fun getItemCount() = PAGE_COUNT

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageHolder {
            val layout = GridLayoutManager(context, DEFAULT_COLUMNS)
            val grid = RecyclerView(context).apply {
                layoutManager = layout
                setRecycledViewPool(cellPool)
                setHasFixedSize(true)
                clipToPadding = false
                addOnLayoutChangeListener { view, _, _, _, _, _, _, _, _ ->
                    val columns = (view.width / dp(CELL_DP)).coerceAtLeast(MIN_COLUMNS)
                    if (layout.spanCount != columns) layout.spanCount = columns
                }
            }
            val hint = TextView(context).apply {
                text = context.getString(R.string.emoji_ei_viimeisimpia)
                textSize = 14f
                gravity = Gravity.CENTER
            }
            val root = FrameLayout(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                addView(grid, FrameLayout.LayoutParams(-1, -1))
                addView(hint, FrameLayout.LayoutParams(-1, -1))
            }
            return PageHolder(root, grid, hint)
        }

        override fun onBindViewHolder(holder: PageHolder, position: Int) {
            val cells = pages[position]
            holder.grid.adapter = CellAdapter(cells)
            holder.hint.setTextColor(theme.hint)
            holder.hint.visibility =
                if (position == RECENT_PAGE && cells.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private inner class CellAdapter(
        private val cells: List<Cell>,
    ) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        override fun getItemCount() = cells.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val view = TextView(context).apply {
                textSize = 26f
                gravity = Gravity.CENTER
                includeFontPadding = false
                layoutParams = RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(CELL_DP),
                )
            }
            return object : RecyclerView.ViewHolder(view) {}
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val cell = cells[position]
            val view = holder.itemView as TextView
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
        const val RECENT_PAGE = 0
        const val PAGE_COUNT = EmojiIndex.CATEGORY_COUNT + 1
        const val TAB_ROW_DP = 44
        const val BOTTOM_ROW_DP = 48
        const val CELL_DP = 46
        const val VARIANT_CELL_DP = 48
        const val DEFAULT_COLUMNS = 8
        const val MIN_COLUMNS = 6
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
