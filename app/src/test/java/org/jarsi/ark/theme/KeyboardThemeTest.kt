package org.jarsi.ark.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.pow

class KeyboardThemeTest {

    private val colors: Map<String, Int> by lazy {
        val xml = File("src/main/res/values/colors.xml").readText()
        Regex("<color name=\"([a-z_]+)\">#([0-9A-Fa-f]{6})</color>")
            .findAll(xml)
            .associate { it.groupValues[1] to it.groupValues[2].toInt(16) }
    }

    private fun luminance(rgb: Int): Double {
        fun channel(shift: Int): Double {
            val c = (rgb shr shift and 0xFF) / 255.0
            return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
    }

    private fun contrast(a: Int, b: Int): Double {
        val high = maxOf(luminance(a), luminance(b))
        val low = minOf(luminance(a), luminance(b))
        return (high + 0.05) / (low + 0.05)
    }

    private fun assertAaa(theme: String, foreground: String, background: String) {
        val ratio = contrast(colors.getValue("${theme}_$foreground"), colors.getValue("${theme}_$background"))
        assertTrue("$theme: $foreground/$background = $ratio", ratio >= 7.0)
    }

    @Test
    fun `kiinteat teemat tayttavat AAA-tason`() {
        for (theme in listOf("vaalea", "tumma", "musta")) {
            for (surface in listOf("tausta", "nappain", "erikoisnappain", "painettu")) {
                assertAaa(theme, "teksti", surface)
            }
            // Vihjeet piirretään näppäimille ja rivien taustalle.
            assertAaa(theme, "vihje", "tausta")
            assertAaa(theme, "vihje", "nappain")
            assertAaa(theme, "korostusteksti", "korostus")
        }
    }

    @Test
    fun `musta teema on oikeasti musta`() {
        assertEquals(0x000000, colors.getValue("musta_tausta"))
    }

    @Test
    fun `tuntematon tai puuttuva valinta seuraa jarjestelmaa`() {
        assertEquals(ThemeChoice.SYSTEM, ThemeChoice.from(null))
        assertEquals(ThemeChoice.SYSTEM, ThemeChoice.from("olematon"))
        assertEquals(ThemeChoice.BLACK, ThemeChoice.from("musta"))
    }
}
