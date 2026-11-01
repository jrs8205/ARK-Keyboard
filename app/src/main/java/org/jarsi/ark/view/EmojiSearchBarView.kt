package org.jarsi.ark.view

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.jarsi.ark.R
import org.jarsi.ark.keyboard.KeyboardHeight
import org.jarsi.ark.theme.KeyboardTheme
import kotlin.math.roundToInt

/**
 * Emojihaku näppäimistön yläpuolella. Korvaa työkalu- ja ehdotusrivin saman
 * korkuisena, joten näppäimistö ei hyppää hakua avattaessa. Näppäily kertyy
 * hakusanaksi palvelussa, eikä mene kenttään ennen emojin valintaa.
 */
class EmojiSearchBarView(context: Context) : LinearLayout(context) {

    /** Hakutulos: emoji ja sen nimi ruudunlukijalle. */
    data class Result(val emoji: String, val name: String?)

    interface Listener {
        fun onResultPicked(emoji: String)
        fun onClearQuery()
        fun onCloseSearch()
    }

    var listener: Listener? = null

    private var theme = KeyboardTheme.load(context)
    private var heightScale = 1f
    private val density = resources.displayMetrics.density
    private fun dp(value: Float) = (value * density).roundToInt()

    private var results: List<Result> = emptyList()
    private val resultAdapter = ResultAdapter()

    private val backButton = ImageView(context).apply {
        setImageResource(R.drawable.ic_arrow_back)
        scaleType = ImageView.ScaleType.CENTER
        contentDescription = context.getString(R.string.emoji_haku_sulje)
        setOnClickListener { listener?.onCloseSearch() }
    }

    private val queryView = TextView(context).apply {
        textSize = 16f
        gravity = Gravity.CENTER_VERTICAL
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.START
    }

    private val clearButton = ImageView(context).apply {
        setImageResource(R.drawable.ic_close)
        scaleType = ImageView.ScaleType.CENTER
        contentDescription = context.getString(R.string.emoji_haku_tyhjenna)
        visibility = View.GONE
        setOnClickListener { listener?.onClearQuery() }
    }

    private val queryRow = LinearLayout(context).apply {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(backButton, LayoutParams(dp(48f), ViewGroup.LayoutParams.MATCH_PARENT))
        addView(queryView, LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
        addView(clearButton, LayoutParams(dp(48f), ViewGroup.LayoutParams.MATCH_PARENT))
    }

    private val resultList = RecyclerView(context).apply {
        layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        adapter = resultAdapter
    }

    private val emptyView = TextView(context).apply {
        textSize = 14f
        gravity = Gravity.CENTER
        visibility = View.GONE
    }

    private val resultFrame = FrameLayout(context).apply {
        addView(resultList, FrameLayout.LayoutParams(-1, -1))
        addView(emptyView, FrameLayout.LayoutParams(-1, -1))
    }

    init {
        orientation = VERTICAL
        addView(queryRow, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(KeyboardHeight.TOOLBAR_DP)))
        addView(resultFrame, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(KeyboardHeight.SUGGESTION_BAR_DP)))
    }

    fun setQuery(query: String) {
        if (query.isEmpty()) {
            queryView.text = context.getString(R.string.emoji_hae_vihje)
            queryView.setTextColor(theme.hint)
            clearButton.visibility = View.GONE
        } else {
            queryView.text = query
            queryView.setTextColor(theme.text)
            clearButton.visibility = View.VISIBLE
        }
    }

    fun setResults(items: List<Result>, noMatch: Boolean) {
        results = items
        resultAdapter.notifyDataSetChanged()
        resultList.scrollToPosition(0)
        emptyView.text = context.getString(R.string.emoji_ei_tuloksia)
        emptyView.visibility = if (noMatch) View.VISIBLE else View.GONE
    }

    fun applySettings(newTheme: KeyboardTheme, scale: Float) {
        theme = newTheme
        setBackgroundColor(theme.background)
        backButton.setColorFilter(theme.text)
        clearButton.setColorFilter(theme.text)
        emptyView.setTextColor(theme.hint)
        if (heightScale != scale) {
            heightScale = scale
            resultFrame.layoutParams = resultFrame.layoutParams.apply {
                height = dp(KeyboardHeight.SUGGESTION_BAR_DP * scale)
            }
        }
        resultAdapter.notifyDataSetChanged()
    }

    private inner class ResultAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        override fun getItemCount() = results.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val view = TextView(context).apply {
                textSize = 24f
                gravity = Gravity.CENTER
                includeFontPadding = false
                layoutParams = RecyclerView.LayoutParams(
                    dp(RESULT_WIDTH_DP),
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
            }
            return object : RecyclerView.ViewHolder(view) {}
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val result = results[position]
            val view = holder.itemView as TextView
            view.text = result.emoji
            view.contentDescription = result.name
            view.background = StateListDrawable().apply {
                addState(
                    intArrayOf(android.R.attr.state_pressed),
                    GradientDrawable().apply {
                        cornerRadius = dp(8f).toFloat()
                        setColor(theme.keyPressed)
                    },
                )
            }
            view.setOnClickListener { listener?.onResultPicked(result.emoji) }
        }
    }

    private companion object {
        const val RESULT_WIDTH_DP = 48f
    }
}
