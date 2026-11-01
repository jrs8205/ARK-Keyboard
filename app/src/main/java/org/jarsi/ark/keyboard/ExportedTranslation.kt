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
         * Kenttään todella mennyt osa viennistä: pituusraja voi katkaista sen tai hylätä
         * kokonaan. Ilman varmuutta vientiä ei pidetä perille menneenä, jolloin lähde säilyy
         * eikä seuraava vienti korvaa kentän tekstiä.
         */
        fun insertedPart(exported: String, textBeforeCursor: String?, cursorMoved: Int?): String =
            when {
                textBeforeCursor == exported -> exported
                cursorMoved == null -> ""
                else -> exported.take(cursorMoved.coerceIn(0, exported.length))
            }
    }
}
