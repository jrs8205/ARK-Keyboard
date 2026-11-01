package org.jarsi.ark.emoji

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EmojiIndexTest {

    private val lines = listOf(
        "0\t😀\t\tleveä hymy\thymy|kasvot|naama|virnistys",
        "0\t🙂\t\thieman hymyilevä\thymy|kasvot",
        "0\t😺\t\thymyilevä kissa\tkissa|kasvot",
        "0\t💘\t\tsydän ja nuoli\tsydän|rakkaus",
        "0\t❤️\t\tpunainen sydän\tsydän|rakkaus",
        "1\t👋\t👋🏻,👋🏿\tkäden heilautus\theilautus|käsi",
        "2\t🐈\t\tkissa\tlemmikki|eläin",
        "4\t🚗\t\tauto\tajoneuvo",
        "4\t🚕\t\ttaksi\tajoneuvo|auto",
        "8\t🇫🇮\t\tlippu: Suomi\tlippu|suomi",
    )

    private fun index(supported: (String) -> Boolean = { true }) =
        EmojiIndex.parse(lines.asSequence(), supported)

    private fun EmojiIndex.emojiFor(query: String) = search(query).map { it.emoji }

    @Test
    fun `kategoriat sailyttavat tiedoston jarjestyksen`() {
        assertEquals(listOf("😀", "🙂", "😺", "💘", "❤️"), index().category(0).map { it.emoji })
        assertEquals(listOf("🚗", "🚕"), index().category(4).map { it.emoji })
        assertTrue(index().category(5).isEmpty())
    }

    @Test
    fun `tuetut emojit suodatetaan myos ihonvareista`() {
        val filtered = index { it != "😀" && it != "👋🏿" }
        assertTrue(filtered.category(0).none { it.emoji == "😀" })
        assertEquals(listOf("👋🏻"), filtered.category(1).single().variants)
    }

    @Test
    fun `rikkinainen rivi ohitetaan`() {
        val parsed = EmojiIndex.parse(sequenceOf("roskaa", "x\t😀\t\tnimi\t", "0\t😀\t\tnimi\t")) { true }
        assertEquals(1, parsed.category(0).size)
    }

    @Test
    fun `haku loytaa nimen ja avainsanan`() {
        assertEquals("🐈", index().emojiFor("kissa").first())
        assertTrue("😺" in index().emojiFor("kissa"))
        assertTrue("🐈" in index().emojiFor("lemmikki"))
    }

    @Test
    fun `tarkka nimi tulee ennen avainsanaa ja alkua`() {
        val results = index().emojiFor("auto")
        assertEquals(listOf("🚗", "🚕"), results)
    }

    @Test
    fun `haku toimii sanan alulla ja kirjainkoosta riippumatta`() {
        assertTrue("🐈" in index().emojiFor("KIS"))
        assertTrue("👋" in index().emojiFor("heilau"))
    }

    @Test
    fun `usea hakusana rajaa tuloksia`() {
        assertEquals(listOf("❤️"), index().emojiFor("punainen sydän"))
    }

    @Test
    fun `sydanhaku antaa ensin punaisen sydamen`() {
        assertEquals("❤️", index().emojiFor("sydän").first())
    }

    @Test
    fun `lippu loytyy maan nimella`() {
        assertEquals(listOf("🇫🇮"), index().emojiFor("suomi"))
    }

    @Test
    fun `tyhja haku ei palauta mitaan`() {
        assertTrue(index().emojiFor("  ").isEmpty())
    }

    @Test
    fun `ehdotus tarkalla sanalla`() {
        assertEquals("🚗", index().suggest("auto").first().emoji)
    }

    @Test
    fun `ehdotus ottaa huomioon taivutuksen`() {
        assertEquals("🐈", index().suggest("kissan").first().emoji)
        assertEquals("🚗", index().suggest("autossa").first().emoji)
    }

    @Test
    fun `ehdotus ei arvaa pitkasta sanasta`() {
        assertTrue(index().suggest("autoilija").isEmpty())
    }

    @Test
    fun `lyhyt sana ei ehdota`() {
        assertTrue(index().suggest("ai").isEmpty())
    }

    @Test
    fun `isolla alkava sana ehdottaa`() {
        assertEquals("🐈", index().suggest("Kissa").first().emoji)
    }

    @Test
    fun `sydan ehdottaa ensin punaista sydanta`() {
        assertEquals("❤️", index().suggest("sydän").first().emoji)
    }

    @Test
    fun `kaytetty emoji nousee kartkeen`() {
        val results = index().suggest("kissa", used = setOf("😺"))
        assertEquals("😺", results.first().emoji)
    }

    @Test
    fun `liian yleinen avainsana ei ehdota ilman tarkkaa nimea`() {
        val broad = (1..13).map { "0\tx$it\t\tnimi$it\tyleinen" }
        val parsed = EmojiIndex.parse(broad.asSequence()) { true }
        assertTrue(parsed.suggest("yleinen").isEmpty())
    }

    @Test
    fun `emoji loytyy myos ihonvarilla`() {
        assertEquals("👋", index().entryFor("👋🏻")?.emoji)
        assertEquals("👋", index().entryFor("👋")?.emoji)
        assertNull(index().entryFor("x"))
    }

    @Test
    fun `lisays erottaa emojin edeltavasta sanasta`() {
        assertEquals(" ❤️ ", EmojiIndex.insertion("sydän", "❤️"))
        assertEquals("❤️ ", EmojiIndex.insertion("sydän ", "❤️"))
        assertEquals("❤️ ", EmojiIndex.insertion("", "❤️"))
        assertEquals("❤️ ", EmojiIndex.insertion("rivi\n", "❤️"))
    }
}
