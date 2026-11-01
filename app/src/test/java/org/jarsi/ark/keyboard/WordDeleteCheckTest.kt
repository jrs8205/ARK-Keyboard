package org.jarsi.ark.keyboard

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WordDeleteCheckTest {

    @Test
    fun `muuttunut teksti tarkoittaa onnistunutta poistoa`() {
        assertFalse(
            WordDeleteCheck.failed("yksi kaksi ", "yksi ", truncated = false, 11, 11)
        )
    }

    @Test
    fun `koko teksti ennallaan tarkoittaa epaonnistunutta poistoa`() {
        assertTrue(
            WordDeleteCheck.failed("yksi kaksi ", "yksi kaksi ", truncated = false, null, null)
        )
    }

    @Test
    fun `katkaistu toistuva hanta ei riita ilman sijaintia`() {
        val tail = "a".repeat(48)
        assertFalse(WordDeleteCheck.failed(tail, tail, truncated = true, null, null))
        assertFalse(WordDeleteCheck.failed(tail, tail, truncated = true, 400, null))
        assertFalse(WordDeleteCheck.failed(tail, tail, truncated = true, null, 400))
    }

    @Test
    fun `katkaistu toistuva hanta ja siirtynyt kursori tarkoittaa onnistunutta poistoa`() {
        val tail = "ab ".repeat(16)
        assertFalse(WordDeleteCheck.failed(tail, tail, truncated = true, 400, 397))
    }

    @Test
    fun `katkaistu hanta ja paikallaan pysynyt kursori tarkoittaa epaonnistunutta poistoa`() {
        val tail = "a".repeat(48)
        assertTrue(WordDeleteCheck.failed(tail, tail, truncated = true, 400, 400))
    }

    @Test
    fun `lukematon kentta ei laukaise uusintaa`() {
        assertFalse(WordDeleteCheck.failed("sana ", null, truncated = false, 5, 5))
    }
}
