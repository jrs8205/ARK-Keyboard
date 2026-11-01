package org.jarsi.ark.keyboard

/**
 * Kursorin odotettu sijainti näppäimistön omien muokkausten jälkeen. Sen
 * avulla valintapäivitykset erotellaan omiin ja vieraisiin ilman
 * aikaikkunaa: päivitykset myöhästyvät sovelluksesta toiseen, mutta käyttäjän
 * kursorihyppy ei koskaan osu omien muokkausten väliin. Logiikka seuraa AOSP:n
 * näppäimistön RichInputConnectionia. Näppäintapahtumien vaikutusta ei voi
 * tietää etukäteen, joten niiden jälkeen sijainti on tuntematon, kunnes
 * seuraava päivitys kertoo sen.
 */
class CursorExpectation {

    enum class Update { OWN, EXTERNAL, UNKNOWN }

    var start = NOT_KNOWN
        private set
    var end = NOT_KNOWN
        private set

    val known: Boolean get() = start >= 0 && end >= 0

    /**
     * Kentän alku. Kentän ilmoittama alkusijainti voi olla vanhentunut
     * (laitteen kääntö, uudelleenfokusointi), joten se tarkistetaan
     * todellisesta tekstistä: [lookback]-rajaa lyhyempi [textBeforeLength]
     * on kursorin koko etäisyys kentän alusta.
     */
    fun begin(initialStart: Int, initialEnd: Int, textBeforeLength: Int?, lookback: Int) {
        if (textBeforeLength == null) {
            onUnknownEdit()
            return
        }
        start = initialStart
        end = initialEnd
        if (textBeforeLength < lookback && (textBeforeLength > start || start < lookback)) {
            val collapsed = start == end
            start = textBeforeLength
            if (collapsed || start > end) end = start
        }
    }

    /** Teksti kirjoitettiin kursorin kohdalle; mahdollinen valinta korvautuu. */
    fun onCommit(length: Int) {
        if (!known) return
        // Vasemmalle laajennetussa valinnassa alku on lopun jäljessä.
        start = minOf(start, end) + length
        end = start
    }

    /** Kursorin edeltä poistettiin [count] merkkiä. */
    fun onDeleteBefore(count: Int) {
        if (!known) return
        start = (start - count).coerceAtLeast(0)
        end = (end - count).coerceAtLeast(0)
    }

    fun onSetSelection(newStart: Int, newEnd: Int) {
        start = newStart
        end = newEnd
    }

    /** Muokkaus, jonka vaikutusta kursoriin ei tiedetä (näppäintapahtuma, sanelu). */
    fun onUnknownEdit() {
        start = NOT_KNOWN
        end = NOT_KNOWN
    }

    fun classify(oldStart: Int, oldEnd: Int, newStart: Int, newEnd: Int): Update {
        if (!known) {
            onSetSelection(newStart, newEnd)
            return Update.UNKNOWN
        }
        if (start == newStart && end == newEnd) return Update.OWN
        val movedFromExpected = start == oldStart && end == oldEnd
        // Myöhästynyt oma päivitys on kursori matkalla vanhasta odotettuun.
        val belated = !movedFromExpected && newStart == newEnd &&
            (newStart - oldStart).toLong() * (start - newStart) >= 0 &&
            (newEnd - oldEnd).toLong() * (end - newEnd) >= 0
        if (belated) return Update.OWN
        onSetSelection(newStart, newEnd)
        return Update.EXTERNAL
    }

    private companion object {
        const val NOT_KNOWN = -1
    }
}
