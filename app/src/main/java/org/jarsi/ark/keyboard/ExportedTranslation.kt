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
}
