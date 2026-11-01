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
    fun `tyhjennys unohtaa viennin`() {
        val export = exported(chat)
        export.clear()
        assertEquals("", export.text)
    }
}
