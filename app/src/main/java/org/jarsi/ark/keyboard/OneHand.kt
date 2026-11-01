package org.jarsi.ark.keyboard

import kotlin.math.min
import kotlin.math.roundToInt

/** Yhden käden tila: näppäimistö kapenee vasempaan tai oikeaan reunaan. */
enum class OneHandMode(val id: String) {
    OFF("pois"),
    LEFT("vasen"),
    RIGHT("oikea");

    companion object {
        fun from(id: String?): OneHandMode = entries.firstOrNull { it.id == id } ?: OFF
    }
}

object OneHand {
    const val PREF_MODE = "yksi_kasi"
    const val PREF_LAST_SIDE = "yksi_kasi_puoli"

    private const val WIDTH_FRACTION = 0.8f

    // Isolla näytöllä näppäimistö on puhelimen kokoinen, jotta peukalo yltää.
    private const val MAX_WIDTH_DP = 420

    /** Työkalurivin kytkin: pois tai viimeksi käytetylle puolelle (oletus oikea). */
    fun toggled(current: OneHandMode, lastSide: OneHandMode): OneHandMode = when {
        current != OneHandMode.OFF -> OneHandMode.OFF
        lastSide == OneHandMode.OFF -> OneHandMode.RIGHT
        else -> lastSide
    }

    fun otherSide(current: OneHandMode): OneHandMode =
        if (current == OneHandMode.LEFT) OneHandMode.RIGHT else OneHandMode.LEFT

    fun keyboardWidthPx(windowWidthPx: Int, density: Float): Int =
        min((windowWidthPx * WIDTH_FRACTION).roundToInt(), (MAX_WIDTH_DP * density).roundToInt())
}
