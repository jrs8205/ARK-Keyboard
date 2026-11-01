package org.jarsi.ark.keyboard

import org.junit.Assert.assertEquals
import org.junit.Test

class OneHandTest {

    @Test
    fun `tallennettu tila luetaan ja tuntematon on pois`() {
        assertEquals(OneHandMode.LEFT, OneHandMode.from("vasen"))
        assertEquals(OneHandMode.RIGHT, OneHandMode.from("oikea"))
        assertEquals(OneHandMode.OFF, OneHandMode.from(null))
        assertEquals(OneHandMode.OFF, OneHandMode.from("x"))
    }

    @Test
    fun `kytkin palauttaa viimeksi kaytetyn puolen`() {
        assertEquals(OneHandMode.LEFT, OneHand.toggled(OneHandMode.OFF, OneHandMode.LEFT))
        assertEquals(OneHandMode.RIGHT, OneHand.toggled(OneHandMode.OFF, OneHandMode.OFF))
        assertEquals(OneHandMode.OFF, OneHand.toggled(OneHandMode.LEFT, OneHandMode.LEFT))
    }

    @Test
    fun `puolen vaihto kaantaa puolen`() {
        assertEquals(OneHandMode.RIGHT, OneHand.otherSide(OneHandMode.LEFT))
        assertEquals(OneHandMode.LEFT, OneHand.otherSide(OneHandMode.RIGHT))
    }

    @Test
    fun `puhelimella leveys on suhteessa ikkunaan`() {
        assertEquals(864, OneHand.keyboardWidthPx(1080, 2.625f))
        assertEquals(576, OneHand.keyboardWidthPx(720, 2f))
    }

    @Test
    fun `isolla naytolla leveys on puhelimen kokoinen`() {
        assertEquals(840, OneHand.keyboardWidthPx(2560, 2f))
    }
}
