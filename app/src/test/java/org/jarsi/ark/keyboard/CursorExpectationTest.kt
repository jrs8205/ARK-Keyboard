package org.jarsi.ark.keyboard

import org.jarsi.ark.keyboard.CursorExpectation.Update
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CursorExpectationTest {

    private val lookback = 1024

    private fun at(position: Int) = CursorExpectation().also {
        it.begin(position, position, position, lookback)
    }

    @Test
    fun `oma kirjoitus tunnistetaan odotetusta sijainnista`() {
        val cursor = at(10)
        cursor.onCommit(3)
        assertEquals(Update.OWN, cursor.classify(10, 10, 13, 13))
    }

    @Test
    fun `vieras siirto vanhasta odotetusta kohdasta on ulkoinen`() {
        val cursor = at(10)
        assertEquals(Update.EXTERNAL, cursor.classify(10, 10, 3, 3))
        assertEquals(3, cursor.start)
    }

    @Test
    fun `nopea kirjoitus jonka paivitykset myohastyvat pysyy omana`() {
        val cursor = at(10)
        cursor.onCommit(1)
        cursor.onCommit(1)
        cursor.onCommit(1)
        assertEquals(Update.OWN, cursor.classify(10, 10, 11, 11))
        assertEquals(Update.OWN, cursor.classify(11, 11, 12, 12))
        assertEquals(Update.OWN, cursor.classify(12, 12, 13, 13))
    }

    @Test
    fun `kursorihyppy kesken myohastyvien paivitysten on ulkoinen`() {
        val cursor = at(10)
        cursor.onCommit(1)
        assertEquals(Update.EXTERNAL, cursor.classify(10, 10, 3, 3))
        assertEquals(Update.EXTERNAL, at(10).also { it.onCommit(1) }.classify(10, 10, 20, 20))
    }

    @Test
    fun `sovelluksen hylkaama merkki ei tee seuraavasta kirjoituksesta ulkoista`() {
        val cursor = at(9)
        cursor.onCommit(1)
        cursor.onCommit(1)
        assertEquals(Update.OWN, cursor.classify(9, 9, 10, 10))
    }

    @Test
    fun `poisto siirtaa odotusta`() {
        val cursor = at(10)
        cursor.onDeleteBefore(4)
        assertEquals(Update.OWN, cursor.classify(10, 10, 6, 6))
    }

    @Test
    fun `poisto ei vie sijaintia negatiiviseksi`() {
        val cursor = at(2)
        cursor.onDeleteBefore(5)
        assertEquals(0, cursor.start)
    }

    @Test
    fun `kirjoitus korvaa valinnan`() {
        val cursor = CursorExpectation()
        cursor.begin(4, 9, 4, lookback)
        cursor.onCommit(2)
        assertEquals(6, cursor.start)
        assertEquals(6, cursor.end)
    }

    @Test
    fun `nappaintapahtuman jalkeen sijainti on tuntematon kunnes paivitys kertoo sen`() {
        val cursor = at(10)
        cursor.onUnknownEdit()
        assertFalse(cursor.known)
        assertEquals(Update.UNKNOWN, cursor.classify(10, 10, 9, 9))
        assertTrue(cursor.known)
        assertEquals(9, cursor.start)
        assertEquals(Update.EXTERNAL, cursor.classify(9, 9, 2, 2))
    }

    @Test
    fun `tuntematon sijainti pysyy tuntemattomana omissa muokkauksissa`() {
        val cursor = at(10)
        cursor.onUnknownEdit()
        cursor.onCommit(3)
        cursor.onDeleteBefore(1)
        assertFalse(cursor.known)
    }

    @Test
    fun `oma valinta tunnistetaan`() {
        val cursor = at(10)
        cursor.onSetSelection(4, 10)
        assertEquals(Update.OWN, cursor.classify(10, 10, 4, 10))
    }

    @Test
    fun `aloitus uskoo kenttaa kun teksti on odotettua lyhyempi`() {
        val cursor = CursorExpectation()
        cursor.begin(10, 10, 5, lookback)
        assertEquals(5, cursor.start)
        assertEquals(5, cursor.end)
    }

    @Test
    fun `aloitus korjaa puuttuvan sijainnin`() {
        val cursor = CursorExpectation()
        cursor.begin(-1, -1, 7, lookback)
        assertEquals(7, cursor.start)
        assertEquals(7, cursor.end)
    }

    @Test
    fun `aloitus sailyttaa valinnan lopun kun se on yha oikein`() {
        val cursor = CursorExpectation()
        cursor.begin(10, 14, 10, lookback)
        assertEquals(10, cursor.start)
        assertEquals(14, cursor.end)
    }

    @Test
    fun `aloitus ei paattele sijaintia katkaistusta tekstista`() {
        val cursor = CursorExpectation()
        cursor.begin(5000, 5000, lookback, lookback)
        assertEquals(5000, cursor.start)
    }

    @Test
    fun `aloitus ilman luettua tekstia jattaa sijainnin tuntemattomaksi`() {
        val cursor = CursorExpectation()
        cursor.begin(10, 10, null, lookback)
        assertFalse(cursor.known)
    }
}
