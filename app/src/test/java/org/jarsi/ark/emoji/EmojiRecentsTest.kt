package org.jarsi.ark.emoji

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmojiRecentsTest {

    @Test
    fun `tuorein ensin ilman kaksoiskappaleita`() {
        val recents = EmojiRecents()
        recents.use("😀")
        recents.use("🐈")
        recents.use("😀")
        assertEquals(listOf("😀", "🐈"), recents.recent)
    }

    @Test
    fun `lista on rajattu`() {
        val recents = EmojiRecents()
        (0 until EmojiRecents.MAX_RECENT + 5).forEach { recents.use("e$it") }
        assertEquals(EmojiRecents.MAX_RECENT, recents.recent.size)
        assertEquals("e${EmojiRecents.MAX_RECENT + 4}", recents.recent.first())
    }

    @Test
    fun `tila sailyy tallennuksen yli`() {
        val recents = EmojiRecents()
        recents.use("😀")
        recents.use("👋🏻")
        recents.rememberVariant("👋", "👋🏻")
        val restored = EmojiRecents(recents.savedRecents(), recents.savedVariants())
        assertEquals(listOf("👋🏻", "😀"), restored.recent)
        assertEquals("👋🏻", restored.variantFor("👋"))
    }

    @Test
    fun `ilman muistettua varia kaytetaan perusemojia`() {
        assertEquals("👋", EmojiRecents().variantFor("👋"))
    }

    @Test
    fun `perusvarin valinta unohtaa aiemman`() {
        val recents = EmojiRecents()
        recents.rememberVariant("👋", "👋🏻")
        recents.rememberVariant("👋", "👋")
        assertEquals("👋", recents.variantFor("👋"))
        assertTrue(recents.savedVariants().isEmpty())
    }

    @Test
    fun `muutokset nakyvat versionumerossa`() {
        val recents = EmojiRecents()
        val start = recents.revision
        recents.use("😀")
        val afterUse = recents.revision
        assertTrue(afterUse != start)
        recents.rememberVariant("👋", "👋🏻")
        assertTrue(recents.revision != afterUse)
    }

    @Test
    fun `tallenne vastaa muistia vasta kun myos ihonvarit tasmaavat`() {
        val recents = EmojiRecents("😀", "👋\t👋🏻")
        assertTrue(recents.matches("😀", "👋\t👋🏻"))
        assertFalse(recents.matches("😀", "👋\t👋🏿"))
        assertFalse(recents.matches("🐈", "👋\t👋🏻"))
    }

    @Test
    fun `puuttuva tallenne vastaa tyhjaa muistia`() {
        assertTrue(EmojiRecents().matches(null, null))
        assertFalse(EmojiRecents("😀").matches(null, null))
    }

    @Test
    fun `vanhan valitsimen historia siirtyy`() {
        val recents = EmojiRecents.fromLegacy("😀,🐈,,😀")
        assertEquals(listOf("😀", "🐈"), recents.recent)
    }

    @Test
    fun `rikkinainen tallenne ei kaada`() {
        val recents = EmojiRecents("\n\n😀\n", "ilman sarkainta\n👋\t👋🏻\n")
        assertEquals(listOf("😀"), recents.recent)
        assertEquals("👋🏻", recents.variantFor("👋"))
    }
}
