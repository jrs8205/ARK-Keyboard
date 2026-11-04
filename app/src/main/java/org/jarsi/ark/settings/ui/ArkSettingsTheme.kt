package org.jarsi.ark.settings.ui

import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.unit.Density
import org.jarsi.ark.R
import org.jarsi.ark.theme.ThemeChoice

/** Tosi, kun käyttäjä on poistanut animaatiot käytöstä (kehittäjäasetus tai esteettömyys). */
val LocalReducedMotion: ProvidableCompositionLocal<Boolean> = staticCompositionLocalOf { false }

/**
 * Asetussivun värit kontrastivahdin läpi: jokainen teksti on ensisijainen
 * Material-rooli, jos se täyttää WCAG AAA:n 7:1 taustaansa vasten, muuten
 * johdettu tai varaväri. Säätimille raja on 3:1. Tummassa tilassa kortti
 * ja raide nostetaan taustaa selvästi vaaleammiksi, koska OLED-mustan
 * lähellä Materialin sävyaskeleet (tone 6 → 12) eivät erotu.
 */
data class SettingsColors(
    /** Osiokortin pinta sivun taustalla. */
    val card: Color,
    val onCard: Color,
    val onCardVariant: Color,
    /** Valmiin tilakortin pinta ja teksti. */
    val heroReady: Surface,
    /** Keskeneräisen tilakortin pinta ja teksti. */
    val heroAttention: Surface,
    /** Sivun taustan värinen nappi tai pilleri kortin päällä: tilakortin nappi, arvopilleri ja tukinapit. */
    val chip: Surface,
    /** Dialogin otsikko ja listan tekstit dialogin pohjalla (surfaceContainerHigh). */
    val onDialog: Color,
    val onDialogVariant: Color,
    /** Dialogin tekstinapit. */
    val dialogAction: Color,
    /** Toissijainen teksti suoraan sivun taustalla (versiorivi). */
    val onPageVariant: Color,
    val control: Color,
    val controlOff: Color,
    /** Liukusäätimen passiivinen raide ja askelmerkit kortin päällä. */
    val trackOff: Color,
) {
    data class Surface(val background: Color, val text: Color)

    companion object {
        /** Kortin vaaleus tummassa tilassa; tausta on tone 6 tai musta. */
        private const val CARD_TONE_DARK = 22.0

        /** Pintojen vähimmäissuhde, jolla kortti erottuu taustasta ja raide kortista. */
        const val SURFACE_MINIMUM = 1.4

        /**
         * Pintaparit valitaan järjestyksessä: laitteen oma roolipari, sitten
         * laitteen sävystä johdettu tone 90/30 -pari (tummassa 30/90), joka
         * täyttää 7:1 aina, ja viimeisenä neutraali pinta. Android 16:n
         * paletti antaa containereille keskitumman sävyn (noin 4,7:1), jolloin
         * johdettu pari pitää kortin värillisenä.
         */
        fun from(scheme: ColorScheme, dark: Boolean): SettingsColors {
            val fallback = scheme.onSurface.toArgb()
            val surfaceArgb = scheme.surface.toArgb()
            val card = if (dark) Tones.tone(surfaceArgb, CARD_TONE_DARK, 8.0) else scheme.surfaceContainer.toArgb()
            fun text(background: Int, preferred: Color, minimum: Double = 7.0) = Color(
                Contrast.textOn(background, preferred.toArgb(), fallback, minimum)
            )
            fun derived(source: Color): Pair<Int, Int> {
                val argb = source.toArgb()
                val light = Tones.tone(argb, 90.0, 20.0)
                val deep = Tones.tone(argb, 30.0, 30.0)
                return if (dark) deep to light else light to deep
            }
            fun surface(vararg candidates: Pair<Int, Int>): Surface {
                val pairs = candidates.toList() + listOf(
                    scheme.surfaceContainerHigh.toArgb() to fallback,
                    scheme.surface.toArgb() to fallback,
                )
                val (bg, fg) = Contrast.pickPair(pairs)
                return Surface(Color(bg), Color(fg))
            }
            // Selite pysyy otsikkoa vaimeampana myös vaaleammalla kortilla:
            // roolin oma sävy, sitten siitä johdettu kirkkaampi, vasta sitten onSurface.
            val variant = scheme.onSurfaceVariant.toArgb()
            val onCardVariant = Contrast.pickPair(
                listOf(
                    card to variant,
                    card to Tones.tone(variant, if (dark) 85.0 else 30.0, 16.0),
                    card to fallback,
                )
            ).second
            val secondaryContainer = scheme.secondaryContainer.toArgb()
            val trackOff = Contrast.pickPair(
                listOf(
                    card to secondaryContainer,
                    card to Tones.tone(scheme.secondary.toArgb(), if (dark) 45.0 else 80.0, 24.0),
                ),
                minimum = SURFACE_MINIMUM,
            ).second
            return SettingsColors(
                card = Color(card),
                onCard = text(card, scheme.onSurface),
                onCardVariant = Color(onCardVariant),
                heroReady = surface(
                    scheme.primaryContainer.toArgb() to scheme.onPrimaryContainer.toArgb(),
                    derived(scheme.primary),
                ),
                heroAttention = surface(
                    scheme.tertiaryContainer.toArgb() to scheme.onTertiaryContainer.toArgb(),
                    derived(scheme.tertiary),
                ),
                chip = surface(
                    surfaceArgb to scheme.primary.toArgb(),
                    surfaceArgb to scheme.onPrimaryContainer.toArgb(),
                ),
                onDialog = text(scheme.surfaceContainerHigh.toArgb(), scheme.onSurface),
                onDialogVariant = text(scheme.surfaceContainerHigh.toArgb(), scheme.onSurfaceVariant),
                dialogAction = text(scheme.surfaceContainerHigh.toArgb(), scheme.primary),
                onPageVariant = text(scheme.background.toArgb(), scheme.onSurfaceVariant),
                control = text(card, scheme.primary, minimum = 3.0),
                controlOff = text(card, scheme.outline, minimum = 3.0),
                trackOff = Color(trackOff),
            )
        }
    }
}

val LocalSettingsColors: ProvidableCompositionLocal<SettingsColors> = staticCompositionLocalOf {
    error("ArkSettingsTheme puuttuu")
}

/**
 * Asetussivun teema näppäimistön teemavalinnan mukaan: Material 3 Expressive,
 * taustakuvan värit Android 12+:n paletista, muuten ARKin oma sininen
 * vaaleana, tummana tai mustalla pohjalla. Vaalea ja tumma tulevat
 * yötilasta, jonka aktiviteetti asettaa samasta valinnasta. Fonttiskaala
 * rajataan 150 prosenttiin, jotta rivit pysyvät luettavina ja säätimet ruudulla.
 */
@Composable
fun ArkSettingsTheme(choice: ThemeChoice, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val black = choice == ThemeChoice.BLACK
    val dark = black || isSystemInDarkTheme()
    val scheme = when {
        choice == ThemeChoice.DYNAMIC && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        black -> staticBlackScheme()
        dark -> staticDarkScheme()
        else -> staticLightScheme()
    }
    val density = LocalDensity.current
    val reducedMotion = remember(context) {
        Settings.Global.getFloat(
            context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f
        ) == 0f
    }
    val settingsColors = remember(scheme, dark) { SettingsColors.from(scheme, dark) }
    MaterialExpressiveTheme(
        colorScheme = scheme,
        motionScheme = MotionScheme.expressive(),
    ) {
        CompositionLocalProvider(
            LocalDensity provides Density(
                density.density,
                Contrast.cappedFontScale(density.fontScale),
            ),
            LocalReducedMotion provides reducedMotion,
            LocalSettingsColors provides settingsColors,
            content = content,
        )
    }
}

/**
 * Dialogi on oma ikkunansa, jonka juuri antaa tiheyden uudelleen; tällä
 * dialogin sisältö saa saman fonttikaton kuin sivu.
 */
@Composable
fun CappedFontScale(content: @Composable () -> Unit) {
    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(density.density, Contrast.cappedFontScale(density.fontScale)),
        content = content,
    )
}

@Composable
private fun staticLightScheme(): ColorScheme = lightColorScheme(
    primary = colorResource(R.color.vaalea_korostus),
    onPrimary = colorResource(R.color.vaalea_korostusteksti),
    primaryContainer = colorResource(R.color.vaalea_korostuspohja),
    onPrimaryContainer = colorResource(R.color.vaalea_korostuspohjateksti),
    secondary = colorResource(R.color.vaalea_toissijainen),
    tertiary = colorResource(R.color.vaalea_tertiaari),
    secondaryContainer = colorResource(R.color.vaalea_toissijainen_pohja),
    onSecondaryContainer = colorResource(R.color.vaalea_toissijainen_teksti),
    tertiaryContainer = colorResource(R.color.vaalea_tertiaari_pohja),
    onTertiaryContainer = colorResource(R.color.vaalea_tertiaari_teksti),
    background = colorResource(R.color.vaalea_pinta),
    onBackground = colorResource(R.color.vaalea_teksti),
    surface = colorResource(R.color.vaalea_pinta),
    onSurface = colorResource(R.color.vaalea_teksti),
    onSurfaceVariant = colorResource(R.color.vaalea_vihje),
    surfaceVariant = colorResource(R.color.vaalea_pinta_korkein),
    surfaceContainerLowest = colorResource(R.color.vaalea_pinta_matalin),
    surfaceContainerLow = colorResource(R.color.vaalea_pinta_matala),
    surfaceContainer = colorResource(R.color.vaalea_kortti_pinta),
    surfaceContainerHigh = colorResource(R.color.vaalea_pinta_korkea),
    surfaceContainerHighest = colorResource(R.color.vaalea_pinta_korkein),
    outline = colorResource(R.color.vaalea_aariviiva),
    outlineVariant = colorResource(R.color.vaalea_aariviiva_vaalea),
)

@Composable
private fun staticDarkScheme(): ColorScheme = darkColorScheme(
    primary = colorResource(R.color.tumma_korostus),
    onPrimary = colorResource(R.color.tumma_korostusteksti),
    primaryContainer = colorResource(R.color.tumma_korostuspohja),
    onPrimaryContainer = colorResource(R.color.tumma_korostuspohjateksti),
    secondary = colorResource(R.color.tumma_toissijainen),
    tertiary = colorResource(R.color.tumma_tertiaari),
    secondaryContainer = colorResource(R.color.tumma_toissijainen_pohja),
    onSecondaryContainer = colorResource(R.color.tumma_toissijainen_teksti),
    tertiaryContainer = colorResource(R.color.tumma_tertiaari_pohja),
    onTertiaryContainer = colorResource(R.color.tumma_tertiaari_teksti),
    background = colorResource(R.color.tumma_pinta),
    onBackground = colorResource(R.color.tumma_teksti),
    surface = colorResource(R.color.tumma_pinta),
    onSurface = colorResource(R.color.tumma_teksti),
    onSurfaceVariant = colorResource(R.color.tumma_vihje),
    surfaceVariant = colorResource(R.color.tumma_pinta_korkein),
    surfaceContainerLowest = colorResource(R.color.tumma_pinta_matalin),
    surfaceContainerLow = colorResource(R.color.tumma_pinta_matala),
    surfaceContainer = colorResource(R.color.tumma_kortti_pinta),
    surfaceContainerHigh = colorResource(R.color.tumma_pinta_korkea),
    surfaceContainerHighest = colorResource(R.color.tumma_pinta_korkein),
    outline = colorResource(R.color.tumma_aariviiva),
    outlineVariant = colorResource(R.color.tumma_aariviiva_tumma),
)

/** Musta teema: tumma paletti mustalla sivupohjalla kuten näppäimistössä. */
@Composable
private fun staticBlackScheme(): ColorScheme {
    val black = colorResource(R.color.musta_tausta)
    return staticDarkScheme().copy(
        background = black,
        surface = black,
        surfaceContainerLowest = black,
    )
}
