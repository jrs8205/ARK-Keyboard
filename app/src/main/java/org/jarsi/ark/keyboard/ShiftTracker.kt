package org.jarsi.ark.keyboard

/**
 * Shift-näppäimen tila Gboardin tapaan. Automaattisesti (lauseen alussa)
 * tullut iso kirjain poistuu yhdellä napautuksella ja pysyy poissa, kunnes
 * teksti tai kursori muuttuu; nopea tuplanapautus lukitsee isot. Käsin
 * otettu tila kiertää pienet → yksi iso → lukko ilman aikarajaa.
 */
class ShiftTracker {

    var state = ShiftState.OFF
        private set

    private var manual = false
    private var dismissed = false
    private var dismissedAt = NOT_DISMISSED

    fun tap(now: Long, doubleTapMs: Long): ShiftState {
        val automatic = state == ShiftState.SHIFT && !manual
        val quickAfterDismiss = dismissedAt != NOT_DISMISSED && now - dismissedAt <= doubleTapMs
        state = when {
            automatic -> ShiftState.OFF
            quickAfterDismiss -> ShiftState.CAPS
            else -> state.nextOnTap()
        }
        manual = state != ShiftState.OFF
        dismissed = automatic
        dismissedAt = if (automatic) now else NOT_DISMISSED
        return state
    }

    /** Sovittaa automaattisen ison tilaan; palauttaa true, jos tila muuttui. */
    fun applyAutoCaps(wanted: Boolean): Boolean {
        if (manual || dismissed || state == ShiftState.CAPS) return false
        val next = if (wanted) ShiftState.SHIFT else ShiftState.OFF
        if (next == state) return false
        state = next
        return true
    }

    /** Merkki kirjoitettiin: yksittäinen iso kuluu; true, jos tila muuttui. */
    fun onCharacterTyped(): Boolean {
        if (state != ShiftState.SHIFT) return false
        state = ShiftState.OFF
        manual = false
        return true
    }

    /** Teksti tai kursori muuttui: peruttu automaattinen iso saa taas tulla. */
    fun onTextChanged() {
        dismissed = false
        dismissedAt = NOT_DISMISSED
    }

    fun reset() {
        state = ShiftState.OFF
        manual = false
        onTextChanged()
    }

    private companion object {
        const val NOT_DISMISSED = Long.MIN_VALUE
    }
}
