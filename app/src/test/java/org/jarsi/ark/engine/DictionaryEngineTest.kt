package org.jarsi.ark.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DictionaryEngineTest {

    private fun engine(vararg lines: String) =
        DictionaryEngine().apply { load(lines.asSequence()) }

    @Test
    fun `aakkosittain ladattu ja sekaisin ladattu lista toimivat samoin`() {
        val sorted = engine("auto 5", "koira 9", "kissa 3".let { "koti 3" })
        val mixed = engine("koti 3", "auto 5", "koira 9")
        for (e in listOf(sorted, mixed)) {
            assertEquals(listOf("koira", "koti"), e.suggest("ko"))
            assertEquals(9L, e.frequencyOf("KOIRA"))
            assertEquals(0L, e.frequencyOf("koir"))
            assertEquals(9L, e.maxFrequency())
            assertEquals(listOf("koira", "auto", "koti"), e.topWords())
        }
    }

    @Test
    fun `near loytaa vaihdetut merkit ja kaksi muokkausta`() {
        val e = engine("koira 100", "tuli 50", "kassi 40")
        assertEquals(listOf("koira"), e.near("koria", 1))
        assertEquals(listOf("tuli"), e.near("talo", 2))
        assertEquals(listOf("kassi"), e.near("kissa", 2))
    }

    @Test
    fun `near ei loyda kauempaa kuin raja`() {
        val e = engine("koira 100")
        assertEquals(emptyList<String>(), e.near("kuura", 1))
    }

    @Test
    fun `aakkoset sailyvat tiiviissa muodossa`() {
        val e = engine("äiti 10", "öljy 5", "åbo 3", "yö-työ 2")
        assertEquals(listOf("äiti"), e.suggest("äi"))
        assertEquals(listOf("öljy"), e.near("öljä", 1))
        assertEquals(listOf("åbo"), e.suggest("å"))
        assertEquals(listOf("yö-työ"), e.suggest("yö"))
    }

    @Test
    fun `listan ulkopuoliset merkit eivat kaada hakua`() {
        val e = engine("привет 5", "auto 3")
        assertEquals(listOf("auto"), e.suggest("a"))
        assertEquals(emptyList<String>(), e.suggest("при"))
        assertEquals(0L, e.frequencyOf("привет"))
        assertEquals(emptyList<String>(), e.near("привет", 1))
    }

    @Test
    fun `uusi lataus korvaa vanhan`() {
        val e = engine("auto 3")
        e.load(sequenceOf("talo 4"))
        assertEquals(emptyList<String>(), e.suggest("au"))
        assertEquals(listOf("talo"), e.suggest("ta"))
    }

    @Test
    fun `etuliitehaku palauttaa sanat yleisyysjarjestyksessa`() {
        val e = engine("sovellus 50", "sovelluksen 30", "sovi 80", "auto 100")
        assertEquals(listOf("sovi", "sovellus", "sovelluksen"), e.suggest("sov"))
    }

    @Test
    fun `near loytaa laheiset lahin ja yleisin ensin`() {
        val e = engine("koira 100", "koiran 50", "kissa 80", "kaira 200")
        assertEquals(listOf("koira", "kaira", "koiran"), e.near("koirra", 2))
    }

    @Test
    fun `near ei palauta sanaa itseaan`() {
        val e = engine("koira 100", "koiran 50")
        assertEquals(listOf("koiran"), e.near("Koira", 2))
    }

    @Test
    fun `haku toimii aakkosilla`() {
        val e = engine("äiti 10", "äijä 5", "auto 100")
        assertEquals(listOf("äiti", "äijä"), e.suggest("äi"))
    }

    @Test
    fun `iso alkukirjain loytaa pienella tallennetun`() {
        val e = engine("helsinki 10")
        assertEquals(listOf("helsinki"), e.suggest("Hel"))
    }

    @Test
    fun `max rajaa tulosten maaran`() {
        val e = engine("aa 1", "ab 2", "ac 3", "ad 4")
        assertEquals(listOf("ad", "ac"), e.suggest("a", max = 2))
    }

    @Test
    fun `tyhja etuliite palauttaa tyhjan`() {
        assertEquals(emptyList<String>(), engine("auto 1").suggest(""))
    }

    @Test
    fun `topWords antaa yleisimmat`() {
        val e = engine("aa 1", "bb 3", "cc 2")
        assertEquals(listOf("bb", "cc", "aa"), e.topWords())
    }

    @Test
    fun `virheelliset rivit ohitetaan`() {
        val e = engine("auto 10", "rikki", " 5", "hyvä x")
        assertTrue(e.isLoaded)
        assertEquals(listOf("auto"), e.suggest("a"))
    }

    @Test
    fun `lataamaton moottori palauttaa tyhjan`() {
        val e = DictionaryEngine()
        assertEquals(emptyList<String>(), e.suggest("a"))
        assertEquals(emptyList<String>(), e.topWords())
    }
}
