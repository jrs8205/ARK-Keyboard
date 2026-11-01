package org.jarsi.ark.keyboard

/**
 * Päättää, jäikö sanapoisto tekemättä. Samanlainen häntä ei yksin riitä, jos
 * luku katkesi lukurajaan: toistuvassa tekstissä häntä näyttää samalta myös
 * onnistuneen poiston jälkeen, ja uusinta poistaisi sanan kahdesti. Silloin
 * vaaditaan riippumaton tieto siitä, ettei kursori liikkunut; ilman sitä
 * uusintaa ei tehdä.
 */
object WordDeleteCheck {
    fun failed(
        textBefore: String,
        textNow: String?,
        truncated: Boolean,
        positionBefore: Int?,
        positionNow: Int?,
    ): Boolean {
        if (textNow != textBefore) return false
        if (!truncated) return true
        return positionBefore != null && positionBefore == positionNow
    }
}
