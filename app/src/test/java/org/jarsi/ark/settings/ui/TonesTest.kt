package org.jarsi.ark.settings.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TonesTest {

    @Test
    fun `vaaleus 100 ilman kylläisyyttä on valkoinen ja 0 musta`() {
        assertEquals(0xFFFFFFFF.toInt(), Tones.lchToArgb(100.0, 0.0, 0.0))
        assertEquals(0xFF000000.toInt(), Tones.lchToArgb(0.0, 0.0, 0.0))
    }

    @Test
    fun `punaisen savykulma on noin 40 astetta`() {
        assertEquals(40.0, Tones.hueOf(0xFFFF0000.toInt()), 1.0)
    }

    @Test
    fun `sininen savykulma on noin 306 astetta`() {
        assertEquals(306.0, Tones.hueOf(0xFF0000FF.toInt()), 1.0)
    }

    @Test
    fun `tone 90 ja 30 samasta savysta ylittavat seitseman`() {
        val source = 0xFF5E73A9.toInt()
        val light = Tones.tone(source, 90.0, 20.0)
        val dark = Tones.tone(source, 30.0, 30.0)
        assertTrue(Contrast.ratio(light, dark) >= 7.0)
    }

    @Test
    fun `tone 30 ja 90 toimivat myos tummassa`() {
        val source = 0xFFA886A6.toInt()
        assertTrue(Contrast.ratio(Tones.tone(source, 30.0, 30.0), Tones.tone(source, 90.0, 20.0)) >= 7.0)
    }

    @Test
    fun `kylläisyys ei vie varia sRGB-avaruuden ulkopuolelle`() {
        // Täysin kylläinen vihreä tone 90:ssä ei mahdu sRGB:hen; kylläisyyttä
        // lasketaan kunnes mahtuu, eikä kanava vuoda yli.
        val argb = Tones.lchToArgb(90.0, 120.0, 140.0)
        val r = argb shr 16 and 0xFF
        val g = argb shr 8 and 0xFF
        val b = argb and 0xFF
        assertTrue(r in 0..255 && g in 0..255 && b in 0..255)
        assertTrue(g > r && g > b)
    }

    @Test
    fun `harmaa lahde pysyy harmaana`() {
        // Monokromaattisella paletilla sävykulma on kohinaa; kylläisyys ei saa ylittää lähteen omaa.
        val gray = Tones.tone(0xFF808080.toInt(), 90.0, 20.0)
        val r = gray shr 16 and 0xFF
        val g = gray shr 8 and 0xFF
        val b = gray and 0xFF
        assertTrue("$r $g $b", maxOf(r, g, b) - minOf(r, g, b) <= 2)
    }
}
