package org.jarsi.ark.keyboard

/**
 * Viimeksi kenttään viety käännös. Se säilyy, kun näppäimistö piilotetaan ja
 * sama kenttä avataan uudelleen: muuten kääntäjä tulkitsisi lähettämättömän
 * viestin lähetetyksi. Uusi syöteistunto unohtaa sen, koska tunnisteet eivät
 * yksin erota verkko- ja Compose-kenttiä toisistaan.
 */
class ExportedTranslation {

    /** Kentän tunniste; verkko- ja Compose-kentissä se voi olla sama kentästä toiseen. */
    data class Field(val packageName: String?, val fieldId: Int, val inputType: Int)

    var text = ""
        private set

    private var field: Field? = null

    fun onFieldStarted(started: Field) {
        if (started == field) return
        field = started
        text = ""
    }

    fun record(exported: String) {
        text = exported
    }

    fun clear() {
        text = ""
    }

    companion object {
        /**
         * Viennin jälkeen kentässä oleva teksti sellaisena kuin kenttä sen tallensi:
         * pituusraja voi katkaista viennin tai hylätä sen kokonaan, ja suodatin voi muuttaa
         * sen asua ja pituutta. Seuraava avaus vertaa kenttää tähän, joten alkuperäinen asu
         * näyttäisi muuntavassa kentässä lähetetyltä viestiltä. Ilman varmuutta vientiä ei
         * pidetä perille menneenä, jolloin lähde säilyy eikä seuraava vienti korvaa kentän tekstiä.
         */
        fun insertedPart(exported: String, cursorMoved: Int?, readBefore: (Int) -> CharSequence?): String {
            if (readBefore(exported.length)?.toString() == exported) return exported
            val moved = cursorMoved?.takeIf { it > 0 } ?: return ""
            return readBefore(moved)?.toString()?.takeIf { it.length == moved }.orEmpty()
        }

        /**
         * Kohta, josta vienti korvaa kentän tekstiä: valinnan pienempi pää, koska
         * käännetyssä valinnassa (kursori valinnan alussa) selectionStart on suurempi.
         * Tuntematon valinta (negatiivinen) ei anna lähtöpistettä.
         */
        fun replaceStart(startOffset: Int, selectionStart: Int, selectionEnd: Int): Int? {
            if (selectionStart < 0 || selectionEnd < 0) return null
            return startOffset + minOf(selectionStart, selectionEnd)
        }

        /** Vienti ei mahtunut: pelkkä asun muutos ei lyhennä sitä. */
        fun cutShort(exported: String, inserted: String): Boolean = inserted.length < exported.length
    }
}
