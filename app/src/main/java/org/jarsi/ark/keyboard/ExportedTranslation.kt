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

    /**
     * Kentän teksti ja valinta kentän kertomana (getExtractedText). [startOffset] on tekstin
     * alku koko sisällössä; valinnan päät ovat tekstin sisäisiä. Tuntematon valinta on negatiivinen.
     */
    data class FieldText(val text: String, val startOffset: Int, val selectionStart: Int, val selectionEnd: Int) {
        /** Kursorin (valinnan alun) paikka koko sisällössä. */
        val position: Int? get() = if (selectionStart >= 0) startOffset + selectionStart else null
    }

    companion object {
        /**
         * Kenttään todella mennyt osa viennistä tai liitoksesta sellaisena kuin kenttä sen
         * tallensi: pituusraja voi katkaista tekstin tai hylätä sen kokonaan, ja suodatin voi
         * muuttaa sen asua ja pituutta. Vertailu tehdään kentän tekstiin ennen ja jälkeen:
         * lisäys on se, mitä valinnan tilalle tuli, kun valintaa edeltävä ja seuraava teksti
         * ovat ennallaan ja kursori on lisäyksen lopussa. Jos kenttä muutti muutakin (esim.
         * puhelinnumeron muotoilija muotoilee koko kentän), kursorin siirtymä ei kerro
         * lisäystä, eikä omaa peruutusta kirjata: se poistaisi kentän omaa tekstiä. Sama, jos
         * kenttä ei kerro tekstiään tai valintaansa. [replacedBefore] merkkiä valinnan edeltä
         * poistettiin ennen vientiä (edellinen viety käännös), eivätkä ne kuulu vertailuun.
         */
        fun insertedPart(before: FieldText?, after: FieldText?, replacedBefore: Int = 0): String {
            if (before == null || after == null) return ""
            if (before.selectionStart < 0 || before.selectionEnd < 0) return ""
            if (after.selectionStart < 0 || after.selectionStart != after.selectionEnd) return ""
            if (before.startOffset != after.startOffset) return ""
            val prefixEnd = minOf(before.selectionStart, before.selectionEnd) - replacedBefore
            val suffixStart = maxOf(before.selectionStart, before.selectionEnd)
            if (prefixEnd < 0 || suffixStart > before.text.length) return ""
            val prefix = before.text.substring(0, prefixEnd)
            val suffix = before.text.substring(suffixStart)
            val insertedEnd = after.text.length - suffix.length
            if (insertedEnd < prefix.length) return ""
            if (!after.text.startsWith(prefix) || !after.text.endsWith(suffix)) return ""
            if (after.selectionStart != insertedEnd) return ""
            return after.text.substring(prefix.length, insertedEnd)
        }

        /** Vienti ei mahtunut: pelkkä asun muutos ei lyhennä sitä. */
        fun cutShort(exported: String, inserted: String): Boolean = inserted.length < exported.length
    }
}
