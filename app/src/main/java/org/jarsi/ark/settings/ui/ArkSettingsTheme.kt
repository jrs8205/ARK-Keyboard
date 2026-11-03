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

/** Tosi, kun käyttäjä on poistanut animaatiot käytöstä (kehittäjäasetus tai esteettömyys). */
val LocalReducedMotion: ProvidableCompositionLocal<Boolean> = staticCompositionLocalOf { false }

/**
 * Asetussivun tekstivärit kontrastivahdin läpi: jokainen on ensisijainen
 * Material-rooli, jos se täyttää WCAG AAA:n 7:1 taustaansa vasten, muuten
 * onSurface. Säätimille raja on 3:1.
 */
data class SettingsColors(
    val onCard: Color,
    val onCardVariant: Color,
    val onHeroPrimary: Color,
    val onHeroTertiary: Color,
    val onTonalButton: Color,
    val control: Color,
    val controlOff: Color,
) {
    companion object {
        fun from(scheme: ColorScheme): SettingsColors {
            val fallback = scheme.onSurface.toArgb()
            fun text(background: Color, preferred: Color, minimum: Double = 7.0) = Color(
                Contrast.textOn(background.toArgb(), preferred.toArgb(), fallback, minimum)
            )
            return SettingsColors(
                onCard = text(scheme.surfaceContainer, scheme.onSurface),
                onCardVariant = text(scheme.surfaceContainer, scheme.onSurfaceVariant),
                onHeroPrimary = text(scheme.primaryContainer, scheme.onPrimaryContainer),
                onHeroTertiary = text(scheme.tertiaryContainer, scheme.onTertiaryContainer),
                onTonalButton = text(scheme.secondaryContainer, scheme.onSecondaryContainer),
                control = text(scheme.surfaceContainer, scheme.primary, minimum = 3.0),
                controlOff = text(scheme.surfaceContainer, scheme.outline, minimum = 3.0),
            )
        }
    }
}

val LocalSettingsColors: ProvidableCompositionLocal<SettingsColors> = staticCompositionLocalOf {
    error("ArkSettingsTheme puuttuu")
}

/**
 * Asetussivun teema: Material 3 Expressive, Android 12+:lla taustakuvan
 * paletti ja sitä vanhemmilla ARKin oma sininen. Fonttiskaala rajataan
 * 150 prosenttiin, jotta rivit pysyvät luettavina ja säätimet ruudulla.
 */
@Composable
fun ArkSettingsTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    val scheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else if (dark) {
        staticDarkScheme()
    } else {
        staticLightScheme()
    }
    val density = LocalDensity.current
    val reducedMotion = remember(context) {
        Settings.Global.getFloat(
            context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f
        ) == 0f
    }
    val settingsColors = remember(scheme) { SettingsColors.from(scheme) }
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

@Composable
private fun staticLightScheme(): ColorScheme = lightColorScheme(
    primary = colorResource(R.color.vaalea_korostus),
    onPrimary = colorResource(R.color.vaalea_korostusteksti),
    primaryContainer = colorResource(R.color.vaalea_korostuspohja),
    onPrimaryContainer = colorResource(R.color.vaalea_korostuspohjateksti),
    secondaryContainer = colorResource(R.color.vaalea_toissijainen_pohja),
    onSecondaryContainer = colorResource(R.color.vaalea_toissijainen_teksti),
    tertiaryContainer = colorResource(R.color.vaalea_tertiaari_pohja),
    onTertiaryContainer = colorResource(R.color.vaalea_tertiaari_teksti),
    background = colorResource(R.color.vaalea_pinta),
    onBackground = colorResource(R.color.vaalea_teksti),
    surface = colorResource(R.color.vaalea_pinta),
    onSurface = colorResource(R.color.vaalea_teksti),
    onSurfaceVariant = colorResource(R.color.vaalea_vihje),
    surfaceContainer = colorResource(R.color.vaalea_kortti_pinta),
    surfaceContainerHighest = colorResource(R.color.vaalea_erikoisnappain),
    outline = colorResource(R.color.vaalea_aariviiva),
)

@Composable
private fun staticDarkScheme(): ColorScheme = darkColorScheme(
    primary = colorResource(R.color.tumma_korostus),
    onPrimary = colorResource(R.color.tumma_korostusteksti),
    primaryContainer = colorResource(R.color.tumma_korostuspohja),
    onPrimaryContainer = colorResource(R.color.tumma_korostuspohjateksti),
    secondaryContainer = colorResource(R.color.tumma_toissijainen_pohja),
    onSecondaryContainer = colorResource(R.color.tumma_toissijainen_teksti),
    tertiaryContainer = colorResource(R.color.tumma_tertiaari_pohja),
    onTertiaryContainer = colorResource(R.color.tumma_tertiaari_teksti),
    background = colorResource(R.color.tumma_pinta),
    onBackground = colorResource(R.color.tumma_teksti),
    surface = colorResource(R.color.tumma_pinta),
    onSurface = colorResource(R.color.tumma_teksti),
    onSurfaceVariant = colorResource(R.color.tumma_vihje),
    surfaceContainer = colorResource(R.color.tumma_kortti_pinta),
    surfaceContainerHighest = colorResource(R.color.tumma_kortti),
    outline = colorResource(R.color.tumma_aariviiva),
)
