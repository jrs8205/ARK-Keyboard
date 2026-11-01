package org.jarsi.ark.keyboard

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SlowConnectionGuardTest {

    @Test
    fun `nopea kentta ei ole hidas`() {
        val guard = SlowConnectionGuard()
        guard.record(durationMs = 199, now = 1000)
        assertFalse(guard.isSlow(now = 1000))
    }

    @Test
    fun `yksi hidas luku riittaa`() {
        val guard = SlowConnectionGuard()
        guard.record(durationMs = 200, now = 1000)
        assertTrue(guard.isSlow(now = 1001))
    }

    @Test
    fun `hitaus muistetaan kymmenen minuuttia`() {
        val guard = SlowConnectionGuard()
        guard.record(durationMs = 500, now = 1000)
        assertTrue(guard.isSlow(now = 1000 + 600_000))
        assertFalse(guard.isSlow(now = 1000 + 600_001))
    }

    @Test
    fun `nopeat luvut eivat kumoa hitautta`() {
        val guard = SlowConnectionGuard()
        guard.record(durationMs = 500, now = 1000)
        guard.record(durationMs = 1, now = 2000)
        assertTrue(guard.isSlow(now = 2001))
    }

    @Test
    fun `uusi sovellus aloittaa puhtaalta poydalta`() {
        val guard = SlowConnectionGuard()
        guard.record(durationMs = 500, now = 1000)
        guard.reset()
        assertFalse(guard.isSlow(now = 1001))
    }
}
