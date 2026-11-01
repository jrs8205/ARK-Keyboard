package org.jarsi.ark.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InlineChipsTest {

    @Test
    fun `chipit korvaavat ehdotusrivin kun niita on`() {
        val state = InlineChips.rowState(suggestionsEnabled = true, chipCount = 2)
        assertTrue(state.chipsVisible)
        assertFalse(state.suggestionsVisible)
    }

    @Test
    fun `ilman chippeja ehdotusrivi seuraa omaa tilaansa`() {
        assertTrue(InlineChips.rowState(suggestionsEnabled = true, chipCount = 0).suggestionsVisible)
        assertFalse(InlineChips.rowState(suggestionsEnabled = false, chipCount = 0).suggestionsVisible)
        assertFalse(InlineChips.rowState(suggestionsEnabled = false, chipCount = 0).chipsVisible)
    }

    @Test
    fun `salasanakentassa chipit nakyvat vaikka ehdotukset ovat pois`() {
        val state = InlineChips.rowState(suggestionsEnabled = false, chipCount = 1)
        assertTrue(state.chipsVisible)
        assertFalse(state.suggestionsVisible)
    }

    @Test
    fun `matalalla rivilla chipin korkeus on rivi ilman reunatayteita`() {
        val density = 2.625f
        val bounds = InlineChips.specBounds(barHeightPx = 100, rowWidthPx = 1080, density = density)
        assertEquals(100 - 2 * Math.round(InlineChips.CHIP_PADDING_DP * density), bounds.height)
        assertEquals(Math.round(InlineChips.MIN_WIDTH_DP * density), bounds.minWidth)
    }

    @Test
    fun `korkealla rivilla chip ei kasva katon yli`() {
        // Korkeusasetus 130 %: rivi kasvaa, mutta chip pysyy siistin kokoisena.
        val bounds = InlineChips.specBounds(barHeightPx = 150, rowWidthPx = 1080, density = 2.625f)
        assertEquals(105, bounds.height)
    }

    @Test
    fun `pitka chip jattaa tilaa seuraavalle`() {
        val bounds = InlineChips.specBounds(barHeightPx = 150, rowWidthPx = 1080, density = 2.625f)
        assertEquals(864, bounds.maxWidth)
        assertTrue(bounds.minWidth <= bounds.maxWidth)
    }

    @Test
    fun `rivin korkeus seuraa korkeusasetusta ilman nakymaa`() {
        // Pyyntö voi tulla ennen näkymän luontia, joten laskenta ei saa vaatia sitä.
        assertEquals(116, InlineChips.barHeightPx(density = 2.625f, heightScale = 1f))
        assertEquals(151, InlineChips.barHeightPx(density = 2.625f, heightScale = 1.3f))
    }

    @Test
    fun `salasanakentta varaa rivin kun taytto on kaytossa`() {
        assertTrue(InlineChips.reservesRow(autofillActive = true, textPassword = true, suggestionsEnabled = true))
    }

    @Test
    fun `rivia ei varata turhaan`() {
        // Ilman täyttöpalvelua, muussa kentässä tai ehdotusrivin ollessa pois päältä
        // näppäimistö pysyy matalana.
        assertFalse(InlineChips.reservesRow(autofillActive = false, textPassword = true, suggestionsEnabled = true))
        assertFalse(InlineChips.reservesRow(autofillActive = true, textPassword = false, suggestionsEnabled = true))
        assertFalse(InlineChips.reservesRow(autofillActive = true, textPassword = true, suggestionsEnabled = false))
    }

    @Test
    fun `chipin mitat otetaan alustan antamista arvoista`() {
        // InlineSuggestion.inflate antaa näkymälle alustan mittaaman koon
        // LayoutParams-oliona; WRAP_CONTENT-leveys mittautuisi nollaksi.
        val size = InlineChips.chipSize(givenWidth = 723, givenHeight = 148, rowChipHeight = 148)
        assertEquals(723, size.width)
        assertEquals(148, size.height)
    }

    @Test
    fun `ilman alustan mittoja kaytetaan rivin chipkorkeutta ja sisallon leveytta`() {
        val size = InlineChips.chipSize(givenWidth = -2, givenHeight = -2, rowChipHeight = 148)
        assertEquals(InlineChips.WRAP_CONTENT, size.width)
        assertEquals(148, size.height)
    }

    @Test
    fun `kapea naytto ei kaanna minimia ja maksimia`() {
        val bounds = InlineChips.specBounds(barHeightPx = 10, rowWidthPx = 50, density = 3f)
        assertTrue(bounds.minWidth <= bounds.maxWidth)
        assertTrue(bounds.height >= 1)
    }
}
