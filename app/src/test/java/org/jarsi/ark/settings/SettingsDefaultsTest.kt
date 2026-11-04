package org.jarsi.ark.settings

import androidx.appcompat.app.AppCompatDelegate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsDefaultsTest {

    @Test
    fun `oletukset tyhjaan tiedostoon suomeksi`() {
        val d = SettingsDefaults.missingDefaults(emptyMap<String, Any>(), finnish = true)
        assertEquals(15, d.size)
        assertEquals(true, d["automaattikorjaus"])
        assertEquals(100, d["korkeus"])
        assertEquals("claude", d["ai_palvelu"])
    }

    @Test
    fun `englanniksi kieliriippuvat ovat pois`() {
        val d = SettingsDefaults.missingDefaults(emptyMap<String, Any>(), finnish = false)
        assertEquals(false, d["ehdotus_yleiset"])
        assertEquals(false, d["automaattikorjaus"])
    }

    @Test
    fun `olemassa olevia ei ylikirjoiteta`() {
        val d = SettingsDefaults.missingDefaults(mapOf("varina" to false, "korkeus" to 120), finnish = true)
        assertFalse("varina" in d)
        assertFalse("korkeus" in d)
        assertEquals(13, d.size)
    }

    @Test
    fun `idempotentti`() {
        val all = SettingsDefaults.defaults(finnish = true)
        assertTrue(SettingsDefaults.missingDefaults(all, finnish = true).isEmpty())
    }

    @Test
    fun `sanelumoottorin tuntematon arvo palautuu oletukseen`() {
        assertEquals("jarjestelma", SettingsDefaults.legalDictationEngine("paikallinen"))
        assertEquals("openai", SettingsDefaults.legalDictationEngine("openai"))
        assertEquals("jarjestelma", SettingsDefaults.legalDictationEngine(null))
    }

    @Test
    fun `taustakuvateema ilman dynaamisia vareja palautuu jarjestelmaan`() {
        assertEquals("jarjestelma", SettingsDefaults.legalTheme("taustakuva", dynamicAvailable = false))
        assertEquals("taustakuva", SettingsDefaults.legalTheme("taustakuva", dynamicAvailable = true))
        assertEquals("musta", SettingsDefaults.legalTheme("musta", dynamicAvailable = false))
        assertEquals("jarjestelma", SettingsDefaults.legalTheme("outo", dynamicAvailable = true))
    }

    @Test
    fun `teemavalinta maaraa sovelluksen yotilan`() {
        assertEquals(AppCompatDelegate.MODE_NIGHT_NO, SettingsDefaults.nightMode("vaalea", systemDarkModeAvailable = true))
        assertEquals(AppCompatDelegate.MODE_NIGHT_YES, SettingsDefaults.nightMode("tumma", systemDarkModeAvailable = true))
        assertEquals(AppCompatDelegate.MODE_NIGHT_YES, SettingsDefaults.nightMode("musta", systemDarkModeAvailable = true))
        assertEquals(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM, SettingsDefaults.nightMode("jarjestelma", systemDarkModeAvailable = true))
        assertEquals(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM, SettingsDefaults.nightMode("taustakuva", systemDarkModeAvailable = true))
        assertEquals(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM, SettingsDefaults.nightMode(null, systemDarkModeAvailable = true))
        // Ennen Android 10:tä ei ole järjestelmän tummaa tilaa: järjestelmän mukainen on tumma.
        assertEquals(AppCompatDelegate.MODE_NIGHT_YES, SettingsDefaults.nightMode("jarjestelma", systemDarkModeAvailable = false))
        assertEquals(AppCompatDelegate.MODE_NIGHT_NO, SettingsDefaults.nightMode("vaalea", systemDarkModeAvailable = false))
    }

    @Test
    fun `taitetut osiot vain tunnetuista`() {
        assertEquals(setOf("sanelu"), SettingsDefaults.encodeCollapsed(setOf("sanelu", "roska")))
    }

    @Test
    fun `kaikki teemavalinnat kelpaavat`() {
        for (choice in org.jarsi.ark.theme.ThemeChoice.entries) {
            if (choice == org.jarsi.ark.theme.ThemeChoice.DYNAMIC) continue
            assertEquals(choice.id, SettingsDefaults.legalTheme(choice.id, dynamicAvailable = false))
        }
    }
}
