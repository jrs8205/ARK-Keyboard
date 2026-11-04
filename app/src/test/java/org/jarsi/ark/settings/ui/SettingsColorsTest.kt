package org.jarsi.ark.settings.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Material 3:n perusoletuspaletilla (violetti) korostusvärinen teksti
 * dialogin pohjalla jää noin 5:1:een; vahdin on pudotettava teksti
 * varaväriin, jotta jokainen pari on vähintään 7:1 ja säätimet 3:1.
 * Tummassa tilassa kortin on lisäksi erotuttava sivun taustasta ja
 * raiteen kortista: OLED-mustan lähellä Materialin sävyaskeleet katoavat.
 */
class SettingsColorsTest {

    private fun ratio(a: Color, b: Color) = Contrast.ratio(a.toArgb(), b.toArgb())

    private fun assertPairs(colors: SettingsColors, scheme: ColorScheme) {
        val card = colors.card
        val dialog = scheme.surfaceContainerHigh
        val page = scheme.background
        val text = listOf(
            "onCard" to ratio(card, colors.onCard),
            "onCardVariant" to ratio(card, colors.onCardVariant),
            "heroReady" to ratio(colors.heroReady.background, colors.heroReady.text),
            "heroAttention" to ratio(colors.heroAttention.background, colors.heroAttention.text),
            "chip" to ratio(colors.chip.background, colors.chip.text),
            "onDialog" to ratio(dialog, colors.onDialog),
            "onDialogVariant" to ratio(dialog, colors.onDialogVariant),
            "dialogAction" to ratio(dialog, colors.dialogAction),
            "onPageVariant" to ratio(page, colors.onPageVariant),
        )
        text.forEach { (name, r) -> assertTrue("$name $r", r >= 7.0) }
        assertTrue(ratio(card, colors.control) >= 3.0)
        assertTrue(ratio(card, colors.controlOff) >= 3.0)
        assertTrue("raide ${ratio(card, colors.trackOff)}", ratio(card, colors.trackOff) >= 1.4)
        // Selite on hierarkiassa otsikkoa vaimeampi, ei sama väri.
        assertNotEquals(colors.onCard, colors.onCardVariant)
    }

    @Test
    fun `vaalea perusoletus tayttaa AAA vahdin kautta`() {
        val scheme = lightColorScheme()
        val colors = SettingsColors.from(scheme, dark = false)
        assertPairs(colors, scheme)
        assertEquals(scheme.surfaceContainer, colors.card)
    }

    @Test
    fun `tumma perusoletus tayttaa AAA ja kortti erottuu taustasta`() {
        val scheme = darkColorScheme()
        val colors = SettingsColors.from(scheme, dark = true)
        assertPairs(colors, scheme)
        assertTrue("kortti ${ratio(scheme.background, colors.card)}", ratio(scheme.background, colors.card) >= 1.4)
    }

    @Test
    fun `musta tausta saa erottuvan kortin`() {
        val scheme = darkColorScheme(background = Color.Black, surface = Color.Black)
        val colors = SettingsColors.from(scheme, dark = true)
        assertPairs(colors, scheme)
        assertTrue(ratio(Color.Black, colors.card) >= 1.4)
        assertEquals(Color.Black, colors.chip.background)
    }

    @Test
    fun `tumma ARK-paletti pitaa selitteen vaimeana`() {
        // colors.xml: tumma_pinta tone 6, tumma_teksti, tumma_vihje tone ~78.
        val scheme = darkColorScheme(
            background = Color(0xFF111316),
            surface = Color(0xFF111316),
            onSurface = Color(0xFFE8EAF0),
            onSurfaceVariant = Color(0xFFBAC0CC),
            secondary = Color(0xFFB3C8E4),
            secondaryContainer = Color(0xFF37485C),
        )
        val colors = SettingsColors.from(scheme, dark = true)
        assertPairs(colors, scheme)
    }

    @Test
    fun `keskitumma container pudottaa johdettuun pariin`() {
        // Android 16:n mitattu paletti: primaryContainer tone 50, teksti valkoinen (4,7:1).
        val scheme = lightColorScheme(
            primary = Color(0xFF2A4174),
            primaryContainer = Color(0xFF5E73A9),
            onPrimaryContainer = Color.White,
            tertiaryContainer = Color(0xFF8A6A89),
            onTertiaryContainer = Color.White,
            secondaryContainer = Color(0xFF6E7488),
            onSecondaryContainer = Color.White,
        )
        val colors = SettingsColors.from(scheme, dark = false)
        assertTrue(ratio(colors.heroReady.background, colors.heroReady.text) >= 7.0)
        assertTrue(ratio(colors.heroAttention.background, colors.heroAttention.text) >= 7.0)
        // Johdettu pari on värillinen, ei neutraali harmaa.
        assertTrue(colors.heroReady.background != scheme.surfaceContainerHigh)
    }
}
