package org.jarsi.ark.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class ImeStatusTest {

    @Test
    fun `ei kaytossa`() {
        assertEquals(ImeStatus.NOT_ENABLED, ImeStatus.of(enabled = false, selected = false))
    }

    @Test
    fun `kaytossa muttei valittu`() {
        assertEquals(ImeStatus.NOT_SELECTED, ImeStatus.of(enabled = true, selected = false))
    }

    @Test
    fun `valmis`() {
        assertEquals(ImeStatus.READY, ImeStatus.of(enabled = true, selected = true))
    }

    @Test
    fun `valittu ilman kayttoonottoa on ei kaytossa`() {
        assertEquals(ImeStatus.NOT_ENABLED, ImeStatus.of(enabled = false, selected = true))
    }
}
