package org.jarsi.ark.settings.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContrastTest {

    private val musta = 0xFF000000.toInt()
    private val valkoinen = 0xFFFFFFFF.toInt()
    private val savy40 = 0xFF3558B6.toInt()

    @Test
    fun `musta ja valkoinen on 21 yhteen`() {
        assertEquals(21.0, Contrast.ratio(musta, valkoinen), 0.01)
    }

    @Test
    fun `jarjestys ei vaikuta`() {
        assertEquals(Contrast.ratio(musta, valkoinen), Contrast.ratio(valkoinen, musta), 1e-9)
    }

    @Test
    fun `savy 30 ja 90 ylittavat seitseman`() {
        assertTrue(Contrast.ratio(0xFF434659.toInt(), 0xFFDAE2FF.toInt()) >= 7.0)
    }

    @Test
    fun `savy 40 valkoisella jaa alle seitseman`() {
        assertTrue(Contrast.ratio(savy40, valkoinen) < 7.0)
    }

    @Test
    fun `textOn palauttaa ensisijaisen kun raja tayttyy`() {
        assertEquals(musta, Contrast.textOn(valkoinen, preferred = musta, fallback = 0xFF444444.toInt()))
    }

    @Test
    fun `textOn pudottaa varavariin kun ensisijainen jaa alle`() {
        assertEquals(musta, Contrast.textOn(valkoinen, preferred = savy40, fallback = musta))
    }

    @Test
    fun `textOn valitsee paremman kun kumpikaan ei riita`() {
        assertEquals(
            0xFF666666.toInt(),
            Contrast.textOn(valkoinen, preferred = 0xFF999999.toInt(), fallback = 0xFF666666.toInt()),
        )
    }

    @Test
    fun `saadinraja kolme`() {
        assertEquals(savy40, Contrast.textOn(valkoinen, preferred = savy40, fallback = musta, minimum = 3.0))
    }

    @Test
    fun `fonttikatto`() {
        assertEquals(1.0f, Contrast.cappedFontScale(1.0f))
        assertEquals(1.3f, Contrast.cappedFontScale(1.3f))
        assertEquals(1.5f, Contrast.cappedFontScale(2.0f))
        assertEquals(0.85f, Contrast.cappedFontScale(0.85f))
    }

    @Test
    fun `pickPair ottaa ensimmaisen riittavan parin`() {
        val mauve = 0xFF8B6B8D.toInt()
        val lightGray = 0xFFE7E8EE.toInt()
        val pairs = listOf(mauve to valkoinen, lightGray to musta)
        assertEquals(lightGray to musta, Contrast.pickPair(pairs))
    }

    @Test
    fun `pickPair pitaa ensimmaisen kun se riittaa`() {
        val pairs = listOf(0xFFDAE2FF.toInt() to 0xFF434659.toInt(), valkoinen to musta)
        assertEquals(pairs[0], Contrast.pickPair(pairs))
    }

    @Test
    fun `pickPair valitsee parhaan kun mikaan ei riita`() {
        val pairs = listOf(0xFF8B6B8D.toInt() to valkoinen, 0xFF999999.toInt() to valkoinen)
        assertEquals(pairs[0], Contrast.pickPair(pairs))
    }
}
