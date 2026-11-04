package org.jarsi.ark.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SuggestionEngineTest {

    private var now = 1_000_000_000_000L

    @Test
    fun `kesken lauseen lauseen alussa opittu iso alkukirjain pienenee`() {
        val (s, l) = build("kiitos 100")
        l.onWordCommitted("Kiitos")
        val shown = s.applyCase(s.suggest("ki"), typed = "ki", sentenceStart = false)
        assertEquals(listOf("kiitos"), shown)
    }

    @Test
    fun `kesken lauseen oma nimi odottaa isoa alkukirjainta`() {
        val (s, l) = build("kiitos 100")
        l.onWordCommitted("Jarkko")
        assertEquals(emptyList<String>(), s.applyCase(s.suggest("ja"), "ja", sentenceStart = false))
        assertEquals(listOf("Jarkko"), s.applyCase(s.suggest("Ja"), "Ja", sentenceStart = false))
    }

    @Test
    fun `pisteen jalkeen ehdotukset alkavat isolla`() {
        val (s, _) = build("ja 100", "on 90")
        val shown = s.applyCase(s.emptyInput(emptyList(), includeCommon = true), "", sentenceStart = true)
        assertEquals(listOf("Ja", "On"), shown)
    }

    @Test
    fun `kesken lauseen seuraavat sanat ovat pienella`() {
        val (s, l) = build("kiitos 100", "ja 90")
        listOf("hei", "Kiitos").forEach { l.onWordCommitted(it) }
        val shown = s.applyCase(s.emptyInput(listOf("hei"), includeCommon = false), "", sentenceStart = false)
        assertEquals(listOf("kiitos"), shown)
    }

    @Test
    fun `pienella aloitettu sana pysyy pienena myos lauseen alussa`() {
        val (s, _) = build("kiitos 100")
        assertEquals(listOf("kiitos"), s.applyCase(s.suggest("ki"), "ki", sentenceStart = true))
    }

    @Test
    fun `isolla aloitettu sana saa ison alkukirjaimen kesken lauseen`() {
        val (s, _) = build("helsinki 100")
        assertEquals(listOf("Helsinki"), s.applyCase(s.suggest("Hel"), "Hel", sentenceStart = false))
    }

    @Test
    fun `korjaus ei tuo opittua isoa alkukirjainta tavalliseen sanaan`() {
        val (s, _) = build("kiitos 100")
        assertEquals("kiitos", s.correctionForm("Kiitos"))
        assertEquals("Jarkko", s.correctionForm("Jarkko"))
        assertEquals("kiitos", s.correctionForm("kiitos"))
    }

    private fun build(vararg dictLines: String): Pair<SuggestionEngine, LearningEngine> {
        val dict = DictionaryEngine().apply { load(dictLines.asSequence()) }
        val learning = LearningEngine { now }.apply { load(emptyList(), emptyList(), emptyList()) }
        return SuggestionEngine(dict, learning) to learning
    }

    @Test
    fun `alternatives ehdottaa laheiset sanat lahin ensin`() {
        val (s, l) = build("koira 100", "koiran 50", "kissa 80")
        l.onWordCommitted("koirra")
        val result = s.alternatives("koirra")
        assertEquals("koira", result.first())
        assertTrue("koiran" in result)
        assertTrue("kissa" !in result)
        assertTrue("koirra" !in result)
    }

    @Test
    fun `alternatives ei ehdota estettya`() {
        val (s, l) = build("talo 100", "tila 90")
        l.blockWord("tila")
        assertTrue("tila" !in s.alternatives("tilat"))
        assertEquals(listOf("talo"), s.alternatives("talto"))
    }

    @Test
    fun `alternatives lyhyt sana sallii vain yhden muokkauksen kirjoitusvirheena`() {
        val (s, _) = build("talo 100")
        assertTrue("talo" in s.alternatives("tilo"))
    }

    @Test
    fun `lyhyt sana saa yleiset sanat vaihtoehdoiksi`() {
        val (s, _) = build("ja 100", "on 90", "tai 80")
        val result = s.alternatives("ei")
        assertTrue("ja" in result)
        assertTrue("on" in result)
        assertTrue("tai" in result)
        assertFalse("ei" in result)
    }

    @Test
    fun `pitkalle sanalle ei tarjota yleisia sanoja`() {
        val (s, _) = build("ja 100", "koira 90")
        assertEquals(listOf("koira"), s.alternatives("koirra"))
    }

    @Test
    fun `laheinen sana voittaa usein kaytetyt pikkusanat`() {
        val (s, l) = build(
            "ja 552162", "on 428026", "se 89469", "nimi 2486", "nimen 1024",
        )
        // Käyttäjä on kirjoittanut pikkusanoja paljon: niiden omat signaalit
        // eivät saa jyrätä kirjoitusasultaan läheisiä sanoja.
        repeat(30) {
            listOf("ja", "on", "se").forEach {
                l.onWordCommitted(it)
                l.resetContext()
            }
        }
        val result = s.alternatives("nime")
        assertEquals("nimi", result[0])
        assertEquals("nimen", result[1])
    }

    @Test
    fun `englannin sana ei muutu suomeksi`() {
        val (s, _) = build("se 100000", "she 1", "wait 1", "lait 300")
        assertEquals(null, s.autoCorrect("she"))
        assertEquals(null, s.autoCorrect("wait"))
    }

    @Test
    fun `suomen kirjoitusvirhe ei muutu englanniksi`() {
        val (s, _) = build("meeting 1", "koira 100")
        assertEquals(null, s.autoCorrect("meetin"))
        assertEquals("koira", s.autoCorrect("koirra"))
    }

    @Test
    fun `itse kaytetty englannin sana kelpaa korjaukseksi`() {
        val (s, l) = build("meeting 1")
        l.onWordCommitted("meeting")
        assertEquals("meeting", s.autoCorrect("meetin"))
    }

    @Test
    fun `englannin sana on ehdotuksissa suomen sanojen jalkeen`() {
        val (s, _) = build("meeting 1", "meetingit 50")
        assertEquals(listOf("meetingit", "meeting"), s.suggest("meet"))
    }

    @Test
    fun `autoCorrect korjaa tuntemattoman lahimpaan tunnettuun`() {
        val (s, _) = build("koira 100", "kissa 90")
        assertEquals("koira", s.autoCorrect("koirra"))
    }

    @Test
    fun `autoCorrect ei koske tunnettua eika omaa sanaa`() {
        val (s, l) = build("koira 100")
        assertEquals(null, s.autoCorrect("koira"))
        l.onWordCommitted("chrome")
        assertEquals(null, s.autoCorrect("chrome"))
        l.onWordCommitted("morjes")
        l.blockWord("morjes")
        assertEquals(null, s.autoCorrect("morjes"))
    }

    @Test
    fun `autoCorrect ei koske koodeja lyhyita eika kaukaisia`() {
        val (s, _) = build("koira 100", "prx5 90")
        assertEquals(null, s.autoCorrect("prx4"))
        assertEquals(null, s.autoCorrect("ab"))
        assertEquals(null, s.autoCorrect("zzzz"))
    }

    @Test
    fun `alternatives taydentaa lyhyen sanan kontekstista`() {
        val (s, l) = build("on 100", "ei 90")
        listOf("koira", "on", "kiva").forEach { l.onWordCommitted(it) }
        l.resetContext()
        assertEquals(listOf("on"), s.alternatives("ei", listOf("Koira")))
    }

    @Test
    fun `alternatives yhdistaa kirjoitusvirheen ja kontekstin`() {
        val (s, l) = build("koira 100", "kissa 90")
        listOf("se", "kissa", "naukuu").forEach { l.onWordCommitted(it) }
        l.resetContext()
        val result = s.alternatives("koirra", listOf("se"))
        assertTrue("koira" in result)
        assertTrue("kissa" in result)
    }

    @Test
    fun `kahdesti kirjoitettu oma sana nousee yleissanan edelle`() {
        val (s, l) = build("auto 100", "autio 50")
        repeat(2) { l.onWordCommitted("autotalli") }
        assertEquals(listOf("autotalli", "auto", "autio"), s.suggest("au"))
    }

    @Test
    fun `kerran kirjoitettu sana on ehdolla mutta ei ohita yleissanaa`() {
        val (s, l) = build("auto 100", "autio 50")
        l.onWordCommitted("autotalli")
        val result = s.suggest("au")
        assertEquals("auto", result.first())
        assertTrue("autotalli" in result)
    }

    @Test
    fun `sama sana vain kerran ja omalla asulla`() {
        val (s, l) = build("jako 100")
        l.onWordCommitted("Jako")
        assertEquals(listOf("Jako"), s.suggest("ja"))
    }

    @Test
    fun `estetty yleissana suodattuu`() {
        val (s, l) = build("auto 100", "autio 50")
        l.blockWord("auto")
        assertEquals(listOf("autio"), s.suggest("au"))
        assertEquals(listOf("autio"), s.emptyInput(emptyList(), includeCommon = true))
    }

    @Test
    fun `tyhjalla etuliitteella tyhja`() {
        val (s, _) = build("auto 100")
        assertTrue(s.suggest("").isEmpty())
    }

    @Test
    fun `max rajaa kokonaismaaran`() {
        val (s, l) = build("aa 5", "ab 4", "ac 3", "ad 2")
        l.onWordCommitted("aatto")
        assertEquals(3, s.suggest("a", max = 3).size)
    }

    @Test
    fun `ennustus nousee karkeen tyhjalla syotteella`() {
        val (s, l) = build("ja 100", "on 90")
        listOf("prx4", "Jako").forEach { l.onWordCommitted(it) }
        assertEquals(listOf("Jako", "ja", "on"), s.emptyInput(listOf("prx4"), includeCommon = true))
    }

    @Test
    fun `ilman yleisia vain ennustukset`() {
        val (s, l) = build("ja 100")
        listOf("prx4", "Jako").forEach { l.onWordCommitted(it) }
        assertEquals(listOf("Jako"), s.emptyInput(listOf("prx4"), includeCommon = false))
    }

    @Test
    fun `etuliite suodattaa ennustukset karkeen`() {
        val (s, l) = build("nopea 100")
        listOf("Jako", "20", "nouto").forEach { l.onWordCommitted(it) }
        assertEquals("nouto", s.suggest("no", listOf("Jako", "20")).first())
    }

    @Test
    fun `kiinnitetty voittaa kaiken`() {
        val (s, l) = build("auto 100", "autio 50")
        l.setPinned("autio", true)
        assertEquals("autio", s.suggest("au").first())
    }

    @Test
    fun `hyvaksytty yleissana ohittaa yleisemman`() {
        val (s, l) = build("auto 100", "autio 50")
        l.onSuggestionAccepted("autio")
        l.onSuggestionAccepted("autio")
        assertEquals(listOf("autio", "auto"), s.suggest("au"))
    }

    @Test
    fun `toistuvat ohitukset pudottavat`() {
        val (s, l) = build("auto 100", "autio 50")
        l.onSuggestionsIgnored(listOf("auto"), "muu")
        l.onSuggestionsIgnored(listOf("auto"), "muu")
        assertEquals(listOf("autio", "auto"), s.suggest("au"))
    }

    @Test
    fun `kiinnitysbonus rajautuu kahteen`() {
        val (s, l) = build("maa 100", "meri 90", "metsa 80", "mies 70")
        l.setPinned("meri", true)
        l.setPinned("metsa", true)
        l.setPinned("mies", true)
        assertEquals(listOf("meri", "metsa", "maa", "mies"), s.suggest("m").take(4))
    }

    @Test
    fun `yksi ohitus ei viela rankaise`() {
        val (s, l) = build("auto 100", "autio 50")
        l.onSuggestionsIgnored(listOf("auto"), "muu")
        assertEquals(listOf("auto", "autio"), s.suggest("au"))
    }

    @Test
    fun `paljon kaytetty sana kestaa ohituksia`() {
        val (s, l) = build("jarsi 10", "jarmo 100")
        repeat(77) { l.onWordCommitted("jarsi") }
        repeat(33) { l.onSuggestionsIgnored(listOf("jarsi"), "muu") }
        assertEquals("jarsi", s.suggest("jar").first())
    }

    @Test
    fun `kerran kirjoitettu ja usein ohitettu putoaa yleissanan alle`() {
        val (s, l) = build("mutta 100", "mutka 50")
        l.onWordCommitted("mutti")
        repeat(10) { l.onSuggestionsIgnored(listOf("mutti"), "mutta") }
        assertEquals(listOf("mutta", "mutka", "mutti"), s.suggest("mut"))
    }

    @Test
    fun `pitka oma sana ohittaa hieman kaytetymman lyhyen oman sanan`() {
        val (s, l) = build("kala 100")
        repeat(4) { l.onWordCommitted("kaupunki") }
        repeat(3) { l.onWordCommitted("kaupunginvaltuustossa") }
        assertEquals(listOf("kaupunginvaltuustossa", "kaupunki"), s.suggest("kaup").take(2))
    }

    @Test
    fun `lyhyt yleinen oma sana pysyy ykkosena`() {
        val (s, l) = build("ja 1000", "jo 900")
        repeat(20) { l.onWordCommitted("ja") }
        repeat(3) { l.onWordCommitted("jotenkin") }
        assertEquals(listOf("ja", "jotenkin"), s.suggest("j").take(2))
    }

    @Test
    fun `tyhja syote ei suosi pitkia sanoja`() {
        val (s, l) = build("kala 100")
        repeat(3) { l.onWordCommitted("hei"); l.onWordCommitted("kaupunginvaltuustossa") }
        l.resetContext()
        repeat(3) { l.onWordCommitted("hei"); l.onWordCommitted("moi") }
        // hei → moi ja hei → kaupunginvaltuustossa ovat tasapelissä; ilman
        // täydennysarvoa kumpikin on kahden kärjessä ja tulos on deterministinen.
        val result = s.emptyInput(listOf("hei"), includeCommon = false)
        assertEquals(setOf("moi", "kaupunginvaltuustossa"), result.take(2).toSet())
        assertEquals(result, s.emptyInput(listOf("hei"), includeCommon = false))
    }

    @Test
    fun `taivutusmuoto nousee perheen voimalla`() {
        val (s, l) = build("sovittu 100")
        repeat(5) { l.onWordCommitted("sovinto") }
        repeat(2) { l.onWordCommitted("sovelluksessa") }
        repeat(10) { l.onWordCommitted("sovellus") }
        val result = s.suggest("sov")
        assertTrue(result.toString(), result.indexOf("sovelluksessa") < result.indexOf("sovinto"))
    }

    @Test
    fun `kaikki omat sanat paasevat ehdolle`() {
        val (s, l) = build("kala 100")
        val shorts = listOf("kaa", "kab", "kac", "kad", "kae", "kaf", "kag", "kah", "kai", "kaj", "kak")
        repeat(3) { shorts.forEach { l.onWordCommitted(it) } }
        repeat(2) { l.onWordCommitted("kaupungissa") }
        assertTrue("kaupungissa" in s.suggest("ka", max = 13))
    }
}
