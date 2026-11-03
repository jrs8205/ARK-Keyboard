package org.jarsi.ark.settings

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.edit
import androidx.core.content.pm.PackageInfoCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.preference.PreferenceManager
import org.jarsi.ark.R
import org.jarsi.ark.data.ApiKeyStore
import org.jarsi.ark.data.ChatGptPlan
import org.jarsi.ark.dictation.OpenAiDictation
import org.jarsi.ark.engine.AiRequests
import org.jarsi.ark.settings.ui.ArkSettingsTheme
import org.jarsi.ark.settings.ui.ModelPicker
import org.jarsi.ark.settings.ui.SettingsActions
import org.jarsi.ark.settings.ui.SettingsScreen
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import javax.net.ssl.HttpsURLConnection

/**
 * Näppäimistön asetukset Composella. Asetusten oma ulkoasu seuraa
 * järjestelmää; näkymän tila elää [SettingsState]-oliossa ja Androidin
 * rajapintoja kutsutaan vain täältä.
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var state: SettingsState
    private lateinit var backup: BackupActions
    private val ioExecutor = Executors.newSingleThreadExecutor()
    private val catalog = ModelCatalog(ioExecutor)
    private val imeStatus = mutableStateOf(ImeStatus.NOT_ENABLED)
    private val modelPicker = mutableStateOf<ModelPicker?>(null)

    private val exportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> if (uri != null) backup.export(uri) }

    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) backup.import(uri) }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Käynnistyskuva kuvakkeella myös Android 8–11:ssä; Android 12+
        // näyttää sen itse, ja kirjasto vaihtaa teemaan sen päätyttyä.
        installSplashScreen()
        // Vanhoilla Android-versioilla ei ole järjestelmän tummaa tilaa;
        // niillä koko sovellus on aina tumma, kuten näppäimistökin.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        prefs = PreferenceManager.getDefaultSharedPreferences(this)
        state = SettingsState(
            prefs,
            finnish = resources.getBoolean(R.bool.kieli_suomi),
            dynamicAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
        )
        backup = BackupActions(applicationContext, ioExecutor, ::toastOnUi)
        // Aiemmin avoimena tallennettu avain salataan kertaalleen.
        ioExecutor.execute {
            ApiKeyStore.read(prefs, ApiKeyStore.Slot.CLAUDE)
            ApiKeyStore.read(prefs, ApiKeyStore.Slot.OPENAI)
        }
        val actions = SettingsActions(
            enableIme = { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) },
            selectIme = {
                (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                    .showInputMethodPicker()
            },
            openIntro = { startActivity(Intent(this, IntroActivity::class.java)) },
            openSymbols = { startActivity(Intent(this, SymbolOrderActivity::class.java)) },
            openToolbar = { startActivity(Intent(this, ToolbarOrderActivity::class.java)) },
            openLearnedWords = { startActivity(Intent(this, LearnedWordsActivity::class.java)) },
            openLanguages = { startActivity(Intent(this, TranslationLanguagesActivity::class.java)) },
            openChatGptLogin = { startActivity(Intent(this, ChatGptLoginActivity::class.java)) },
            signOutChatGpt = ::signOutChatGpt,
            exportBackup = {
                val date = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date())
                exportLauncher.launch("ark-varmuuskopio-$date.json")
            },
            importBackup = { importLauncher.launch(arrayOf("application/json")) },
            pickClaudeModel = ::pickClaudeModel,
            pickOpenAiModel = ::pickOpenAiModel,
            pickDictationModel = ::pickDictationModel,
            saveClaudeKey = { saveKey(it, ApiKeyStore.Slot.CLAUDE) },
            saveOpenAiKey = { saveKey(it, ApiKeyStore.Slot.OPENAI) },
            openUrl = { SupportLinks.open(this, it) },
            showToast = { Toast.makeText(this, it, Toast.LENGTH_LONG).show() },
        )
        val versionLabel = appVersion()
        setContent {
            ArkSettingsTheme {
                SettingsScreen(
                    state = state,
                    imeStatus = imeStatus.value,
                    versionLabel = versionLabel,
                    modelPicker = modelPicker.value,
                    onDismissModelPicker = { modelPicker.value = null },
                    actions = actions,
                )
            }
        }
        // Ensimmäinen avaus ohjataan esittelyyn, joka opastaa myös
        // näppäimistön käyttöönoton.
        if (savedInstanceState == null && !prefs.getBoolean(IntroActivity.PREF_SEEN, false)) {
            startActivity(Intent(this, IntroActivity::class.java))
        }
    }

    override fun onStart() {
        super.onStart()
        state.start()
    }

    override fun onStop() {
        state.stop()
        super.onStop()
    }

    // Käyttöönoton tila päivittyy, kun järjestelmän asetuksista tai
    // näppäimistövalitsimesta palataan tähän näkymään.
    override fun onResume() {
        super.onResume()
        refreshImeStatus()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) refreshImeStatus()
    }

    override fun onDestroy() {
        ioExecutor.shutdown()
        super.onDestroy()
    }

    private fun refreshImeStatus() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val enabled = imm.enabledInputMethodList.any { it.packageName == packageName }
        val selected = ImeSetup.isOwnIme(
            Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD),
            packageName,
        )
        imeStatus.value = ImeStatus.of(enabled, selected)
    }

    /** Sovelluksen nimi, asennettu versio ja koontinumero, esim. "ARK-näppäimistö 0.19.0 (17)". */
    private fun appVersion(): String {
        val info = packageManager.getPackageInfo(packageName, 0)
        return "${getString(R.string.app_name)} ${info.versionName} " +
            "(${PackageInfoCompat.getLongVersionCode(info)})"
    }

    /**
     * API-avain salataan Android Keystorella. Salaus voi epäonnistua
     * laitteella, jonka Keystore on rikki: avain tallennetaan silti, jotta
     * ominaisuus toimii, mutta käyttäjän pitää tietää se, koska
     * asetusteksti lupaa salauksen.
     */
    private fun saveKey(key: String, slot: ApiKeyStore.Slot) {
        if (!ApiKeyStore.save(prefs, key, slot)) {
            Toast.makeText(this, R.string.asetus_avain_ei_salausta, Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Uloskirjautuminen kumoaa tokenit myös palvelimella, ettei laitteelta
     * mahdollisesti kopioitu tietue jää käyttökelpoiseksi. Paikallinen
     * poisto tehdään aina; jos kumoaminen ei onnistu (ei yhteyttä), lupa
     * jää voimaan OpenAI:n päässä ja siitä on kerrottava.
     */
    private fun signOutChatGpt() {
        val login = ChatGptPlan.load(prefs) ?: return
        ioExecutor.execute {
            val revoked = ChatGptPlan.revoke(login)
            ChatGptPlan.clear(prefs)
            toastOnUi(
                getString(
                    if (revoked) {
                        R.string.chatgpt_kirjauduttu_ulos
                    } else {
                        R.string.chatgpt_kirjauduttu_ulos_paikallisesti
                    }
                )
            )
        }
    }

    private fun pickClaudeModel() {
        val apiKey = ApiKeyStore.read(prefs, ApiKeyStore.Slot.CLAUDE).orEmpty()
        if (apiKey.isEmpty()) {
            Toast.makeText(this, R.string.malli_aseta_avain, Toast.LENGTH_SHORT).show()
            return
        }
        catalog.fetch(
            url = AiRequests.MODELS_ENDPOINT,
            apiKey = apiKey,
            connectionError = getString(R.string.yhteys_virhe),
            authorize = { connection, key ->
                connection.setRequestProperty("x-api-key", key)
                connection.setRequestProperty("anthropic-version", "2023-06-01")
            },
            parse = AiRequests::parseModels,
        ) { showModels(it, R.string.asetus_malli, "claude_malli", AiRequests.MODEL, hints = true) }
    }

    /**
     * AI-käännöksen ChatGPT-malli: kirjautuneena lista haetaan tilauksen
     * tokenilla (eri mallijoukko ja oma asetusavain), muuten API-avaimella.
     */
    private fun pickOpenAiModel() {
        if (ChatGptPlan.exists(prefs)) {
            catalog.fetchPlan(prefs) {
                showModels(it, R.string.asetus_openai_malli, ChatGptPlan.PREF_MODEL, null, hints = false)
            }
            return
        }
        val apiKey = ApiKeyStore.read(prefs, ApiKeyStore.Slot.OPENAI).orEmpty()
        if (apiKey.isEmpty()) {
            Toast.makeText(this, R.string.malli_aseta_avain, Toast.LENGTH_SHORT).show()
            return
        }
        catalog.fetch(
            url = AiRequests.OPENAI_MODELS_ENDPOINT,
            apiKey = apiKey,
            connectionError = getString(R.string.yhteys_virhe),
            authorize = ::bearer,
            parse = AiRequests::parseOpenAiModels,
        ) { showModels(it, R.string.asetus_openai_malli, "openai_malli", AiRequests.OPENAI_MODEL, hints = true) }
    }

    private fun pickDictationModel() {
        val apiKey = ApiKeyStore.read(prefs, ApiKeyStore.Slot.OPENAI).orEmpty()
        if (apiKey.isEmpty()) {
            Toast.makeText(this, R.string.malli_aseta_avain, Toast.LENGTH_SHORT).show()
            return
        }
        catalog.fetch(
            url = AiRequests.OPENAI_MODELS_ENDPOINT,
            apiKey = apiKey,
            connectionError = getString(R.string.yhteys_virhe),
            authorize = ::bearer,
            parse = AiRequests::parseTranscribeModels,
        ) {
            showModels(
                it, R.string.asetus_sanelu_malli, OpenAiDictation.PREF_MODEL,
                OpenAiDictation.DEFAULT_MODEL, hints = true,
            )
        }
    }

    private fun bearer(connection: HttpsURLConnection, key: String) {
        connection.setRequestProperty("authorization", "Bearer $key")
    }

    /**
     * Mallivalikko haetusta listasta; ilman listaa syy kerrotaan, jotta
     * väärä tai rajattu avain erottuu verkko-ongelmasta. Tulos näytetään
     * vain, jos näkymä on yhä käyttäjän edessä.
     */
    private fun showModels(
        result: ModelCatalog.Result,
        titleRes: Int,
        prefKey: String,
        defaultModel: String?,
        hints: Boolean,
    ) {
        runOnUiThread {
            if (!lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) return@runOnUiThread
            val models = result.models
            if (models.isNullOrEmpty()) {
                val message = result.error
                    ?.let { getString(R.string.malli_haku_virhe_syy, it) }
                    ?: getString(R.string.malli_haku_virhe)
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                return@runOnUiThread
            }
            val labels = ModelCatalog.labels(models, hints)
            modelPicker.value = ModelPicker(
                title = getString(titleRes),
                options = models.indices.map { models[it].first to labels[it] },
                selected = prefs.getString(prefKey, null) ?: defaultModel,
                onSelect = { value ->
                    prefs.edit { putString(prefKey, value) }
                    modelPicker.value = null
                },
            )
        }
    }

    private fun toastOnUi(message: String) {
        runOnUiThread {
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            }
        }
    }
}
