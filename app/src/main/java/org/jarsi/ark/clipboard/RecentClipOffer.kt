package org.jarsi.ark.clipboard

/**
 * Tuore kopio tarjotaan ehdotusrivillä liitettäväksi yhdellä napautuksella.
 * Tarjous vanhenee nopeasti, ettei vanha leike jää näkyviin rivin kärkeen;
 * kaikki leikkeet löytyvät yhä leikepöytäpaneelista. [clock] on kulunutta
 * aikaa mittaava kello (ei seinäkello): kellonajan siirto taaksepäin ei saa
 * pidentää tarjousta.
 */
class RecentClipOffer(private val clock: () -> Long) {

    data class Offer(val clipId: Long, val text: String)

    private var offer: Offer? = null
    private var offeredAt = 0L

    fun offer(clipId: Long, text: String) {
        offer = Offer(clipId, text)
        offeredAt = clock()
    }

    fun current(): Offer? {
        if (clock() - offeredAt >= OFFER_MS) offer = null
        return offer
    }

    fun clear() {
        offer = null
    }

    /** Aikaa tarjouksen vanhenemiseen; 0, kun tarjousta ei ole tai se on vanhentunut. */
    fun remainingMs(): Long {
        current() ?: return 0L
        return (OFFER_MS - (clock() - offeredAt)).coerceAtLeast(0L)
    }

    /** Poistettu leike ei saa jäädä liitettäväksi; true, jos tarjous poistui. */
    fun clearIfClip(clipId: Long): Boolean {
        if (offer?.clipId != clipId) return false
        offer = null
        return true
    }

    companion object {
        const val OFFER_MS = 60_000L
        const val PREVIEW_CHARS = 24

        private val WHITESPACE = Regex("\\s+")

        fun preview(text: String): String {
            val flat = text.trim().replace(WHITESPACE, " ")
            if (flat.length <= PREVIEW_CHARS) return flat
            val cut = if (flat[PREVIEW_CHARS - 1].isHighSurrogate()) {
                PREVIEW_CHARS - 1
            } else {
                PREVIEW_CHARS
            }
            return flat.substring(0, cut) + "…"
        }
    }
}
