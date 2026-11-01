package org.jarsi.ark.keyboard

import android.view.KeyEvent
import java.util.Locale

/**
 * Näppäinten sanottavat nimet ruudunlukijalle. Puhesynteesi ohittaa monet
 * välimerkit, joten ne sanotaan nimeltä. Salasanakentässä merkkiä ei
 * paljasteta, ellei kuulokkeita ole kytketty ([obscured]-parametri).
 */
object KeySpeech {

    enum class Language { FI, EN }

    private class Words(
        val character: String,
        val capital: String,
        val shift: String,
        val shiftOn: String,
        val capsOn: String,
        val backspace: String,
        val newline: String,
        val space: String,
        val symbols: String,
        val moreSymbols: String,
        val letters: String,
        val up: String,
        val down: String,
        val left: String,
        val right: String,
        val on: String,
    )

    private val FI_WORDS = Words(
        character = "merkki",
        capital = "iso",
        shift = "Vaihto",
        shiftOn = "Vaihto, iso kirjain",
        capsOn = "Vaihto, isot kirjaimet lukittu",
        backspace = "Askelpalautin",
        newline = "Rivinvaihto",
        space = "Välilyönti",
        symbols = "Symbolit",
        moreSymbols = "Lisää symboleja",
        letters = "Kirjaimet",
        up = "Ylös",
        down = "Alas",
        left = "Vasemmalle",
        right = "Oikealle",
        on = "päällä",
    )

    private val EN_WORDS = Words(
        character = "character",
        capital = "capital",
        shift = "Shift",
        shiftOn = "Shift, capital letter",
        capsOn = "Shift, caps lock on",
        backspace = "Backspace",
        newline = "New line",
        space = "Space",
        symbols = "Symbols",
        moreSymbols = "More symbols",
        letters = "Letters",
        up = "Up",
        down = "Down",
        left = "Left",
        right = "Right",
        on = "on",
    )

    private val FI = Locale.forLanguageTag("fi")

    private val SYMBOLS_EN = mapOf(
        "." to "full stop",
        "," to "comma",
        "?" to "question mark",
        "!" to "exclamation mark",
        ":" to "colon",
        ";" to "semicolon",
        "-" to "hyphen",
        "–" to "dash",
        "_" to "underscore",
        "@" to "at sign",
        "/" to "slash",
        "\\" to "backslash",
        "(" to "left parenthesis",
        ")" to "right parenthesis",
        "[" to "left bracket",
        "]" to "right bracket",
        "{" to "left brace",
        "}" to "right brace",
        "<" to "less than",
        ">" to "greater than",
        "\"" to "quotation mark",
        "'" to "apostrophe",
        "#" to "hash",
        "%" to "percent",
        "&" to "ampersand",
        "+" to "plus",
        "=" to "equals",
        "*" to "asterisk",
        "€" to "euro",
        "$" to "dollar",
        "£" to "pound",
        "¤" to "currency sign",
        "…" to "ellipsis",
        "~" to "tilde",
        "^" to "caret",
        "`" to "backtick",
        "|" to "vertical bar",
        "§" to "section sign",
        "°" to "degree",
        "•" to "bullet",
    )

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

    fun symbol(text: String, language: Language = Language.FI): String =
        (if (language == Language.FI) SYMBOLS else SYMBOLS_EN)[text] ?: text

    fun describe(
        key: Key,
        shift: ShiftState,
        enterText: String,
        selectionActive: Boolean,
        obscured: Boolean,
        language: Language = Language.FI,
    ): String {
        val words = if (language == Language.FI) FI_WORDS else EN_WORDS
        return when (val action = key.action) {
            is KeyAction.Text -> when {
                obscured -> words.character
                action.text.length == 1 && action.text[0].isLetter() && shift != ShiftState.OFF ->
                    words.capital + " " + action.text.uppercase(FI)
                else -> symbol(action.text, language)
            }
            KeyAction.Shift -> when (shift) {
                ShiftState.OFF -> words.shift
                ShiftState.SHIFT -> words.shiftOn
                ShiftState.CAPS -> words.capsOn
            }
            KeyAction.Backspace -> words.backspace
            KeyAction.Enter -> if (enterText == "⏎") words.newline else enterText
            KeyAction.Space -> words.space
            KeyAction.Symbols -> words.symbols
            KeyAction.SymbolsMore -> words.moreSymbols
            KeyAction.Letters -> words.letters
            is KeyAction.Arrow -> when (action.keyCode) {
                KeyEvent.KEYCODE_DPAD_UP -> words.up
                KeyEvent.KEYCODE_DPAD_DOWN -> words.down
                KeyEvent.KEYCODE_DPAD_LEFT -> words.left
                else -> words.right
            }
            is KeyAction.Edit ->
                if (selectionActive && action.command == EditCommand.SELECT) {
                    "${key.label}, ${words.on}"
                } else {
                    key.label
                }
            KeyAction.None -> ""
        }
    }
}
