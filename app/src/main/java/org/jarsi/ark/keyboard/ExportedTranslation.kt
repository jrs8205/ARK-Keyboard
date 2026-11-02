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
         * Kenttään todella mennyt osa viennistä tai liitoksesta sellaisena kuin kenttä sen
         * tallensi: pituusraja voi katkaista tekstin tai hylätä sen kokonaan, ja suodatin voi
         * muuttaa sen asua ja pituutta. Kursorin siirtymä ratkaisee, kun se tunnetaan: kentässä
         * jo ollut sama teksti ei ole lisäys, eikä hylätyn liitoksen peruutus saa poistaa sitä.
         * Ilman siirtymää verrataan tekstiin, ja ilman varmuutta lisäystä ei oleteta.
         */
        fun insertedPart(exported: String, cursorMoved: Int?, readBefore: (Int) -> CharSequence?): String {
            if (cursorMoved == null) {
                return if (readBefore(exported.length)?.toString() == exported) exported else ""
            }
            if (cursorMoved <= 0) return ""
            return readBefore(cursorMoved)?.toString()?.takeIf { it.length == cursorMoved }.orEmpty()
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
