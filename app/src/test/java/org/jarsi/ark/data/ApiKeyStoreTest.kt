package org.jarsi.ark.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Avainvaraston välimuisti ja asetustiedosto pysyvät samassa tilassa myös
 * kirjoituksen ilmoituksen aikana: asetussivu lukee tietueen suoraan
 * kuuntelijasta, ja uloskirjautumisen jälkeen sen on nähtävä poisto.
 * Testiympäristössä Keystorea ei ole, joten avain tallentuu varareitille
 * avoimena; välimuistin logiikka on sama.
 */
class ApiKeyStoreTest {

    private val slot = ApiKeyStore.Slot.CHATGPT_LOGIN

    @Test
    fun `poisto nakyy kuuntelijalle jo ilmoituksen aikana`() {
        val prefs = FakePrefs()
        ApiKeyStore.save(prefs, "vanha", slot)
        assertEquals("vanha", ApiKeyStore.read(prefs, slot))
        val seen = mutableListOf<String?>()
        prefs.registerOnSharedPreferenceChangeListener { p, _ -> seen += ApiKeyStore.read(p, slot) }

        ApiKeyStore.save(prefs, "", slot)

        assertEquals(listOf<String?>(null), seen)
        assertNull(ApiKeyStore.read(prefs, slot))
    }

    @Test
    fun `uusi avain nakyy kuuntelijalle jo ilmoituksen aikana`() {
        val prefs = FakePrefs()
        ApiKeyStore.save(prefs, "", slot)
        val seen = mutableListOf<String?>()
        prefs.registerOnSharedPreferenceChangeListener { p, _ -> seen += ApiKeyStore.read(p, slot) }

        ApiKeyStore.save(prefs, "uusi", slot)

        assertEquals(listOf<String?>("uusi"), seen)
        assertEquals("uusi", ApiKeyStore.read(prefs, slot))
    }
}
