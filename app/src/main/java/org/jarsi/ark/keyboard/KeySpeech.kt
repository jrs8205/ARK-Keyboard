package org.jarsi.ark.keyboard

import android.view.KeyEvent
import java.util.Locale

/**
 * Näppäinten sanottavat nimet ruudunlukijalle. Puhesynteesi ohittaa monet
 * välimerkit, joten ne sanotaan nimeltä. Salasanakentässä merkkiä ei
 * paljasteta, ellei kuulokkeita ole kytketty ([obscured]-parametri).
 */
object KeySpeech {

    private val FI = Locale.forLanguageTag("fi")

    private val SYMBOLS = mapOf(
        "." to "piste",
        "," to "pilkku",
        "?" to "kysymysmerkki",
        "!" to "huutomerkki",
        ":" to "kaksoispiste",
        ";" to "puolipiste",
        "-" to "viiva",
        "–" to "ajatusviiva",
        "_" to "alaviiva",
        "@" to "ät",
        "/" to "kauttaviiva",
        "\\" to "kenoviiva",
        "(" to "vasen sulku",
        ")" to "oikea sulku",
        "[" to "vasen hakasulku",
        "]" to "oikea hakasulku",
        "{" to "vasen aaltosulku",
        "}" to "oikea aaltosulku",
        "<" to "pienempi kuin",
        ">" to "suurempi kuin",
        "\"" to "lainausmerkki",
        "'" to "heittomerkki",
        "#" to "risuaita",
        "%" to "prosentti",
        "&" to "et-merkki",
        "+" to "plus",
        "=" to "yhtäsuuruusmerkki",
        "*" to "tähti",
        "€" to "euro",
        "$" to "dollari",
        "£" to "punta",
        "¤" to "valuuttamerkki",
        "…" to "kolme pistettä",
        "~" to "aaltoviiva",
        "^" to "hattu",
        "`" to "gravis",
        "|" to "pystyviiva",
        "§" to "pykälä",
        "°" to "aste",
        "•" to "luettelomerkki",
    )

    fun symbol(text: String): String = SYMBOLS[text] ?: text

    fun describe(
        key: Key,
        shift: ShiftState,
        enterText: String,
        selectionActive: Boolean,
        obscured: Boolean,
    ): String = when (val action = key.action) {
        is KeyAction.Text -> when {
            obscured -> "merkki"
            action.text.length == 1 && action.text[0].isLetter() && shift != ShiftState.OFF ->
                "iso " + action.text.uppercase(FI)
            else -> symbol(action.text)
        }
        KeyAction.Shift -> when (shift) {
            ShiftState.OFF -> "Vaihto"
            ShiftState.SHIFT -> "Vaihto, iso kirjain"
            ShiftState.CAPS -> "Vaihto, isot kirjaimet lukittu"
        }
        KeyAction.Backspace -> "Askelpalautin"
        KeyAction.Enter -> if (enterText == "⏎") "Rivinvaihto" else enterText
        KeyAction.Space -> "Välilyönti"
        KeyAction.Symbols -> "Symbolit"
        KeyAction.SymbolsMore -> "Lisää symboleja"
        KeyAction.Letters -> "Kirjaimet"
        is KeyAction.Arrow -> when (action.keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> "Ylös"
            KeyEvent.KEYCODE_DPAD_DOWN -> "Alas"
            KeyEvent.KEYCODE_DPAD_LEFT -> "Vasemmalle"
            else -> "Oikealle"
        }
        is KeyAction.Edit ->
            if (selectionActive && action.command == EditCommand.SELECT) {
                "${key.label}, päällä"
            } else {
                key.label
            }
        KeyAction.None -> ""
    }
}
