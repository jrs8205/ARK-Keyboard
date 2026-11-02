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

    /** Kentta, jossa kursorin edella on [before]: lyhyempi teksti palautuu kokonaan kuten Androidilla. */
    private fun field(before: String): (Int) -> CharSequence? = { before.takeLast(it) }

    @Test
    fun `kokonaan kenttaan mennyt vienti tunnistetaan tekstista`() {
        assertEquals("Hi there", ExportedTranslation.insertedPart("Hi there", null, field("Hi there")))
    }

    @Test
    fun `pituusrajan katkaisema vienti on kursorin siirtyman mittainen`() {
        assertEquals("Hi th", ExportedTranslation.insertedPart("Hi there", 5, field("Hi th")))
    }

    @Test
    fun `hylatty vienti on tyhja`() {
        assertEquals("", ExportedTranslation.insertedPart("Hi there", 0, field("Abcde")))
    }

    @Test
    fun `ilman varmuutta vientia ei oleteta perille menneeksi`() {
        assertEquals("", ExportedTranslation.insertedPart("Hi there", null, field("Abcde")))
        assertEquals("", ExportedTranslation.insertedPart("Hi there", null) { null })
    }

    @Test
    fun `kentan muuntama vienti tallentuu kentan asussa`() {
        assertEquals("HI THERE", ExportedTranslation.insertedPart("Hi there", 8, field("HI THERE")))
    }

    @Test
    fun `aiempi teksti ei kuulu muunnettuun vientiin`() {
        assertEquals("HI THERE", ExportedTranslation.insertedPart("Hi there", 8, field("alku HI THERE")))
    }

    @Test
    fun `muunnos voi pidentaa vientia`() {
        assertEquals("STRASSE", ExportedTranslation.insertedPart("Straße", 7, field("alku STRASSE")))
    }

    @Test
    fun `muunnettu ja katkaistu vienti tallentuu kentan asussa`() {
        assertEquals("HI T", ExportedTranslation.insertedPart("Hi there", 4, field("HI T")))
    }

    @Test
    fun `siirtymaa vastaamaton luku ei ole varma vienti`() {
        assertEquals("", ExportedTranslation.insertedPart("Hi there", 8, field("HI")))
        assertEquals("", ExportedTranslation.insertedPart("Hi there", 8) { null })
    }

    @Test
    fun `taaksepain siirtynyt kursori ei ole vienti`() {
        assertEquals("", ExportedTranslation.insertedPart("Hi there", -3, field("HI THERE")))
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
    fun `tyhjennys unohtaa viennin`() {
        val export = exported(chat)
        export.clear()
        assertEquals("", export.text)
    }
}
