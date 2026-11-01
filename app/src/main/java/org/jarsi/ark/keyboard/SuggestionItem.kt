package org.jarsi.ark.keyboard

/** Ehdotusrivin kohde: sana, sanaan liittyvä emoji tai tuore kopio liitettäväksi. */
sealed interface SuggestionItem {
    val text: String

    data class Word(override val text: String) : SuggestionItem
    data class Emoji(override val text: String) : SuggestionItem

    /** [text] on rivillä näkyvä esikatselu; liitettävä teksti on tarjouksessa. */
    data class RecentClip(override val text: String) : SuggestionItem
}
