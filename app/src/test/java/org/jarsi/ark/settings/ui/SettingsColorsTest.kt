package org.jarsi.ark.settings.ui

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Material 3:n perusoletuspaletilla (violetti) korostusvärinen teksti
 * dialogin pohjalla jää noin 5:1:een; vahdin on pudotettava teksti
 * varaväriin, jotta jokainen pari on vähintään 7:1 ja säätimet 3:1.
 */
class SettingsColorsTest {

    private fun ratio(a: Color, b: Color) = Contrast.ratio(a.toArgb(), b.toArgb())

    private fun assertPairs(colors: SettingsColors, card: Color, dialog: Color, page: Color) {
        val text = listOf(
            "onCard" to ratio(card, colors.onCard),
            "onCardVariant" to ratio(card, colors.onCardVariant),
            "heroReady" to ratio(colors.heroReady.background, colors.heroReady.text),
            "heroAttention" to ratio(colors.heroAttention.background, colors.heroAttention.text),
            "heroButton" to ratio(colors.heroButton.background, colors.heroButton.text),
            "tonalButton" to ratio(colors.tonalButton.background, colors.tonalButton.text),
            "onDialog" to ratio(dialog, colors.onDialog),
            "onDialogVariant" to ratio(dialog, colors.onDialogVariant),
            "dialogAction" to ratio(dialog, colors.dialogAction),
            "onPageVariant" to ratio(page, colors.onPageVariant),
        )
        text.forEach { (name, r) -> assertTrue("$name $r", r >= 7.0) }
        assertTrue(ratio(card, colors.control) >= 3.0)
        assertTrue(ratio(card, colors.controlOff) >= 3.0)
    }

    @Test
    fun `vaalea perusoletus tayttaa AAA vahdin kautta`() {
        val scheme = lightColorScheme()
        assertPairs(
            SettingsColors.from(scheme, dark = false),
            card = scheme.surfaceContainer,
            dialog = scheme.surfaceContainerHigh,
            page = scheme.background,
        )
    }

    @Test
    fun `tumma perusoletus tayttaa AAA vahdin kautta`() {
        val scheme = darkColorScheme()
        assertPairs(
            SettingsColors.from(scheme, dark = true),
            card = scheme.surfaceContainer,
            dialog = scheme.surfaceContainerHigh,
            page = scheme.background,
        )
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
        assertTrue(ratio(colors.tonalButton.background, colors.tonalButton.text) >= 7.0)
        // Johdettu pari on värillinen, ei neutraali harmaa.
        assertTrue(colors.heroReady.background != scheme.surfaceContainerHigh)
    }
}
