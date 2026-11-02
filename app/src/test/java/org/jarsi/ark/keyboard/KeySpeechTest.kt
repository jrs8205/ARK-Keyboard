package org.jarsi.ark.keyboard

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Test

class KeySpeechTest {

    @Test
    fun `englanniksi nappaimet sanotaan englanniksi`() {
        val space = Key(KeyAction.Space, "")
        assertEquals("Space", KeySpeech.describe(space, ShiftState.OFF, "", false, false, KeySpeech.Language.EN))
        assertEquals("Välilyönti", KeySpeech.describe(space, ShiftState.OFF, "", false, false))
        assertEquals("slash", KeySpeech.symbol("/", KeySpeech.Language.EN))
        assertEquals("kauttaviiva", KeySpeech.symbol("/"))
        val letter = Key(KeyAction.Text("a"), "a")
        assertEquals("capital A", KeySpeech.describe(letter, ShiftState.SHIFT, "", false, false, KeySpeech.Language.EN))
        assertEquals("character", KeySpeech.describe(letter, ShiftState.OFF, "", false, true, KeySpeech.Language.EN))
    }

    private fun say(
        key: Key,
        shift: ShiftState = ShiftState.OFF,
        enter: String = "⏎",
        obscured: Boolean = false,
    ) = KeySpeech.describe(key, shift, enter, selectionActive = false, obscured = obscured)

    private fun text(char: String) = Key(KeyAction.Text(char), char)

    @Test
    fun `kirjain sanotaan sellaisenaan`() {
        assertEquals("q", say(text("q")))
        assertEquals("ä", say(text("ä")))
    }

    @Test
    fun `isolla kirjaimella on oma kuvaus`() {
        assertEquals("iso Q", say(text("q"), ShiftState.SHIFT))
        assertEquals("iso Ä", say(text("ä"), ShiftState.CAPS))
    }

    @Test
    fun `shift ei vaikuta numeroihin ja valimerkkeihin`() {
        assertEquals("1", say(text("1"), ShiftState.SHIFT))
        assertEquals("pilkku", say(text(","), ShiftState.SHIFT))
    }

    @Test
    fun `valimerkit sanotaan nimella`() {
        assertEquals("piste", say(text(".")))
        assertEquals("ät", say(text("@")))
        assertEquals("kysymysmerkki", say(text("?")))
    }

    @Test
    fun `monimerkkinen nappain sanotaan sellaisenaan`() {
        assertEquals("https://", say(text("https://")))
    }

    @Test
    fun `salasanakentassa merkkia ei paljasteta`() {
        assertEquals("merkki", say(text("q"), obscured = true))
        assertEquals("merkki", say(text("1"), obscured = true))
        assertEquals("Askelpalautin", say(Key(KeyAction.Backspace), obscured = true))
    }

    @Test
    fun `erikoisnappaimet sanotaan nimella`() {
        assertEquals("Välilyönti", say(Key(KeyAction.Space)))
        assertEquals("Askelpalautin", say(Key(KeyAction.Backspace)))
        assertEquals("Symbolit", say(Key(KeyAction.Symbols)))
        assertEquals("Lisää symboleja", say(Key(KeyAction.SymbolsMore)))
        assertEquals("Kirjaimet", say(Key(KeyAction.Letters)))
    }

    @Test
    fun `vaihto kertoo tilansa`() {
        assertEquals("Vaihto", say(Key(KeyAction.Shift)))
        assertEquals("Vaihto, iso kirjain", say(Key(KeyAction.Shift), ShiftState.SHIFT))
        assertEquals("Vaihto, isot kirjaimet lukittu", say(Key(KeyAction.Shift), ShiftState.CAPS))
    }

    @Test
    fun `enter kertoo kentan toiminnon`() {
        assertEquals("Rivinvaihto", say(Key(KeyAction.Enter)))
        assertEquals("Hae", say(Key(KeyAction.Enter), enter = "Hae"))
    }

    @Test
    fun `nuolet ja muokkaustoiminnot`() {
        assertEquals("Ylös", say(Key(KeyAction.Arrow(KeyEvent.KEYCODE_DPAD_UP))))
        assertEquals("Vasemmalle", say(Key(KeyAction.Arrow(KeyEvent.KEYCODE_DPAD_LEFT))))
        assertEquals("Kopioi", say(Key(KeyAction.Edit(EditCommand.COPY), "Kopioi")))
    }

    @Test
    fun `valinta kertoo tilansa`() {
        val select = Key(KeyAction.Edit(EditCommand.SELECT), "Valitse")
        assertEquals("Valitse", KeySpeech.describe(select, ShiftState.OFF, "⏎", false, false))
        assertEquals("Valitse, päällä", KeySpeech.describe(select, ShiftState.OFF, "⏎", true, false))
    }

    @Test
    fun `merkkivalikon merkki kuvataan samoin`() {
        assertEquals("kauttaviiva", KeySpeech.symbol("/"))
        assertEquals("Å", KeySpeech.symbol("Å"))
    }

    @Test
    fun `vaihtoehtomerkki salataan salasanakentassa`() {
        assertEquals("pilkku", KeySpeech.symbol(",", KeySpeech.Language.FI))
        assertEquals("merkki", KeySpeech.symbol(",", KeySpeech.Language.FI, obscured = true))
        assertEquals("character", KeySpeech.symbol(";", KeySpeech.Language.EN, obscured = true))
        assertEquals("merkki", KeySpeech.symbol("Q", KeySpeech.Language.FI, obscured = true))
    }
}
