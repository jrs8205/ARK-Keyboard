package org.jarsi.ark.keyboard

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Täyttöpalvelun inline-ehdotusten (esim. salasanojen hallinnan tunnusehdotus) rivin
 * säännöt. Chipit ovat ehdotusrivin paikalla: kun niitä on, sanaehdotukset
 * väistyvät, ja salasanakentässäkin rivi näkyy vain chippien kanssa.
 */
object InlineChips {
    const val MAX_SUGGESTIONS = 5
    const val MIN_WIDTH_DP = 48f
    const val CHIP_PADDING_DP = 4f
    const val MAX_CHIP_HEIGHT_DP = 40f

    /** Pitkäkin chip jättää tilaa, jotta seuraava näkyy ja rivin voi huomata rullautuvan. */
    const val MAX_WIDTH_FRACTION = 0.8f

    data class RowState(val chipsVisible: Boolean, val suggestionsVisible: Boolean)

    fun rowState(suggestionsEnabled: Boolean, chipCount: Int): RowState =
        if (chipCount > 0) RowState(chipsVisible = true, suggestionsVisible = false)
        else RowState(chipsVisible = false, suggestionsVisible = suggestionsEnabled)

    /**
     * Salasanakentässä rivi pysyy varattuna, kun täyttöpalvelu on käytössä. Muuten
     * näppäimistö madaltuisi kentän vaihtuessa ja kasvaisi takaisin chipin tullessa.
     * Numerokentissä näppäimistö on muutenkin eri korkuinen.
     */
    fun reservesRow(autofillActive: Boolean, textPassword: Boolean, suggestionsEnabled: Boolean): Boolean =
        autofillActive && textPassword && suggestionsEnabled

    /** Rivin korkeus; lasketaan ilman näkymää, koska pyyntö voi tulla ennen sen luontia. */
    fun barHeightPx(density: Float, heightScale: Float): Int =
        ((KeyboardHeight.SUGGESTION_BAR_DP * density).roundToInt() * heightScale).roundToInt()

    /** Sama arvo kuin ViewGroup.LayoutParams.WRAP_CONTENT; puhdas, ettei testi tarvitse Androidia. */
    const val WRAP_CONTENT = -2

    data class Size(val width: Int, val height: Int)

    /**
     * Rivin chipin asettelumitat. Alusta antaa chipille mittaamansa koon
     * LayoutParams-oliona; se on säilytettävä, koska chipillä ei ole omaa
     * sisäistä kokoa ja WRAP_CONTENT-leveys mittautuisi nollaksi (näkymätön
     * chip, opittu 23.9.2026).
     */
    fun chipSize(givenWidth: Int, givenHeight: Int, rowChipHeight: Int): Size = Size(
        width = if (givenWidth > 0) givenWidth else WRAP_CONTENT,
        height = if (givenHeight > 0) givenHeight else rowChipHeight,
    )

    /** Chipin sallitut mitat pikseleinä täyttöpalvelulle. */
    data class Bounds(val minWidth: Int, val maxWidth: Int, val height: Int)

    fun specBounds(barHeightPx: Int, rowWidthPx: Int, density: Float): Bounds {
        val minWidth = (MIN_WIDTH_DP * density).roundToInt().coerceAtMost(rowWidthPx)
        val height = min(
            barHeightPx - 2 * (CHIP_PADDING_DP * density).roundToInt(),
            (MAX_CHIP_HEIGHT_DP * density).roundToInt(),
        )
        return Bounds(
            minWidth = max(1, minWidth),
            maxWidth = max(1, max(minWidth, (rowWidthPx * MAX_WIDTH_FRACTION).roundToInt())),
            height = max(1, height),
        )
    }
}
