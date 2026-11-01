package org.jarsi.ark.engine

import java.util.Locale
import java.util.PriorityQueue
import kotlin.math.abs

/**
 * Yleinen sanasto: lataa taajuuslistan muistiin ja hakee täydennykset
 * etuliitteelle binäärihaulla. Lataus tehdään kutsujan taustasäikeessä;
 * [suggest] ja [topWords] palauttavat tyhjän kunnes lataus on valmis.
 */
class DictionaryEngine {

    /**
     * Sanat tiiviisti aakkosjärjestyksessä: yksi Latin-1-tavutaulukko sekä taulukot
     * alkukohdille, taajuuksille ja kirjainjoukoille. Yli 400 000 sanaa vie näin
     * noin 10 Mt, kun erillisinä olioina se veisi lähes 40 Mt.
     */
    private class Words(
        val text: ByteArray,
        val starts: IntArray,
        val freqs: IntArray,
        val letters: IntArray,
    ) {
        val size: Int get() = freqs.size

        fun length(index: Int) = starts[index + 1] - starts[index]

        fun word(index: Int) = String(text, starts[index], length(index), Charsets.ISO_8859_1)

        fun compareToKey(index: Int, key: String): Int {
            val start = starts[index]
            val length = length(index)
            for (i in 0 until minOf(length, key.length)) {
                val difference = (text[start + i].toInt() and 0xFF) - key[i].code
                if (difference != 0) return difference
            }
            return length - key.length
        }

        fun startsWithKey(index: Int, key: String): Boolean {
            if (length(index) < key.length) return false
            val start = starts[index]
            for (i in key.indices) {
                if ((text[start + i].toInt() and 0xFF) != key[i].code) return false
            }
            return true
        }

        /** Ensimmäinen indeksi, jonka sana on >= avain. */
        fun lowerBound(key: String): Int {
            var low = 0
            var high = size
            while (low < high) {
                val mid = (low + high) ushr 1
                if (compareToKey(mid, key) < 0) low = mid + 1 else high = mid
            }
            return low
        }
    }

    private class Builder {
        var text = ByteArray(1 shl 16)
        var textSize = 0
        var starts = IntArray(1 shl 12)
        var freqs = IntArray(1 shl 12)
        var letters = IntArray(1 shl 12)
        var count = 0

        fun add(word: String, freq: Int) {
            if (count + 1 >= starts.size) {
                starts = starts.copyOf(starts.size * 2)
                freqs = freqs.copyOf(freqs.size * 2)
                letters = letters.copyOf(letters.size * 2)
            }
            if (textSize + word.length > text.size) {
                text = text.copyOf(maxOf(text.size * 2, textSize + word.length))
            }
            starts[count] = textSize
            freqs[count] = freq
            letters[count] = letterSet(word)
            for (c in word) text[textSize++] = c.code.toByte()
            count++
        }

        fun build(): Words {
            starts[count] = textSize
            return Words(
                text.copyOf(textSize),
                starts.copyOf(count + 1),
                freqs.copyOf(count),
                letters.copyOf(count),
            )
        }
    }

    @Volatile private var words: Words? = null
    @Volatile private var common: List<String> = emptyList()
    @Volatile private var maxFreq = 0L

    private val fiLocale = Locale.forLanguageTag("fi")

    val isLoaded: Boolean get() = (words?.size ?: 0) > 0

    /**
     * Lataa rivit muodossa "sana taajuus". Virheelliset rivit ja sanat, joissa on
     * Latin-1:n ulkopuolisia merkkejä, ohitetaan. Lista kannattaa tuoda
     * aakkosjärjestyksessä, jolloin järjestämiseen ei kulu aikaa eikä muistia.
     */
    fun load(lines: Sequence<String>, topWordCount: Int = 8) {
        val builder = Builder()
        var previous = ""
        var ascending = true
        for (line in lines) {
            val sep = line.lastIndexOf(' ')
            if (sep <= 0) continue
            val freq = line.substring(sep + 1).toIntOrNull() ?: continue
            val word = line.substring(0, sep).trim().lowercase(fiLocale)
            if (word.isEmpty() || word.any { it.code > 0xFF }) continue
            if (word < previous) ascending = false
            previous = word
            builder.add(word, freq)
        }
        val loaded = builder.build().let { if (ascending) it else sorted(it) }
        common = topIndexes(loaded, 0 until loaded.size, topWordCount).map(loaded::word)
        maxFreq = loaded.freqs.maxOrNull()?.toLong() ?: 0L
        words = loaded
    }

    private fun sorted(unsorted: Words): Words {
        val builder = Builder()
        (0 until unsorted.size)
            .sortedBy(unsorted::word)
            .forEach { builder.add(unsorted.word(it), unsorted.freqs[it]) }
        return builder.build()
    }

    /** Suurin listalla esiintyvä taajuus pisteiden normalisointia varten. */
    fun maxFrequency(): Long = maxFreq

    /** Sanan taajuus tai 0, jos sana ei ole listalla. */
    fun frequencyOf(word: String): Long {
        val list = words ?: return 0L
        val key = word.lowercase(fiLocale)
        val index = list.lowerBound(key)
        return if (index < list.size && list.compareToKey(index, key) == 0) {
            list.freqs[index].toLong()
        } else {
            0L
        }
    }

    /** Yleisimmät sanat tyhjälle syötteelle. */
    fun topWords(): List<String> = common

    /** Sanat enintään [maxDistance] muokkauksen päässä; lähin ja yleisin ensin. */
    fun near(word: String, maxDistance: Int, max: Int = 8): List<String> {
        val list = words
        if (list == null || word.isEmpty() || max <= 0) return emptyList()
        val key = word.lowercase(fiLocale)
        val keyLetters = letterSet(key)
        val hits = ArrayList<Pair<Int, Int>>()
        for (index in 0 until list.size) {
            if (abs(list.length(index) - key.length) > maxDistance) continue
            // Yksi muokkaus muuttaa kirjainjoukkoa enintään kahdesta kohdasta. Valtaosa
            // listasta karsiutuu tässä ilman raskasta etäisyyslaskentaa.
            if (Integer.bitCount(list.letters[index] xor keyLetters) > 2 * maxDistance) continue
            if (list.compareToKey(index, key) == 0) continue
            val distance = WordTools.editDistanceAtMost(list.word(index), key, maxDistance)
            if (distance <= maxDistance) hits += index to distance
        }
        return hits
            .sortedWith(compareBy({ it.second }, { -list.freqs[it.first] }))
            .take(max)
            .map { list.word(it.first) }
    }

    /** Etuliitteellä alkavat sanat yleisyysjärjestyksessä, enintään [max] kpl. */
    fun suggest(prefix: String, max: Int = 8): List<String> {
        val list = words
        if (list == null || prefix.isEmpty() || max <= 0) return emptyList()
        val key = prefix.lowercase(fiLocale)
        val start = list.lowerBound(key)
        var end = start
        while (end < list.size && list.startsWithKey(end, key)) end++
        return topIndexes(list, start until end, max).map(list::word)
    }

    /** Yleisimmät indeksit väliltä; pienin taajuus jonon kärjessä putoaa, kun parempi löytyy. */
    private fun topIndexes(list: Words, range: IntRange, max: Int): List<Int> {
        if (max <= 0) return emptyList()
        val best = PriorityQueue<Int>(max, compareBy { list.freqs[it] })
        for (index in range) {
            if (best.size < max) {
                best.add(index)
            } else if (list.freqs[best.peek()!!] < list.freqs[index]) {
                best.poll()
                best.add(index)
            }
        }
        return best.sortedByDescending { list.freqs[it] }
    }

    private companion object {
        /** Sanan kirjaimet bitteinä; samaan bittiin osuvat kirjaimet vain heikentävät karsintaa. */
        fun letterSet(word: String): Int {
            var set = 0
            for (c in word) set = set or (1 shl (c.code and 31))
            return set
        }
    }
}
