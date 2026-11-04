package org.jarsi.ark.engine

import java.util.Locale
import kotlin.math.ln

/**
 * Kokoaa ehdotusrivin yhtenäisellä pisteytysmallilla: yleisyys, oma käyttö,
 * kontekstiosumat (bigram/trigram), hyväksynnät, kiinnitys ja ohitukset
 * summautuvat yhdeksi pistemääräksi (kokonaissuunnitelman kohta 22).
 * Kaikki alulla alkavat omat sanat pääsevät pisteytykseen; esikarsinta
 * koskee vain sanalistaa, jotta pitkä harvemmin käytetty oma sana ei jää
 * lyhyiden ja yleisten sanojen korin ulkopuolelle.
 */
class SuggestionEngine(
    private val dictionary: DictionaryEngine,
    private val learning: LearningEngine,
) {

    private val fiLocale = Locale.forLanguageTag("fi")

    /** Ehdotukset keskeneräiselle sanalle; [context] on edeltävät 1–2 sanaa. */
    fun suggest(prefix: String, context: List<String> = emptyList(), max: Int = 8): List<String> {
        if (prefix.isEmpty() || max <= 0) return emptyList()
        val prefixKey = prefix.lowercase(fiLocale)
        val contextMatches = learning.contextMatches(context)
        val candidates = LinkedHashSet<String>()
        for (word in dictionary.suggest(prefix, DICTIONARY_CANDIDATES)) {
            candidates += word.lowercase(fiLocale)
        }
        for (word in learning.suggest(prefix, Int.MAX_VALUE)) {
            candidates += word.lowercase(fiLocale)
        }
        for (key in contextMatches.keys) {
            if (key.startsWith(prefixKey)) candidates += key
        }
        return rank(candidates, contextMatches, max, prefixLength = prefixKey.length)
    }

    /**
     * Koko sanan vaihtoehdot automaattikorjaukselle. Ehdolle pääsevät
     * kirjoitusasultaan läheiset sanat (kirjoitusvirheet) sekä sanat, jotka
     * sopivat paikkaan edeltävien sanojen perusteella (omat sanaparit) —
     * siksi lyhyetkin sanat saavat vaihtoehtoja. Kaikki pisteytetään samalla
     * mallilla; muokkausetäisyys painaa kaukaisempia alaspäin.
     */
    fun alternatives(
        word: String,
        context: List<String> = emptyList(),
        max: Int = 8,
    ): List<String> {
        if (word.isEmpty() || max <= 0) return emptyList()
        val key = word.lowercase(fiLocale)
        val candidates = LinkedHashSet<String>()
        if (key.length >= MIN_TYPO_LENGTH) {
            val maxDistance = if (key.length <= SHORT_WORD_LENGTH) 1 else 2
            for (w in dictionary.near(word, maxDistance, DICTIONARY_CANDIDATES)) {
                candidates += w.lowercase(fiLocale)
            }
            for (w in learning.near(word, maxDistance, OWN_CANDIDATES)) {
                candidates += w.lowercase(fiLocale)
            }
        }
        if (key.length <= SHORT_WORD_LENGTH) {
            // Lyhyet sanat ovat yleensä pikkusanoja, joiden vaihtoehdot ovat
            // toisia pikkusanoja — yleisimmät sanat pohjaehdokkaiksi, jotta
            // rivi ei jää tyhjäksi ennen kuin omia sanapareja on kertynyt.
            for (w in dictionary.topWords()) {
                candidates += w.lowercase(fiLocale)
            }
        }
        val contextMatches = learning.contextMatches(context)
        candidates += contextMatches.keys
        candidates -= key
        return rank(candidates, contextMatches, max) { candidate, base ->
            base * closeness(key, candidate)
        }
    }

    // Jyrkkä porrastus: yhden muokkauksen sana säilyttää pisteensä, kaukainen
    // romahtaa — muuten usein käytetyt pikkusanat jyräisivät omilla
    // signaaleillaan kirjoitusasultaan läheiset korjaukset.
    private fun closeness(key: String, candidate: String): Float =
        when (WordTools.editDistanceAtMost(key, candidate, MAX_CLOSENESS_DISTANCE)) {
            0, 1 -> 1f
            2 -> 0.3f
            3 -> 0.08f
            else -> 0.03f
        }

    /**
     * Varovainen automaattikorjaus: korjaa vain kun kirjoitettu sana on
     * tuntematon, koostuu kirjaimista ja yhden muokkauksen päässä on
     * tunnettu sana. Koodit (prx4), osoitteet ja käyttäjän omat tai
     * estämät sanat jäävät aina rauhaan. Palauttaa null, jos ei korjata.
     */
    fun autoCorrect(word: String, context: List<String> = emptyList()): String? {
        if (word.length < MIN_AUTOCORRECT_LENGTH) return null
        if (!word.all { it.isLetter() || it == '-' }) return null
        val key = word.lowercase(fiLocale)
        if (dictionary.frequencyOf(key) > 0) return null
        if (learning.isOwnWord(word) || learning.isBlocked(word)) return null
        return alternatives(word, context, max = AUTOCORRECT_CANDIDATES)
            .firstOrNull {
                WordTools.editDistanceAtMost(key, it.lowercase(fiLocale), 1) <= 1 &&
                    isCorrectionTarget(it)
            }
    }

    /** Suomen kirjoitusvirhe ei saa muuttua englanniksi, ellei käyttäjä itse käytä sanaa. */
    private fun isCorrectionTarget(word: String): Boolean =
        dictionary.frequencyOf(word) != DictionaryEngine.SUPPLEMENT_FREQUENCY ||
            learning.isOwnWord(word)

    /**
     * Ehdotusten kirjainkoko: lauseen alussa ja isolla aloitetussa sanassa iso
     * alkukirjain, muuten pieni. Opittu iso alkukirjain tulee yleensä lauseen
     * alusta, joten tavallinen sana näytetään pienellä. Oma nimi tai lyhenne, jota
     * sanasto ei tunne, odottaa käyttäjän isolla aloittamaa sanaa.
     */
    fun applyCase(suggestions: List<String>, typed: String, sentenceStart: Boolean): List<String> {
        val capitalize = if (typed.isEmpty()) sentenceStart else typed.first().isUpperCase()
        if (capitalize) return suggestions.map { s -> s.replaceFirstChar { it.titlecase(fiLocale) } }
        return suggestions.map(::correctionForm).filterNot { it.first().isUpperCase() }
    }

    /** Pienellä kirjoitetun sanan korjaus: tavallinen sana ei saa opittua isoa alkukirjainta. */
    fun correctionForm(word: String): String =
        if (word.first().isUpperCase() && dictionary.frequencyOf(word) > 0) {
            word.lowercase(fiLocale)
        } else {
            word
        }

    /** Rivi tyhjälle syötteelle: ennustukset ja halutessa yleisimmät täytteeksi. */
    fun emptyInput(context: List<String>, includeCommon: Boolean, max: Int = 8): List<String> {
        val contextMatches = learning.contextMatches(context)
        val candidates = LinkedHashSet<String>()
        candidates += contextMatches.keys
        if (includeCommon) {
            for (word in dictionary.topWords()) {
                candidates += word.lowercase(fiLocale)
            }
        }
        return rank(candidates, contextMatches, max)
    }

    /**
     * Pisteyttää ehdokkaat. [prefixLength] on kirjoitetun alun pituus, kun
     * täydennetään sanaa; tyhjälle syötteelle ja korjausvaihtoehdoille sitä
     * ei anneta, jolloin säästetyt kirjaimet eivät vaikuta pisteisiin.
     */
    private fun rank(
        candidateKeys: Collection<String>,
        contextMatches: Map<String, NextMatch>,
        max: Int,
        prefixLength: Int = -1,
        adjust: (String, Float) -> Float = { _, base -> base },
    ): List<String> {
        val maxFreq = dictionary.maxFrequency()
        val candidates = candidateKeys.mapNotNull { key ->
            val signals = learning.signals(key)
            if (signals?.blocked == true) return@mapNotNull null
            val saved = if (prefixLength >= 0) key.length - prefixLength else 0
            Candidate(
                key,
                adjust(key, baseScore(key, signals, contextMatches[key], maxFreq, saved)),
                signals?.pinned == true,
            )
        }
        // Kiinnitysbonus vain kahdelle parhaalle, etteivät kiinnitetyt sanat
        // täytä koko riviä, jos samalla alulla on monta kiinnitystä.
        val bonusKeys = candidates
            .filter { it.pinned }
            .sortedByDescending { it.base }
            .take(PINNED_TOP)
            .mapTo(HashSet()) { it.key }
        return candidates
            .sortedByDescending { it.base + if (it.key in bonusKeys) PINNED_WEIGHT else 0f }
            .take(max)
            .map { learning.displayForm(it.key) }
    }

    private class Candidate(val key: String, val base: Float, val pinned: Boolean)

    private fun baseScore(
        key: String,
        signals: WordSignals?,
        match: NextMatch?,
        maxFreq: Long,
        saved: Int,
    ): Float {
        var score = 0f
        if (maxFreq > 0) {
            val freq = dictionary.frequencyOf(key)
            score += (ln(1.0 + freq) / ln(1.0 + maxFreq)).toFloat() * FREQUENCY_WEIGHT
        }
        if (match != null) {
            score += match.bigram / (match.bigram + 3f) * BIGRAM_WEIGHT
            score += match.trigram / (match.trigram + 3f) * TRIGRAM_WEIGHT
        }
        if (signals != null) {
            // Oma käyttö logaritmisesti kuten yleisyys: 800 ja 400 käyttöä
            // erottuvat yhä toisistaan, kun kyllästyvä muoto tasasi ne jo
            // muutaman käytön jälkeen.
            score += (ln(1.0 + signals.usage) / ln(1.0 + USAGE_SCALE)).toFloat() * USAGE_WEIGHT
            score += signals.acceptedCount / (signals.acceptedCount + 3f) * ACCEPTED_WEIGHT
            if (signals.manuallyTyped) {
                score += MANUAL_WEIGHT
                // Täydennysarvo: mitä enemmän kirjaimia ehdotus säästää, sitä
                // enemmän se saa, mutta vain vähintään kahdesti kirjoitetuille
                // omille sanoille. Sanalistan sanat eivät saa termiä, jotta
                // lyhyet yleissanat pysyvät kärjessä lyhyellä alulla.
                if (saved > 0) {
                    score += saved / (saved + COMPLETION_SATURATION) * COMPLETION_WEIGHT
                }
            }
            if (signals.ignoredCount >= IGNORED_THRESHOLD) {
                // Ohitukset suhteessa käyttöön: kerran kirjoitettu ja aina
                // ohitettu sana katoaa, paljon käytetty kestää ohituksia, ja
                // jokainen käyttö nollaa laskurin, joten sana nousee heti
                // takaisin kun sitä taas käyttää.
                val ignored = signals.ignoredCount.toFloat()
                val used = signals.count + signals.acceptedCount + IGNORED_SATURATION
                score -= ignored / (ignored + used) * IGNORED_WEIGHT
            }
        }
        return score
    }

    private companion object {
        const val DICTIONARY_CANDIDATES = 20
        // Vain korjausvaihtoehtojen (alternatives) omien sanojen kori.
        const val OWN_CANDIDATES = 10
        const val IGNORED_THRESHOLD = 2
        const val PINNED_TOP = 2
        const val MIN_TYPO_LENGTH = 2
        const val SHORT_WORD_LENGTH = 4
        const val MAX_CLOSENESS_DISTANCE = 3
        const val MIN_AUTOCORRECT_LENGTH = 3
        const val AUTOCORRECT_CANDIDATES = 3

        // Kokonaissuunnitelman kohdan 22 painokertoimet.
        const val FREQUENCY_WEIGHT = 1.0f
        const val USAGE_WEIGHT = 2.0f
        const val USAGE_SCALE = 1000.0
        const val BIGRAM_WEIGHT = 4.0f
        const val TRIGRAM_WEIGHT = 7.0f
        const val ACCEPTED_WEIGHT = 3.0f
        const val MANUAL_WEIGHT = 2.5f
        const val PINNED_WEIGHT = 20.0f
        const val IGNORED_WEIGHT = 5.0f
        const val IGNORED_SATURATION = 3f
        const val COMPLETION_WEIGHT = 0.75f
        const val COMPLETION_SATURATION = 6f
    }
}
