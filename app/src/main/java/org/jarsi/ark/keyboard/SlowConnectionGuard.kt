package org.jarsi.ark.keyboard

/**
 * Tunnistaa kentän, jonka tekstikyselyt vievät kauan (raskaat chat- ja
 * verkkosovellukset). Kyselyt tehdään näppäimistön päälangalta, joten hidas
 * vastaus jäädyttää näppäilyn; hitaassa kentässä valinnaiset kyselyt
 * niputetaan kirjoitustauon ajalle. Kynnys ja muistiaika kuten AOSP:n
 * näppäimistössä.
 */
class SlowConnectionGuard {

    private var lastSlowAt: Long? = null

    fun record(durationMs: Long, now: Long) {
        if (durationMs >= SLOW_READ_MS) lastSlowAt = now
    }

    fun isSlow(now: Long): Boolean {
        val slowAt = lastSlowAt ?: return false
        return now - slowAt <= PERSIST_MS
    }

    fun reset() {
        lastSlowAt = null
    }

    private companion object {
        const val SLOW_READ_MS = 200L
        const val PERSIST_MS = 10L * 60 * 1000
    }
}
