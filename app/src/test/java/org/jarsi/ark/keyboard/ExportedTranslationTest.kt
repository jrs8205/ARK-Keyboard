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

    @Test
    fun `kokonaan kenttaan mennyt vienti tunnistetaan tekstista`() {
        assertEquals("Hi there", ExportedTranslation.insertedPart("Hi there", "Hi there", null))
    }

    @Test
    fun `pituusrajan katkaisema vienti on kursorin siirtyman mittainen`() {
        assertEquals("Hi th", ExportedTranslation.insertedPart("Hi there", "Hi th", 5))
    }

    @Test
    fun `hylatty vienti on tyhja`() {
        assertEquals("", ExportedTranslation.insertedPart("Hi there", "Abcde", 0))
    }

    @Test
    fun `ilman varmuutta vientia ei oleteta perille menneeksi`() {
        assertEquals("", ExportedTranslation.insertedPart("Hi there", "Abcde", null))
        assertEquals("", ExportedTranslation.insertedPart("Hi there", null, null))
    }

    @Test
    fun `siirtyma rajataan viennin mittaan`() {
        assertEquals("Hi there", ExportedTranslation.insertedPart("Hi there", "x", 99))
        assertEquals("", ExportedTranslation.insertedPart("Hi there", "x", -3))
    }

    @Test
    fun `tyhjennys unohtaa viennin`() {
        val export = exported(chat)
        export.clear()
        assertEquals("", export.text)
    }
}
