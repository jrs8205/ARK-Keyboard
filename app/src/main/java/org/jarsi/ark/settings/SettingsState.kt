package org.jarsi.ark.settings

import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import org.jarsi.ark.data.ApiKeyStore
import org.jarsi.ark.data.ChatGptPlan
import org.jarsi.ark.dictation.OpenAiDictation
import org.jarsi.ark.theme.KeyboardTheme

/**
 * Asetussivun tila: arvot luetaan samasta asetustiedostosta, jota
 * näppäimistöpalvelu lukee, ja pidetään ajan tasalla tiedoston
 * kuuntelijalla. Siksi myös varmuuskopion tuonti ja avaimen tallennus
 * näkyvät riveillä ilman erillistä päivitystä.
 */
class SettingsState(
    private val prefs: SharedPreferences,
    private val finnish: Boolean,
    val dynamicAvailable: Boolean,
) {
    var theme by mutableStateOf("jarjestelma"); private set
    var height by mutableIntStateOf(100); private set
    var sounds by mutableStateOf(false); private set
    var vibrate by mutableStateOf(true); private set
    var preview by mutableStateOf(true); private set
    var numberRow by mutableStateOf(true); private set
    var doubleSpace by mutableStateOf(true); private set
    var suggestions by mutableStateOf(true); private set
    var suggestionSpace by mutableStateOf(true); private set
    var commonWords by mutableStateOf(false); private set
    var autoCorrect by mutableStateOf(false); private set
    var translationMemory by mutableIntStateOf(30); private set
    var dictationSilence by mutableIntStateOf(5); private set
    var dictationEngine by mutableStateOf("jarjestelma"); private set
    var dictationModel by mutableStateOf<String?>(null); private set
    var aiService by mutableStateOf("claude"); private set
    var claudeModel by mutableStateOf<String?>(null); private set
    var openAiModel by mutableStateOf<String?>(null); private set
    var planModel by mutableStateOf<String?>(null); private set
    var claudeKeySaved by mutableStateOf(false); private set
    var openAiKeySaved by mutableStateOf(false); private set
    var planSignedIn by mutableStateOf(false); private set
    var planEmail by mutableStateOf<String?>(null); private set
    var collapsed by mutableStateOf<Set<String>>(emptySet()); private set

    // Tiedosto pitää kuuntelijaa vain heikosti, joten viite on kentässä.
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> readAll() }

    /** Kirjoittaa puuttuvat oletukset, lukee kaiken ja alkaa kuunnella muutoksia. */
    fun start() {
        ensureDefaults()
        readAll()
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    fun stop() {
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
    }

    /** Oletukset tiedostoon kuten aiempi asetuslista teki; vain puuttuvat avaimet. */
    private fun ensureDefaults() {
        val missing = SettingsDefaults.missingDefaults(prefs.all, finnish)
        val engine = prefs.getString("sanelu_moottori", null)
        val legalEngine = SettingsDefaults.legalDictationEngine(engine)
        val theme = prefs.getString(KeyboardTheme.PREF_KEY, null)
        val legalTheme = SettingsDefaults.legalTheme(theme, dynamicAvailable)
        if (missing.isEmpty() && engine == legalEngine && theme == legalTheme) return
        prefs.edit {
            missing.forEach { (key, value) ->
                when (value) {
                    is Boolean -> putBoolean(key, value)
                    is Int -> putInt(key, value)
                    is String -> putString(key, value)
                }
            }
            if (engine != legalEngine) putString("sanelu_moottori", legalEngine)
            if (theme != null && theme != legalTheme) putString(KeyboardTheme.PREF_KEY, legalTheme)
        }
    }

    private fun readAll() {
        theme = prefs.getString(KeyboardTheme.PREF_KEY, null) ?: "jarjestelma"
        height = prefs.getInt("korkeus", 100)
        sounds = prefs.getBoolean("aanet", false)
        vibrate = prefs.getBoolean("varina", true)
        preview = prefs.getBoolean("esikatselu", true)
        numberRow = prefs.getBoolean("numerorivi", true)
        doubleSpace = prefs.getBoolean("kaksoisvali_piste", true)
        suggestions = prefs.getBoolean("ehdotukset", true)
        suggestionSpace = prefs.getBoolean("ehdotus_valilyonti", true)
        commonWords = prefs.getBoolean("ehdotus_yleiset", finnish)
        autoCorrect = prefs.getBoolean("automaattikorjaus", finnish)
        translationMemory = prefs.getInt("kaannos_muisti", 30)
        dictationSilence = prefs.getInt("sanelu_hiljaisuus", 5)
        dictationEngine = SettingsDefaults.legalDictationEngine(prefs.getString("sanelu_moottori", null))
        dictationModel = prefs.getString(OpenAiDictation.PREF_MODEL, null)?.takeIf { it.isNotBlank() }
        aiService = prefs.getString("ai_palvelu", null) ?: "claude"
        claudeModel = prefs.getString("claude_malli", null)?.takeIf { it.isNotBlank() }
        openAiModel = prefs.getString("openai_malli", null)?.takeIf { it.isNotBlank() }
        planModel = prefs.getString(ChatGptPlan.PREF_MODEL, null)?.takeIf { it.isNotBlank() }
        claudeKeySaved = ApiKeyStore.exists(prefs, ApiKeyStore.Slot.CLAUDE)
        openAiKeySaved = ApiKeyStore.exists(prefs, ApiKeyStore.Slot.OPENAI)
        val login = ChatGptPlan.load(prefs)
        planSignedIn = login != null
        planEmail = login?.email
        collapsed = prefs.getStringSet(SettingsDefaults.PREF_COLLAPSED, null)?.toSet().orEmpty()
    }

    fun setBoolean(key: String, value: Boolean) = prefs.edit { putBoolean(key, value) }

    fun setInt(key: String, value: Int) = prefs.edit { putInt(key, value) }

    fun setString(key: String, value: String) = prefs.edit { putString(key, value) }

    /** Taitettu tila talteen heti, jotta sivu aukeaa samoin kuin se jätettiin. */
    fun setCollapsed(id: String, isCollapsed: Boolean) {
        val next = if (isCollapsed) collapsed + id else collapsed - id
        prefs.edit {
            putStringSet(SettingsDefaults.PREF_COLLAPSED, SettingsDefaults.encodeCollapsed(next))
        }
    }

    /** Onko valitulla AI-palvelulla avain tai kirjautuminen. */
    fun aiServiceHasKey(): Boolean =
        if (aiService == "chatgpt") planSignedIn || openAiKeySaved else claudeKeySaved
}
