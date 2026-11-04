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
 * (omat sanat, joiden paria ei tässä kohdassa tunneta). Ajetaan vain, kun
 * ympäristömuuttuja ARK_BACKUP osoittaa varmuuskopiotiedostoon; muuten
 * testit ohitetaan. Raportti: build/reports/prediction-benchmark.txt.
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

    private class Metrics(
        val cases: Int,
        val lettersTop3: Double,
        val lettersTop1: Double,
        val top3ByTwo: Double,
        val saved: Double,
        val junkPerThousand: Double,
        val queries: Long,
        val msPerQuery: Double,
    ) {
        fun lines(suffix: String) = listOf(
            "tapauksia$suffix=$cases",
            "kirjaimia_top3$suffix=${"%.3f".format(Locale.ROOT, lettersTop3)}",
            "kirjaimia_top1$suffix=${"%.3f".format(Locale.ROOT, lettersTop1)}",
            "osuus_top3_viimeistaan_2$suffix=${"%.1f".format(Locale.ROOT, top3ByTwo)}",
            "saasto$suffix=${"%.1f".format(Locale.ROOT, saved)}",
            "roska_per_1000_kyselya$suffix=${"%.2f".format(Locale.ROOT, junkPerThousand)}",
            "kyselyja$suffix=$queries",
            "ms_per_kysely$suffix=${"%.3f".format(Locale.ROOT, msPerQuery)}",
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
            val lines = measure(loaded.cases).lines("") + measure(loaded.coldCases).lines("_kylma")
            val text = lines.joinToString("\n")
            File(REPORT_PATH).apply { parentFile?.mkdirs() }.writeText(text + "\n", Charsets.UTF_8)
            return text
        }

        fun measure(cases: List<Case>): Metrics {
            val engine = loaded.engine
            var weightSum = 0L
            var lettersTop3 = 0.0
            var lettersTop1 = 0.0
            var top3ByTwo = 0L
            var saved = 0.0
            var queries = 0L
            var junkHits = 0L
            val started = System.nanoTime()
            for (case in cases) {
                val target = case.next
                var firstTop3 = target.length
                var firstTop1 = target.length
                for (p in 1 until target.length) {
                    val result = engine.suggest(target.take(p), case.context)
                    queries++
                    val visible = result.take(TOP).map { it.lowercase() }
                    // Roska lasketaan vain top3-osumaan asti, jotta luku ei
                    // riipu siitä, kuinka pitkälle top1-haku jatkuu.
                    if (firstTop3 == target.length) junkHits += visible.count { it in loaded.junk }
                    if (firstTop1 == target.length && visible.firstOrNull() == target) firstTop1 = p
                    if (firstTop3 == target.length && target in visible) firstTop3 = p
                    if (firstTop1 < target.length) break
                }
                val w = case.weight
                weightSum += w
                lettersTop3 += w * firstTop3
                lettersTop1 += w * firstTop1
                if (firstTop3 <= 2) top3ByTwo += w
                saved += w * (target.length - firstTop3).toDouble() / target.length
            }
            val elapsedMs = (System.nanoTime() - started) / 1_000_000.0
            return Metrics(
                cases = cases.size,
                lettersTop3 = lettersTop3 / weightSum,
                lettersTop1 = lettersTop1 / weightSum,
                top3ByTwo = 100.0 * top3ByTwo / weightSum,
                saved = 100.0 * saved / weightSum,
                junkPerThousand = 1000.0 * junkHits / queries,
                queries = queries,
                msPerQuery = elapsedMs / queries,
            )
        }
    }
}
