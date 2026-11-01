package org.jarsi.ark.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShiftTrackerTest {

    private val doubleTapMs = 300L

    private fun tracker(wantsCaps: Boolean = false) = ShiftTracker().also {
        if (wantsCaps) it.applyAutoCaps(true)
    }

    @Test
    fun `automaattinen iso muuttuu napautuksella pieneksi`() {
        val shift = tracker(wantsCaps = true)
        assertEquals(ShiftState.SHIFT, shift.state)
        assertEquals(ShiftState.OFF, shift.tap(now = 1000, doubleTapMs))
    }

    @Test
    fun `peruttu automaattinen iso ei palaa samassa kohdassa`() {
        val shift = tracker(wantsCaps = true)
        shift.tap(now = 1000, doubleTapMs)
        assertFalse(shift.applyAutoCaps(true))
        assertEquals(ShiftState.OFF, shift.state)
    }

    @Test
    fun `tekstin muutos palauttaa automaattisen ison`() {
        val shift = tracker(wantsCaps = true)
        shift.tap(now = 1000, doubleTapMs)
        shift.onTextChanged()
        assertTrue(shift.applyAutoCaps(true))
        assertEquals(ShiftState.SHIFT, shift.state)
    }

    @Test
    fun `nopea tuplanapautus automaattisesta lukitsee isot`() {
        val shift = tracker(wantsCaps = true)
        shift.tap(now = 1000, doubleTapMs)
        assertEquals(ShiftState.CAPS, shift.tap(now = 1200, doubleTapMs))
    }

    @Test
    fun `hidas toinen napautus perutusta antaa yhden ison`() {
        val shift = tracker(wantsCaps = true)
        shift.tap(now = 1000, doubleTapMs)
        assertEquals(ShiftState.SHIFT, shift.tap(now = 2000, doubleTapMs))
    }

    @Test
    fun `kasin kierto on ennallaan ilman aikarajaa`() {
        val shift = tracker()
        assertEquals(ShiftState.SHIFT, shift.tap(now = 1000, doubleTapMs))
        assertEquals(ShiftState.CAPS, shift.tap(now = 9000, doubleTapMs))
        assertEquals(ShiftState.OFF, shift.tap(now = 20000, doubleTapMs))
    }

    @Test
    fun `kasin otettu iso ei sammu automaattisesti`() {
        val shift = tracker()
        shift.tap(now = 1000, doubleTapMs)
        assertFalse(shift.applyAutoCaps(false))
        assertEquals(ShiftState.SHIFT, shift.state)
    }

    @Test
    fun `lukitut isot eivat sammu automaattisesti eivatka kirjoittaessa`() {
        val shift = tracker()
        shift.tap(now = 1000, doubleTapMs)
        shift.tap(now = 1100, doubleTapMs)
        shift.onCharacterTyped()
        shift.onTextChanged()
        assertFalse(shift.applyAutoCaps(false))
        assertEquals(ShiftState.CAPS, shift.state)
    }

    @Test
    fun `kirjain kuluttaa yhden ison ja automatiikka jatkuu`() {
        val shift = tracker()
        shift.tap(now = 1000, doubleTapMs)
        assertTrue(shift.onCharacterTyped())
        assertEquals(ShiftState.OFF, shift.state)
        assertTrue(shift.applyAutoCaps(true))
    }

    @Test
    fun `kirjain ilman isoa ei muuta tilaa`() {
        assertFalse(tracker().onCharacterTyped())
    }

    @Test
    fun `kasin sammutetut lukitut isot palauttavat automatiikan`() {
        val shift = tracker()
        shift.tap(now = 1000, doubleTapMs)
        shift.tap(now = 1100, doubleTapMs)
        shift.tap(now = 1200, doubleTapMs)
        assertTrue(shift.applyAutoCaps(true))
    }

    @Test
    fun `kentan vaihto nollaa kaiken`() {
        val shift = tracker()
        shift.tap(now = 1000, doubleTapMs)
        shift.tap(now = 1100, doubleTapMs)
        shift.reset()
        assertEquals(ShiftState.OFF, shift.state)
        assertTrue(shift.applyAutoCaps(true))
    }

    @Test
    fun `kentan vaihto unohtaa tuplanapautuksen`() {
        val shift = tracker(wantsCaps = true)
        shift.tap(now = 1000, doubleTapMs)
        shift.reset()
        assertEquals(ShiftState.SHIFT, shift.tap(now = 1100, doubleTapMs))
    }
}
