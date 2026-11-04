package org.jarsi.ark.engine

import java.io.File
import java.util.Locale
import org.jarsi.ark.data.BackupCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/**
 * Ennustusmittari omistajan omalla sanastolla: ajaa oikeat moottoriluokat
 * varmuuskopiolla ja sanalistalla ja laskee, monennellako kirjaimella
 * oikea sana nousee kolmen näkyvän ehdotuksen joukkoon. Kaksi joukkoa:
 * opitut sanaparit (konteksti tunnetaan) ja kylmä joukko ilman kontekstia
 * (omat sanat, joiden paria ei tässä kohdassa tunneta). Luvut lasketaan
 * sekä moottorin raakajärjestyksestä (säätöä varten) että rivin listasta
 * (applyCase + kirjoitettu sana kärkeen), joka vastaa näkyvää riviä.
 * Ajetaan vain, kun ympäristömuuttuja ARK_BACKUP osoittaa
 * varmuuskopiotiedostoon; muuten testit ohitetaan. Raportti:
 * build/reports/prediction-benchmark.txt.
 */
class PredictionBenchmarkTest {

    @Before
    fun vaatiiVarmuuskopion() {
        assumeTrue("ARK_BACKUP puuttuu", System.getenv("ARK_BACKUP") != null)
    }

    @Test
    fun `j antaa ja ensimmaisena`() =
        assertEquals("ja", engine.suggest("j").first().lowercase())

    @Test
    fun `o antaa on ensimmaisena`() =
        assertEquals("on", engine.suggest("o").first().lowercase())

    @Test
    fun `s antaa se ensimmaisena`() =
        assertEquals("se", engine.suggest("s").first().lowercase())

    @Test
    fun `mittari kirjoittaa raportin`() {
        println(report)
        assertTrue(File(REPORT_PATH).exists())
    }

    private class Case(val context: List<String>, val next: String, val weight: Int)

    /** Yhden listan (raaka tai rivi) tulokset. */
    private class Hits {
        var weightSum = 0L
        var lettersTop3 = 0.0
        var lettersTop1 = 0.0
        var top3ByTwo = 0L
        var saved = 0.0

        fun add(weight: Int, length: Int, firstTop3: Int, firstTop1: Int) {
            weightSum += weight
            lettersTop3 += weight * firstTop3
            lettersTop1 += weight * firstTop1
            if (firstTop3 <= 2) top3ByTwo += weight
            saved += weight * (length - firstTop3).toDouble() / length
        }

        fun lines(suffix: String) = listOf(
            "kirjaimia_top3$suffix=${"%.3f".format(Locale.ROOT, lettersTop3 / weightSum)}",
            "kirjaimia_top1$suffix=${"%.3f".format(Locale.ROOT, lettersTop1 / weightSum)}",
            "osuus_top3_viimeistaan_2$suffix=${"%.1f".format(Locale.ROOT, 100.0 * top3ByTwo / weightSum)}",
            "saasto$suffix=${"%.1f".format(Locale.ROOT, 100.0 * saved / weightSum)}",
        )
    }

    private class Loaded(
        val engine: SuggestionEngine,
        val cases: List<Case>,
        val coldCases: List<Case>,
        val junk: Set<String>,
    )

    private companion object {
        const val REPORT_PATH = "build/reports/prediction-benchmark.txt"
        const val TOP = 3

        val loaded by lazy { load() }
        val engine: SuggestionEngine get() = loaded.engine
        val report by lazy { measure() }

        fun load(): Loaded {
            val json = File(System.getenv("ARK_BACKUP")).readText(Charsets.UTF_8)
            val backup = BackupCodec.decode(json)
            val words = backup.words.map {
                LearnedWord(
                    it.word, it.count, it.lastUsed, it.blocked, it.created,
                    it.acceptedCount, it.ignoredCount, it.pinned,
                )
            }
            val bigrams = backup.bigrams.map { LearnedBigram(it.previous, it.next, it.count, it.lastUsed) }
            val trigrams = backup.trigrams.map {
                LearnedTrigram(it.first, it.second, it.next, it.count, it.lastUsed)
            }
            // Kello lukitaan varmuuskopion tuoreimpaan hetkeen, jotta
            // tuoreuskertoimet ovat samat joka ajolla.
            val clock = maxOf(
                words.maxOfOrNull { it.lastUsed } ?: 0L,
                bigrams.maxOfOrNull { it.lastUsed } ?: 0L,
            )
            val learning = LearningEngine { clock }
            learning.load(words, bigrams, trigrams)
            val dictionary = DictionaryEngine()
            val list = listOf("src/main/assets/sanalista.txt", "app/src/main/assets/sanalista.txt")
                .map(::File)
                .first { it.exists() }
            list.bufferedReader(Charsets.UTF_8).useLines { dictionary.load(it) }
            val engine = SuggestionEngine(dictionary, learning)
            val junk = words
                .filter { it.count <= 1 && it.ignoredCount >= 10 }
                .mapTo(HashSet()) { it.word.lowercase() }
            val cases = bigrams
                .filter { b ->
                    b.count >= 2 && b.next.length >= 4 &&
                        b.next.all { it.isLetter() || it == '-' } &&
                        learning.isOwnWord(b.next)
                }
                .map { Case(listOf(it.previous), it.next, it.count) }
            // Kylmä joukko: omat sanat ilman kontekstia, eli tilanne jossa
            // paria ei ole opittu (89 % omistajan pareista on nähty vain kerran).
            val coldCases = words
                .filter { w ->
                    !w.blocked && w.count >= 3 && w.word.length >= 6 &&
                        w.word.all { it.isLetter() || it == '-' }
                }
                .map { Case(emptyList(), it.word.lowercase(), it.count) }
            return Loaded(engine, cases, coldCases, junk)
        }

        fun measure(): String {
            val lines = measure(loaded.cases, "") + measure(loaded.coldCases, "_kylma")
            val text = lines.joinToString("\n")
            File(REPORT_PATH).apply { parentFile?.mkdirs() }.writeText(text + "\n", Charsets.UTF_8)
            return text
        }

        /** Rivin lista kuten KeyboardService sen muodostaa: kirjainkoko ja kirjoitettu sana kärkeen. */
        fun rowList(engine: SuggestionEngine, prefix: String, raw: List<String>): List<String> =
            WordTools.withTypedWord(prefix, engine.applyCase(raw, prefix, sentenceStart = false))

        fun measure(cases: List<Case>, suffix: String): List<String> {
            val engine = loaded.engine
            val raw = Hits()
            val row = Hits()
            var queries = 0L
            var junkQueries = 0L
            var junkHits = 0L
            val started = System.nanoTime()
            for (case in cases) {
                val target = case.next
                val length = target.length
                var rawTop3 = length
                var rawTop1 = length
                var rowTop3 = length
                var rowTop1 = length
                for (p in 1 until length) {
                    val prefix = target.take(p)
                    val result = engine.suggest(prefix, case.context)
                    queries++
                    val visibleRaw = result.take(TOP).map { it.lowercase() }
                    val visibleRow = rowList(engine, prefix, result).take(TOP).map { it.lowercase() }
                    // Roska lasketaan raa'an top3-osumaan asti ja samasta
                    // kyselyjoukosta kuin sen nimittäjä.
                    if (rawTop3 == length) {
                        junkQueries++
                        junkHits += visibleRaw.count { it in loaded.junk }
                    }
                    if (rawTop1 == length && visibleRaw.firstOrNull() == target) rawTop1 = p
                    if (rawTop3 == length && target in visibleRaw) rawTop3 = p
                    if (rowTop1 == length && visibleRow.firstOrNull() == target) rowTop1 = p
                    if (rowTop3 == length && target in visibleRow) rowTop3 = p
                    if (rawTop1 < length && rowTop1 < length) break
                }
                raw.add(case.weight, length, rawTop3, rawTop1)
                row.add(case.weight, length, rowTop3, rowTop1)
            }
            val elapsedMs = (System.nanoTime() - started) / 1_000_000.0
            return listOf("tapauksia$suffix=${cases.size}") +
                raw.lines(suffix) +
                row.lines("_rivi$suffix") +
                listOf(
                    "roska_per_1000_kyselya$suffix=${"%.2f".format(Locale.ROOT, 1000.0 * junkHits / junkQueries)}",
                    "kyselyja$suffix=$queries",
                    "ms_per_kysely$suffix=${"%.3f".format(Locale.ROOT, elapsedMs / queries)}",
                )
        }
    }
}
