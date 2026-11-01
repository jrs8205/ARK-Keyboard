package org.jarsi.ark.keyboard

import android.text.InputType
import android.view.inputmethod.EditorInfo

/**
 * Kentän lipuista johdetut säännöt yhdessä paikassa. Sovelluskohtaiset
 * poikkeukset korjataan ensin [AppQuirks]-oliossa, joten muu näppäimistö ei
 * tunne pakettien nimiä.
 *
 * NO_SUGGESTIONS poistaa tarkoituksella vain automaattikorjauksen: esim.
 * Google Keep merkitsee sillä tavalliset kirjoituskenttänsä, ja oma oppiminen
 * on tämän näppäimistön ydin.
 */
data class FieldProfile(
    val password: Boolean,
    val numeric: Boolean,
    /** Kenttä ei tue tekstikyselyjä (TYPE_NULL), vaan odottaa näppäintapahtumia. */
    val rawKeys: Boolean,
    val extraKey: String?,
    val smartSpace: Boolean,
    val capSentences: Boolean,
    val learningAllowed: Boolean,
    val autoCorrectAllowed: Boolean,
    val suggestionsAllowed: Boolean,
    /** Enterin suorittama toiminto; null tarkoittaa rivinvaihtoa. */
    val enterAction: Int?,
) {
    companion object {
        fun from(inputType: Int, imeOptions: Int, packageName: String?): FieldProfile {
            val options = AppQuirks.adjustImeOptions(imeOptions, packageName)
            val inputClass = inputType and InputType.TYPE_MASK_CLASS
            val variation = inputType and InputType.TYPE_MASK_VARIATION
            val isText = inputClass == InputType.TYPE_CLASS_TEXT
            val password = isPassword(inputClass, variation)
            val numeric = inputClass == InputType.TYPE_CLASS_NUMBER ||
                inputClass == InputType.TYPE_CLASS_PHONE ||
                inputClass == InputType.TYPE_CLASS_DATETIME
            val rawKeys = inputType == InputType.TYPE_NULL
            val address = variation == InputType.TYPE_TEXT_VARIATION_URI ||
                variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS ||
                variation == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS
            return FieldProfile(
                password = password,
                numeric = numeric,
                rawKeys = rawKeys,
                extraKey = if (isText) extraKeyFor(variation) else null,
                smartSpace = isText && !password && !address,
                capSentences = isText && inputType and InputType.TYPE_TEXT_FLAG_CAP_SENTENCES != 0,
                learningAllowed = !password &&
                    options and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING == 0,
                autoCorrectAllowed = !(isText && inputType and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS != 0),
                suggestionsAllowed = !password && !numeric && !rawKeys,
                enterAction = enterActionFor(options),
            )
        }

        private fun isPassword(inputClass: Int, variation: Int): Boolean = when (inputClass) {
            InputType.TYPE_CLASS_TEXT ->
                variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                    variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                    variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
            InputType.TYPE_CLASS_NUMBER -> variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD
            else -> false
        }

        private fun extraKeyFor(variation: Int): String? = when (variation) {
            InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
            InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS -> "@"
            InputType.TYPE_TEXT_VARIATION_URI -> "/"
            else -> null
        }

        // Toiminto suoritetaan aina, kun kenttä ei ole sitä kieltänyt — myös
        // monirivisissä viestikentissä, joissa lähetysnappi on nimenomaan pyydetty.
        private fun enterActionFor(options: Int): Int? {
            if (options and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0) return null
            val action = options and EditorInfo.IME_MASK_ACTION
            return if (action == EditorInfo.IME_ACTION_NONE ||
                action == EditorInfo.IME_ACTION_UNSPECIFIED
            ) {
                null
            } else {
                action
            }
        }
    }
}

/**
 * Sovelluskohtaiset poikkeukset, kun sovellus ilmoittaa kenttänsä väärin.
 * Uusi poikkeus lisätään tänne vain todennetun vian perusteella.
 */
object AppQuirks {

    // Pixelin käynnistimen haku kieltää enter-toiminnon, vaikka enterin pitää
    // suorittaa haku; rivinvaihto ei tee hakukentässä mitään hyödyllistä.
    private const val PIXEL_LAUNCHER = "com.google.android.apps.nexuslauncher"

    fun adjustImeOptions(imeOptions: Int, packageName: String?): Int =
        if (packageName == PIXEL_LAUNCHER) {
            imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION.inv()
        } else {
            imeOptions
        }
}
