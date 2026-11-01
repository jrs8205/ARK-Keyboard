package org.jarsi.ark.keyboard

import android.text.InputType
import android.view.inputmethod.EditorInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FieldProfileTest {

    private val text = InputType.TYPE_CLASS_TEXT

    private fun profile(inputType: Int, imeOptions: Int = 0, packageName: String? = "esim") =
        FieldProfile.from(inputType, imeOptions, packageName)

    @Test
    fun `tavallinen tekstikentta sallii kaiken`() {
        val field = profile(text or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES)
        assertFalse(field.password)
        assertFalse(field.numeric)
        assertFalse(field.rawKeys)
        assertTrue(field.smartSpace)
        assertTrue(field.capSentences)
        assertTrue(field.learningAllowed)
        assertTrue(field.autoCorrectAllowed)
        assertTrue(field.suggestionsAllowed)
        assertNull(field.extraKey)
    }

    @Test
    fun `salasanakentat tunnistetaan`() {
        listOf(
            text or InputType.TYPE_TEXT_VARIATION_PASSWORD,
            text or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            text or InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
            InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD,
        ).forEach { type ->
            val field = profile(type)
            assertTrue(field.password)
            assertFalse(field.learningAllowed)
            assertFalse(field.suggestionsAllowed)
            assertFalse(field.smartSpace)
        }
    }

    @Test
    fun `numero- puhelin- ja aikakentat saavat numerosivun ilman ehdotuksia`() {
        listOf(
            InputType.TYPE_CLASS_NUMBER,
            InputType.TYPE_CLASS_PHONE,
            InputType.TYPE_CLASS_DATETIME,
        ).forEach { type ->
            val field = profile(type)
            assertTrue(field.numeric)
            assertFalse(field.suggestionsAllowed)
            assertFalse(field.smartSpace)
        }
    }

    @Test
    fun `osoite- ja sahkopostikenttien lisamerkki ja valimerkkisaannot`() {
        val url = profile(text or InputType.TYPE_TEXT_VARIATION_URI)
        assertEquals("/", url.extraKey)
        assertFalse(url.smartSpace)
        listOf(
            InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
            InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS,
        ).forEach { variation ->
            val mail = profile(text or variation)
            assertEquals("@", mail.extraKey)
            assertFalse(mail.smartSpace)
        }
    }

    @Test
    fun `no suggestions poistaa vain automaattikorjauksen`() {
        val keep = profile(text or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS)
        assertFalse(keep.autoCorrectAllowed)
        assertTrue(keep.suggestionsAllowed)
        assertTrue(keep.smartSpace)
        assertTrue(keep.learningAllowed)
    }

    @Test
    fun `oppimisen kieltava kentta ei opi mutta ehdottaa`() {
        val field = profile(text, EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING)
        assertFalse(field.learningAllowed)
        assertTrue(field.suggestionsAllowed)
    }

    @Test
    fun `tyypiton kentta kayttaa nappaintapahtumia ilman ehdotuksia`() {
        val field = profile(InputType.TYPE_NULL)
        assertTrue(field.rawKeys)
        assertFalse(field.suggestionsAllowed)
        assertFalse(field.smartSpace)
    }

    @Test
    fun `enter suorittaa kentan pyytaman toiminnon`() {
        assertEquals(
            EditorInfo.IME_ACTION_SEARCH,
            profile(text, EditorInfo.IME_ACTION_SEARCH).enterAction,
        )
    }

    @Test
    fun `enter tekee rivinvaihdon kun toimintoa ei ole tai se on kielletty`() {
        assertNull(profile(text).enterAction)
        assertNull(profile(text, EditorInfo.IME_ACTION_NONE).enterAction)
        assertNull(
            profile(
                text,
                EditorInfo.IME_ACTION_SEND or EditorInfo.IME_FLAG_NO_ENTER_ACTION,
            ).enterAction
        )
    }

    @Test
    fun `pixelin kaynnistimen haku suorittaa haun vaikka kentta kieltaa toiminnon`() {
        val options = EditorInfo.IME_ACTION_SEARCH or EditorInfo.IME_FLAG_NO_ENTER_ACTION
        assertEquals(
            EditorInfo.IME_ACTION_SEARCH,
            profile(text, options, "com.google.android.apps.nexuslauncher").enterAction,
        )
    }
}
