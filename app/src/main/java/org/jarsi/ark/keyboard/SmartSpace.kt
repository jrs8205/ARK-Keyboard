package org.jarsi.ark.keyboard

/** Välimerkkisääntöjen merkkiluokat: jälkiväli ja kaksoisvälilyönnin piste. */
object SmartSpace {

    /** Välimerkit, jotka saavat välin heti peräänsä. */
    const val PUNCTUATION = ".,!?:;…"

    /** Lauseen päättävät merkit, joiden jälkeen uusi lause alkaa isolla. */
    private const val SENTENCE_ENDERS = ".!?…"

    fun isPunctuation(c: Char): Boolean = c in PUNCTUATION

    fun isSentenceEnder(c: Char): Boolean = c in SENTENCE_ENDERS

    /**
     * Voiko merkin perään syntyä piste kaksoisvälilyönnillä: kirjaimet,
     * numerot ja sulkevat merkit kelpaavat, välimerkit eivät (ei "..").
     */
    fun canEndSentence(c: Char): Boolean =
        c.isLetterOrDigit() || c in "\"')]}»>%"
}
