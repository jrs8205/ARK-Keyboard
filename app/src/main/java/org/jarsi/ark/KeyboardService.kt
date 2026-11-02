package org.jarsi.ark

import android.Manifest
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.Icon
import android.util.Size
import android.view.ViewGroup
import android.view.inputmethod.InlineSuggestionsRequest
import android.view.inputmethod.InlineSuggestionsResponse
import android.widget.inline.InlinePresentationSpec
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import androidx.annotation.RequiresApi
import androidx.autofill.inline.UiVersions
import androidx.autofill.inline.common.TextViewStyle
import androidx.autofill.inline.common.ViewStyle
import androidx.autofill.inline.v1.InlineSuggestionUi
import android.inputmethodservice.InputMethodService
import android.net.Uri
import android.database.ContentObserver
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.InputDevice
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.Gravity
import android.view.View
import android.view.ViewConfiguration
import android.os.SystemClock
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import android.webkit.MimeTypeMap
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.core.view.inputmethod.InputConnectionCompat
import androidx.core.view.inputmethod.InputContentInfoCompat
import androidx.preference.PreferenceManager
import org.jarsi.ark.clipboard.Clip
import org.jarsi.ark.clipboard.ClipStore
import org.jarsi.ark.clipboard.NewClipActivity
import org.jarsi.ark.clipboard.RecentClipOffer
import org.jarsi.ark.data.ApiKeyStore
import org.jarsi.ark.data.ChatGptPlan
import org.jarsi.ark.data.DeviceLock
import org.jarsi.ark.data.ClipEntity
import org.jarsi.ark.dictation.DictationController
import org.jarsi.ark.dictation.DictationText
import org.jarsi.ark.dictation.OpenAiDictation
import org.jarsi.ark.dictation.RecordAudioPermissionActivity
import org.jarsi.ark.emoji.EmojiIndex
import org.jarsi.ark.emoji.EmojiRecents
import org.jarsi.ark.data.BigramEntity
import org.jarsi.ark.data.LearnedDataStamp
import org.jarsi.ark.data.LearnedDatabase
import org.jarsi.ark.data.TrigramEntity
import org.jarsi.ark.data.WordEntity
import org.jarsi.ark.engine.AiRequests
import org.jarsi.ark.engine.DictionaryEngine
import org.jarsi.ark.engine.LearnedBigram
import org.jarsi.ark.engine.LearnedTrigram
import org.jarsi.ark.engine.LearnedWord
import org.jarsi.ark.engine.LearningEngine
import org.jarsi.ark.engine.SuggestionEngine
import org.jarsi.ark.engine.WordTools
import org.jarsi.ark.keyboard.AutoCaps
import org.jarsi.ark.keyboard.CursorExpectation
import org.jarsi.ark.keyboard.EditCommand
import org.jarsi.ark.keyboard.EditLabels
import org.jarsi.ark.keyboard.ExportedTranslation
import org.jarsi.ark.keyboard.FieldProfile
import org.jarsi.ark.keyboard.InlineChips
import org.jarsi.ark.keyboard.KeyAction
import org.jarsi.ark.keyboard.KeyboardHeight
import org.jarsi.ark.keyboard.Layouts
import org.jarsi.ark.keyboard.OneHand
import org.jarsi.ark.keyboard.OneHandMode
import org.jarsi.ark.keyboard.ShiftState
import org.jarsi.ark.keyboard.ShiftTracker
import org.jarsi.ark.keyboard.SlowConnectionGuard
import org.jarsi.ark.keyboard.SmartSpace
import org.jarsi.ark.keyboard.SuggestionItem
import org.jarsi.ark.keyboard.SymbolOrder
import org.jarsi.ark.keyboard.TextUndo
import org.jarsi.ark.keyboard.ToolbarOrder
import org.jarsi.ark.keyboard.WordDeleteCheck
import org.jarsi.ark.keyboard.TranslateBuffer
import org.jarsi.ark.keyboard.TranslateLines
import org.jarsi.ark.keyboard.TranslatePrep
import org.jarsi.ark.keyboard.TranslationMemory
import org.jarsi.ark.settings.SettingsActivity
import org.jarsi.ark.theme.KeyboardTheme
import org.jarsi.ark.view.InlineChipsView
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.common.sdkinternal.MlKitContext
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.languageid.LanguageIdentificationOptions
import com.google.mlkit.nl.languageid.LanguageIdentifier
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import org.jarsi.ark.view.ClipboardPanelView
import org.jarsi.ark.view.EmojiPanelView
import org.jarsi.ark.view.EmojiSearchBarView
import org.jarsi.ark.view.KeyboardView
import org.jarsi.ark.view.OneHandSideView
import org.jarsi.ark.view.TranslateBarView
import org.jarsi.ark.view.SuggestionBarView
import org.jarsi.ark.view.ToolbarView
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import kotlin.math.roundToInt

/** ARK-näppäimistön pääpalvelu. */
class KeyboardService : InputMethodService(), KeyboardView.Listener {

    private enum class Page { LETTERS, SYMBOLS1, SYMBOLS2, SYMBOLS3, NUMERIC, ARROWS }

    private var keyboardView: KeyboardView? = null

    // Puhelimen käynnistyksen jälkeen ennen ensimmäistä avausta käytössä on
    // vain tyhjä laitesuojattu asetustiedosto: ei oppimista, leikkeitä eikä
    // työkaluja, ainoastaan näppäimet lukitusnäytön salasanaa varten.
    private var lockedMode = false

    private var keyRow: LinearLayout? = null
    private var keyColumn: LinearLayout? = null
    private var oneHandSide: OneHandSideView? = null
    private var suggestionBar: SuggestionBarView? = null
    private var toolbar: ToolbarView? = null
    private var page = Page.LETTERS
    private var field = FieldProfile.from(0, 0, null)
    private val shift = ShiftTracker()
    private var symbolOrder: List<String> = SymbolOrder.default
    private var soundEnabled = false
    private var vibrationEnabled = true

    private val dictionary = DictionaryEngine()
    private val learning = LearningEngine()
    private val suggestionEngine = SuggestionEngine(dictionary, learning)
    private var database: LearnedDatabase? = null
    private var learningEnabled = false
    private var loadedStamp = 0L
    private var pendingDictation = false
    private var pendingClipboardPanel = false

    private val clipStore = ClipStore()

    // Tuore kopio tarjotaan ehdotusrivillä, kunnes tarjous vanhenee.
    // Kulunut aika monotonisella kellolla: kellonajan siirto ei pidennä tarjousta.
    private val recentClip = RecentClipOffer(SystemClock::elapsedRealtime)
    private val recentClipExpiry = object : Runnable {
        override fun run() {
            // Ajastin kulkee uptime-kellolla ja tarjous elapsedRealtime-kellolla.
            // Jos tarjous on yhä voimassa, odotetaan loppuaika: esikatselu ei
            // saa jäädä riville ilman uutta vanhenemista.
            val left = recentClip.remainingMs()
            if (left > 0) mainHandler.postDelayed(this, left) else refreshSuggestionRow()
        }
    }

    // Kursoritilan Valitse: nuolet laajentavat valintaa.
    private var selectionMode = false

    // Askelpalauttimen liu'utus: teksti luetaan kerran liu'utuksen alussa.
    private class BackspaceSwipe(val before: CharSequence, val end: Int) {
        var selected = 0
    }

    private var backspaceSwipe: BackspaceSwipe? = null
    private var translateSwipeWords = 0
    private val pendingClips = mutableListOf<Clip>()
    private var clipboardPanel: ClipboardPanelView? = null
    private var emojiPanel: EmojiPanelView? = null
    private var emojiSearchBar: EmojiSearchBarView? = null

    // Emojiaineisto latautuu taustalla; null, kunnes se on valmis.
    private var emojiIndex: EmojiIndex? = null

    // Kieli, jolla emojiaineisto on viimeksi ladattu tai latautumassa.
    @Volatile
    private var emojiIndexFinnish: Boolean? = null
    private var emojiRecents: EmojiRecents? = null

    // Emojihaku: näppäily kertyy hakusanaksi eikä mene kenttään.
    private var emojiSearchActive = false
    private val emojiQuery = TranslateBuffer()
    private var pageBeforeSearch = Page.LETTERS
    private var translateBar: TranslateBarView? = null
    private var translateMode = false
    private val translateBuffer = TranslateBuffer()
    private var translateRunnable: Runnable? = null

    // Käännöstä voi korjata paikallaan ennen vientiä: näppäily kohdistuu
    // omaan puskuriinsa, kunnes lähdetekstiä napautetaan.
    private val translationBuffer = TranslateBuffer()
    private var translationEditing = false

    /** Puskuri, johon näppäily käännöstilassa kohdistuu. */
    private fun activeTranslateBuffer(): TranslateBuffer =
        if (translationEditing) translationBuffer else translateBuffer

    // Sanelun keskeneräisen jakson pituus käännösnäkymässä: seuraava tulos
    // korvaa sen, kuten kentässä keskeneräinen composing-teksti.
    private var dictationPending = 0

    /**
     * Kirjoittaa sanelun tuloksen käännösnäkymään sille alueelle, jota
     * parhaillaan kirjoitetaan. Keskeneräinen jakso korvautuu seuraavalla,
     * jottei sama puhe päädy tekstiin kahdesti.
     */
    private fun dictateIntoTranslation(text: String, final: Boolean) {
        val buffer = activeTranslateBuffer()
        if (dictationPending > 0) buffer.deleteBeforeCursor(dictationPending)
        val inserted = if (final) "$text " else text
        buffer.insert(inserted)
        dictationPending = if (final) 0 else inserted.length
        onTranslateBufferChanged()
    }

    /** Kohdistus takaisin lähdetekstiin; keskeneräinen korjaus jää käännökseen. */
    private fun stopTranslationEditing() {
        translationEditing = false
        translationBuffer.clear()
        // Kirjoitettava alue vaihtui: sanelun keskeneräinen jakso ei enää
        // osoita mihinkään.
        dictationPending = 0
    }

    // Käännöstilan viimeisin käyttöhetki muistiajan vertailuun; puskuri
    // elää vain prosessin muistissa, joten elapsedRealtime riittää.
    private var translateLastUsed = 0L

    // Näkyvä käännös ja sen tuoreus: tuore käännös vastaa rivin nykyistä
    // sisältöä ja kelpaa vietäväksi kenttään sellaisenaan.
    private var currentTranslation = ""
    private var translationFresh = false
    private var translationGeneration = 0
    private var aiTranslateGeneration = 0

    // AI-käännös on kesken: estää saman pyynnön laskuttamisen moneen kertaan.
    private var aiTranslateRunning = false

    // AI- ja käsin korjatut käännökset tekstin ja kieliparin mukaan: sama
    // teksti saa paremman käännöksensä takaisin ilman uutta maksullista
    // pyyntöä. Unohtuu muistiajan kuluttua kuten puskurikin.
    private val translationMemory = TranslationMemory()

    /** Muistettu käännös [text]-tekstille nykyisellä kieliparilla. */
    private fun rememberedTranslation(
        text: String = translateBuffer.toString(),
    ): TranslationMemory.Remembered? =
        translationMemory.get(text, translationSource(), translationTarget())
    private var translator: Translator? = null
    private var translatorReady = false

    // Lähdekielen päättely kirjoitetusta tekstistä; käsin valittu lähde
    // lukitsee päättelyn, kunnes rivi tyhjennetään.
    private var languageIdentifier: LanguageIdentifier? = null
    private var translateSourceLocked = false

    // Kieliparilta puuttuu ladattu malli; lataus odottaa käyttäjän lupaa.
    private var translationModelsMissing = false
    private var translationLangs: List<String> =
        listOf(TranslateLanguage.FINNISH, TranslateLanguage.ENGLISH)
    private var clipboardManager: ClipboardManager? = null
    private val clipChangedListener =
        ClipboardManager.OnPrimaryClipChangedListener { handleClipChanged() }

    // Jaettu kuuntelija: sama tulosten käsittely laitteen tunnistimelle
    // ja OpenAI-suoratoistomoottorille.
    private val dictationListener = object : DictationController.Listener {
                override fun onPartialText(text: String) {
                    markOwnEdit()
                    val shown = if (dictationCapNext) DictationText.capitalize(text) else text
                    if (translateMode) {
                        dictateIntoTranslation(shown, final = false)
                        return
                    }
                    cursor.onUnknownEdit()
                    currentInputConnection?.setComposingText(shown, 1)
                }

                override fun onFinalText(text: String) {
                    markOwnEdit()
                    val committed =
                        if (dictationCapNext) DictationText.capitalize(text) else text
                    if (translateMode) {
                        dictateIntoTranslation(committed, final = true)
                    } else {
                        val ic = currentInputConnection ?: return
                        // Lopullinen teksti korvaa keskeneräisen — ei sen perään,
                        // ettei sama puhe päädy kenttään kahdesti.
                        ic.beginBatchEdit()
                        ic.setComposingText("$committed ", 1)
                        ic.finishComposingText()
                        ic.endBatchEdit()
                        cursor.onUnknownEdit()
                        textUndo.record("$committed ")
                    }
                    // Lauseen loppu nostaa seuraavankin jakson isolle alkukirjaimelle.
                    dictationCapNext = DictationText.endsSentence(committed)
                    if (learningEnabled) {
                        // Sanellut sanat oppivat samoin kuin kirjoitetut.
                        WordTools.words(text).forEach { learning.onWordCommitted(it) }
                        maybeFlush()
                    }
                }

                override fun onDictationStateChanged(active: Boolean) {
                    toolbar?.micActive = active
                    if (!active) {
                        dictationPending = 0
                        cursor.onUnknownEdit()
                        currentInputConnection?.finishComposingText()
                    }
                }

                override fun onDictationError(messageResId: Int) {
                    showMessage(messageResId)
                }

                override fun onDictationErrorMessage(message: String) {
                    showMessage(getString(R.string.sanelu_verkkovirhe, message), long = true)
                }

                override fun onDictationConnectionLost() {
                    showMessage(
                        getString(R.string.sanelu_verkkovirhe, getString(R.string.yhteys_virhe)),
                        long = true,
                    )
                }

                override fun onSpeechLevel(level: Float) {
                    toolbar?.micLevel = level
                }
    }

    private val dictation by lazy {
        DictationController(this, dictationListener).also {
            // Omat opitut sanat vihjeiksi tunnistimelle (prx4, jarsi.org…).
            it.biasWords = { learning.biasWords(BIAS_WORD_MAX) }
        }
    }

    // OpenAI-tunnistus luodaan vasta, kun se on asetuksista valittu.
    private var openAiDictation: OpenAiDictation? = null

    // Kentän tai tilan vaihtuessa OpenAI-tulokset hylätään, ettei vanha
    // puhe valu uuteen kenttään; mikkinapin pysäytys viimeistelee ne.
    private fun stopDictation() {
        dictation.stop()
        openAiDictation?.cancel()
    }

    private fun dictationActive(): Boolean =
        dictation.isActive || openAiDictation?.isActive == true

    /**
     * Käynnistää sanelun asetusten hiljaisuusrajalla ja valitulla
     * moottorilla; alku isolla kirjaimella. Jos OpenAI-tunnistus on
     * valittu mutta avainta ei ole, pudotaan laitteen tunnistukseen,
     * jottei sanelu jää mykäksi.
     */
    private fun startDictation() {
        val silence = prefs.getInt(PREF_DICTATION_SILENCE, 5).coerceIn(2, 10) * 1000L
        dictationCapNext = true
        if (prefs.getString(PREF_DICTATION_ENGINE, null) == "openai") {
            if (ApiKeyStore.exists(prefs, ApiKeyStore.Slot.OPENAI)) {
                val language = resources.configuration.locales[0].language
                val engine = openAiDictation
                    ?: OpenAiDictation(prefs, language, dictationListener).also {
                        // Omat opitut sanat vihjeiksi kuten järjestelmätunnistimelle.
                        it.biasWords = { learning.biasWords(BIAS_WORD_MAX) }
                        openAiDictation = it
                    }
                // Puhelimen kieli voi vaihtua moottorin eläessä: kieli joka istunnolle.
                engine.language = language
                engine.silenceLimitMs = silence
                engine.start()
                return
            }
            showMessage(R.string.sanelu_openai_ei_avainta)
        }
        dictation.silenceLimitMs = silence
        dictation.start()
    }
    // Seuraava sanelujakso alkaa isolla kirjaimella (istunnon ja lauseen alut).
    private var dictationCapNext = true

    private val ioExecutor = Executors.newSingleThreadExecutor()
    private val suggestExecutor = Executors.newSingleThreadExecutor()

    // AI-käännöksen verkkopyynnöt omassa säikeessään, ettei hidas
    // yhteys viivästytä oppimisdatan kirjoituksia.
    private val aiExecutor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    // Salasanakentän puhesuoja seuraa äänilaitteita ja puheasetusta myös kentän
    // ollessa auki: kuulokkeiden irrotus ei saa jättää merkkejä kaiuttimesta
    // puhuttaviksi, eikä asetuksen muutos saa odottaa kentän uudelleenavausta.
    private val audioDeviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) = refreshObscureKeyLabels()
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) = refreshObscureKeyLabels()
    }
    private val speakPasswordObserver = object : ContentObserver(mainHandler) {
        override fun onChange(selfChange: Boolean) = refreshObscureKeyLabels()
    }
    @Volatile private var suggestGeneration = 0
    private var suggestionsVisible = true
    private var inlineChips: InlineChipsView? = null
    // Kasvaa jokaisella inline-vastauksella ja kentän vaihdolla: myöhässä
    // valmistuva chip ei saa päätyä uudemman kentän riville.
    private var inlineGeneration = 0
    // Kehys pyytää inline-ehdotuksia vain, kun käytössä on niitä tukeva täyttöpalvelu.
    private var inlineAutofillActive = false
    private var spaceAfterSuggestion = true
    private var commonWordsEnabled = true

    // 2 = automaattinen välilyönti juuri lisätty, 1 = commitin oma kursoripäivitys
    // ohitettu, 0 = ei voimassa. Välimerkki imaisee välin vain tilassa > 0.
    private var autoSpaceState = 0

    // Välimerkin perään lisättiin juuri automaattiväli; heti perään
    // painettu välilyönti ohitetaan tuplavälin estämiseksi.
    private var punctSpaceAdded = false

    // Edellisen välilyöntipainalluksen hetki kaksoisvälilyönnin pisteelle.
    private var lastSpaceTime = 0L
    private var doubleSpacePeriodEnabled = true

    // Työkalurivin peruutusnapin kertaperuutus näppäimistön toimenpiteille.
    private val textUndo = TextUndo()

    private var autoCorrectEnabled = true
    private var numberRowEnabled = true

    // Editori-istunnon tunniste: viivästynyt callback (automaattikorjaus,
    // käännöksen viimeistely) ei saa kirjoittaa toiseen kenttään.
    private var editorSessionId = 0

    // Tuhotulle palvelulle palaavat callbackit eivät saa jonottaa uutta työtä.
    private var destroyed = false

    // Viimeisin oma muokkaushetki: varareitti valintamuutosten erotteluun,
    // kun kursorin sijaintia ei tiedetä (näppäintapahtumat, sanelu).
    private var lastEditTime = 0L

    // Odotettu kursorin sijainti erottaa omat muokkaukset käyttäjän
    // kursorihypyistä, jotka katkaisevat sanaketjun.
    private val cursor = CursorExpectation()

    // Hitaan kentän valinnaiset kyselyt niputetaan kirjoitustauolle.
    private val slowConnection = SlowConnectionGuard()
    private var lastPackage: String? = null
    private val deferredRefresh = Runnable {
        updateAutoCaps()
        updateSuggestions(immediate = true)
    }

    // Sanapoisto ei tehonnut tähän kenttään (eräät selainkentät): loppuistunto
    // poistetaan näppäintapahtumin.
    private var deleteWordsWithKeys = false
    private var selectionUpdates = 0
    private var wordDeleteCheck: Runnable? = null

    // Kaksoisvälilyönnin piste: askelpalautin heti perään palauttaa välit.
    private var pendingPeriodRevert = false

    // Viimeisin automaattikorjaus (kirjoitettu, korjattu): askelpalautin
    // heti perään palauttaa kirjoitetun; mikä tahansa muu näppäin mitätöi.
    private var pendingRevert: Pair<String, String>? = null

    // Viimeksi näytetyt täydennysehdotukset ohitusten kirjaamista varten.
    // Vain täydennysrivit lasketaan — ei tyhjän syötteen ennustuksia.
    private var shownCompletions: List<String> = emptyList()

    private val fiLocale = Locale.forLanguageTag("fi")

    private lateinit var prefs: SharedPreferences

    // Ulkoasuasetukset otetaan käyttöön heti asetuksissa vaihdettaessa, jotta
    // näppäimistö on jo oikeassa teemassa kun asetuksista palataan.
    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "korkeus" || key == "esikatselu" || key == "varina" ||
            key == KeyboardTheme.PREF_KEY
        ) {
            applyVisualSettings()
        }
        // Tyhjennys tai varmuuskopion tuonti muutti muistettuja emojeja muualta.
        if ((key == PREF_EMOJI_RECENTS || key == PREF_EMOJI_VARIANTS) &&
            emojiRecents?.matches(
                prefs.getString(PREF_EMOJI_RECENTS, null),
                prefs.getString(PREF_EMOJI_VARIANTS, null),
            ) == false
        ) {
            emojiRecents = loadEmojiRecents()
        }
    }

    override fun onCreate() {
        super.onCreate()
        getSystemService(AudioManager::class.java)
            ?.registerAudioDeviceCallback(audioDeviceCallback, mainHandler)
        @Suppress("DEPRECATION")
        contentResolver.registerContentObserver(
            Settings.Secure.getUriFor(Settings.Secure.ACCESSIBILITY_SPEAK_PASSWORD),
            false,
            speakPasswordObserver,
        )
        lockedMode = !DeviceLock.isUnlocked(this)
        if (lockedMode) {
            prefs = PreferenceManager.getDefaultSharedPreferences(
                createDeviceProtectedStorageContext()
            )
            prefs.registerOnSharedPreferenceChangeListener(prefListener)
        } else {
            startUserServices()
        }
    }

    /** Ensimmäinen avaus: oikeat asetukset ja oppimisdata tulevat käyttöön. */
    private fun completeUnlock() {
        lockedMode = false
        prefs.unregisterOnSharedPreferenceChangeListener(prefListener)
        startUserServices()
        toolbar?.visibility = View.VISIBLE
        applyVisualSettings()
    }

    private fun startUserServices() {
        // Kirjaston oma alustus ei käynnisty, jos prosessi syntyi ennen laitteen avausta.
        MlKitContext.initializeIfNeeded(applicationContext)
        prefs = PreferenceManager.getDefaultSharedPreferences(this)
        prefs.registerOnSharedPreferenceChangeListener(prefListener)
        clipboardManager = getSystemService(ClipboardManager::class.java)
        clipboardManager?.addPrimaryClipChangedListener(clipChangedListener)
        emojiRecents = loadEmojiRecents()
        // Sanalista ja oppimisdata ladataan taustalla; ehdotusrivi on tyhjä
        // kunnes lataus valmistuu.
        Thread {
            try {
                assets.open("sanalista.txt").bufferedReader().useLines {
                    // Laajempi yleisten sanojen joukko palvelee myös
                    // automaattikorjauksen pikkusanavaihtoehtoja.
                    dictionary.load(it, topWordCount = COMMON_WORD_POOL)
                }
            } catch (e: IOException) {
                // Sanalista puuttuu tai ei aukea: ehdotusrivi jää tyhjäksi.
            }
            try {
                val db = LearnedDatabase.create(this)
                val stamp = LearnedDataStamp.stamp
                val (words, pairs, triples) = readLearnedData(db)
                val clips = db.dao().allClips()
                    .map { Clip(it.id, it.text, it.imagePath, it.created, it.pinned) }
                mainHandler.post {
                    if (destroyed) return@post
                    database = db
                    loadedStamp = stamp
                    learning.load(words, pairs, triples)
                    // Latauksen aikana kopioidut leikkeet säilyvät ja tallentuvat.
                    val pending = pendingClips.toList()
                    pendingClips.clear()
                    clipStore.load(clips + pending)
                    pending.forEach(::persistClip)
                    pruneClips()
                }
            } catch (e: Exception) {
                // Tietokanta ei auennut: oppiminen jää pois, näppäimistö toimii silti.
            }
            mainHandler.post { updateSuggestions() }
            // Viimeisenä: fonttituen tarkistus ei saa viivästyttää omia sanoja.
            loadEmojiIndex()
        }.start()
    }

    private fun loadEmojiIndex() {
        try {
            // Laitteen fontista puuttuvat emojit eivät kelpaa: ne näkyisivät neliöinä.
            val paint = Paint()
            val finnish = finnishUi
            emojiIndexFinnish = finnish
            val asset = if (finnish) EMOJI_ASSET else EMOJI_ASSET_EN
            val index = assets.open(asset).bufferedReader().useLines { lines ->
                EmojiIndex.parse(lines) { paint.hasGlyph(it) }
            }
            mainHandler.post {
                // Kieli ehti vaihtua latauksen aikana: uudempi lataus on jo matkalla.
                if (destroyed || finnish != finnishUi) return@post
                emojiIndex = index
                emojiPanel?.setData(index, emojiRecents)
                if (emojiPanel?.visibility == View.VISIBLE) emojiPanel?.refresh()
                if (emojiSearchActive) updateEmojiSearch()
                updateSuggestions()
            }
        } catch (e: IOException) {
            // Aineisto ei aukea: emojipaneeli jää tyhjäksi, kirjoitus toimii silti.
        }
    }

    /** Lukee muistetut emojit; ensimmäisellä kerralla mukaan tulee vanhan valitsimen historia. */
    private fun loadEmojiRecents(): EmojiRecents {
        val saved = prefs.getString(PREF_EMOJI_RECENTS, null)
        if (saved != null) return EmojiRecents(saved, prefs.getString(PREF_EMOJI_VARIANTS, null))
        val legacy = getSharedPreferences(EmojiRecents.LEGACY_PREFS, MODE_PRIVATE)
            .getString(EmojiRecents.LEGACY_KEY, null)
            ?: return EmojiRecents()
        // Siirto tehdään kerran: muuten tyhjennetty historia palaisi vanhasta tiedostosta.
        val migrated = EmojiRecents.fromLegacy(legacy)
        prefs.edit().putString(PREF_EMOJI_RECENTS, migrated.savedRecents()).apply()
        deleteSharedPreferences(EmojiRecents.LEGACY_PREFS)
        return migrated
    }

    private fun rememberEmojiUse(emoji: String) {
        // Salasana- ja incognito-kentissä ei muisteta mitään, ei emojejakaan.
        if (!learningEnabled) return
        val recents = emojiRecents ?: return
        recents.use(emoji)
        prefs.edit()
            .putString(PREF_EMOJI_RECENTS, recents.savedRecents())
            .putString(PREF_EMOJI_VARIANTS, recents.savedVariants())
            .apply()
    }

    override fun onDestroy() {
        editorSessionId++
        cancelDeferredFieldWork()
        getSystemService(AudioManager::class.java)?.unregisterAudioDeviceCallback(audioDeviceCallback)
        contentResolver.unregisterContentObserver(speakPasswordObserver)
        watchTranslateBack(false)
        mainHandler.removeCallbacks(recentClipExpiry)
        dictation.stop()
        openAiDictation?.destroy()
        translator?.close()
        languageIdentifier?.close()
        clipboardManager?.removePrimaryClipChangedListener(clipChangedListener)
        prefs.unregisterOnSharedPreferenceChangeListener(prefListener)
        flushLearned()
        destroyed = true
        suggestExecutor.shutdownNow()
        aiExecutor.shutdownNow()
        // Sulkeutuu vasta kun jonossa oleva kirjoitus on valmis.
        ioExecutor.shutdown()
        super.onDestroy()
    }

    // Oma ilmoituskupla näppäimistön yllä. Järjestelmä vaimentaa
    // syötepalvelun Toast-ilmoitukset ("Suppressing toast ... by user
    // request"), koska palvelu ei ole etualan sovellus eikä sillä ole
    // ilmoituslupaa — esimerkiksi AI-käännöksen virheen syy ei näkynyt.
    private var messagePopup: PopupWindow? = null
    private val hideMessageRunnable = Runnable { hideMessage() }

    private fun showMessage(resId: Int, long: Boolean = false) =
        showMessage(getString(resId), long)

    private fun showMessage(text: String, long: Boolean = false) {
        hideMessage()
        val anchor = toolbar?.takeIf { it.isShown && it.windowToken != null }
        if (anchor == null) {
            // Näppäimistö ei ole esillä: kupla ei voi näkyä.
            Toast.makeText(this, text, if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
            return
        }
        val theme = KeyboardTheme.load(this)
        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).roundToInt()
        val view = TextView(this).apply {
            this.text = text
            textSize = 14f
            gravity = Gravity.CENTER
            // Käänteiset värit kuten Toastissa: kupla erottuu näppäimistön
            // ja käännösnäkymän pinnoista kummassakin teemassa.
            setTextColor(theme.background)
            setPadding(dp(16), dp(10), dp(16), dp(10))
            background = GradientDrawable().apply {
                cornerRadius = dp(20).toFloat()
                setColor(theme.text)
            }
        }
        view.measure(
            View.MeasureSpec.makeMeasureSpec(anchor.width - dp(32), View.MeasureSpec.AT_MOST),
            View.MeasureSpec.UNSPECIFIED,
        )
        val location = IntArray(2)
        anchor.getLocationInWindow(location)
        messagePopup = PopupWindow(view, view.measuredWidth, view.measuredHeight).apply {
            isTouchable = false
            isClippingEnabled = false
            elevation = dp(4).toFloat()
            showAtLocation(
                anchor,
                Gravity.NO_GRAVITY,
                location[0] + (anchor.width - view.measuredWidth) / 2,
                location[1] - view.measuredHeight - dp(8),
            )
        }
        mainHandler.postDelayed(hideMessageRunnable, if (long) MESSAGE_LONG_MS else MESSAGE_SHORT_MS)
    }

    private fun hideMessage() {
        mainHandler.removeCallbacks(hideMessageRunnable)
        messagePopup?.dismiss()
        messagePopup = null
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        // Piilossa oleva näppäimistö ei saa siepata sovelluksen takaisin-nappia.
        watchTranslateBack(false)
        cancelDeferredFieldWork()
        hideMessage()
        keyboardView?.dismissPopups()
        // Lupa-aktiviteetin avaus piilottaa näppäimistön hetkeksi; silloin
        // sanelua ei pysäytetä, jotta se voi alkaa luvan myöntämisen jälkeen.
        if (!pendingDictation) {
            stopDictation()
        }
        flushLearned()
        learning.resetContext()
        clearInlineChips()
        super.onFinishInputView(finishingInput)
    }

    private fun cancelDeferredFieldWork() {
        mainHandler.removeCallbacks(deferredRefresh)
        wordDeleteCheck?.let { mainHandler.removeCallbacks(it) }
        wordDeleteCheck = null
    }

    /** Kirjoittaa kenttään ja pitää kursorin odotuksen ajan tasalla. */
    private fun commitToField(ic: InputConnection, text: CharSequence) {
        ic.commitText(text, 1)
        cursor.onCommit(text.length)
    }

    /** Poistaa kursorin edeltä ja pitää kursorin odotuksen ajan tasalla. */
    private fun deleteFromField(ic: InputConnection, count: Int) {
        ic.deleteSurroundingText(count, 0)
        cursor.onDeleteBefore(count)
    }

    /** Näppäintapahtuma: sen vaikutus kursoriin selviää vasta päivityksestä. */
    private fun sendKeyToField(keyCode: Int) {
        sendDownUpKeyEvents(keyCode)
        cursor.onUnknownEdit()
    }

    /** Kentän tekstikysely ajanotolla: hidas vastaus merkitsee kentän hitaaksi. */
    private fun readBefore(ic: InputConnection, count: Int): CharSequence? {
        val started = SystemClock.uptimeMillis()
        val text = ic.getTextBeforeCursor(count, 0)
        val now = SystemClock.uptimeMillis()
        slowConnection.record(now - started, now)
        return text
    }

    private fun readBefore(count: Int): CharSequence? =
        currentInputConnection?.let { readBefore(it, count) }

    private fun readLearnedData(
        db: LearnedDatabase,
    ): Triple<List<LearnedWord>, List<LearnedBigram>, List<LearnedTrigram>> {
        val words = db.dao().allWords().map {
            LearnedWord(
                it.word, it.count, it.lastUsed, it.blocked, it.created,
                it.acceptedCount, it.ignoredCount, it.pinned,
            )
        }
        val pairs = db.dao().allBigrams()
            .map { LearnedBigram(it.previous, it.next, it.count, it.lastUsed) }
        val triples = db.dao().allTrigrams()
            .map { LearnedTrigram(it.first, it.second, it.next, it.count, it.lastUsed) }
        return Triple(words, pairs, triples)
    }

    /** Lataa oppimisdatan uudelleen, kun hallintanäkymä on muuttanut sitä. */
    private fun reloadLearnedIfChanged() {
        val db = database ?: return
        if (loadedStamp == LearnedDataStamp.stamp) return
        val stamp = LearnedDataStamp.stamp
        loadedStamp = stamp
        // Lukuhetken jälkeen kirjoitetut sanat jonoon, ettei tilannekuva
        // pyyhi niitä muistista päälle kirjoittaessaan.
        learning.beginReload()
        Thread {
            try {
                val (words, pairs, triples) = readLearnedData(db)
                val clips = db.dao().allClips()
                    .map { Clip(it.id, it.text, it.imagePath, it.created, it.pinned) }
                mainHandler.post {
                    if (destroyed) return@post
                    learning.load(words, pairs, triples)
                    clipStore.load(clips)
                    pruneClips()
                    updateSuggestions()
                    refreshClipboardPanel()
                }
            } catch (e: Exception) {
                // Lukuvirhe: vanha data jää käyttöön, yritetään seuraavalla kerralla.
                loadedStamp = stamp - 1
            }
        }.start()
    }

    private fun handleClipChanged() {
        val manager = clipboardManager ?: return
        try {
            val clip = manager.primaryClip ?: return
            // Arkaluonteisiksi merkityt kopiot (esim. salasanat) ohitetaan kokonaan.
            val sensitive = clip.description.extras
                ?.getBoolean("android.content.extra.IS_SENSITIVE", false) == true
            if (sensitive || clip.itemCount == 0) return
            val item = clip.getItemAt(0)
            val text = item.text
            if (!text.isNullOrBlank()) {
                val saved = clipStore.addText(text.toString()) ?: return
                persistClip(saved)
                pruneClips()
                refreshClipboardPanel()
                saved.text?.let { recentClip.offer(saved.id, it) }
                mainHandler.removeCallbacks(recentClipExpiry)
                mainHandler.postDelayed(recentClipExpiry, RecentClipOffer.OFFER_MS)
                refreshSuggestionRow()
            } else {
                val uri = item.uri ?: return
                // Tyyppi tarjoajalta; jos se ei kerro, leikkeen oma kuvaus
                // riittää (jotkin sovellukset ilmoittavat kuvan vain siinä).
                val type = contentResolver.getType(uri) ?: clip.description.imageMimeType()
                if (type?.startsWith("image/") == true) {
                    copyImageClip(uri, type)
                }
            }
        } catch (e: Exception) {
            // Leikepöydän luku voi olla estetty taustalla; ohitetaan hiljaisesti.
        }
    }

    private fun ClipDescription.imageMimeType(): String? =
        (0 until mimeTypeCount).map(::getMimeType).firstOrNull { it.startsWith("image/") }

    private fun copyImageClip(uri: Uri, type: String) {
        // Tiedostopääte säilyttää todellisen kuvatyypin liittämistä varten.
        // Tuntematonta tyyppiä ei tallenneta väärällä nimellä eikä väitetä
        // liitettäessä PNG:ksi — sellainen leike ohitetaan.
        val extension = when (type) {
            "image/jpeg", "image/jpg" -> "jpg"
            "image/webp" -> "webp"
            "image/png" -> "png"
            "image/gif" -> "gif"
            else -> MimeTypeMap.getSingleton().getExtensionFromMimeType(type)
        } ?: return
        executeIo {
            val dir = File(filesDir, "clips").apply { mkdirs() }
            val file = File(dir, "leike_${System.currentTimeMillis()}.$extension")
            try {
                contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(file).use { output ->
                        // Kokoraja suojaa tallennustilaa jättikuvilta.
                        val buffer = ByteArray(8192)
                        var total = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            total += read
                            if (total > MAX_IMAGE_CLIP_BYTES) error("kuvaleike on liian suuri")
                            output.write(buffer, 0, read)
                        }
                    }
                } ?: return@executeIo
                mainHandler.post {
                    val saved = clipStore.addImage(file.absolutePath)
                    persistClip(saved)
                    pruneClips()
                    refreshClipboardPanel()
                }
            } catch (e: Exception) {
                // Epäonnistunut tai liian suuri kopio siivotaan levyltä pois.
                file.delete()
            }
        }
    }

    private fun persistClip(clip: Clip) {
        val db = database
        if (db == null) {
            // Tietokanta latautuu vielä: leike talteen, tallennus latauksen perään.
            pendingClips += clip
            return
        }
        executeIo {
            try {
                db.dao().upsertClip(
                    ClipEntity(clip.id, clip.text, clip.imagePath, clip.created, clip.pinned)
                )
            } catch (e: Exception) {
                // Kirjoitusvirhe ei saa kaataa näppäimistöä.
            }
        }
    }

    private fun pruneClips() {
        val prune = clipStore.prune()
        if (prune.removedIds.isEmpty()) return
        val db = database
        executeIo {
            try {
                prune.removedIds.forEach { db?.dao()?.deleteClip(it) }
                prune.removedImagePaths.forEach { File(it).delete() }
            } catch (e: Exception) {
                // Siivousvirhe ei saa kaataa näppäimistöä.
            }
        }
    }

    private fun refreshClipboardPanel() {
        if (clipboardPanel?.visibility == View.VISIBLE) {
            clipboardPanel?.setClips(clipStore.all())
        }
    }

    private fun showClipboardPanel() {
        val panel = clipboardPanel ?: return
        val kb = keyboardView ?: return
        hideEmojiPanel()
        // Käännösnäkymä jää auki kuten emojipaneelinkin alla: leike
        // liitetään siihen alueeseen, jota parhaillaan kirjoitetaan.
        stopDictation()
        resetSidePage()
        if (kb.height > 0) {
            panel.layoutParams = panel.layoutParams.apply { height = kb.height }
        }
        panel.setClips(clipStore.all())
        kb.visibility = View.GONE
        panel.visibility = View.VISIBLE
        toolbar?.clipboardActive = true
    }

    private fun hideClipboardPanel() {
        val panel = clipboardPanel ?: return
        if (panel.visibility != View.VISIBLE) return
        panel.closeMenu()
        panel.visibility = View.GONE
        keyboardView?.visibility = View.VISIBLE
        toolbar?.clipboardActive = false
    }

    private fun hideAllPanels() {
        hideClipboardPanel()
        hideEmojiPanel()
        closeEmojiSearch(returnToPanel = false)
    }

    /**
     * Sivutila palaa kirjaimiin paneelin tai sanelun alta, ettei nuoli-
     * tai www-korostus jää aktiiviseksi toisen tilan rinnalle.
     */
    private fun resetSidePage() {
        if (page == Page.ARROWS || page == Page.SYMBOLS3) {
            page = Page.LETTERS
            updateLayout()
            updateAutoCaps()
        }
    }

    private fun showEmojiPanel() {
        val panel = emojiPanel ?: return
        val kb = keyboardView ?: return
        hideAllPanels()
        stopDictation()
        resetSidePage()
        panel.rememberChoices = learningEnabled
        panel.setData(emojiIndex, emojiRecents)
        panel.refresh()
        if (kb.height > 0) {
            panel.layoutParams = panel.layoutParams.apply { height = kb.height }
        }
        kb.visibility = View.GONE
        panel.visibility = View.VISIBLE
        toolbar?.emojiActive = true
    }

    private fun hideEmojiPanel() {
        val panel = emojiPanel ?: return
        if (panel.visibility != View.VISIBLE) return
        panel.dismissVariants()
        panel.visibility = View.GONE
        keyboardView?.visibility = View.VISIBLE
        toolbar?.emojiActive = false
    }

    /**
     * Emojihaku: paneelin tilalle tulee näppäimistö, ja hakurivi korvaa
     * työkalu- ja ehdotusrivin saman korkuisena.
     */
    private fun openEmojiSearch() {
        hideEmojiPanel()
        emojiSearchActive = true
        emojiQuery.clear()
        // Haku kirjoitetaan kirjaimilla myös numerokentässä; sivu palaa haun jälkeen.
        pageBeforeSearch = page
        page = Page.LETTERS
        shift.reset()
        updateLayout()
        toolbar?.visibility = View.GONE
        emojiSearchBar?.visibility = View.VISIBLE
        updateSuggestionRow()
        updateEmojiSearch()
    }

    private fun closeEmojiSearch(returnToPanel: Boolean) {
        if (!emojiSearchActive) return
        emojiSearchActive = false
        emojiQuery.clear()
        emojiSearchBar?.visibility = View.GONE
        toolbar?.visibility = View.VISIBLE
        page = pageBeforeSearch
        updateLayout()
        updateSuggestionRow()
        if (returnToPanel) {
            showEmojiPanel()
        } else {
            updateAutoCaps()
            updateSuggestions()
        }
    }

    private fun updateEmojiSearch() {
        val bar = emojiSearchBar ?: return
        val index = emojiIndex
        val query = emojiQuery.text
        bar.setQuery(query)
        val recents = emojiRecents
        val results = when {
            index == null -> emptyList()
            query.isBlank() -> recents?.recent.orEmpty().map {
                EmojiSearchBarView.Result(it, index.entryFor(it)?.name)
            }
            else -> index.search(query).map {
                EmojiSearchBarView.Result(recents?.variantFor(it.emoji) ?: it.emoji, it.name)
            }
        }
        bar.setResults(results, noMatch = query.isNotBlank() && results.isEmpty())
    }

    /** Näppäinten kohtalo haun aikana; true, jos näppäin käsiteltiin. */
    private fun handleEmojiSearchKey(action: KeyAction): Boolean {
        when (action) {
            KeyAction.Backspace -> emojiQuery.backspace()
            KeyAction.Space -> emojiQuery.insert(" ")
            KeyAction.Enter -> {
                feedback(AudioManager.FX_KEYPRESS_RETURN)
                closeEmojiSearch(returnToPanel = true)
                return true
            }
            // Haku on kirjainkoosta riippumaton.
            KeyAction.Shift -> return true
            KeyAction.Symbols, KeyAction.SymbolsMore, KeyAction.Letters -> return false
            else -> return true
        }
        updateEmojiSearch()
        feedback()
        return true
    }

    /** Emoji kenttään tai käännösnäkymään; valitsin ja haku käyttävät samaa reittiä. */
    private fun insertEmoji(emoji: String) {
        markOwnEdit()
        if (translateMode) {
            // Emojit kulkevat käännösnäkymän kautta muun tekstin mukana.
            activeTranslateBuffer().insert(emoji)
            onTranslateBufferChanged()
        } else {
            currentInputConnection?.let { ic -> commitToField(ic, emoji) }
            textUndo.record(emoji)
        }
        rememberEmojiUse(emoji)
        feedback()
    }

    /** Ehdotusriviltä valittu emoji: kirjoitettu sana säilyy ja emoji tulee perään. */
    private fun pickEmojiSuggestion(emoji: String) {
        markOwnEdit()
        pendingRevert = null
        pendingPeriodRevert = false
        rememberEmojiUse(emoji)
        if (translateMode) {
            val buffer = activeTranslateBuffer()
            buffer.insert(EmojiIndex.insertion(buffer.text.substring(0, buffer.cursor), emoji))
            feedback()
            onTranslateBufferChanged()
            return
        }
        val ic = currentInputConnection ?: return
        learnCurrentWord()
        val text = EmojiIndex.insertion(readBefore(ic, 1) ?: "", emoji)
        commitToField(ic, text)
        textUndo.record(text)
        // Heti perään kirjoitettu välimerkki siirtää välin taakseen kuten sanoilla.
        autoSpaceState = 2
        feedback()
        updateAutoCaps()
        updateSuggestions()
    }

    private fun translationSource(): String {
        val default = if (finnishUi) TranslateLanguage.FINNISH else TranslateLanguage.ENGLISH
        return prefs.getString(PREF_TRANSLATE_SOURCE, default) ?: default
    }

    private fun translationTarget(): String {
        val default = if (finnishUi) TranslateLanguage.ENGLISH else TranslateLanguage.FINNISH
        return prefs.getString(PREF_TRANSLATE_TARGET, default) ?: default
    }

    private val finnishUi: Boolean get() = resources.getBoolean(R.bool.kieli_suomi)

    private val uiLocale: Locale get() = resources.configuration.locales[0]

    private fun languageName(code: String): String =
        Locale(code).getDisplayLanguage(uiLocale).replaceFirstChar { it.titlecase(uiLocale) }

    /** Kielen nimi kielipillereitä varten; suomeksi pienellä alkukirjaimella. */
    private fun languageNameLower(code: String): String =
        Locale(code).getDisplayLanguage(uiLocale)

    /**
     * Käännösnäkymä: näppäily kertyy ylärivin lähdetekstiin ja käännös
     * näkyy alarivillä livenä. Kenttään ei kirjoitu mitään itsestään —
     * käännös viedään Lisää-napilla tai enterillä. Ehdotusrivi jää
     * näkyviin ja palvelee lähdetekstiä.
     */
    private fun showTranslateBar() {
        val bar = translateBar ?: return
        // Salasanakenttiä ei käännetä.
        if (field.password) return
        forgetSentTranslation()
        // Vanha käännösteksti unohtuu muistiajan kuluttua: tuntien takainen
        // teksti avautuisi muuten yllättäen uuden käännöstyön pohjaksi.
        val memoryMs = prefs.getInt(PREF_TRANSLATE_MEMORY, 30) * 60_000L
        val expired = translateLastUsed > 0 &&
            SystemClock.elapsedRealtime() - translateLastUsed > memoryMs
        // Muistetut käännökset unohtuvat samalla, vaikka rivi olisi jo tyhjä.
        if (expired) translationMemory.clear()
        if (translateBuffer.text.isNotEmpty() && expired) {
            translateBuffer.clear()
            exportedTranslation.clear()
        }
        if (translateBuffer.text.isEmpty()) translateSourceLocked = false
        // Sanelu ja käännös eivät voi olla yhtä aikaa päällä.
        stopDictation()
        hideAllPanels()
        resetSidePage()
        translateMode = true
        watchTranslateBack(true)
        // Aiempi AI- tai käsin korjattu käännös palaa näkyviin heti, eikä
        // konekäännös korvaa sitä — muuten maksullinen käännös pitäisi
        // pyytää uudelleen joka kerta näkymään palatessa.
        val remembered = rememberedTranslation()
        currentTranslation = remembered?.translation.orEmpty()
        translationFresh = remembered != null
        stopTranslationEditing()
        bar.visibility = View.VISIBLE
        toolbar?.translationActive = true
        refreshTranslationLanguages()
        updateTranslateBar()
        checkTranslationModels()
        updateSuggestionRow()
        updateSuggestions()
    }

    /**
     * Vienti sulki näkymän. Jos viety teksti on yhä kursorin edellä, näkymä
     * jatkaa siitä: lähdeteksti on muokattavana, ja uusi vienti korvaa kentän
     * tekstin. Jos sitä ei enää ole, viesti lähti tai kenttä vaihtui, ja näkymä
     * aukeaa tyhjänä seuraavaa käännöstä varten kuten Gboardissa.
     */
    private fun forgetSentTranslation() {
        if (!translationExported) return
        translationExported = false
        val previous = exportedTranslation.text
        val stillInField = previous.isNotEmpty() &&
            readBefore(previous.length)?.toString() == previous
        if (stillInField) return
        translateBuffer.clear()
        exportedTranslation.clear()
        translateSourceLocked = false
    }

    /** Vie käännöksen ja sulkee näkymän, jolloin sovelluksen lähetysnappi on heti esillä. */
    private fun exportTranslation() {
        if (translateBuffer.text.isBlank()) return
        // Maksullinen käännös on tulossa: vienti ei saa ohittaa sitä konekäännöksellä.
        if (aiTranslateRunning) {
            showMessage(R.string.kaannos_ai_kaannetaan)
            return
        }
        insertTranslation {
            // Kenttään ei mennyt mitään: lähde säilyy, koska mitään ei lähetetty.
            translationExported = exportedTranslation.text.isNotEmpty()
            hideTranslateBar()
        }
    }

    /**
     * Sulkee käännösnäkymän. Kenttään ei kosketa, ja teksti säilyy
     * puskurissa kunnes käyttäjä itse tyhjentää sen (✕ tai poistot) —
     * käännöstyö ei katoa sulkuun, kentän vaihtoon eikä sovelluksen
     * vaihtoon, koska mikään ei koskaan kirjoitu kenttään itsestään.
     */
    private fun hideTranslateBar() {
        if (!translateMode) return
        translateLastUsed = SystemClock.elapsedRealtime()
        translateMode = false
        watchTranslateBack(false)
        translateRunnable?.let { mainHandler.removeCallbacks(it) }
        translationGeneration++
        aiTranslateGeneration++
        translator?.close()
        translator = null
        translatorReady = false
        translationModelsMissing = false
        currentTranslation = ""
        translationFresh = false
        stopTranslationEditing()
        translateBar?.visibility = View.GONE
        updateSuggestionRow()
        toolbar?.translationActive = false
        updateAutoCaps()
        updateSuggestions()
    }

    // Takaisin-nappi sulkee ensin kääntäjän kuten Gboardissa, ja vasta
    // seuraava painallus piilottaa näppäimistön. Android 13:sta alkaen takaisin
    // ei välttämättä kulje näppäintapahtumana, vaan järjestelmä piilottaisi
    // näppäimistön suoraan (mitattu API 36:lla), joten tarvitaan oma kuuntelija.
    // Vanhemmilla versioilla toimii näppäintapahtuma.
    private var translateBackCallback: Any? = null

    private fun watchTranslateBack(active: Boolean) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val dispatcher = window?.onBackInvokedDispatcher ?: return
        val current = translateBackCallback as? OnBackInvokedCallback
        if (active && current == null) {
            val callback = OnBackInvokedCallback { hideTranslateBar() }
            dispatcher.registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                callback,
            )
            translateBackCallback = callback
        } else if (!active && current != null) {
            dispatcher.unregisterOnBackInvokedCallback(current)
            translateBackCallback = null
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && translateMode && isInputViewShown) {
            event.startTracking()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && translateMode && isInputViewShown) {
            hideTranslateBar()
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    private fun updateTranslateBar() {
        val bar = translateBar ?: return
        bar.setLanguages(
            translationSource(),
            languageNameLower(translationSource()),
            translationTarget(),
            languageNameLower(translationTarget()),
        )
        // Kirjoittaa saa heti myös malleja odottaessa; vain valmistelu
        // näyttää oman vihjeensä.
        val hint = if (translatorReady || translationModelsMissing) {
            getString(R.string.kaannos_kirjoita, languageName(translationSource()))
        } else {
            getString(R.string.kaannos_ladataan)
        }
        bar.pasteAvailable = clipboardManager?.hasPrimaryClip() == true
        // ✨ piilotetaan kokonaan, kun valitulla palvelulla ei ole avainta —
        // nappi joka ei tee mitään hämmentäisi.
        bar.aiEnabled = if (openAiSelected()) {
            ChatGptPlan.exists(prefs) || ApiKeyStore.exists(prefs, ApiKeyStore.Slot.OPENAI)
        } else {
            ApiKeyStore.exists(prefs, ApiKeyStore.Slot.CLAUDE)
        }
        // Kursori näkyy vain siinä alueessa, johon näppäily kohdistuu.
        bar.setBuffer(
            translateBuffer.text,
            if (translationEditing) -1 else translateBuffer.cursor,
            hint,
        )
        bar.setTranslation(
            currentTranslation,
            getString(
                if (translationModelsMissing) {
                    R.string.kaannos_mallit_puuttuvat
                } else {
                    R.string.kaannos_tyhja
                }
            ),
            if (translationEditing) translationBuffer.cursor else -1,
        )
        bar.setModelsMissing(translationModelsMissing)
        // Shift-nuoli seuraa käännösrivin tekstiä kuten kenttää.
        if (translateMode) updateAutoCaps()
    }

    private fun onTranslatePairChanged() {
        // Uusi kielipari tuo uuden käännöksen, joten korjaus päättyy.
        stopTranslationEditing()
        // Vanha käännös on tehty toiselle kieliparille: se ei saa jäädä
        // näkyviin tuoreena eikä kelvata vietäväksi, muuten kenttään voisi
        // päätyä englantia ruotsi valittuna. Sukupolven nosto hylkää myös
        // kesken olevan AI-käännöksen vastauksen.
        currentTranslation = ""
        translationFresh = false
        aiTranslateGeneration++
        updateTranslateBar()
        checkTranslationModels()
        scheduleLiveTranslate()
    }

    /**
     * Mallit ladataan vasta käyttäjän luvalla: jos kieliparilta puuttuu
     * malli, käännösalue näyttää kertalatauksen kehotteen Lataa-nappeineen
     * eikä mitään ladata itsestään. Tarkistusvirheessä toimitaan kuten
     * ennen, ettei kääntäjä jää verkko-ongelmasta lukkoon.
     */
    private fun checkTranslationModels() {
        val generation = ++translationGeneration
        translator?.close()
        translator = null
        translatorReady = false
        RemoteModelManager.getInstance()
            .getDownloadedModels(TranslateRemoteModel::class.java)
            .addOnSuccessListener { models ->
                if (generation != translationGeneration || !translateMode) {
                    return@addOnSuccessListener
                }
                val have = models.mapTo(mutableSetOf()) { it.language }
                translationModelsMissing =
                    translationSource() !in have || translationTarget() !in have
                if (translationModelsMissing) {
                    updateTranslateBar()
                } else {
                    prepareTranslator()
                }
            }
            .addOnFailureListener {
                if (generation == translationGeneration && translateMode) {
                    prepareTranslator()
                }
            }
    }

    /** Avaa kääntäjän kieliparille ja varmistaa mallit (kertalataus). */
    private fun prepareTranslator() {
        val generation = ++translationGeneration
        translator?.close()
        translatorReady = false
        val client = Translation.getClient(
            TranslatorOptions.Builder()
                .setSourceLanguage(translationSource())
                .setTargetLanguage(translationTarget())
                .build()
        )
        translator = client
        client.downloadModelIfNeeded(DownloadConditions.Builder().build())
            .addOnSuccessListener {
                if (generation == translationGeneration && translateMode) {
                    translatorReady = true
                    updateTranslateBar()
                    scheduleLiveTranslate()
                }
            }
            .addOnFailureListener {
                if (generation == translationGeneration && translateMode) {
                    showMessage(R.string.kaannos_lataus_virhe)
                }
            }
    }

    private fun onTranslateBufferChanged() {
        if (translationEditing) {
            // Käsin korjattu käännös on se, joka viedään ja kopioidaan;
            // uutta konekäännöstä ei haeta ennen kuin lähdeteksti muuttuu.
            currentTranslation = translationBuffer.toString()
            translationFresh = currentTranslation.isNotBlank()
            // Korjaus muistetaan tekstille; korjattu AI-käännös pysyy
            // AI-käännöksenä, ettei ✨ pyydä sitä turhaan uudelleen.
            val text = translateBuffer.toString()
            translationMemory.put(
                text,
                translationSource(),
                translationTarget(),
                currentTranslation,
                fromAi = rememberedTranslation(text)?.fromAi == true,
            )
            updateTranslateBar()
            updateSuggestions()
            return
        }
        // Lähdetekstin muutos vanhentaa näkyvän käännöksen, kunnes uusi valmistuu.
        translationFresh = false
        updateTranslateBar()
        scheduleLiveTranslate()
        updateSuggestions()
    }

    // Käännetään pienellä viiveellä, ettei jokaista näppäilyä käännetä erikseen.
    private fun scheduleLiveTranslate() {
        translateRunnable?.let { mainHandler.removeCallbacks(it) }
        val runnable = Runnable { runLiveTranslate() }
        translateRunnable = runnable
        mainHandler.postDelayed(runnable, LIVE_TRANSLATE_DELAY_MS)
    }

    private fun runLiveTranslate() {
        // Käsin korjattua käännöstä ei ylikirjoiteta kesken muokkauksen.
        if (!translateMode || translationEditing) return
        val text = translateBuffer.toString()
        // Muistettu AI- tai käsin korjattu käännös voittaa konekäännöksen
        // ja näkyy heti, vaikka kääntäjä vasta valmistuisi.
        val remembered = rememberedTranslation(text)
        if (remembered != null) {
            currentTranslation = remembered.translation
            translationFresh = true
            translateBar?.setTranslation(
                currentTranslation, getString(R.string.kaannos_tyhja)
            )
            return
        }
        if (!translatorReady) return
        if (text.isBlank()) {
            currentTranslation = ""
            translationFresh = false
            translateBar?.setTranslation("", getString(R.string.kaannos_tyhja))
            return
        }
        detectSourceLanguage(text)
        val client = translator ?: return
        val generation = translationGeneration
        translateKeepingLines(
            client,
            text,
            onResult = { result ->
                // Tulos kelpaa vain, jos rivi ei ehtinyt muuttua välissä.
                if (translateMode && generation == translationGeneration &&
                    text == translateBuffer.toString()
                ) {
                    currentTranslation = result
                    translationFresh = true
                    translateBar?.setTranslation(
                        currentTranslation, getString(R.string.kaannos_tyhja)
                    )
                }
            },
        )
    }

    /**
     * Päättelee lähdekielen kirjoitetusta tekstistä käyttäjän omien
     * (ladattujen) kielten joukosta, ettei päättely koskaan vaadi uuden
     * mallin latausta. Kohdekielellä kirjoitettu teksti kääntää suunnan.
     * Rinnakkainen käännös nykyparilla saa jatkua: kielenvaihto käynnistää
     * uuden käännöksen ja korvaa tuloksen.
     */
    private fun detectSourceLanguage(text: String) {
        if (translateSourceLocked || text.length < AUTODETECT_MIN_CHARS) return
        val identifier = languageIdentifier ?: LanguageIdentification.getClient(
            LanguageIdentificationOptions.Builder()
                .setConfidenceThreshold(AUTODETECT_CONFIDENCE)
                .build()
        ).also { languageIdentifier = it }
        identifier.identifyLanguage(text)
            .addOnSuccessListener { tag ->
                if (!translateMode || text != translateBuffer.toString()) {
                    return@addOnSuccessListener
                }
                val code = TranslateLanguage.fromLanguageTag(tag)
                    ?: return@addOnSuccessListener
                if (code !in translationLangs || code == translationSource()) {
                    return@addOnSuccessListener
                }
                pickTranslationLanguage(sourceSide = true, code = code)
            }
    }

    /**
     * Kääntää tekstin rivi kerrallaan, jotta rivinvaihdot säilyvät —
     * esikäsittely ja konekäännös litistäisivät ne muuten yhdeksi riviksi.
     * [onResult] kutsutaan päälangalla vain onnistuneella käännöksellä;
     * [onFailure] virheellä.
     */
    private fun translateKeepingLines(
        client: Translator,
        text: String,
        onResult: (String) -> Unit,
        onFailure: () -> Unit = {},
    ) {
        val lines = TranslateLines.split(text)
        val jobs = TranslateLines.translatable(lines).map { TranslatePrep.prepare(it) }
        if (jobs.isEmpty()) {
            onResult("")
            return
        }
        val english = translationTarget() == TranslateLanguage.ENGLISH
        Tasks.whenAllSuccess<String>(jobs.map { client.translate(it.text) })
            .addOnSuccessListener { results ->
                val cleaned = results.mapIndexed { index, result ->
                    TranslatePrep.clean(result, jobs[index], english)
                }
                onResult(TranslateLines.merge(lines, cleaned))
            }
            .addOnFailureListener { onFailure() }
    }

    /**
     * Vie näkyvän käännöksen kenttään ja suorittaa [onDone] vasta sen
     * jälkeen. Tuore käännös viedään heti; muuten käännetään ensin, ettei
     * kenttään mene rivin sisältöä vastaamatonta tekstiä.
     */
    private fun insertTranslation(onDone: () -> Unit = {}) {
        translateRunnable?.let { mainHandler.removeCallbacks(it) }
        val text = translateBuffer.toString()
        if (text.isBlank()) {
            onDone()
            return
        }
        if (translationFresh && currentTranslation.isNotBlank()) {
            commitTranslation(currentTranslation)
            onDone()
            return
        }
        rememberedTranslation(text)?.let { remembered ->
            commitTranslation(remembered.translation)
            onDone()
            return
        }
        val client = translator
        if (client == null || !translatorReady) {
            showMessage(R.string.kaannos_ladataan)
            return
        }
        val generation = ++translationGeneration
        val session = editorSessionId
        translateKeepingLines(
            client,
            text,
            onResult = { result ->
                // Vanhentunut tulos hylätään: käyttäjä ehti jatkaa, sulkea
                // tilan, vaihtaa kenttää tai syötenäkymä ehti sulkeutua.
                if (!translateMode || generation != translationGeneration ||
                    session != editorSessionId || translateBuffer.toString() != text
                ) {
                    return@translateKeepingLines
                }
                commitTranslation(result)
                onDone()
            },
            onFailure = {
                if (translateMode && generation == translationGeneration &&
                    session == editorSessionId
                ) {
                    showMessage(R.string.kaannos_virhe)
                }
            },
        )
    }

    // Viimeisin kenttään viety käännös: uusi vienti korvaa sen, ettei
    // jatkettu teksti kahdennu kenttään.
    private val exportedTranslation = ExportedTranslation()

    // Näkymä suljettiin viennin takia: seuraavalla avauksella katsotaan, onko
    // viety teksti yhä lähettämättä kentässä.
    private var translationExported = false

    /**
     * Kirjoittaa käännöksen kenttään. Jos edellinen viety käännös on
     * yhä kursorin edellä, se korvataan uudella versiolla — kentässä
     * pysyy aina yksi ajantasainen kopio käännöksestä. Tekstit säilyvät
     * näkymässä muokattavina; ✕ tyhjentää.
     */
    private fun commitTranslation(translation: String) {
        val ic = currentInputConnection ?: return
        markOwnEdit()
        // Valinta korvautuu viennillä: lähtöpiste on valinnan pienempi pää myös
        // käännetyssä valinnassa, ja valittu teksti palaa peruutuksessa.
        val positionBefore = ic.getExtractedText(ExtractedTextRequest(), 0)?.let {
            ExportedTranslation.replaceStart(it.startOffset, it.selectionStart, it.selectionEnd)
        }
        val selected = ic.getSelectedText(0)?.toString().orEmpty()
        ic.beginBatchEdit()
        val previous = exportedTranslation.text
        val replaced = previous.isNotEmpty() && readBefore(ic, previous.length)?.toString() == previous
        if (replaced) deleteFromField(ic, previous.length)
        commitToField(ic, translation)
        ic.endBatchEdit()
        val positionAfter = fieldPosition(ic)
        positionAfter?.let { cursor.onSetSelection(it, it) }
        val moved = if (positionBefore != null && positionAfter != null) {
            positionAfter - positionBefore + if (replaced) previous.length else 0
        } else {
            null
        }
        val inserted = ExportedTranslation.insertedPart(translation, moved) { readBefore(ic, it) }
        if (inserted.isNotEmpty()) {
            textUndo.record(inserted, (if (replaced) previous else "") + selected)
        }
        exportedTranslation.record(inserted)
        if (ExportedTranslation.cutShort(translation, inserted)) showMessage(R.string.kaannos_ei_mahdu)
        feedback()
    }

    /** Hakee laadukkaamman käännöksen valitulta AI-palvelulta alariville. */
    private fun requestAiTranslation() {
        feedback()
        // ✨ jää näkyviin pyynnön ajaksi, joten uudet napautukset jonottaisivat
        // omat maksulliset pyyntönsä, joiden tulokset kaikki hylätään
        // sukupolvivertailussa. Yksi pyyntö kerrallaan.
        if (aiTranslateRunning) return
        val text = translateBuffer.toString()
        if (text.isBlank()) return
        if (text.length > AiRequests.MAX_INPUT_CHARS) {
            showMessage(R.string.kaannos_ai_liian_pitka)
            return
        }
        // Sama teksti on jo AI-käännetty tällä kieliparilla: muistettu
        // käännös näytetään eikä uutta maksullista pyyntöä tehdä.
        val remembered = rememberedTranslation(text)
        if (remembered?.fromAi == true) {
            stopTranslationEditing()
            translateRunnable?.let { mainHandler.removeCallbacks(it) }
            currentTranslation = remembered.translation
            translationFresh = true
            updateTranslateBar()
            showMessage(R.string.kaannos_ai_muistista)
            return
        }
        val openAi = openAiSelected()
        // ChatGPT-valinnalla kirjautunut tilaus menee API-avaimen edelle.
        val usePlan = openAi && ChatGptPlan.exists(prefs)
        val apiKey = if (usePlan) {
            ""
        } else {
            ApiKeyStore.read(
                prefs,
                if (openAi) ApiKeyStore.Slot.OPENAI else ApiKeyStore.Slot.CLAUDE,
            ).orEmpty()
        }
        if (!usePlan && apiKey.isEmpty()) {
            showMessage(R.string.malli_aseta_avain)
            return
        }
        // AI-käännös korvaa alueen sisällön, joten käsin korjaus päättyy.
        stopTranslationEditing()
        translateBar?.setTranslation("", getString(R.string.kaannos_ai_kaannetaan))
        val sourceCode = translationSource()
        val targetCode = translationTarget()
        // Palvelun ohje on suomeksi, joten kielten nimetkin ovat sitä sovelluksen kielestä riippumatta.
        val sourceName = Locale(sourceCode).getDisplayLanguage(fiLocale)
        val targetName = Locale(targetCode).getDisplayLanguage(fiLocale)
        val generation = ++aiTranslateGeneration
        // Tekstin muistettu merkintä pyynnön alkaessa: vastaus saa korvata
        // sen vain, jos käyttäjä ei ole korjannut käännöstä välissä.
        val rememberedAtStart = translationMemory.get(text, sourceCode, targetCode)
        aiTranslateRunning = true
        aiExecutor.execute {
            val (translation, error) = if (usePlan) {
                translateWithPlan(text, sourceName, targetName)
            } else {
                translateWithKey(openAi, apiKey, text, sourceName, targetName)
            }
            mainHandler.post {
                // Vapautetaan lukko ennen kaikkia paluureittejä, ettei nappi
                // jää pysyvästi lukkoon keskeytyneen pyynnön jälkeen.
                aiTranslateRunning = false
                if (destroyed) return@post
                // Maksettu käännös muistetaan, vaikka näkymä ehdittiin sulkea
                // tai kielipari vaihtaa — palatessa se on valmiina. Käyttäjän
                // pyynnön aikana tekemää korjausta se ei kuitenkaan korvaa;
                // ennen pyyntöä tehty korjaus taas ei saa hukata maksettua
                // tulosta, muuten seuraava ✨ maksaisi saman käännöksen uudelleen.
                if (!translation.isNullOrBlank()) {
                    translationMemory.putIfUnchanged(
                        text,
                        sourceCode,
                        targetCode,
                        translation,
                        fromAi = true,
                        expected = rememberedAtStart,
                    )
                }
                if (generation != aiTranslateGeneration || !translateMode) {
                    return@post
                }
                if (text != translateBuffer.toString()) return@post
                if (!translation.isNullOrBlank()) {
                    currentTranslation = translation
                    translationFresh = true
                    translateBar?.setTranslation(
                        translation, getString(R.string.kaannos_tyhja)
                    )
                } else {
                    // Live-käännös palaa näkyviin ja syy kerrotaan.
                    updateTranslateBar()
                    val reason = error ?: getString(R.string.kaannos_ai_tyhja_vastaus)
                    showMessage(getString(R.string.kaannos_ai_virhe_syy, reason), long = true)
                }
            }
        }
    }

    /** Kielivalinnat kiertävät ladattujen mallien joukossa. */
    private fun refreshTranslationLanguages() {
        RemoteModelManager.getInstance()
            .getDownloadedModels(TranslateRemoteModel::class.java)
            .addOnSuccessListener { models ->
                translationLangs = (
                    models.map { it.language } +
                        listOf(TranslateLanguage.FINNISH, TranslateLanguage.ENGLISH)
                    )
                    .distinct()
                    .sortedBy { languageName(it) }
                // Kielivalikossa ovat kaikki tuetut kielet: omat (ladatut)
                // ensin ja loput latausmerkinnällä aakkosjärjestyksessä.
                val downloaded = translationLangs.toSet()
                translateBar?.setLanguageChoices(
                    TranslateLanguage.getAllLanguages()
                        .map {
                            TranslateBarView.LanguageChoice(
                                it, languageName(it), it in downloaded
                            )
                        }
                        .sortedWith(
                            compareByDescending<TranslateBarView.LanguageChoice> {
                                it.downloaded
                            }.thenBy { it.name }
                        )
                )
            }
    }

    /** Asettaa käännösparin kielen; sama kieli molemmin puolin vaihtaa suunnan. */
    private fun pickTranslationLanguage(sourceSide: Boolean, code: String) {
        val other = if (sourceSide) translationTarget() else translationSource()
        if (code == other) {
            prefs.edit()
                .putString(PREF_TRANSLATE_SOURCE, translationTarget())
                .putString(PREF_TRANSLATE_TARGET, translationSource())
                .apply()
        } else {
            prefs.edit()
                .putString(
                    if (sourceSide) PREF_TRANSLATE_SOURCE else PREF_TRANSLATE_TARGET,
                    code,
                )
                .apply()
        }
        onTranslatePairChanged()
    }

    /** Onko asetuksissa valittu AI-palveluksi ChatGPT. */
    private fun openAiSelected(): Boolean =
        prefs.getString(PREF_AI_SERVICE, "claude") == "chatgpt"

    /** Käännös API-avaimella: (käännös, virhe). Taustasäikeessä. */
    private fun translateWithKey(
        openAi: Boolean,
        apiKey: String,
        text: String,
        sourceName: String,
        targetName: String,
    ): Pair<String?, String?> {
        val body = if (openAi) {
            val model = prefs.getString(PREF_OPENAI_MODEL, null)
                ?.takeIf { it.isNotBlank() } ?: AiRequests.OPENAI_MODEL
            AiRequests.buildOpenAiTranslateRequest(text, sourceName, targetName, model)
        } else {
            val model = prefs.getString(PREF_CLAUDE_MODEL, null)
                ?.takeIf { it.isNotBlank() } ?: AiRequests.MODEL
            AiRequests.buildTranslateRequest(text, sourceName, targetName, model)
        }
        val reply = postAi(openAi, apiKey, body)
        val translation = reply.body?.let {
            if (openAi) AiRequests.parseOpenAiResponse(it) else AiRequests.parseResponse(it)
        }
        return translation to reply.error
    }

    /**
     * Käännös ChatGPT-tilauksen tokenilla: token päivitetään tarvittaessa
     * ennen pyyntöä, ja hylätty token (401) päivitetään kerran ennen
     * luovuttamista. Vastaus on suoratoisto, joka luetaan loppuun ja
     * tulkitaan kokonaisuutena.
     */
    private fun translateWithPlan(
        text: String,
        sourceName: String,
        targetName: String,
    ): Pair<String?, String?> {
        var login = when (val access = ChatGptPlan.access(prefs)) {
            is ChatGptPlan.Access.Ok -> access.login
            is ChatGptPlan.Access.Failed ->
                return null to planFailure(access.message, access.signInRequired)
        }
        val model = ChatGptPlan.model(prefs, login.accessToken)
            ?: return null to getString(R.string.kaannos_ai_tilaus_ei_mallia)
        val body = AiRequests.buildChatGptPlanTranslateRequest(text, sourceName, targetName, model)
        var reply = postAi(openAi = true, apiKey = login.accessToken, body = body)
        if (reply.status == 401) {
            login = when (val access = ChatGptPlan.refresh(prefs, login)) {
                is ChatGptPlan.Access.Ok -> access.login
                is ChatGptPlan.Access.Failed ->
                    return null to planFailure(access.message, access.signInRequired)
            }
            reply = postAi(openAi = true, apiKey = login.accessToken, body = body)
        }
        val raw = reply.body ?: return null to planErrorText(reply.error)
        val result = AiRequests.parseChatGptStream(raw)
        return result.text to planErrorText(result.error)
    }

    private fun planFailure(message: String, signInRequired: Boolean): String =
        if (signInRequired) getString(R.string.kaannos_ai_kirjaudu_uudelleen) else message

    // Kiintiövirhe suomeksi; muut syyt sellaisinaan.
    private fun planErrorText(error: String?): String? = when {
        error == null -> null
        "subscription_sharing_usage_limit_exceeded" in error ->
            getString(R.string.kaannos_ai_kiintio)
        else -> error
    }

    private data class AiReply(val status: Int, val body: String?, val error: String?)

    /**
     * Lähettää pyynnön valittuun AI-palveluun ja palauttaa vastausrungon
     * tai virheen selitteen käyttäjälle näytettäväksi.
     */
    private fun postAi(
        openAi: Boolean,
        apiKey: String,
        body: String,
    ): AiReply = try {
        val endpoint = if (openAi) AiRequests.OPENAI_ENDPOINT else AiRequests.ENDPOINT
        val connection = java.net.URL(endpoint)
            .openConnection() as javax.net.ssl.HttpsURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 10_000
            connection.readTimeout = 60_000
            connection.doOutput = true
            connection.setRequestProperty("content-type", "application/json")
            if (openAi) {
                connection.setRequestProperty("authorization", "Bearer $apiKey")
            } else {
                connection.setRequestProperty("x-api-key", apiKey)
                connection.setRequestProperty("anthropic-version", "2023-06-01")
            }
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            if (status in 200..299) {
                AiReply(status, connection.inputStream.bufferedReader().use { it.readText() }, null)
            } else {
                val errorBody = connection.errorStream
                    ?.bufferedReader()?.use { it.readText() }
                AiReply(status, null, AiRequests.parseErrorMessage(errorBody) ?: "HTTP $status")
            }
        } finally {
            connection.disconnect()
        }
    } catch (e: Exception) {
        AiReply(0, null, getString(R.string.yhteys_virhe))
    }

    private fun pasteClip(clip: Clip) {
        val ic = currentInputConnection ?: return
        markOwnEdit()
        if (clip.text != null) {
            if (translateMode) {
                // Liitetty teksti menee käännösnäkymään sille alueelle,
                // jota käyttäjä parhaillaan kirjoittaa.
                activeTranslateBuffer().insert(clip.text)
                onTranslateBufferChanged()
            } else {
                commitToField(ic, clip.text)
                textUndo.record(clip.text)
            }
            hideClipboardPanel()
            feedback()
            return
        }
        val path = clip.imagePath ?: return
        val info = currentInputEditorInfo ?: return
        val mime = when {
            path.endsWith(".jpg") || path.endsWith(".jpeg") -> "image/jpeg"
            path.endsWith(".webp") -> "image/webp"
            path.endsWith(".gif") -> "image/gif"
            path.endsWith(".png") -> "image/png"
            else -> MimeTypeMap.getSingleton()
                .getMimeTypeFromExtension(path.substringAfterLast('.', ""))
        }
        if (mime == null) {
            showMessage(R.string.leike_kuva_ei_tuettu)
            return
        }
        val supported = EditorInfoCompat.getContentMimeTypes(info)
            .any { ClipDescription.compareMimeTypes(mime, it) }
        if (!supported) {
            showMessage(R.string.leike_kuva_ei_tuettu)
            return
        }
        try {
            val uri = FileProvider.getUriForFile(this, FILE_AUTHORITY, File(path))
            val content = InputContentInfoCompat(uri, ClipDescription("leike", arrayOf(mime)), null)
            val committed = InputConnectionCompat.commitContent(
                ic, info, content,
                InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION, null,
            )
            cursor.onUnknownEdit()
            // Kenttä voi hylätä sisällön, vaikka se ilmoitti tukevansa tyyppiä.
            if (committed) {
                hideClipboardPanel()
                feedback()
            } else {
                showMessage(R.string.leike_kuva_ei_tuettu)
            }
        } catch (e: Exception) {
            showMessage(R.string.leike_kuva_ei_tuettu)
        }
    }

    /** Kirjoittaa kertyneet oppimismuutokset tietokantaan taustasäikeessä. */
    private fun flushLearned() {
        val db = database ?: return
        if (learning.dirtyCount == 0) return
        val dirty = learning.drainDirty()
        executeIo {
            try {
                // Erä kirjoitetaan transaktiossa: joko kaikki tai ei mitään.
                db.runInTransaction {
                    dirty.removedWords.forEach { db.dao().deleteWord(it) }
                    dirty.removedChainWords.forEach {
                        db.dao().deleteBigramsFor(it)
                        db.dao().deleteTrigramsFor(it)
                    }
                    if (dirty.words.isNotEmpty()) {
                        db.dao().upsertWords(
                            dirty.words.map {
                                WordEntity(
                                    it.word.lowercase(fiLocale), it.word,
                                    it.count, it.lastUsed, it.blocked, it.created,
                                    it.acceptedCount, it.ignoredCount, it.pinned,
                                )
                            }
                        )
                    }
                    if (dirty.bigrams.isNotEmpty()) {
                        db.dao().upsertBigrams(
                            dirty.bigrams.map {
                                BigramEntity(it.previous, it.next, it.count, it.lastUsed)
                            }
                        )
                    }
                    if (dirty.trigrams.isNotEmpty()) {
                        db.dao().upsertTrigrams(
                            dirty.trigrams.map {
                                TrigramEntity(it.first, it.second, it.next, it.count, it.lastUsed)
                            }
                        )
                    }
                }
            } catch (e: Exception) {
                // Kirjoitusvirhe ei saa kaataa näppäimistöä; erä palaa
                // jonoon ja kirjoitetaan seuraavan tallennuksen mukana.
                learning.requeueDirty(dirty)
            }
        }
    }

    private fun maybeFlush() {
        if (learning.dirtyCount >= FLUSH_THRESHOLD) flushLearned()
    }

    // Palvelu voi tuhoutua taustalatauksen aikana; suljettuun suorittimeen
    // ei saa jonottaa uutta työtä.
    private fun executeSuggest(task: Runnable) {
        if (destroyed || suggestExecutor.isShutdown) return
        try {
            suggestExecutor.execute(task)
        } catch (e: RejectedExecutionException) {
            // Palvelu on sulkeutumassa.
        }
    }

    private fun executeIo(task: Runnable) {
        if (destroyed || ioExecutor.isShutdown) return
        try {
            ioExecutor.execute(task)
        } catch (e: RejectedExecutionException) {
            // Palvelu on sulkeutumassa.
        }
    }

    private fun handleBackspaceKey() {
        if (translateMode && activeTranslateBuffer().isNotEmpty()) {
            // Poisto kohdistuu käännösnäkymään, ei kenttään; alueen alussa
            // poisto ei valu kenttään vaan jää tekemättä.
            if (activeTranslateBuffer().backspace()) {
                onTranslateBufferChanged()
            }
        } else if (!revertDoubleSpacePeriod() && !revertAutoCorrect()) {
            // Näppäintapahtumana, jotta valitun tekstin poisto toimii
            // sovelluksissa oikein.
            sendKeyToField(KeyEvent.KEYCODE_DEL)
        }
        // Poiston jälkeen kenttä ei enää vastaa kirjattua toimenpidettä.
        textUndo.clear()
        feedback(AudioManager.FX_KEYPRESS_DELETE)
    }

    /**
     * Kaksoisvälilyönti pisteeksi: nopeasti kahdesti painettu väli muuttaa
     * "sana " -> "sana. ". Laukeaa vain lauseen loppuun sopivan merkin
     * jäljessä, joten "sana,  " tai "sana.  " eivät saa ylimääräistä
     * pistettä.
     */
    private fun performDoubleSpacePeriod(): Boolean {
        val ic = currentInputConnection ?: return false
        // Ison alkukirjaimen ennuste tarvitsee koko sanan (lyhennesääntö),
        // ei vain viimeisiä merkkejä.
        val before = readBefore(ic, MAX_WORD_LOOKBACK)?.toString().orEmpty()
        if (before.length < 2 || !before.endsWith(" ")) return false
        if (!SmartSpace.canEndSentence(before[before.length - 2])) return false
        ic.beginBatchEdit()
        deleteFromField(ic, 1)
        commitToField(ic, ". ")
        ic.endBatchEdit()
        // Heti perään painettu kolmas väli ohitetaan kuten automaattivälin.
        punctSpaceAdded = true
        pendingPeriodRevert = true
        updateAutoCaps(before.dropLast(1) + ". ")
        return true
    }

    override fun onSpaceLongPress() {
        getSystemService(InputMethodManager::class.java)?.showInputMethodPicker()
    }

    override fun onBackspaceSwipe(words: Int) {
        if (emojiSearchActive) return
        if (translateMode) {
            // Käännösnäkymässä ei ole valintaa näytettäväksi: poisto tehdään lopuksi.
            translateSwipeWords = words
            swipeTick()
            return
        }
        val ic = currentInputConnection ?: return
        val swipe = backspaceSwipe ?: startBackspaceSwipe(ic) ?: return
        val length = WordTools.clampToWholeCharacter(
            swipe.before,
            WordTools.wordsBackspaceLength(swipe.before, words),
            BACKSPACE_SWIPE_LOOKBACK,
        )
        if (length == swipe.selected) return
        swipe.selected = length
        markOwnEdit()
        selectInField(ic, swipe.end - length, swipe.end)
        swipeTick()
    }

    /**
     * Aloittaa liu'utuksen kursorista. Jos kentässä on jo valinta tai kursorin
     * sijaintia ei saa selville, liu'utus jää pelkäksi poistoksi lopussa.
     *
     * Sijainti luetaan aina kentältä: odotettu kursori voi olla väärä esimerkiksi
     * silloin, kun pituusraja hylkäsi merkin, koska siitä ei tule valintapäivitystä.
     * Väärästä kohdasta alkava valinta poistaisi väärää tekstiä.
     */
    private fun startBackspaceSwipe(ic: InputConnection): BackspaceSwipe? {
        val extracted = ic.getExtractedText(ExtractedTextRequest(), 0) ?: return null
        if (extracted.selectionStart != extracted.selectionEnd || extracted.selectionEnd < 0) return null
        val end = extracted.startOffset + extracted.selectionEnd
        val before = ic.getTextBeforeCursor(BACKSPACE_SWIPE_LOOKBACK, 0) ?: return null
        cursor.onSetSelection(end, end)
        return BackspaceSwipe(before, end).also { backspaceSwipe = it }
    }

    /** Valinta on yhä se, jonka liu'utus teki: muu muokkaus ei saa joutua poistettavaksi. */
    private fun swipeSelectionIntact(swipe: BackspaceSwipe): Boolean {
        val selected = currentInputConnection?.getSelectedText(0) ?: return cursor.known &&
            cursor.start == swipe.end - swipe.selected && cursor.end == swipe.end
        val expected = swipe.before.subSequence(swipe.before.length - swipe.selected, swipe.before.length)
        return selected.toString() == expected.toString()
    }

    private fun selectInField(ic: InputConnection, start: Int, end: Int) {
        ic.setSelection(start, end)
        cursor.onSetSelection(start, end)
    }

    private fun swipeTick() {
        if (vibrationEnabled) {
            keyboardView?.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
    }

    override fun onBackspaceSwipeEnd() {
        if (emojiSearchActive) {
            emojiQuery.clear()
            updateEmojiSearch()
            return
        }
        autoSpaceState = 0
        punctSpaceAdded = false
        pendingRevert = null
        pendingPeriodRevert = false
        textUndo.clear()
        markOwnEdit()
        if (translateMode) {
            val words = translateSwipeWords
            translateSwipeWords = 0
            if (words > 0 && activeTranslateBuffer().isNotEmpty()) {
                repeat(words) { activeTranslateBuffer().backspaceWord() }
                onTranslateBufferChanged()
            }
            feedback(AudioManager.FX_KEYPRESS_DELETE)
            return
        }
        val swipe = backspaceSwipe
        backspaceSwipe = null
        // Ilman liu'utustilaa (kentässä valmis valinta) poistetaan valinta.
        if (swipe == null || (swipe.selected > 0 && swipeSelectionIntact(swipe))) {
            sendKeyToField(KeyEvent.KEYCODE_DEL)
            learning.resetContext()
        }
        feedback(AudioManager.FX_KEYPRESS_DELETE)
    }

    override fun onBackspaceSwipeCancel() {
        translateSwipeWords = 0
        val swipe = backspaceSwipe ?: return
        backspaceSwipe = null
        if (swipe.selected > 0) {
            currentInputConnection?.let { selectInField(it, swipe.end, swipe.end) }
        }
    }

    /** Kursoritilan muokkaustoiminnot; käännösnäkymässä ne koskevat kirjoitettavaa aluetta. */
    private fun handleEdit(command: EditCommand) {
        feedback()
        if (translateMode) {
            handleTranslateEdit(command)
            return
        }
        val ic = currentInputConnection ?: return
        when (command) {
            EditCommand.SELECT -> {
                selectionMode = !selectionMode
                keyboardView?.selectionActive = selectionMode
            }
            EditCommand.SELECT_ALL -> ic.performContextMenuAction(android.R.id.selectAll)
            EditCommand.COPY -> {
                if (cursor.known && cursor.start == cursor.end) {
                    showMessage(R.string.muokkaus_valitse_ensin)
                } else {
                    ic.performContextMenuAction(android.R.id.copy)
                    showMessage(R.string.kaannos_kopioitu)
                }
            }
            EditCommand.CUT -> {
                if (cursor.known && cursor.start == cursor.end) {
                    showMessage(R.string.muokkaus_valitse_ensin)
                } else {
                    normalizeSelection(ic)
                    ic.performContextMenuAction(android.R.id.cut)
                    endSelectionMode()
                }
            }
            EditCommand.PASTE -> {
                normalizeSelection(ic)
                ic.performContextMenuAction(android.R.id.paste)
                endSelectionMode()
            }
            EditCommand.HOME -> sendMovementKey(KeyEvent.KEYCODE_MOVE_HOME)
            EditCommand.END -> sendMovementKey(KeyEvent.KEYCODE_MOVE_END)
        }
        if (command != EditCommand.HOME && command != EditCommand.END) cursor.onUnknownEdit()
        learning.resetContext()
    }

    /**
     * Vasemmalle laajennetussa valinnassa alku on lopun jäljessä. Osa
     * sovelluksista ei kestä sitä liittäessään (Google Messages kaatui:
     * "replace (14 ... 12) has end before start"), joten valinta käännetään.
     */
    private fun normalizeSelection(ic: InputConnection) {
        val extracted = ic.getExtractedText(ExtractedTextRequest(), 0) ?: return
        if (extracted.selectionStart <= extracted.selectionEnd) return
        selectInField(
            ic,
            extracted.startOffset + extracted.selectionEnd,
            extracted.startOffset + extracted.selectionStart,
        )
    }

    private fun handleTranslateEdit(command: EditCommand) {
        val buffer = activeTranslateBuffer()
        when (command) {
            EditCommand.HOME -> {
                buffer.moveToStart()
                updateTranslateBar()
            }
            EditCommand.END -> {
                buffer.moveToEnd()
                updateTranslateBar()
            }
            EditCommand.COPY, EditCommand.CUT -> {
                if (buffer.isEmpty()) return
                clipboardManager?.setPrimaryClip(ClipData.newPlainText("", buffer.text))
                showMessage(R.string.kaannos_kopioitu)
                if (command == EditCommand.CUT) {
                    buffer.clear()
                    onTranslateBufferChanged()
                }
            }
            EditCommand.PASTE -> {
                val clipText = clipboardManager?.primaryClip
                    ?.takeIf { it.itemCount > 0 }
                    ?.getItemAt(0)?.coerceToText(this)?.toString()
                if (clipText.isNullOrEmpty()) return
                buffer.insert(clipText)
                onTranslateBufferChanged()
            }
            // Käännösnäkymän oma valinta tehdään pitkällä painalluksella.
            EditCommand.SELECT, EditCommand.SELECT_ALL ->
                showMessage(R.string.muokkaus_kaannos_valinta)
        }
    }

    private fun endSelectionMode() {
        selectionMode = false
        keyboardView?.selectionActive = false
    }

    /** Nuoli tai rivin alku/loppu; Valitse-tilassa shift pohjassa, jolloin valinta laajenee. */
    private fun sendMovementKey(keyCode: Int) {
        if (!selectionMode) {
            sendKeyToField(keyCode)
            return
        }
        val ic = currentInputConnection ?: return
        val time = SystemClock.uptimeMillis()
        val meta = KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON
        fun event(action: Int, code: Int, state: Int) = KeyEvent(
            time, time, action, code, 0, state,
            KeyCharacterMap.VIRTUAL_KEYBOARD, 0,
            KeyEvent.FLAG_SOFT_KEYBOARD or KeyEvent.FLAG_KEEP_TOUCH_MODE,
            InputDevice.SOURCE_KEYBOARD,
        )
        // Tekstikenttä lukee shift-tilan shift-näppäimen omasta tapahtumasta,
        // ei pelkästä nuolen metatilasta.
        ic.sendKeyEvent(event(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_SHIFT_LEFT, meta))
        ic.sendKeyEvent(event(KeyEvent.ACTION_DOWN, keyCode, meta))
        ic.sendKeyEvent(event(KeyEvent.ACTION_UP, keyCode, meta))
        ic.sendKeyEvent(event(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_SHIFT_LEFT, 0))
        cursor.onUnknownEdit()
    }

    /**
     * Askelpalautin heti kaksoisvälilyönnin pisteen jälkeen palauttaa välit,
     * jos kentän teksti on yhä se, mikä pisteestä jäi.
     */
    private fun revertDoubleSpacePeriod(): Boolean {
        if (!pendingPeriodRevert) return false
        pendingPeriodRevert = false
        val ic = currentInputConnection ?: return false
        if (readBefore(ic, 2)?.toString() != ". ") return false
        markOwnEdit()
        ic.beginBatchEdit()
        deleteFromField(ic, 2)
        commitToField(ic, "  ")
        ic.endBatchEdit()
        return true
    }

    /** Pohjassa pidetyn askelpalauttimen toistokerta: poistaa sanan kerrallaan. */
    override fun onKeyRepeat(action: KeyAction) {
        if (action != KeyAction.Backspace) {
            onKey(action)
            return
        }
        if (emojiSearchActive) {
            handleEmojiSearchKey(action)
            return
        }
        autoSpaceState = 0
        punctSpaceAdded = false
        pendingRevert = null
        pendingPeriodRevert = false
        if (translateMode && activeTranslateBuffer().isNotEmpty()) {
            if (activeTranslateBuffer().backspaceWord()) {
                onTranslateBufferChanged()
            }
        } else {
            val ic = currentInputConnection ?: return
            markOwnEdit()
            // Valinta poistetaan ensin: tekstipoisto osuisi valinnan edellä olevaan tekstiin.
            val hasSelection = !ic.getSelectedText(0).isNullOrEmpty() ||
                (cursor.known && cursor.start != cursor.end)
            val before = if (hasSelection) null else readBefore(ic, WORD_BACKSPACE_LOOKBACK)
            if (before.isNullOrEmpty()) {
                sendKeyToField(KeyEvent.KEYCODE_DEL)
            } else {
                deleteWordFromField(ic, before)
            }
        }
        textUndo.clear()
        feedback(AudioManager.FX_KEYPRESS_DELETE)
    }

    /**
     * Poistaa sanan kursorin edeltä. Jos kenttä ei näytä reagoivan tekstipoistoon
     * (eräät selainkentät), seuraavat poistot tehdään näppäintapahtumin. Tätä
     * sanaa ei poisteta uudelleen: muuttumaton teksti ei todista, ettei poisto
     * ole vielä matkalla, ja uusinta voisi viedä myös edeltävää tekstiä. Parempi
     * yksi tehoton painallus kuin hävinnyttä tekstiä.
     */
    private fun deleteWordFromField(ic: InputConnection, before: CharSequence) {
        val length = WordTools.clampToWholeCharacter(
            before,
            WordTools.wordBackspaceLength(before),
            WORD_BACKSPACE_LOOKBACK,
        )
        if (length == 0) {
            sendKeyToField(KeyEvent.KEYCODE_DEL)
            return
        }
        val word = before.subSequence(before.length - length, before.length).toString()
        val fullBefore = before.toString()
        if (deleteWordsWithKeys) {
            deleteWordWithKeys(ic, word)
            return
        }
        val positionBefore = if (cursor.known) cursor.start else null
        deleteFromField(ic, length)
        wordDeleteCheck?.let { mainHandler.removeCallbacks(it) }
        val session = editorSessionId
        val updatesAtDelete = selectionUpdates
        val check = Runnable {
            wordDeleteCheck = null
            // Valintapäivitys kertoo, että poisto meni perille.
            if (session != editorSessionId || updatesAtDelete != selectionUpdates) {
                return@Runnable
            }
            val current = currentInputConnection ?: return@Runnable
            val textNow = readBefore(current, fullBefore.length)?.toString()
            val truncated = fullBefore.length >= WORD_BACKSPACE_LOOKBACK
            val positionNow = if (truncated && textNow == fullBefore) {
                fieldPosition(current)
            } else {
                null
            }
            if (WordDeleteCheck.failed(fullBefore, textNow, truncated, positionBefore, positionNow)) {
                deleteWordsWithKeys = true
            }
        }
        wordDeleteCheck = check
        mainHandler.postDelayed(check, WORD_DELETE_CHECK_MS)
    }

    /**
     * Näppäintapahtumien määrä ei vastaa grafeemeja (yhdistävä tarke poistuu erikseen), joten
     * sana valitaan ja poistetaan kerralla. Jos kenttä ei tue valintaa, yksi tapahtuma grafeemia
     * kohden voi jättää sanaa osittain, mutta ei poista koskaan liikaa.
     */
    private fun deleteWordWithKeys(ic: InputConnection, word: String) {
        val end = fieldPosition(ic)
        if (end != null && end >= word.length) {
            selectInField(ic, end - word.length, end)
            if (ic.getSelectedText(0)?.toString() == word) {
                sendKeyToField(KeyEvent.KEYCODE_DEL)
                return
            }
            selectInField(ic, end, end)
        }
        repeat(WordTools.graphemeCount(word)) { sendKeyToField(KeyEvent.KEYCODE_DEL) }
    }

    private fun fieldPosition(ic: InputConnection): Int? =
        ic.getExtractedText(ExtractedTextRequest(), 0)
            ?.takeIf { it.selectionStart >= 0 }
            ?.let { it.startOffset + it.selectionStart }

    /**
     * Työkalurivin peruutus: viimeisin näppäimistön toimenpide perutaan
     * omalla kirjauksella, jos kentän teksti on yhä ennallaan; muuten
     * pyydetään kentän omaa peruutusta (Ctrl+Z), jota useimmat
     * muokkauskentät tukevat.
     */
    private fun performUndo() {
        // Käännösrivillä poisto hoituu askelpalauttimella; peruutus koskee kenttää.
        if (translateMode) return
        val ic = currentInputConnection ?: return
        markOwnEdit()
        val undo = textUndo.consume { length -> readBefore(ic, length) }
        if (undo != null) {
            ic.beginBatchEdit()
            deleteFromField(ic, undo.deleteLength)
            if (undo.restore.isNotEmpty()) commitToField(ic, undo.restore)
            ic.endBatchEdit()
            // Peruttu teksti ei saa jäädä sanaketjuun.
            learning.resetContext()
        } else {
            sendCtrlZ()
        }
        feedback()
        updateSuggestions()
    }

    private fun sendCtrlZ() {
        val ic = currentInputConnection ?: return
        val time = SystemClock.uptimeMillis()
        val meta = KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON
        ic.sendKeyEvent(KeyEvent(time, time, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_Z, 0, meta))
        ic.sendKeyEvent(KeyEvent(time, time, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_Z, 0, meta))
        cursor.onUnknownEdit()
    }

    /**
     * Välilyönti päättää sanan: sana opitaan, ja tuntematon sana korjataan
     * taustalla lähimpään tunnettuun. Korjauksen peruu askelpalauttimella.
     */
    private fun commitSpaceWithAutoCorrect() {
        val ic = currentInputConnection ?: return
        val known = readBefore(ic, MAX_WORD_LOOKBACK)?.toString()
        val before = known.orEmpty()
        val typed = WordTools.currentWord(before)
        val context = WordTools.previousWords(before)
        val shown = shownCompletions
        shownCompletions = emptyList()
        commitToField(ic, " ")
        updateAutoCaps(known?.let { "$it " })
        if (typed.isEmpty() || !learningEnabled) return
        if (!autoCorrectEnabled || !field.autoCorrectAllowed) {
            learning.onSuggestionsIgnored(shown, typed)
            learning.onWordCommitted(typed)
            maybeFlush()
            return
        }
        // Ratkaisu tehdään taustalla, ettei sanastohaku nyi näppäilyä.
        val session = editorSessionId
        executeSuggest {
            val corrected = suggestionEngine.autoCorrect(typed, context)
            mainHandler.post { applyAutoCorrect(typed, corrected, shown, session) }
        }
    }

    private fun applyAutoCorrect(
        typed: String,
        corrected: String?,
        shown: List<String>,
        session: Int,
    ) {
        val display = corrected?.let {
            if (typed.first().isUpperCase()) {
                it.replaceFirstChar { c -> c.titlecase(fiLocale) }
            } else {
                suggestionEngine.correctionForm(it)
            }
        }
        var applied = false
        val ic = currentInputConnection
        // Kenttä ei saa olla vaihtunut: sama häntä toisessa kentässä ei riitä.
        if (display != null && display != typed && ic != null && session == editorSessionId) {
            val tail = "$typed "
            // Korjataan vain jos teksti on yhä ennallaan — nopea kirjoittaja
            // on voinut jo jatkaa, eikä tekstiä saa muuttaa selän takana.
            if (readBefore(ic, tail.length)?.toString() == tail) {
                markOwnEdit()
                ic.beginBatchEdit()
                deleteFromField(ic, tail.length)
                commitToField(ic, "$display ")
                ic.endBatchEdit()
                pendingRevert = typed to display
                textUndo.record("$display ", "$typed ")
                applied = true
            }
        }
        val finalWord = if (applied) display ?: typed else typed
        learning.onSuggestionsIgnored(shown, finalWord)
        learning.onWordCommitted(finalWord)
        maybeFlush()
    }

    /** Askelpalautin heti korjauksen jälkeen palauttaa kirjoitetun sanan. */
    private fun revertAutoCorrect(): Boolean {
        val (typed, corrected) = pendingRevert ?: return false
        pendingRevert = null
        val ic = currentInputConnection ?: return false
        val tail = "$corrected "
        if (readBefore(ic, tail.length)?.toString() != tail) return false
        markOwnEdit()
        ic.beginBatchEdit()
        deleteFromField(ic, tail.length)
        commitToField(ic, typed)
        ic.endBatchEdit()
        if (learningEnabled) {
            // Peruttu sana opitaan omaksi, jottei sitä korjata enää uudestaan;
            // ketju katkaistaan, ettei korjatusta jää väärää sanaparia.
            learning.resetContext()
            learning.onWordCommitted(typed)
            maybeFlush()
        }
        return true
    }

    /** Oppii kursorin edellä olevan keskeneräisen sanan, jos oppiminen on sallittu. */
    private fun learnCurrentWord() {
        if (!learningEnabled) return
        val before = readBefore(MAX_WORD_LOOKBACK) ?: return
        val word = WordTools.currentWord(before)
        if (word.isNotEmpty()) {
            learning.onSuggestionsIgnored(shownCompletions, word)
            shownCompletions = emptyList()
            learning.onWordCommitted(word)
            maybeFlush()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Järjestelmän tumman tilan vaihtuminen päivittää teeman heti.
        applyVisualSettings()
        // Puhelimen kielen vaihto ei käynnistä prosessia uudelleen.
        val indexLanguage = emojiIndexFinnish
        if (indexLanguage != null && indexLanguage != finnishUi) {
            Thread { loadEmojiIndex() }.start()
        }
    }

    /**
     * Korkeusasetus on pystysuunnan koko; vaakasuunnassa ja pienillä
     * näytöillä rivit kutistuvat, ettei näppäimistö peitä koko sovellusta.
     */
    private fun effectiveHeightScale(): Float {
        val metrics = resources.displayMetrics
        return KeyboardHeight.effectiveScale(
            prefScale = prefs.getInt("korkeus", 100) / 100f,
            landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE,
            windowHeightDp = metrics.heightPixels / metrics.density,
            bottomInsetDp = (keyboardView?.bottomInset ?: 0) / metrics.density,
        )
    }

    /**
     * Salasanakentässä ruudunlukija ei sano merkkejä kaiuttimesta. Kuulokkeilla
     * tai järjestelmän asetuksen salliessa ne saa kuulla.
     */
    private fun refreshObscureKeyLabels() {
        keyboardView?.obscureKeyLabels = shouldObscureKeyLabels()
    }

    private fun shouldObscureKeyLabels(): Boolean {
        if (!field.password) return false
        @Suppress("DEPRECATION")
        val speakPasswords = Settings.Secure.getInt(
            contentResolver, Settings.Secure.ACCESSIBILITY_SPEAK_PASSWORD, 0
        ) != 0
        if (speakPasswords) return false
        val outputs = getSystemService(AudioManager::class.java)
            ?.getDevices(AudioManager.GET_DEVICES_OUTPUTS).orEmpty()
        return outputs.none { it.type in PRIVATE_AUDIO_OUTPUTS }
    }

    /** Yhden käden tilan asettelu; lukitusnäytöllä aina täysi leveys. */
    private fun oneHandMode(): OneHandMode = if (lockedMode) {
        OneHandMode.OFF
    } else {
        OneHandMode.from(prefs.getString(OneHand.PREF_MODE, null))
    }

    private fun applyOneHand() {
        val row = keyRow ?: return
        val column = keyColumn ?: return
        val side = oneHandSide ?: return
        val mode = oneHandMode()
        val metrics = resources.displayMetrics
        val columnParams = LinearLayout.LayoutParams(
            if (mode == OneHandMode.OFF) {
                LinearLayout.LayoutParams.MATCH_PARENT
            } else {
                OneHand.keyboardWidthPx(metrics.widthPixels, metrics.density)
            },
            LinearLayout.LayoutParams.WRAP_CONTENT,
        )
        val sideParams = LinearLayout.LayoutParams(
            0,
            LinearLayout.LayoutParams.MATCH_PARENT,
            1f,
        )
        row.removeAllViews()
        if (mode == OneHandMode.RIGHT) row.addView(side, sideParams)
        row.addView(column, columnParams)
        if (mode == OneHandMode.LEFT) row.addView(side, sideParams)
        toolbar?.oneHandActive = mode != OneHandMode.OFF
    }

    private fun setOneHandMode(mode: OneHandMode) {
        prefs.edit().apply {
            putString(OneHand.PREF_MODE, mode.id)
            if (mode != OneHandMode.OFF) putString(OneHand.PREF_LAST_SIDE, mode.id)
        }.apply()
        // Avoinna oleva valikko kuuluu vanhaan asetteluun.
        keyboardView?.dismissPopups()
        emojiPanel?.dismissVariants()
        applyOneHand()
    }

    private fun applyVisualSettings() {
        val theme = KeyboardTheme.load(this)
        val heightScale = effectiveHeightScale()
        vibrationEnabled = prefs.getBoolean("varina", true)
        keyboardView?.applySettings(
            theme,
            heightScale,
            // Salasanakentässä esikatselukupla jää pois, ettei syöte näy sivullisille.
            prefs.getBoolean("esikatselu", true) && !field.password,
            vibrationEnabled,
        )
        suggestionBar?.applySettings(theme, heightScale)
        emojiSearchBar?.applySettings(theme, heightScale)
        inlineChips?.applySettings(theme, heightScale)
        toolbar?.applySettings(theme)
        oneHandSide?.applySettings(theme)
        applyOneHand()
        clipboardPanel?.applySettings(theme)
        emojiPanel?.applySettings(theme)
        translateBar?.applySettings(theme)
        applyNavigationBarStyle(theme)
    }

    // Valittu teema voi poiketa järjestelmän tummasta tilasta: palkin kuvakkeiden
    // pitää erottua näppäimistön taustasta.
    private fun applyNavigationBarStyle(theme: KeyboardTheme) {
        val imeWindow = window?.window ?: return
        WindowCompat.getInsetsController(imeWindow, imeWindow.decorView)
            .isAppearanceLightNavigationBars = theme.light
        // Järjestelmän oma kontrastisuoja piirtäisi palkin alueelle eri värisen raidan.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            imeWindow.isNavigationBarContrastEnforced = false
        }
    }

    override fun onCreateInputView(): View {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        )
        // Yhden käden tilassa palsta kapenee ja sivupalkki täyttää loput.
        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        keyColumn = column
        keyRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        oneHandSide = OneHandSideView(this).also {
            it.listener = object : OneHandSideView.Listener {
                override fun onSwapSide() {
                    feedback()
                    val current = OneHandMode.from(prefs.getString(OneHand.PREF_MODE, null))
                    setOneHandMode(OneHand.otherSide(current))
                }

                override fun onExitOneHand() {
                    feedback()
                    setOneHandMode(OneHandMode.OFF)
                }
            }
        }
        toolbar = ToolbarView(this).also {
            it.listener = object : ToolbarView.Listener {
                override fun onToggleArrows() {
                    // Paneelit peittävät näppäimistön: sivunvaihto ei
                    // näkyisi, joten ne suljetaan ja siirrytään suoraan.
                    hideAllPanels()
                    page = if (page == Page.ARROWS) Page.LETTERS else Page.ARROWS
                    updateLayout()
                    updateAutoCaps()
                    updateSuggestions()
                    feedback()
                }

                override fun onToggleWeb() {
                    hideAllPanels()
                    page = if (page == Page.SYMBOLS3) Page.LETTERS else Page.SYMBOLS3
                    updateLayout()
                    updateAutoCaps()
                    updateSuggestions()
                    feedback()
                }

                override fun onToggleDictation() {
                    feedback()
                    // Sanelu ei saa kirjoittaa kenttään kesken korjauksen,
                    // ja sivutila palaa kirjaimiin ettei kahta korostusta
                    // jää päälle yhtä aikaa.
                    hideAllPanels()
                    resetSidePage()
                    // Käännöstilassa sanelu kirjoittaa käännösnäkymään sille
                    // alueelle, jota parhaillaan kirjoitetaan — kenttään ei
                    // mene mitään ennen vientiä.
                    when {
                        field.password -> Unit
                        dictationActive() -> {
                            dictation.stop()
                            openAiDictation?.stop()
                        }
                        checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
                            PackageManager.PERMISSION_GRANTED -> startDictation()
                        else -> {
                            // Lupa kysytään erillisellä aktiviteetilla; sanelu
                            // jatkuu automaattisesti kenttään palattaessa.
                            pendingDictation = true
                            startActivity(
                                Intent(
                                    this@KeyboardService,
                                    RecordAudioPermissionActivity::class.java,
                                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
                    }
                }

                override fun onToggleClipboard() {
                    feedback()
                    if (clipboardPanel?.visibility == View.VISIBLE) {
                        hideClipboardPanel()
                    } else {
                        showClipboardPanel()
                    }
                }

                override fun onToggleEmoji() {
                    feedback()
                    if (emojiPanel?.visibility == View.VISIBLE) {
                        hideEmojiPanel()
                    } else {
                        showEmojiPanel()
                    }
                }

                override fun onToggleTranslation() {
                    feedback()
                    if (translateMode) hideTranslateBar() else showTranslateBar()
                }

                override fun onUndo() = performUndo()

                override fun onToggleOneHand() {
                    feedback()
                    setOneHandMode(
                        OneHand.toggled(
                            OneHandMode.from(prefs.getString(OneHand.PREF_MODE, null)),
                            OneHandMode.from(prefs.getString(OneHand.PREF_LAST_SIDE, null)),
                        )
                    )
                }

                override fun onOpenSettings() {
                    // Näppäimistö suljetaan ensin, ettei se jää sovelluksen
                    // ruutukaappaukseen ja välähdä vanhalla teemalla palattaessa.
                    requestHideSelf(0)
                    startActivity(
                        Intent(this@KeyboardService, SettingsActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            }
            column.addView(it, LinearLayout.LayoutParams(params))
        }
        translateBar = TranslateBarView(this).also {
            it.listener = object : TranslateBarView.Listener {
                override fun onPickSource(code: String) {
                    feedback()
                    // Käsivalinta ohittaa kielen päättelyn tältä tekstiltä.
                    translateSourceLocked = true
                    pickTranslationLanguage(sourceSide = true, code = code)
                }

                override fun onSwap() {
                    feedback()
                    // Käsin käännettyä suuntaa ei saa päätellä heti takaisin.
                    translateSourceLocked = true
                    // Tuore käännös siirtyy suunnanvaihdossa lähdetekstiksi
                    // kuten Google Kääntäjässä — muuten rivillä olisi
                    // vaihdon jälkeen kahdesti samankielinen teksti.
                    val moved = currentTranslation
                        .takeIf { translationFresh && it.isNotBlank() }
                    val source = translationSource()
                    prefs.edit()
                        .putString(PREF_TRANSLATE_SOURCE, translationTarget())
                        .putString(PREF_TRANSLATE_TARGET, source)
                        .apply()
                    if (moved != null) {
                        translateBuffer.clear()
                        translateBuffer.insert(moved)
                        currentTranslation = ""
                        translationFresh = false
                    }
                    stopTranslationEditing()
                    onTranslatePairChanged()
                }

                override fun onPickTarget(code: String) {
                    feedback()
                    pickTranslationLanguage(sourceSide = false, code = code)
                }

                override fun onClear() {
                    feedback()
                    translateBuffer.clear()
                    currentTranslation = ""
                    translationFresh = false
                    stopTranslationEditing()
                    // Uusi teksti saa taas päätellä kielensä.
                    translateSourceLocked = false
                    // Vietyä käännöstä EI unohdeta: tyhjennyksen jälkeen
                    // kirjoitettu uusi teksti korvaa Lisää-napilla vanhan
                    // kentästä, joten viestin voi kirjoittaa kokonaan
                    // uusiksi ennen lähettämistä.
                    updateTranslateBar()
                    updateSuggestions()
                }

                override fun onAiTranslate() = requestAiTranslation()

                override fun onCopyTranslation() {
                    val translation = currentTranslation
                    if (translation.isBlank()) return
                    feedback()
                    clipboardManager?.setPrimaryClip(
                        ClipData.newPlainText("", translation)
                    )
                    // Järjestelmä ei näytä omaa kopiointikuplaansa
                    // näppäimistön kopioinneista, joten kuittaus on omamme.
                    showMessage(R.string.kaannos_kopioitu)
                }

                override fun onInsert() = exportTranslation()

                override fun onCloseView() {
                    feedback()
                    hideTranslateBar()
                }

                override fun onDownloadModels() {
                    feedback()
                    translationModelsMissing = false
                    updateTranslateBar()
                    // Käyttäjä antoi luvan: lataus ja valmistelu käyntiin.
                    prepareTranslator()
                }

                override fun onCursorTap(position: Int) {
                    // Paluu lähdetekstiin päättää käännöksen muokkauksen;
                    // korjattu käännös jää näkyviin ja vietäväksi, kunnes
                    // lähdetekstin muutos tuo tilalle uuden käännöksen.
                    stopTranslationEditing()
                    translateBuffer.setCursor(position)
                    updateTranslateBar()
                    updateSuggestions()
                    feedback()
                }

                override fun onTranslationTap(position: Int) {
                    // Käännöstä korjataan paikallaan: teksti siirtyy omaan
                    // puskuriinsa ja näppäily kohdistuu siihen.
                    if (currentTranslation.isEmpty()) return
                    if (!translationEditing) {
                        translationBuffer.clear()
                        translationBuffer.insert(currentTranslation)
                        translationEditing = true
                        dictationPending = 0
                        // Kesken oleva käännös ei saa pyyhkiä korjausta.
                        translateRunnable?.let { mainHandler.removeCallbacks(it) }
                        translationGeneration++
                        aiTranslateGeneration++
                    }
                    translationBuffer.setCursor(position)
                    // Korjattu käännös on se, joka viedään ja kopioidaan.
                    translationFresh = true
                    updateTranslateBar()
                    updateSuggestions()
                    feedback()
                }

                override fun onCopy(text: String) {
                    feedback()
                    clipboardManager?.setPrimaryClip(ClipData.newPlainText("", text))
                    // Järjestelmä ei näytä omaa kopiointikuplaansa
                    // näppäimistön kopioinneista, joten kuittaus on omamme.
                    showMessage(R.string.kaannos_kopioitu)
                }

                override fun onPaste() {
                    feedback()
                    val clipText = clipboardManager?.primaryClip
                        ?.takeIf { it.itemCount > 0 }
                        ?.getItemAt(0)?.coerceToText(this@KeyboardService)
                        ?.toString()
                    if (clipText.isNullOrEmpty()) return
                    markOwnEdit()
                    activeTranslateBuffer().insert(clipText)
                    onTranslateBufferChanged()
                }
            }
            it.visibility = View.GONE
            // Käännösalueet kaiken ylle: työkalurivi, ehdotukset ja
            // näppäimet pysyvät yhtenäisenä pakettina kuten aina. Paino
            // tekee näkymästä joustavan: se saa tilan, joka muilta jää,
            // eikä esimerkiksi ?123-sivun ylimääräinen näppäinrivi työnnä
            // alinta riviä navigointipalkin alle.
            container.addView(
                it,
                0,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f,
                ),
            )
        }
        inlineChips = InlineChipsView(this).also {
            it.visibility = View.GONE
            column.addView(it, LinearLayout.LayoutParams(params))
        }
        suggestionBar = SuggestionBarView(this).also {
            it.listener = ::onSuggestionItemPicked
            it.menuListener = object : SuggestionBarView.MenuListener {
                override fun isOwnWord(word: String) = learning.isOwnWord(word)

                override fun isPinned(word: String) = learning.isPinned(word)

                override fun onTogglePin(word: String) {
                    learning.setPinned(word, !learning.isPinned(word))
                    flushLearned()
                    updateSuggestions()
                    feedback()
                }

                override fun onDeleteLearned(word: String) {
                    learning.removeWord(word)
                    flushLearned()
                    updateSuggestions()
                    feedback()
                }

                override fun onBlockWord(word: String) {
                    learning.blockWord(word)
                    flushLearned()
                    updateSuggestions()
                    feedback()
                }
            }
            column.addView(it, LinearLayout.LayoutParams(params))
        }
        emojiSearchBar = EmojiSearchBarView(this).also {
            it.listener = object : EmojiSearchBarView.Listener {
                override fun onResultPicked(emoji: String) = insertEmoji(emoji)

                override fun onClearQuery() {
                    feedback()
                    emojiQuery.clear()
                    updateEmojiSearch()
                }

                override fun onCloseSearch() {
                    feedback()
                    closeEmojiSearch(returnToPanel = true)
                }
            }
            it.visibility = View.GONE
            column.addView(it, LinearLayout.LayoutParams(params))
        }
        keyboardView = KeyboardView(this).also {
            it.listener = this
            // Navigointipalkin varaus selviää vasta ikkunassa; korkeuskatto
            // lasketaan silloin uudelleen.
            it.onBottomInsetChanged = { applyVisualSettings() }
            column.addView(it, LinearLayout.LayoutParams(params))
        }
        clipboardPanel = ClipboardPanelView(this).also {
            it.listener = object : ClipboardPanelView.Listener {
                override fun onPaste(clip: Clip) = pasteClip(clip)

                override fun onTogglePin(clip: Clip) {
                    clipStore.setPinned(clip.id, !clip.pinned)?.let(::persistClip)
                    clipboardPanel?.setClips(clipStore.all())
                }

                override fun onWebSearch(clip: Clip) {
                    val query = clip.text ?: return
                    hideClipboardPanel()
                    try {
                        // Suora hakutulossivu: haku suoritetaan heti eikä vain
                        // esitäytetä hakukenttää.
                        val url = "https://www.google.com/search?q=" + Uri.encode(query)
                        startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    } catch (e: Exception) {
                        showMessage(R.string.leike_haku_epaonnistui)
                    }
                }

                override fun onDelete(clip: Clip) {
                    clipStore.remove(clip.id)
                    if (recentClip.clearIfClip(clip.id)) refreshSuggestionRow()
                    val db = database
                    executeIo {
                        try {
                            db?.dao()?.deleteClip(clip.id)
                            clip.imagePath?.let { path -> File(path).delete() }
                        } catch (e: Exception) {
                            // Poistovirhe ei saa kaataa näppäimistöä.
                        }
                    }
                    clipboardPanel?.setClips(clipStore.all())
                }

                override fun onCreatePin() {
                    // Oma kiinnitetty leike luodaan pienessä ikkunassa;
                    // leikepöytä avataan takaisin kenttään palattaessa.
                    pendingClipboardPanel = true
                    startActivity(
                        Intent(this@KeyboardService, NewClipActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            }
            it.visibility = View.GONE
            column.addView(it, LinearLayout.LayoutParams(params))
        }
        emojiPanel = EmojiPanelView(this).also {
            it.listener = object : EmojiPanelView.Listener {
                override fun onEmojiPicked(emoji: String) = insertEmoji(emoji)

                override fun onBackspace() = handleBackspaceKey()

                override fun onSpace() {
                    markOwnEdit()
                    if (translateMode) {
                        activeTranslateBuffer().insert(" ")
                        onTranslateBufferChanged()
                    } else {
                        currentInputConnection?.let { ic -> commitToField(ic, " ") }
                    }
                    feedback(AudioManager.FX_KEYPRESS_SPACEBAR)
                }

                override fun onClose() {
                    feedback()
                    hideEmojiPanel()
                    updateAutoCaps()
                    updateSuggestions()
                }

                override fun onOpenSearch() {
                    feedback()
                    openEmojiSearch()
                }
            }
            it.visibility = View.GONE
            column.addView(it, LinearLayout.LayoutParams(params))
        }
        container.addView(keyRow, LinearLayout.LayoutParams(params))
        applyOneHand()
        return container
    }

    /** Ehdotusrivin paikka: täyttöchipit, kun niitä on, muuten sanaehdotukset. */
    private fun updateSuggestionRow() {
        if (emojiSearchActive) {
            inlineChips?.visibility = View.GONE
            suggestionBar?.visibility = View.GONE
            return
        }
        val reserved = InlineChips.reservesRow(
            autofillActive = inlineAutofillActive,
            textPassword = field.password && !field.numeric,
            suggestionsEnabled = prefs.getBoolean("ehdotukset", true),
        )
        val state = InlineChips.rowState(
            suggestionsVisible || recentClipItem() != null || reserved,
            // Käännösnäkymän ehdotukset koskevat sen omaa tekstiä; chipit väistyvät.
            if (translateMode) 0 else inlineChips?.count ?: 0,
        )
        inlineChips?.visibility = if (state.chipsVisible) View.VISIBLE else View.GONE
        suggestionBar?.visibility = if (state.suggestionsVisible) View.VISIBLE else View.GONE
    }

    /**
     * Tuore kopio rivin kohteena, jos sen saa näyttää. Numerokentässäkin:
     * vahvistuskoodin liittäminen on tavallisin käyttö. Salasanoja ei näytetä.
     */
    private fun recentClipItem(): SuggestionItem.RecentClip? {
        if (field.password || field.rawKeys || page == Page.ARROWS) return null
        if (!prefs.getBoolean("ehdotukset", true)) return null
        return recentClip.current()?.let { SuggestionItem.RecentClip(RecentClipOffer.preview(it.text)) }
    }

    private fun refreshSuggestionRow() {
        if (!isInputViewShown) return
        updateSuggestionRow()
        updateSuggestions()
    }

    private fun onSuggestionItemPicked(item: SuggestionItem) {
        when (item) {
            is SuggestionItem.Word -> onSuggestionPicked(item.text)
            is SuggestionItem.Emoji -> pickEmojiSuggestion(item.text)
            is SuggestionItem.RecentClip -> pasteRecentClip()
        }
    }

    private fun pasteRecentClip() {
        val offer = recentClip.current() ?: return
        recentClip.clear()
        mainHandler.removeCallbacks(recentClipExpiry)
        val ic = currentInputConnection
        if (ic != null) {
            markOwnEdit()
            if (translateMode) {
                activeTranslateBuffer().insert(offer.text)
                onTranslateBufferChanged()
            } else {
                commitToField(ic, offer.text)
                textUndo.record(offer.text)
                updateAutoCaps()
            }
            feedback()
        }
        refreshSuggestionRow()
    }

    private fun clearInlineChips() {
        inlineGeneration++
        if ((inlineChips?.count ?: 0) == 0) return
        inlineChips?.clear()
        updateSuggestionRow()
    }

    /**
     * Täyttöpalvelun (esim. salasanojen hallinnan) ehdotukset pyydetään näppäimistön riville
     * kentän viereisen pudotusvalikon sijaan: chip näkyy silloinkin, kun
     * kenttä itse on näppäimistön alla piilossa. Ulkoasu tulee teeman väreistä.
     *
     * Pyyntö tulee ensimmäisellä avauksella ennen näkymän luontia. Jos siihen
     * vastattaisiin tyhjällä, ehdotus näkyisi pudotusvalikkona kentän vieressä.
     */
    @RequiresApi(Build.VERSION_CODES.R)
    override fun onCreateInlineSuggestionsRequest(uiExtras: Bundle): InlineSuggestionsRequest? {
        if (lockedMode) return null
        if (!inlineAutofillActive) {
            inlineAutofillActive = true
            updateSuggestionRow()
        }
        val metrics = resources.displayMetrics
        val rowWidth = if (oneHandMode() == OneHandMode.OFF) {
            metrics.widthPixels
        } else {
            OneHand.keyboardWidthPx(metrics.widthPixels, metrics.density)
        }
        val bounds = InlineChips.specBounds(
            InlineChips.barHeightPx(metrics.density, effectiveHeightScale()),
            rowWidth,
            metrics.density,
        )
        val spec = InlinePresentationSpec.Builder(
            Size(bounds.minWidth, bounds.height),
            Size(bounds.maxWidth, bounds.height),
        ).setStyle(inlineChipStyle()).build()
        return InlineSuggestionsRequest.Builder(listOf(spec))
            .setMaxSuggestionCount(InlineChips.MAX_SUGGESTIONS)
            .build()
    }

    // ViewStyle.Builderin setBackground/setPadding periytyvät kirjaston sisäiseksi
    // merkitystä BaseBuilderista, vaikka ne ovat julkista API:a; lintin
    // RestrictedApi on tässä väärä hälytys (androidx.autofill 1.1.0).
    @SuppressLint("RestrictedApi")
    @RequiresApi(Build.VERSION_CODES.R)
    private fun inlineChipStyle(): Bundle {
        val theme = KeyboardTheme.load(this)
        val pad = (12 * resources.displayMetrics.density).toInt()
        val chip = ViewStyle.Builder()
            .setBackground(
                Icon.createWithResource(this, R.drawable.inline_chip_bg).setTint(theme.specialKey)
            )
            .setPadding(pad, 0, pad, 0)
            .build()
        val style = InlineSuggestionUi.newStyleBuilder()
            .setChipStyle(chip)
            .setSingleIconChipStyle(chip)
            .setTitleStyle(TextViewStyle.Builder().setTextColor(theme.text).setTextSize(15f).build())
            .setSubtitleStyle(TextViewStyle.Builder().setTextColor(theme.hint).setTextSize(13f).build())
            .build()
        return UiVersions.newStylesBuilder().addStyle(style).build()
    }

    @RequiresApi(Build.VERSION_CODES.R)
    override fun onInlineSuggestionsResponse(response: InlineSuggestionsResponse): Boolean {
        val chips = inlineChips ?: return false
        val generation = ++inlineGeneration
        val suggestions = response.inlineSuggestions
        if (suggestions.isEmpty()) {
            chips.clear()
            updateSuggestionRow()
            return true
        }
        val views = arrayOfNulls<View>(suggestions.size)
        var pending = suggestions.size
        suggestions.forEachIndexed { index, suggestion ->
            // Korkeus pyynnön mukaan: inflate kaatuu, jos koko ei mahdu sen rajoihin, ja
            // näytön kääntö tai korkeusasetus voi muuttaa riviä pyynnön ja vastauksen välissä.
            val size = Size(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                suggestion.info.inlinePresentationSpec.maxSize.height,
            )
            suggestion.inflate(this, size, mainExecutor) { view ->
                // Vastaus vanheni (kenttä vaihtui tai uusi vastaus tuli)
                // ennen kuin chip valmistui.
                if (generation != inlineGeneration) return@inflate
                views[index] = view
                if (--pending == 0) {
                    chips.setChips(views.filterNotNull())
                    updateSuggestionRow()
                }
            }
        }
        return true
    }

    // Koko näytön muokkaustila vaakasuunnassa peittäisi sovelluksen — pidetään pois.
    override fun onEvaluateFullscreenMode(): Boolean = false

    /**
     * Käännösnäkymä piirtyy sovelluksen päälle Gboardin laajenevien
     * paneelien tapaan: sovellukselle kerrotaan vain näppäimistöosan
     * viemä tila. Ilman tätä koko ruudun korkuinen näkymä kutistaisi
     * sovelluksen olemattomiin, jolloin kenttä menettää fokuksen ja
     * käynnistää syötteen uudelleen — vienti kenttään katkesi ja näkymä
     * saattoi sulkeutua heti avauksen perään.
     */
    override fun onComputeInsets(outInsets: Insets) {
        super.onComputeInsets(outInsets)
        if (!translateMode) return
        val bar = translateBar ?: return
        if (bar.visibility != View.VISIBLE || bar.height == 0) return
        outInsets.contentTopInsets += bar.height
        outInsets.visibleTopInsets += bar.height
        // Koko näkymä ottaa silti kosketukset vastaan.
        outInsets.touchableInsets = Insets.TOUCHABLE_INSETS_FRAME
    }

    override fun onStartInput(attribute: EditorInfo, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        // Eri kentillä voi olla samat tunnisteet, joten viety käännös sidotaan
        // syöteistuntoon. Näppäimistön piilotus ei aloita uutta istuntoa.
        if (!restarting) exportedTranslation.clear()
    }

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        if (lockedMode && DeviceLock.isUnlocked(this)) completeUnlock()
        soundEnabled = prefs.getBoolean("aanet", false)
        vibrationEnabled = prefs.getBoolean("varina", true)

        field = FieldProfile.from(info.inputType, info.imeOptions, info.packageName)
        // Uudessa kentässä ei ole vietyä käännöstä korvattavaksi. Saman kentän
        // uudelleenavaus (näppäimistö piilotettiin välissä) säilyttää sen.
        exportedTranslation.onFieldStarted(
            ExportedTranslation.Field(info.packageName, info.fieldId, info.inputType)
        )

        applyVisualSettings()
        refreshObscureKeyLabels()
        if (lockedMode) toolbar?.visibility = View.GONE

        // Kentän vaihtuessa haku suljetaan ensin, ettei sen palauttama sivu jää
        // voimaan uudessa kentässä. Saman kentän uudelleenkäynnistys jättää
        // haun auki, ja sivu palaa siitä kentän mukaisena.
        if (!restarting) closeEmojiSearch(returnToPanel = false)
        val fieldPage = if (field.numeric) Page.NUMERIC else Page.LETTERS
        if (emojiSearchActive) pageBeforeSearch = fieldPage else page = fieldPage

        keyboardView?.enterText = enterLabel()
        editorSessionId++
        cancelDeferredFieldWork()
        if (info.packageName != lastPackage) {
            lastPackage = info.packageName
            slowConnection.reset()
        }
        if (!restarting) deleteWordsWithKeys = false
        cursor.begin(
            info.initialSelStart,
            info.initialSelEnd,
            // Kynnys koskee lyhyitä näppäilyn aikaisia lukuja, ei tätä isoa kertalukua.
            currentInputConnection?.getTextBeforeCursor(CURSOR_CHECK_LOOKBACK, 0)?.length,
            CURSOR_CHECK_LOOKBACK,
        )
        shownCompletions = emptyList()
        shift.reset()
        // Salasanakentissä ja oppimisen kieltävissä kentissä (esim. incognito)
        // ei opita mitään; kentän vaihto katkaisee sanaketjun.
        learningEnabled = field.learningAllowed && !lockedMode
        learning.resetContext()
        reloadLearnedIfChanged()
        if (!restarting) {
            // Vanhan kentän täyttöchipit eivät kuulu uudelle kentälle.
            clearInlineChips()
            // Kentän vaihto (esim. sovelluksen lähetysnappi) katkaisee
            // sanelun ja sulkee näkymät. Saman kentän syötteen uudelleen-
            // käynnistys (restartInput, esim. sovelluksen reaktio näkymän
            // koon muutokseen) ei saa sulkea mitään — käännösnäkymä
            // välähtäisi muuten auki ja heti kiinni.
            stopDictation()
            hideAllPanels()
            // Kenttä vaihtui: näkymä sulkeutuu, mutta käännöstyö säilyy
            // puskurissa, koska mikään ei kirjoitu kenttiin itsestään.
            hideTranslateBar()
        }
        if (pendingDictation) {
            pendingDictation = false
            if (!field.password &&
                checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
            ) {
                startDictation()
            }
        }
        if (pendingClipboardPanel) {
            pendingClipboardPanel = false
            showClipboardPanel()
        }
        if (translateMode) watchTranslateBack(true)
        spaceAfterSuggestion = prefs.getBoolean("ehdotus_valilyonti", true)
        commonWordsEnabled = prefs.getBoolean("ehdotus_yleiset", finnishUi)
        autoCorrectEnabled = prefs.getBoolean("automaattikorjaus", finnishUi)
        numberRowEnabled = prefs.getBoolean(PREF_NUMBER_ROW, true)
        doubleSpacePeriodEnabled = prefs.getBoolean(PREF_DOUBLE_SPACE, true)
        lastSpaceTime = 0
        symbolOrder = SymbolOrder.load(prefs.getString(SymbolOrder.PREF_KEY, null))
        toolbar?.tools = ToolbarOrder.load(prefs.getString(ToolbarOrder.PREF_KEY, null)).visible
        pendingRevert = null
        pendingPeriodRevert = false
        textUndo.clear()
        suggestionsVisible = prefs.getBoolean("ehdotukset", true) && field.suggestionsAllowed &&
            !lockedMode
        updateSuggestionRow()
        updateLayout()
        updateAutoCaps()
        updateSuggestions()
    }

    private fun onSuggestionPicked(word: String) {
        markOwnEdit()
        if (translateMode) {
            // Valinta korvaa käännösnäkymän keskeneräisen sanan; oppiminen
            // toimii kuten kentässä.
            val buffer = activeTranslateBuffer()
            val beforeCursor = buffer.text.substring(0, buffer.cursor)
            val current = WordTools.currentWord(beforeCursor)
            buffer.deleteBeforeCursor(current.length)
            buffer.insert(if (spaceAfterSuggestion) "$word " else word)
            if (learningEnabled) {
                learning.onSuggestionsIgnored(shownCompletions, word)
                shownCompletions = emptyList()
                if (word == current) {
                    learning.onTypedWordAccepted(word)
                } else {
                    learning.onSuggestionAccepted(word)
                }
                maybeFlush()
            }
            feedback()
            onTranslateBufferChanged()
            return
        }
        val ic = currentInputConnection ?: return
        val known = readBefore(ic, MAX_WORD_LOOKBACK)?.toString()
        val before = known.orEmpty()
        val current = WordTools.currentWord(before)
        val committed = if (spaceAfterSuggestion) "$word " else word
        ic.beginBatchEdit()
        if (current.isNotEmpty()) deleteFromField(ic, current.length)
        commitToField(ic, committed)
        ic.endBatchEdit()
        textUndo.record(committed, current)
        autoSpaceState = if (spaceAfterSuggestion) 2 else 0
        if (learningEnabled) {
            // Muut näkyneet täydennykset ohitettiin; valittu sana saa hyväksynnän.
            learning.onSuggestionsIgnored(shownCompletions, word)
            shownCompletions = emptyList()
            if (word == current) {
                // Rivin kärjessä ollut oma kirjoitettu sana opitaan kuin kirjoitettuna.
                learning.onTypedWordAccepted(word)
            } else {
                learning.onSuggestionAccepted(word)
            }
            maybeFlush()
        }
        feedback()
        updateAutoCaps(known?.let { it.dropLast(current.length) + committed })
        updateSuggestions()
    }

    private fun updateSuggestions(immediate: Boolean = false) {
        val bar = suggestionBar ?: return
        // Jokainen päivitys mitätöi kesken olevan laskennan, myös silloin kun
        // uutta ei aloiteta: vanha tulos ei saa ylikirjoittaa uuden kentän riviä.
        val generation = ++suggestGeneration
        if (emojiSearchActive) return
        // Hitaassa kentässä tekstikysely odottaa kirjoitustaukoa, ettei
        // jokainen näppäily jää odottamaan sovelluksen vastausta. Ajastus tehdään
        // myös ilman ehdotuksia, koska se päivittää myös ison alkukirjaimen.
        val deferred = !immediate && !translateMode && slowConnection.isSlow(SystemClock.uptimeMillis())
        if (deferred) {
            mainHandler.removeCallbacks(deferredRefresh)
            mainHandler.postDelayed(deferredRefresh, SLOW_REFRESH_MS)
        }
        val clipItem = recentClipItem()
        // Nuolitilassa kursoria liikutellaan tekstin yli; ehdotukset olisivat vain häiriöksi.
        // Tämä ei kysele kentältä mitään, joten vanhat sanat eivät jää salasanakentän
        // riville hitaankaan kentän viiveen ajaksi.
        if (!suggestionsVisible || page == Page.ARROWS) {
            bar.setSuggestions(listOfNotNull(clipItem))
            return
        }
        if (deferred) return
        // Käännöstilassa ehdotukset lasketaan siitä alueesta, jota kirjoitetaan.
        val before: CharSequence = if (translateMode) {
            val buffer = activeTranslateBuffer()
            buffer.text.substring(0, buffer.cursor)
        } else {
            readBefore(MAX_WORD_LOOKBACK) ?: ""
        }
        // Lauseen alussa (automaattinen iso kirjain päällä) ehdotukset alkavat isolla.
        val shiftActive = shift.state != ShiftState.OFF
        val index = emojiIndex
        val recents = emojiRecents
        executeSuggest {
            val word = WordTools.currentWord(before)
            val context = WordTools.previousWords(before)
            // Emoji kirjoitettavalle tai juuri valmistuneelle sanalle.
            val emoji = index
                ?.suggest(word.ifEmpty { context.lastOrNull().orEmpty() }, recents?.recent.orEmpty().toSet())
                .orEmpty()
                .map { SuggestionItem.Emoji(recents?.variantFor(it.emoji) ?: it.emoji) }
            val candidates = if (word.isNotEmpty()) {
                suggestionEngine.suggest(word, context)
            } else {
                suggestionEngine.emptyInput(context, commonWordsEnabled)
            }
            val result = suggestionEngine.applyCase(candidates, word, shiftActive)
            if (generation == suggestGeneration) {
                mainHandler.post {
                    if (generation == suggestGeneration) {
                        // Kopio väistyy, kun käyttäjä kirjoittaa sanaa.
                        val clip = if (word.isEmpty()) clipItem else null
                        val words = WordTools.withTypedWord(word, result).map(SuggestionItem::Word)
                        // Kirjoitettu sana pysyy kärjessä ja emoji heti sen perässä.
                        val head = if (word.isEmpty()) 0 else 1
                        bar.setSuggestions(
                            listOfNotNull(clip) + words.take(head) + emoji + words.drop(head)
                        )
                        shownCompletions =
                            if (word.isNotEmpty()) result.take(SHOWN_TOP_COUNT) else emptyList()
                    }
                }
            }
        }
    }

    private fun enterLabel(): String = when (field.enterAction) {
        EditorInfo.IME_ACTION_SEARCH -> getString(R.string.enter_hae)
        EditorInfo.IME_ACTION_SEND -> getString(R.string.enter_laheta)
        EditorInfo.IME_ACTION_GO -> getString(R.string.enter_siirry)
        EditorInfo.IME_ACTION_NEXT -> getString(R.string.enter_seuraava)
        EditorInfo.IME_ACTION_DONE -> getString(R.string.enter_valmis)
        else -> "⏎"
    }

    private fun updateLayout() {
        val layout = when (page) {
            Page.LETTERS -> Layouts.letters(field.extraKey, numberRowEnabled)
            Page.SYMBOLS1 -> Layouts.symbols1(symbolOrder)
            Page.SYMBOLS2 -> Layouts.symbols2(symbolOrder)
            Page.SYMBOLS3 -> Layouts.symbols3
            Page.NUMERIC -> Layouts.numeric
            Page.ARROWS -> Layouts.arrows(
                EditLabels(
                    home = getString(R.string.muokkaus_alkuun),
                    end = getString(R.string.muokkaus_loppuun),
                    select = getString(R.string.muokkaus_valitse),
                    copy = getString(R.string.muokkaus_kopioi),
                    paste = getString(R.string.muokkaus_liita),
                    selectAll = getString(R.string.muokkaus_valitse_kaikki),
                    cut = getString(R.string.muokkaus_leikkaa),
                )
            )
        }
        keyboardView?.setKeyboardLayout(layout)
        keyboardView?.shiftState = shift.state
        if (page != Page.ARROWS) endSelectionMode()
        // Työkalurivin korostukset seuraavat aina nykyistä sivua.
        toolbar?.arrowsActive = page == Page.ARROWS
        toolbar?.webActive = page == Page.SYMBOLS3
    }

    override fun onText(text: String) {
        if (emojiSearchActive) {
            emojiQuery.insert(text.lowercase(fiLocale))
            updateEmojiSearch()
            feedback()
            return
        }
        val ic = currentInputConnection ?: return
        markOwnEdit()
        pendingRevert = null
        pendingPeriodRevert = false
        textUndo.clear()
        if (translateMode) {
            // Käännöstilassa näppäily kertyy käännösnäkymään; kenttään
            // kirjoittuu vain valmis käännös.
            val output = if (shift.state != ShiftState.OFF && text.length == 1) {
                text.uppercase(fiLocale)
            } else {
                text
            }
            activeTranslateBuffer().smartType(output)
            if (shift.onCharacterTyped()) keyboardView?.shiftState = shift.state
            onTranslateBufferChanged()
            feedback()
            return
        }
        // Erotinmerkki päättää keskeneräisen sanan: opitaan se ennen merkin syöttöä.
        if (text.length == 1 && !text[0].isLetterOrDigit() && text[0] != '-') {
            learnCurrentWord()
        }
        val punctuation = text.length == 1 && SmartSpace.isPunctuation(text[0])
        // Tekstikyselyt tehdään vain välimerkille: tavallinen kirjain ei
        // tarvitse niitä, ja jokainen kysely odottaa sovelluksen vastausta.
        // Rivin loppu kelpaa kuten kentän loppu: monirivisissä kentissä
        // (esim. Keepin muistiinpanot) kursorin jäljessä on rivinvaihto,
        // eikä se saa estää välimerkkisääntöjä.
        val afterCursor = if (punctuation) ic.getTextAfterCursor(1, 0)?.toString() else null
        val atLineEnd = afterCursor.isNullOrEmpty() || afterCursor == "\n"
        // Edeltävä teksti luetaan ennen muokkausta: välimerkkisäännöt
        // katsovat viimeistä merkkiä, ja ison alkukirjaimen ennusteeseen
        // muokkaus sovelletaan siihen kerran.
        val before = if (punctuation) readBefore(ic, MAX_WORD_LOOKBACK)?.toString() else null
        val prev = before?.lastOrNull()
        if ((autoSpaceState > 0 || field.smartSpace) && punctuation && prev == ' ' && atLineEnd) {
            // Välilyönti siirtyy välimerkin taakse: "sana ." -> "sana. "
            ic.beginBatchEdit()
            deleteFromField(ic, 1)
            commitToField(ic, text + " ")
            ic.endBatchEdit()
            if (autoSpaceState > 0) autoSpaceState = 2
            punctSpaceAdded = true
            updateAutoCaps(before.orEmpty().dropLast(1) + text + " ")
            feedback()
            return
        }
        autoSpaceState = 0
        punctSpaceAdded = false
        // Vain yksittäiset merkit shiftautuvat; esim. verkko-osoitepalat pysyvät ennallaan.
        val output = if (shift.state != ShiftState.OFF && text.length == 1) {
            text.uppercase(fiLocale)
        } else {
            text
        }
        // Välitön jälkiväli kuten muissakin näppäimistöissä: välimerkki
        // sanan perässä saa välin heti peräänsä. Numeron perässä sääntö
        // ei laukea (3,14) eikä keskellä tekstiä; osoitteen (jarsi.org)
        // välin saa pois yhdellä askelpalauttimella.
        if (field.smartSpace && punctuation) {
            if (prev != null && prev != ' ' && !prev.isDigit() && atLineEnd) {
                commitToField(ic, text + " ")
                punctSpaceAdded = true
                updateAutoCaps(before.orEmpty() + text + " ")
                feedback()
                return
            }
        }
        commitToField(ic, output)
        if (shift.onCharacterTyped()) keyboardView?.shiftState = shift.state
        feedback()
    }

    override fun onKey(action: KeyAction) {
        if (emojiSearchActive && handleEmojiSearchKey(action)) return
        // Tekstiä muuttava näppäin katkaisee automaattivälin seurannan.
        when (action) {
            KeyAction.Backspace, KeyAction.Enter, is KeyAction.Arrow, is KeyAction.Edit -> {
                autoSpaceState = 0
                punctSpaceAdded = false
                markOwnEdit()
            }
            KeyAction.Space -> {
                autoSpaceState = 0
                markOwnEdit()
            }
            else -> Unit
        }
        // Korjauksen voi perua vain heti perään; muut näppäimet mitätöivät.
        if (action != KeyAction.Backspace && action != KeyAction.Shift) {
            pendingRevert = null
            pendingPeriodRevert = false
            textUndo.clear()
        }
        when (action) {
            KeyAction.Shift -> handleShift()
            KeyAction.Backspace -> handleBackspaceKey()
            KeyAction.Enter -> {
                if (translateMode) {
                    // Käännösnäkymä on oma työkalunsa: enter tekee
                    // rivinvaihdon omaan tekstiin kuten Google Kääntäjässä.
                    activeTranslateBuffer().insert("\n")
                    onTranslateBufferChanged()
                    feedback()
                    return
                }
                // Rivinvaihto päättää sanan muttei katkaise sanaketjua.
                learnCurrentWord()
                handleEnter()
                // Rivinvaihdon jälkeen kirjoitus jatkuu kirjaimilla, joten
                // erikoismerkkisivu ei saa jäädä päälle.
                if (page == Page.SYMBOLS1 || page == Page.SYMBOLS2) {
                    page = Page.LETTERS
                    updateLayout()
                    updateAutoCaps()
                    updateSuggestions()
                }
            }
            KeyAction.Space -> {
                val swallowPunctSpace = punctSpaceAdded
                punctSpaceAdded = false
                val doubleTap = doubleSpacePeriodEnabled &&
                    SystemClock.uptimeMillis() - lastSpaceTime < DOUBLE_SPACE_PERIOD_MS
                lastSpaceTime = SystemClock.uptimeMillis()
                if (translateMode) {
                    val buffer = activeTranslateBuffer()
                    if (doubleTap && buffer.doubleSpacePeriod()) {
                        // Kaksoisvälilyönti pisteeksi myös käännösnäkymässä.
                        lastSpaceTime = 0
                    } else {
                        buffer.smartSpace()
                    }
                    onTranslateBufferChanged()
                } else if (doubleTap && field.smartSpace && performDoubleSpacePeriod()) {
                    lastSpaceTime = 0
                } else if (swallowPunctSpace && readBefore(1)?.toString() == " ") {
                    // Automaattivälin perään painettu väli ohitetaan,
                    // ettei "sana, " saa tuplaväliä totutusta näppäilystä.
                } else {
                    commitSpaceWithAutoCorrect()
                }
                feedback(AudioManager.FX_KEYPRESS_SPACEBAR)
            }
            KeyAction.Symbols -> {
                page = Page.SYMBOLS1
                updateLayout()
                feedback()
            }
            KeyAction.SymbolsMore -> {
                page = Page.SYMBOLS2
                updateLayout()
                feedback()
            }
            KeyAction.Letters -> {
                page = Page.LETTERS
                updateLayout()
                updateAutoCaps()
                updateSuggestions()
                feedback()
            }
            is KeyAction.Arrow -> {
                if (translateMode) {
                    // Nuolet liikuttavat kirjoitettavan alueen kursoria:
                    // vasen/oikea grafeemin, ylös alkuun ja alas loppuun.
                    val buffer = activeTranslateBuffer()
                    when (action.keyCode) {
                        KeyEvent.KEYCODE_DPAD_LEFT -> buffer.moveLeft()
                        KeyEvent.KEYCODE_DPAD_RIGHT -> buffer.moveRight()
                        KeyEvent.KEYCODE_DPAD_UP -> buffer.moveToStart()
                        KeyEvent.KEYCODE_DPAD_DOWN -> buffer.moveToEnd()
                        else -> Unit
                    }
                    updateTranslateBar()
                } else {
                    sendMovementKey(action.keyCode)
                    // Kursorin siirto katkaisee sanaketjun.
                    learning.resetContext()
                }
                feedback()
            }
            is KeyAction.Edit -> handleEdit(action.command)
            KeyAction.None -> Unit
            is KeyAction.Text -> Unit
        }
    }

    override fun onSpaceSwipe(steps: Int) {
        if (emojiSearchActive) return
        if (translateMode) {
            // Liu'utus liikuttaa käännösnäkymän kursoria kentän sijaan.
            activeTranslateBuffer().move(steps)
            updateTranslateBar()
        } else {
            sendKeyToField(
                if (steps > 0) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT
            )
            // Kursorin siirto katkaisee sanaketjun.
            learning.resetContext()
        }
        // Jokaisesta askeleesta sama tuntuma kuin näppäimestä: kevyempi
        // CLOCK_TICK ei tuntunut puhelimissa lainkaan.
        if (vibrationEnabled) {
            keyboardView?.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        }
    }


    private fun handleShift() {
        val state = shift.tap(
            SystemClock.uptimeMillis(),
            ViewConfiguration.getDoubleTapTimeout().toLong(),
        )
        keyboardView?.shiftState = state
        feedback()
        // Tyhjän syötteen ennustukset seuraavat shiftin tilaa.
        updateSuggestions()
    }

    private fun handleEnter() {
        val ic = currentInputConnection ?: return
        val action = field.enterAction
        when {
            action != null -> {
                ic.performEditorAction(action)
                cursor.onUnknownEdit()
            }
            // Tyypitön kenttä (esim. pääte) ei välttämättä ymmärrä tekstinä
            // tulevaa rivinvaihtoa.
            field.rawKeys -> sendKeyToField(KeyEvent.KEYCODE_ENTER)
            else -> {
                commitToField(ic, "\n")
                // Rivinvaihdon jälkeen edeltävällä tekstillä ei ole väliä.
                updateAutoCaps("\n")
            }
        }
        feedback(AudioManager.FX_KEYPRESS_RETURN)
    }

    /**
     * Iso alkukirjain päätellään kursorin edeltävästä tekstistä itse, ei
     * kentän getCursorCapsMode-vastauksesta: moni sovellus ei toteuta sitä
     * tai vastaa vanhentuneesta tekstistä, ja vastaus voi jäädä kokonaan
     * tulematta sovelluksen ollessa kiireinen. [predicted] on kursorin
     * edeltävä teksti oman muokkauksen jälkeen: ennen muokkausta luettu
     * teksti, johon muokkaus on sovellettu täsmälleen kerran (kentältä
     * luettu teksti voi jo sisältää sen, ja kahdesti sovellettuna
     * lyhennesääntö pettäisi). Sen kanssa vuoro vaihtuu heti näkyvästi
     * kuten Gboardissa eikä odota sovelluksen valintapäivitystä, joka jää
     * joissain sovelluksissa kokonaan tulematta. Ilman ennustetta teksti
     * luetaan kentältä.
     */
    private fun updateAutoCaps(predicted: CharSequence? = null) {
        if (page != Page.LETTERS || emojiSearchActive) return
        val caps = if (translateMode) {
            val buffer = activeTranslateBuffer()
            AutoCaps.wanted(buffer.text.substring(0, buffer.cursor), AutoCaps.SENTENCES)
        } else {
            val info = currentInputEditorInfo ?: return
            if (!AutoCaps.requested(info.inputType)) {
                false
            } else {
                val ic = currentInputConnection ?: return
                val before = predicted
                    ?: readBefore(ic, MAX_WORD_LOOKBACK)
                    ?: return
                AutoCaps.wanted(before, info.inputType)
            }
        }
        if (shift.applyAutoCaps(caps)) keyboardView?.shiftState = shift.state
    }

    override fun onUpdateSelection(
        oldSelStart: Int,
        oldSelEnd: Int,
        newSelStart: Int,
        newSelEnd: Int,
        candidatesStart: Int,
        candidatesEnd: Int,
    ) {
        super.onUpdateSelection(
            oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd
        )
        selectionUpdates++
        val update = cursor.classify(oldSelStart, oldSelEnd, newSelStart, newSelEnd)
        val external = when (update) {
            CursorExpectation.Update.OWN -> false
            CursorExpectation.Update.EXTERNAL -> true
            CursorExpectation.Update.UNKNOWN ->
                SystemClock.uptimeMillis() - lastEditTime > EXTERNAL_SELECTION_MS
        }
        // Maalaus ja vieras kursorin siirto (kentän napautus) katkaisevat
        // sanaketjun kuten nuolinäppäimetkin.
        if (newSelStart != newSelEnd || external) {
            shift.onTextChanged()
            learning.resetContext()
            // Korjauksen peruutus ja undo tunnistavat kohteensa pelkästä
            // kursoria edeltävästä tekstistä. Kursorihypyn jälkeen sama
            // teksti voi olla muuallakin, jolloin askelpalautin muuttaisi
            // väärää esiintymää — sidonta katkeaa kun kursori siirtyy.
            pendingRevert = null
            pendingPeriodRevert = false
            textUndo.clear()
        }
        // Hitaassa kentässä oman muokkauksen tarkistus odottaa kirjoitustaukoa.
        if (external || !slowConnection.isSlow(SystemClock.uptimeMillis())) updateAutoCaps()
        if (autoSpaceState > 0) autoSpaceState--
        updateSuggestions()
    }

    /**
     * Kirjaa oman tekstimuokkauksen: heti perään tulevat valintamuutokset
     * ovat omia eivätkä katkaise sanaketjua. Ei-muokkaavat näppäimet
     * (shift, työkalurivi) eivät kutsu tätä.
     */
    private fun markOwnEdit() {
        lastEditTime = SystemClock.uptimeMillis()
        shift.onTextChanged()
    }

    private fun feedback(soundEffect: Int = AudioManager.FX_KEYPRESS_STANDARD) {
        if (vibrationEnabled) {
            keyboardView?.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        }
        if (soundEnabled) {
            getSystemService(AudioManager::class.java)?.playSoundEffect(soundEffect, -1f)
        }
    }

    private companion object {
        const val MAX_WORD_LOOKBACK = 48
        const val WORD_BACKSPACE_LOOKBACK = 48
        const val COMMON_WORD_POOL = 24
        const val BIAS_WORD_MAX = 200
        const val PREF_TRANSLATE_SOURCE = "kaannos_lahde"
        const val PREF_TRANSLATE_TARGET = "kaannos_kohde"
        const val PREF_CLAUDE_MODEL = "claude_malli"
        const val PREF_OPENAI_MODEL = "openai_malli"
        const val PREF_AI_SERVICE = "ai_palvelu"
        const val PREF_NUMBER_ROW = "numerorivi"
        const val PREF_DICTATION_SILENCE = "sanelu_hiljaisuus"
        const val PREF_TRANSLATE_MEMORY = "kaannos_muisti"

        // Ilmoituskuplan näkyvyysajat kuten Toastin lyhyt ja pitkä.
        const val MESSAGE_SHORT_MS = 2000L
        const val MESSAGE_LONG_MS = 3500L

        /** Kielen päättely vaatii vähintään tämän verran tekstiä. */
        private const val AUTODETECT_MIN_CHARS = 6

        /** Oletuskynnystä (0,5) tiukempi raja lähikielten sekaannuksia vastaan. */
        private const val AUTODETECT_CONFIDENCE = 0.7f
        const val PREF_DICTATION_ENGINE = "sanelu_moottori"
        const val PREF_DOUBLE_SPACE = "kaksoisvali_piste"
        const val PREF_EMOJI_RECENTS = "emoji_viimeisimmat"
        const val PREF_EMOJI_VARIANTS = "emoji_ihonvarit"
        const val EMOJI_ASSET = "emoji.tsv"
        const val EMOJI_ASSET_EN = "emoji-en.tsv"
        val PRIVATE_AUDIO_OUTPUTS = setOf(
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
            AudioDeviceInfo.TYPE_USB_HEADSET,
        )

        const val DOUBLE_SPACE_PERIOD_MS = 1100L
        const val LIVE_TRANSLATE_DELAY_MS = 300L
        const val EXTERNAL_SELECTION_MS = 1000L
        const val CURSOR_CHECK_LOOKBACK = 1024
        const val BACKSPACE_SWIPE_LOOKBACK = 1024
        const val SLOW_REFRESH_MS = 150L
        const val WORD_DELETE_CHECK_MS = 200L
        const val MAX_IMAGE_CLIP_BYTES = 10L * 1024 * 1024
        const val FLUSH_THRESHOLD = 50
        const val SHOWN_TOP_COUNT = 3
        const val FILE_AUTHORITY = "org.jarsi.ark.nappaimisto.tiedostot"
    }
}
