package org.jarsi.ark.emoji

import java.util.Locale

/** Yksi emoji aineistosta: ihonvärit, nimi ja avainsanat sovelluksen kielellä. */
data class EmojiEntry(
    val emoji: String,
    val category: Int,
    val variants: List<String>,
    val name: String,
    val keywords: List<String>,
)

/**
 * Emojiaineisto valitsimelle, haulle ja ehdotuksille (assets/emoji*.tsv, ks.
 * docs/emojit.md). Laitteen fontin puuttuvat emojit suodatetaan jo latauksessa,
 * joten haku ja ehdotukset eivät tarjoa neliöitä.
 */
class EmojiIndex private constructor(entries: List<EmojiEntry>) {

    private val all = entries
    private val byCategory = entries.groupBy { it.category }
    private val byEmoji = buildMap {
        for (entry in entries) {
            put(normalize(entry.emoji), entry)
            entry.variants.forEach { put(normalize(it), entry) }
        }
    }

    // Avainsanat ja nimet tarkkoina hakusanoina tiedoston järjestyksessä.
    private val byKeyword = buildMap<String, MutableList<EmojiEntry>> {
        for (entry in entries) {
            (entry.keywords + entry.name).distinct().forEach { keyword ->
                getOrPut(keyword.lowercase(FI)) { mutableListOf() }.add(entry)
            }
        }
    }

    fun category(index: Int): List<EmojiEntry> = byCategory[index].orEmpty()

    fun entryFor(emoji: String): EmojiEntry? = byEmoji[normalize(emoji)]

    /** Haku nimistä ja avainsanoista; kaikkien hakusanojen on löydyttävä. */
    fun search(query: String, limit: Int = SEARCH_LIMIT): List<EmojiEntry> {
        val normalized = query.trim().lowercase(FI)
        val terms = normalized.split(WHITESPACE).filter { it.isNotEmpty() }
        if (terms.isEmpty()) return emptyList()
        return all.asSequence()
            .mapNotNull { entry -> rank(entry, normalized, terms)?.let { entry to it } }
            .sortedBy { it.second }
            .take(limit)
            .map { it.first }
            .toList()
    }

    private fun rank(entry: EmojiEntry, query: String, terms: List<String>): Int? {
        val name = entry.name.lowercase(FI)
        if (entry.emoji == PREFERRED[query]) return RANK_PREFERRED
        if (name == query) return RANK_NAME
        if (entry.keywords.any { it == query }) return RANK_KEYWORD
        val nameTokens = tokens(name)
        val allTokens = nameTokens + entry.keywords.flatMap(::tokens)
        var worst = RANK_NAME_TOKEN
        for (term in terms) {
            val termRank = when {
                term in nameTokens -> RANK_NAME_TOKEN
                term in allTokens -> RANK_KEYWORD_TOKEN
                allTokens.any { it.startsWith(term) } -> RANK_PREFIX
                else -> return null
            }
            if (termRank > worst) worst = termRank
        }
        return worst
    }

    /**
     * Emojiehdotukset kirjoitetulle sanalle: tarkka nimi tai avainsana,
     * myös yleisimmillä taivutuspäätteillä. Hyvin yleinen avainsana (käsi,
     * naama) ehdottaa vain, jos se on emojin koko nimi. Käytetyt nousevat kärkeen.
     */
    fun suggest(
        word: String,
        used: Set<String> = emptySet(),
        limit: Int = SUGGEST_LIMIT,
    ): List<EmojiEntry> {
        val normalized = word.lowercase(FI)
        if (normalized.length < MIN_SUGGEST_LENGTH) return emptyList()
        val stem = if (matchesFor(normalized).isNotEmpty()) {
            normalized
        } else {
            INFLECTIONS.asSequence()
                .filter { normalized.endsWith(it) }
                .map { normalized.removeSuffix(it) }
                .filter { it.length >= MIN_STEM_LENGTH }
                .firstOrNull { matchesFor(it).isNotEmpty() }
                ?: return emptyList()
        }
        return matchesFor(stem)
            .sortedBy { entry ->
                when {
                    entry.emoji in used -> 0
                    entry.emoji == PREFERRED[stem] -> 1
                    entry.name.lowercase(FI) == stem -> 2
                    else -> 3
                }
            }
            .take(limit)
    }

    private fun matchesFor(word: String): List<EmojiEntry> {
        val matches = byKeyword[word] ?: return emptyList()
        if (matches.size <= BROAD_KEYWORD) return matches
        val preferred = PREFERRED[word]
        return matches.filter { it.name.lowercase(FI) == word || it.emoji == preferred }
    }

    companion object {
        const val CATEGORY_COUNT = 9

        private val FI = Locale.forLanguageTag("fi")
        private val WHITESPACE = Regex("\\s+")
        private val TOKEN_SPLIT = Regex("[^\\p{L}\\p{N}]+")
        private const val SEARCH_LIMIT = 60
        private const val SUGGEST_LIMIT = 2
        private const val MIN_SUGGEST_LENGTH = 3
        private const val MIN_STEM_LENGTH = 4
        private const val BROAD_KEYWORD = 12
        private const val RANK_PREFERRED = -1
        private const val RANK_NAME = 0
        private const val RANK_KEYWORD = 1
        private const val RANK_NAME_TOKEN = 2
        private const val RANK_KEYWORD_TOKEN = 3
        private const val RANK_PREFIX = 4

        // Aineiston järjestys antaisi näille harvinaisemman emojin ensin.
        private val PREFERRED = mapOf(
            "sydän" to "❤️", "hymy" to "🙂", "heart" to "❤️", "smile" to "🙂",
        )

        // Yleisimmät päätteet: tarkka lista estää arvaukset kuten maja → majava.
        private val INFLECTIONS = listOf(
            "ssa", "ssä", "sta", "stä", "lla", "llä", "lta", "ltä", "lle", "ksi",
            "nsa", "nsä", "ni", "si", "ja", "jä", "n", "a", "ä", "t",
        )

        private fun tokens(text: String): List<String> =
            text.split(TOKEN_SPLIT).filter { it.isNotEmpty() }

        private fun normalize(emoji: String) = emoji.replace("️", "")

        /** Lukee aineiston; rikkinäiset rivit ja laitteelta puuttuvat emojit jäävät pois. */
        fun parse(lines: Sequence<String>, supported: (String) -> Boolean): EmojiIndex {
            val entries = lines.mapNotNull { line ->
                val parts = line.split('\t')
                if (parts.size != 5) return@mapNotNull null
                val category = parts[0].toIntOrNull() ?: return@mapNotNull null
                if (!supported(parts[1])) return@mapNotNull null
                EmojiEntry(
                    emoji = parts[1],
                    category = category,
                    variants = parts[2].split(',').filter { it.isNotEmpty() && supported(it) },
                    name = parts[3],
                    keywords = parts[4].split('|').filter { it.isNotEmpty() },
                )
            }.toList()
            return EmojiIndex(entries)
        }

        /** Rivin ehdotuksesta lisättävä teksti: sana säilyy, emoji tulee sen perään. */
        fun insertion(before: CharSequence, emoji: String): String =
            if (before.isEmpty() || before.last().isWhitespace()) "$emoji " else " $emoji "
    }
}
