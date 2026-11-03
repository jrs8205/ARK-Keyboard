package org.jarsi.ark.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SupportLinksTest {

    @Test
    fun `paypal yrittaa ensin sovellusta`() {
        val t = SupportLinks.targets(SupportLinks.PAYPAL_URL)
        assertEquals(2, t.size)
        assertEquals(SupportLinks.PAYPAL_PACKAGE, t[0].packageName)
        assertEquals(SupportLinks.PAYPAL_URL, t[0].url)
        assertNull(t[1].packageName)
        assertEquals(SupportLinks.PAYPAL_URL, t[1].url)
    }

    @Test
    fun `muut linkit avataan suoraan`() {
        assertEquals(
            listOf(SupportLinks.Target(SupportLinks.SPONSOR_URL, null)),
            SupportLinks.targets(SupportLinks.SPONSOR_URL),
        )
        assertEquals(1, SupportLinks.targets(SupportLinks.COFFEE_URL).size)
        assertEquals(1, SupportLinks.targets(SupportLinks.REPO_URL).size)
    }
}
