package org.jarsi.ark.keyboard

import org.junit.Assert.assertEquals
import org.junit.Test

class ExportedTranslationTest {

    private val chat = ExportedTranslation.Field("viesti", 1, 1)
    private val other = ExportedTranslation.Field("viesti", 2, 1)
    private val otherApp = ExportedTranslation.Field("muistio", 1, 1)

    private fun exported(field: ExportedTranslation.Field) = ExportedTranslation().also {
        it.onFieldStarted(field)
        it.record("Hi there")
    }

    @Test
    fun `saman kentan uudelleenavaus sailyttaa viennin`() {
        val export = exported(chat)
        export.onFieldStarted(chat)
        assertEquals("Hi there", export.text)
    }

    @Test
    fun `toinen kentta unohtaa viennin`() {
        val export = exported(chat)
        export.onFieldStarted(other)
        assertEquals("", export.text)
    }

    @Test
    fun `toinen sovellus unohtaa viennin`() {
        val export = exported(chat)
        export.onFieldStarted(otherApp)
        assertEquals("", export.text)
    }

    @Test
    fun `paluu alkuperaiseen kenttaan ei palauta unohdettua`() {
        val export = exported(chat)
        export.onFieldStarted(other)
        export.onFieldStarted(chat)
        assertEquals("", export.text)
    }

    /** Kentän teksti ja valinta kentän kertomana; [cursor] yksin tarkoittaa pelkkää kursoria. */
    private fun field(text: String, cursor: Int, end: Int = cursor, offset: Int = 0) =
        ExportedTranslation.FieldText(text, offset, cursor, end)

    private fun inserted(
        before: ExportedTranslation.FieldText?,
        after: ExportedTranslation.FieldText?,
        replacedBefore: Int = 0,
    ) = ExportedTranslation.insertedPart(before, after, replacedBefore)

    @Test
    fun `ilman kursoritietoa lisaysta ei oleteta tekstin perusteella`() {
        // Kenttä ei kerro tekstiään tai valintaansa: jälkikäteen luettu sama teksti voi olla kentän omaa.
        assertEquals("", inserted(null, field("Hi there", 8)))
        assertEquals("", inserted(field("", 0), null))
        assertEquals("", inserted(field("", -1), field("Hi there", 8)))
        assertEquals("", inserted(field("", 0, -1), field("Hi there", 8)))
        assertEquals("", inserted(field("", 0), field("Hi there", -1)))
    }

    @Test
    fun `vienti tyhjaan kenttaan on koko teksti`() {
        assertEquals("Hi there", inserted(field("", 0), field("Hi there", 8)))
    }

    @Test
    fun `vienti tekstin keskelle sailyttaa ympariston`() {
        assertEquals("Hi", inserted(field("alku loppu", 5), field("alku Hiloppu", 7)))
    }

    @Test
    fun `pituusrajan katkaisema vienti on kentassa oleva osa`() {
        assertEquals("Hi th", inserted(field("", 0), field("Hi th", 5)))
        assertEquals("HI T", inserted(field("", 0), field("HI T", 4)))
    }

    @Test
    fun `hylatty vienti on tyhja`() {
        assertEquals("", inserted(field("Abcde", 5), field("Abcde", 5)))
    }

    @Test
    fun `hylatty vienti on tyhja vaikka kentan teksti sattuu vastaamaan`() {
        // Kenttä ei muuttunut: kentässä ollut "alpha " ei ole vienti.
        assertEquals("", inserted(field("alpha ", 6), field("alpha ", 6)))
    }

    @Test
    fun `kentan muuntama vienti tallentuu kentan asussa`() {
        assertEquals("HI THERE", inserted(field("", 0), field("HI THERE", 8)))
        assertEquals("HI THERE", inserted(field("alku ", 5), field("alku HI THERE", 13)))
        assertEquals("STRASSE", inserted(field("alku ", 5), field("alku STRASSE", 12)))
    }

    @Test
    fun `valinnan korvaava vienti on valinnan tilalle tullut teksti`() {
        assertEquals("X", inserted(field("alpha beta gamma", 6, 10), field("alpha X gamma", 7)))
        // Käännetty valinta: kursori valinnan alussa.
        assertEquals("X", inserted(field("alpha beta gamma", 10, 6), field("alpha X gamma", 7)))
        // Valinnan tilalle ei tullut mitään: peruutukselle ei ole omaa lisäystä.
        assertEquals("", inserted(field("alpha beta gamma", 6, 10), field("alpha  gamma", 6)))
    }

    @Test
    fun `edellinen vienti poistettiin ennen uutta`() {
        assertEquals("Hello", inserted(field("alku Hi", 7), field("alku Hello", 10), replacedBefore = 2))
        assertEquals("", inserted(field("Hi", 1), field("Hello", 5), replacedBefore = 2))
    }

    @Test
    fun `muotoilijan muuttama aiempi teksti ei ole vienti`() {
        // Puhelinnumeron muotoilija muotoili koko kentän: kursorin siirtymä ei kerro lisäystä.
        assertEquals("", inserted(field("234-5678", 8), field("(234) 567-8999", 14)))
    }

    @Test
    fun `muuttunut kursorin jalkeinen teksti ei ole vienti`() {
        assertEquals("", inserted(field("ab cd", 2), field("abXY cd!", 4)))
        assertEquals("", inserted(field("ab cd", 2), field("abXYcd", 4)))
    }

    @Test
    fun `kursori muualla kuin lisayksen lopussa ei ole vienti`() {
        assertEquals("", inserted(field("ab", 2), field("abXY", 3)))
        assertEquals("", inserted(field("ab", 2), field("abXY", 2, 4)))
    }

    @Test
    fun `lyhentynyt teksti ei ole vienti`() {
        assertEquals("", inserted(field("abc", 3), field("ab", 2)))
    }

    @Test
    fun `eri alkukohdasta luettu teksti ei kelpaa vertailuun`() {
        assertEquals("", inserted(field("", 0), field("Hi there", 8, offset = 5)))
    }

    @Test
    fun `epajohdonmukainen valinta ei ole vienti`() {
        assertEquals("", inserted(field("ab", 5), field("abXY", 4)))
    }

    @Test
    fun `vain lyhentynyt vienti jai vajaaksi`() {
        assertEquals(false, ExportedTranslation.cutShort("Hi there", "Hi there"))
        assertEquals(false, ExportedTranslation.cutShort("Hi there", "HI THERE"))
        assertEquals(false, ExportedTranslation.cutShort("Straße", "STRASSE"))
        assertEquals(true, ExportedTranslation.cutShort("Hi there", "HI T"))
        assertEquals(true, ExportedTranslation.cutShort("Hi there", ""))
    }

    @Test
    fun `kentan paikka on kursorin paikka koko sisallossa`() {
        assertEquals(7, field("alku Hi", 7).position)
        assertEquals(107, field("alku Hi", 7, offset = 100).position)
        assertEquals(106, field("alku Hi", 6, 7, offset = 100).position)
        assertEquals(null, field("alku Hi", -1).position)
    }

    @Test
    fun `tyhjennys unohtaa viennin`() {
        val export = exported(chat)
        export.clear()
        assertEquals("", export.text)
    }
}
