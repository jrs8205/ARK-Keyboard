package org.jarsi.ark.theme

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import androidx.annotation.ColorRes
import androidx.annotation.RequiresApi
import androidx.core.graphics.ColorUtils
import androidx.preference.PreferenceManager
import org.jarsi.ark.R
import org.jarsi.ark.data.DeviceLock

/** Käyttäjän valitsema teema; [SYSTEM] seuraa puhelimen tummaa tilaa. */
enum class ThemeChoice(val id: String) {
    SYSTEM("jarjestelma"),
    LIGHT("vaalea"),
    DARK("tumma"),
    BLACK("musta"),
    DYNAMIC("taustakuva");

    companion object {
        fun from(id: String?): ThemeChoice = entries.firstOrNull { it.id == id } ?: SYSTEM
    }
}

/**
 * Näppäimistön väriteema. Värit määritellään kertaalleen colors.xml:ssä
 * Material 3 -rooleittain; asetussivu käyttää samoja värejä teemansa kautta.
 * Kaikki parit täyttävät WCAG AAA -tason (7:1), ks. KeyboardThemeTest.
 */
data class KeyboardTheme(
    val background: Int,
    val key: Int,
    val specialKey: Int,
    val keyPressed: Int,
    val text: Int,
    val hint: Int,
    val accent: Int,
    val accentText: Int,
    /** Vaalea pinta: navigointipalkin kuvakkeet piirretään tummina. */
    val light: Boolean,
) {
    companion object {
        const val PREF_KEY = "teema"

        /**
         * Lataa valitun teeman. Vanhoilla Android-versioilla (ennen 10:tä) ei
         * ole järjestelmän tummaa tilaa, joten niillä järjestelmän mukainen
         * teema on aina tumma. Lukitusnäytöllä ennen ensimmäistä avausta
         * asetuksia ei voi lukea, ja silloin seurataan järjestelmää.
         */
        fun load(context: Context): KeyboardTheme {
            val systemLight = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                context.resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK != Configuration.UI_MODE_NIGHT_YES
            val choice = if (DeviceLock.isUnlocked(context)) {
                ThemeChoice.from(
                    PreferenceManager.getDefaultSharedPreferences(context)
                        .getString(PREF_KEY, null)
                )
            } else {
                ThemeChoice.SYSTEM
            }
            return when (choice) {
                ThemeChoice.SYSTEM -> if (systemLight) light(context) else dark(context)
                ThemeChoice.LIGHT -> light(context)
                ThemeChoice.DARK -> dark(context)
                ThemeChoice.BLACK -> black(context)
                ThemeChoice.DYNAMIC ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        dynamic(context, systemLight)
                    } else if (systemLight) {
                        light(context)
                    } else {
                        dark(context)
                    }
            }
        }

        private fun light(context: Context) = KeyboardTheme(
            background = context.getColor(R.color.vaalea_tausta),
            key = context.getColor(R.color.vaalea_nappain),
            specialKey = context.getColor(R.color.vaalea_erikoisnappain),
            keyPressed = context.getColor(R.color.vaalea_painettu),
            text = context.getColor(R.color.vaalea_teksti),
            hint = context.getColor(R.color.vaalea_vihje),
            accent = context.getColor(R.color.vaalea_korostus),
            accentText = context.getColor(R.color.vaalea_korostusteksti),
            light = true,
        )

        private fun dark(context: Context) = KeyboardTheme(
            background = context.getColor(R.color.tumma_tausta),
            key = context.getColor(R.color.tumma_nappain),
            specialKey = context.getColor(R.color.tumma_erikoisnappain),
            keyPressed = context.getColor(R.color.tumma_painettu),
            text = context.getColor(R.color.tumma_teksti),
            hint = context.getColor(R.color.tumma_vihje),
            accent = context.getColor(R.color.tumma_korostus),
            accentText = context.getColor(R.color.tumma_korostusteksti),
            light = false,
        )

        private fun black(context: Context) = KeyboardTheme(
            background = context.getColor(R.color.musta_tausta),
            key = context.getColor(R.color.musta_nappain),
            specialKey = context.getColor(R.color.musta_erikoisnappain),
            keyPressed = context.getColor(R.color.musta_painettu),
            text = context.getColor(R.color.musta_teksti),
            hint = context.getColor(R.color.musta_vihje),
            accent = context.getColor(R.color.musta_korostus),
            accentText = context.getColor(R.color.musta_korostusteksti),
            light = false,
        )

        /**
         * Taustakuvan värit (Android 12+). Järjestelmän sävyt ovat luminanssiltaan
         * kiinteitä väristä riippumatta, joten valitut sävyparit säilyttävät
         * kontrastin taustakuvasta riippumatta: T10/T80 yli 10:1, T20/T80 ja
         * T30/T95 noin 8:1.
         */
        @RequiresApi(Build.VERSION_CODES.S)
        private fun dynamic(context: Context, light: Boolean): KeyboardTheme {
            fun color(@ColorRes id: Int) = context.getColor(id)
            return if (light) {
                KeyboardTheme(
                    background = color(android.R.color.system_neutral1_50),
                    key = color(android.R.color.system_neutral1_0),
                    specialKey = color(android.R.color.system_neutral2_100),
                    keyPressed = color(android.R.color.system_neutral2_200),
                    text = color(android.R.color.system_neutral1_900),
                    hint = color(android.R.color.system_neutral2_700),
                    accent = color(android.R.color.system_accent1_700),
                    accentText = color(android.R.color.system_accent1_0),
                    light = true,
                )
            } else {
                val background = color(android.R.color.system_neutral1_900)
                val key = color(android.R.color.system_neutral2_800)
                KeyboardTheme(
                    background = background,
                    key = key,
                    specialKey = ColorUtils.blendARGB(background, key, 0.5f),
                    keyPressed = color(android.R.color.system_neutral2_700),
                    text = color(android.R.color.system_neutral1_50),
                    hint = color(android.R.color.system_neutral2_200),
                    accent = color(android.R.color.system_accent1_200),
                    accentText = color(android.R.color.system_accent1_800),
                    light = false,
                )
            }
        }
    }
}
