package org.jarsi.ark.engine

import java.text.BreakIterator
import kotlin.math.abs

/** Tekstityökalut: keskeneräisen sanan poiminta kursorin edeltä. */
object WordTools {

    private fun isCore(c: Char) = c.isLetterOrDigit() || c == '-'

    private val NON_WORD = Regex("[^\\p{L}\\p{N}.-]+")

    // Taivutusperheen karkea vartalo: liitepartikkelit, tavallisimmat
    // sijapäätteet ja vartalon loppuvokaalit kuoritaan, jotta saman sanan
    // muodot (sovellus, sovelluksen, sovelluksessa) saavat saman avaimen.
    // Heuristiikka: astevaihtelua ei tunneta (kaupunki/kaupungin eroavat),
    // mutta sama funktio käsittelee kaikki muodot, joten ryhmittely on
    // johdonmukainen. Vartalo on aina vähintään [STEM_MIN] merkkiä.
    private const val STEM_MIN = 4
    private val CLITICS = listOf("kaan", "kään", "kin", "han", "hän", "ko", "kö", "pa", "pä")
    private val CASE_ENDINGS = listOf(
        "ssa", "ssä", "sta", "stä", "lla", "llä", "lta", "ltä", "lle", "ksi", "tta", "ttä",
        "ta", "tä", "na", "nä", "a", "ä", "n", "t",
    )
    private const val VOWELS = "aeiouyäö"

    /**
     * Karkea vartalo pienennetylle avaimelle [key]. Muut kuin kirjaimista
     * koostuvat avaimet (jarsi.org, ark-launcher, 10a) ja alle [STEM_MIN]
     * merkin sanat palautuvat sellaisinaan.
     */
    fun stem(key: String): String {
        if (key.length < STEM_MIN || !key.all { it.isLetter() }) return key
        var s = key
        fun strip(suffixes: List<String>) {
            for (suffix in suffixes) {
                if (s.length - suffix.length >= STEM_MIN && s.endsWith(suffix)) {
                    s = s.dropLast(suffix.length)
                    return
                }
            }
        }
        strip(CLITICS)
        strip(CASE_ENDINGS)
        // Pitkä loppuvokaali yhdeksi (taloon → talo, kaikkiin → kaikki).
        if (s.length > STEM_MIN && s[s.length - 1] == s[s.length - 2] && s.last() in VOWELS) {
            s = s.dropLast(1)
        }
        // Vartalon sidevokaali pois (sovellukse → sovelluks, kaikki → kaikk).
        while (s.length > STEM_MIN && (s.last() == 'e' || s.last() == 'i')) s = s.dropLast(1)
        // us/ukse-vaihtelu (sovelluks → sovellus).
        if (s.length > STEM_MIN && s.endsWith("ks")) s = s.dropLast(2) + "s"
        return s
    }

    /** Pilkkoo tekstin sanoiksi esimerkiksi sanellun lauseen oppimista varten. */
    fun words(text: CharSequence): List<String> =
        text.split(NON_WORD)
            .map { it.trim('-', '.') }
            .filter { it.isNotEmpty() }

    /**
     * Palauttaa kursoria välittömästi edeltävän keskeneräisen sanan tai
     * tyhjän, jos kursorin edellä on sananraja. Sanassa voi olla kirjaimia,
     * numeroita, yhdysmerkkejä ja sisäisiä pisteitä (jarsi.org); lauseen
     * lopettava piste ei tartu sanaan.
     */
    fun currentWord(textBefore: CharSequence): String {
        var start = textBefore.length
        while (start > 0) {
            val c = textBefore[start - 1]
            val internalDot = c == '.' && start < textBefore.length &&
                isCore(textBefore[start]) && start >= 2 && isCore(textBefore[start - 2])
            if (isCore(c) || internalDot) start-- else break
        }
        return textBefore.subSequence(start, textBefore.length).toString()
            .trimStart('-', '.')
    }

    /**
     * Ehdotusrivin lopullinen lista: kirjoitettava sana nousee kärkeen
     * sellaisenaan, jos sitä ei jo ole ehdotuksissa, jotta sen voi hyväksyä
     * napauttamalla. Asut erotellaan, jotta napautus säilyttää kirjoitetun
     * kirjainkoon.
     */
    fun withTypedWord(typed: String, suggestions: List<String>): List<String> =
        if (typed.isEmpty() || typed in suggestions) suggestions
        else listOf(typed) + suggestions

    /**
     * Kaikkien sanojen sijainnit tekstissä samoilla säännöillä kuin
     * [currentWord]: sisäiset pisteet kuuluvat sanaan, reunojen
     * yhdysmerkit ja pisteet eivät.
     */
    fun wordRanges(text: CharSequence): List<IntRange> {
        val ranges = mutableListOf<IntRange>()
        var i = 0
        while (i < text.length) {
            if (!isCore(text[i])) {
                i++
                continue
            }
            var end = i
            while (end < text.length) {
                val c = text[end]
                val internalDot = c == '.' && end + 1 < text.length &&
                    isCore(text[end + 1]) && end > 0 && isCore(text[end - 1])
                if (isCore(c) || internalDot) end++ else break
            }
            var start = i
            while (start < end && (text[start] == '-' || text[start] == '.')) start++
            var last = end - 1
            while (last >= start && (text[last] == '-' || text[last] == '.')) last--
            if (last >= start) ranges += start..last
            i = end
        }
        return ranges
    }

    /**
     * Sana kohdassa [index] tai sitä lähin sana [wordRanges]-säännöin;
     * null jos tekstissä ei ole yhtään sanaa. Kohta osuu sanaan myös
     * heti sen perässä, kuten tekstikentän sanavalinnassa.
     */
    fun wordRangeAt(text: CharSequence, index: Int): IntRange? {
        val ranges = wordRanges(text)
        if (ranges.isEmpty()) return null
        val i = index.coerceIn(0, text.length)
        return ranges.minByOrNull { range ->
            when {
                i < range.first -> range.first - i
                i > range.last + 1 -> i - (range.last + 1)
                else -> 0
            }
        }
    }

    /**
     * Damerau–Levenshtein-etäisyys, jossa viereisten merkkien vaihdos on
     * yksi muokkaus. Laskenta katkeaa rajaan: paluuarvo on [max] + 1 heti,
     * kun etäisyys ylittää sen.
     */
    fun editDistanceAtMost(a: String, b: String, max: Int): Int {
        if (a == b) return 0
        val la = a.length
        val lb = b.length
        if (abs(la - lb) > max) return max + 1
        var prev2 = IntArray(0)
        var prev = IntArray(lb + 1) { it }
        for (i in 1..la) {
            val cur = IntArray(lb + 1)
            cur[0] = i
            var rowMin = i
            for (j in 1..lb) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                var value = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + cost)
                if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) {
                    value = minOf(value, prev2[j - 2] + 1)
                }
                cur[j] = value
                if (value < rowMin) rowMin = value
            }
            if (rowMin > max) return max + 1
            prev2 = prev
            prev = cur
        }
        return if (prev[lb] <= max) prev[lb] else max + 1
    }

    // Lauseen päättävät merkit (samat kuin SmartSpace-luokassa); rivinvaihto
    // ei päätä lausetta, joten ennustus toimii rivinvaihtojen yli.
    private const val SENTENCE_ENDERS = ".!?…"

    /**
     * Palauttaa enintään [count] valmista sanaa kursorin keskeneräisen sanan
     * edeltä aikajärjestyksessä (lähin viimeisenä). Välilyönnit ja
     * rivinvaihdot ohitetaan. Lauseen päättävä merkki on raja: sitä
     * vanhempia sanoja ei oteta, vaan lista alkaa rajamerkillä
     * [LearningEngine.SENTENCE_START], joka vie yhden paikan kiintiöstä.
     * Tekstin alku on lauseen alku, kun [startIsSentence] on tosi; kutsuja
     * antaa epätoden, kun [textBefore] on vain lukuikkunan verran pidemmästä
     * tekstistä. Sanan sisäinen piste (jarsi.org, 3.5) ei ole raja.
     */
    fun previousWords(
        textBefore: CharSequence,
        count: Int = 2,
        startIsSentence: Boolean = true,
    ): List<String> {
        val words = ArrayDeque<String>()
        var end = textBefore.length - currentWord(textBefore).length
        while (words.size < count) {
            var boundary = false
            while (end > 0 && !isCore(textBefore[end - 1])) {
                if (textBefore[end - 1] in SENTENCE_ENDERS) boundary = true
                end--
            }
            if (boundary || (end == 0 && startIsSentence)) {
                words.addFirst(LearningEngine.SENTENCE_START)
                break
            }
            if (end == 0) break
            val word = currentWord(textBefore.subSequence(0, end))
            if (word.isEmpty()) break
            words.addFirst(word)
            end -= word.length
        }
        return words.toList()
    }

    /** Liu'utuspoiston pituus: [words] sanaa kursorin edeltä, enintään koko teksti. */
    fun wordsBackspaceLength(textBefore: CharSequence, words: Int): Int {
        var length = 0
        repeat(words) {
            val rest = textBefore.subSequence(0, textBefore.length - length)
            if (rest.isEmpty()) return length
            length += wordBackspaceLength(rest)
        }
        return length
    }

    /**
     * Rajaa poiston ehjään merkkiin. Lukuraja voi katketa keskelle merkkiä
     * (emojin puolikas): jos poisto yltäisi katkaistun luvun alkuun asti,
     * ensimmäinen merkki jää poistamatta, jottei kenttään jää puolikasta.
     */
    fun clampToWholeCharacter(textBefore: CharSequence, length: Int, readLimit: Int): Int {
        if (length < textBefore.length || textBefore.length < readLimit) return length
        val iterator = BreakIterator.getCharacterInstance()
        iterator.setText(textBefore.toString())
        val firstBoundary = iterator.following(0)
        return if (firstBoundary == BreakIterator.DONE) 0 else length - firstBoundary
    }

    /** Grafeemien määrä: montako näppäintapahtumana lähetettyä poistoa teksti vaatii. */
    fun graphemeCount(text: CharSequence): Int {
        val iterator = BreakIterator.getCharacterInstance()
        iterator.setText(text.toString())
        var count = 0
        while (iterator.next() != BreakIterator.DONE) count++
        return count
    }

    /**
     * Sanapoiston pituus kursorin edeltä: häntävälit ja niitä edeltävä
     * yhtenäinen merkkijono välimerkkeineen ("sana. " poistuu kerralla).
     */
    fun wordBackspaceLength(textBefore: CharSequence): Int {
        var i = textBefore.length
        while (i > 0 && textBefore[i - 1].isWhitespace()) i--
        while (i > 0 && !textBefore[i - 1].isWhitespace()) i--
        return textBefore.length - i
    }
}
