package org.jarsi.ark.clipboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RecentClipOfferTest {

    private var now = 1000L
    private val offer = RecentClipOffer { now }

    @Test
    fun `tuore kopio tarjotaan`() {
        offer.offer(7L, "teksti")
        assertEquals(7L, offer.current()?.clipId)
        assertEquals("teksti", offer.current()?.text)
    }

    @Test
    fun `tarjous vanhenee minuutissa`() {
        offer.offer(7L, "teksti")
        now += RecentClipOffer.OFFER_MS - 1
        assertEquals(7L, offer.current()?.clipId)
        now += 1
        assertNull(offer.current())
    }

    @Test
    fun `uusi kopio korvaa vanhan ja aloittaa ajan alusta`() {
        offer.offer(7L, "vanha")
        now += RecentClipOffer.OFFER_MS - 1
        offer.offer(8L, "uusi")
        now += RecentClipOffer.OFFER_MS - 1
        assertEquals("uusi", offer.current()?.text)
    }

    @Test
    fun `liitetty tarjous poistuu`() {
        offer.offer(7L, "teksti")
        offer.clear()
        assertNull(offer.current())
    }

    @Test
    fun `poistettu leike poistaa tarjouksen`() {
        offer.offer(7L, "teksti")
        assertEquals(true, offer.clearIfClip(7L))
        assertNull(offer.current())
    }

    @Test
    fun `toisen leikkeen poisto ei koske tarjousta`() {
        offer.offer(7L, "teksti")
        assertEquals(false, offer.clearIfClip(8L))
        assertEquals(7L, offer.current()?.clipId)
    }

    @Test
    fun `esikatselu litistaa rivit ja valit`() {
        assertEquals("yksi kaksi kolme", RecentClipOffer.preview("  yksi\n\nkaksi \t kolme "))
    }

    @Test
    fun `esikatselu lyhentaa pitkan tekstin`() {
        val preview = RecentClipOffer.preview("a".repeat(100))
        assertEquals(RecentClipOffer.PREVIEW_CHARS + 1, preview.length)
        assertEquals('…', preview.last())
    }

    @Test
    fun `esikatselu ei katkaise emojia kahtia`() {
        val text = "a".repeat(RecentClipOffer.PREVIEW_CHARS - 1) + "😀😀"
        assertEquals("a".repeat(RecentClipOffer.PREVIEW_CHARS - 1) + "…", RecentClipOffer.preview(text))
    }
}
