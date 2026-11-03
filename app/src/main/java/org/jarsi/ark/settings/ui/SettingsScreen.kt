package org.jarsi.ark.settings.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.jarsi.ark.R
import org.jarsi.ark.dictation.OpenAiDictation
import org.jarsi.ark.engine.AiRequests
import org.jarsi.ark.settings.ImeStatus
import org.jarsi.ark.settings.SettingsState
import org.jarsi.ark.settings.SupportLinks
import org.jarsi.ark.theme.KeyboardTheme
import org.jarsi.ark.theme.ThemeChoice

/** Aktiviteetin tarjoamat toimet; näkymä ei tunne Androidin rajapintoja. */
data class SettingsActions(
    val enableIme: () -> Unit,
    val selectIme: () -> Unit,
    val openIntro: () -> Unit,
    val openSymbols: () -> Unit,
    val openToolbar: () -> Unit,
    val openLearnedWords: () -> Unit,
    val openLanguages: () -> Unit,
    val openChatGptLogin: () -> Unit,
    val signOutChatGpt: () -> Unit,
    val exportBackup: () -> Unit,
    val importBackup: () -> Unit,
    val pickClaudeModel: () -> Unit,
    val pickOpenAiModel: () -> Unit,
    val pickDictationModel: () -> Unit,
    val saveClaudeKey: (String) -> Unit,
    val saveOpenAiKey: (String) -> Unit,
    val openUrl: (String) -> Unit,
    val showToast: (Int) -> Unit,
)

/** Haettu mallilista valintadialogia varten. */
data class ModelPicker(
    val title: String,
    val options: List<Pair<String, String>>,
    val selected: String?,
    val onSelect: (String) -> Unit,
)

private enum class Dialog { THEME, AI_SERVICE, DICTATION_ENGINE, CLAUDE_KEY, OPENAI_KEY, SIGN_OUT }

@Composable
fun SettingsScreen(
    state: SettingsState,
    imeStatus: ImeStatus,
    versionLabel: String,
    modelPicker: ModelPicker?,
    onDismissModelPicker: () -> Unit,
    actions: SettingsActions,
) {
    val colors = LocalSettingsColors.current
    // Pyöritys ei sulje avointa dialogia, kuten ei ennenkään.
    var dialog by rememberSaveable { mutableStateOf<Dialog?>(null) }
    val themeNames = stringArrayResource(R.array.teema_nimet)
    val themeValues = stringArrayResource(R.array.teema_arvot)
    val engineNames = stringArrayResource(R.array.sanelu_moottori_nimet)
    val engineValues = stringArrayResource(R.array.sanelu_moottori_arvot)
    val serviceNames = stringArrayResource(R.array.ai_palvelu_nimet)
    val serviceValues = stringArrayResource(R.array.ai_palvelu_arvot)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp),
    ) {
        Text(
            text = stringResource(R.string.asetukset),
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 12.dp),
        )
        ImeStatusCard(
            status = imeStatus,
            onEnable = actions.enableIme,
            onSelect = actions.selectIme,
            onIntro = actions.openIntro,
        )

        Section(state, "ulkoasu", stringResource(R.string.asetukset_ulkoasu)) {
            LinkRow(
                title = stringResource(R.string.asetus_teema),
                description = themeNames.getOrNull(themeValues.indexOf(state.theme)) ?: themeNames[0],
            ) { dialog = Dialog.THEME }
            SliderRow(
                title = stringResource(R.string.asetus_korkeus),
                description = stringResource(R.string.asetus_korkeus_kuvaus),
                value = state.height,
                range = 80..130,
                unit = "%",
            ) { state.setInt("korkeus", it) }
        }

        Section(state, "kirjoittaminen", stringResource(R.string.asetukset_kirjoittaminen)) {
            SwitchRow(stringResource(R.string.asetus_aanet), checked = state.sounds) { state.setBoolean("aanet", it) }
            SwitchRow(stringResource(R.string.asetus_varina), checked = state.vibrate) { state.setBoolean("varina", it) }
            SwitchRow(stringResource(R.string.asetus_esikatselu), checked = state.preview) { state.setBoolean("esikatselu", it) }
            SwitchRow(
                stringResource(R.string.asetus_numerorivi),
                stringResource(R.string.asetus_numerorivi_kuvaus),
                checked = state.numberRow,
            ) { state.setBoolean("numerorivi", it) }
            SwitchRow(
                stringResource(R.string.asetus_kaksoisvali),
                stringResource(R.string.asetus_kaksoisvali_kuvaus),
                checked = state.doubleSpace,
            ) { state.setBoolean("kaksoisvali_piste", it) }
            LinkRow(stringResource(R.string.erikoismerkit), stringResource(R.string.erikoismerkit_kuvaus), actions.openSymbols)
            LinkRow(stringResource(R.string.tyokalurivi), stringResource(R.string.tyokalurivi_kuvaus), actions.openToolbar)
        }

        Section(state, "ehdotukset", stringResource(R.string.asetukset_ehdotukset)) {
            SwitchRow(stringResource(R.string.asetus_ehdotukset), checked = state.suggestions) { state.setBoolean("ehdotukset", it) }
            SwitchRow(stringResource(R.string.asetus_ehdotus_valilyonti), checked = state.suggestionSpace) { state.setBoolean("ehdotus_valilyonti", it) }
            SwitchRow(stringResource(R.string.asetus_ehdotus_yleiset), checked = state.commonWords) { state.setBoolean("ehdotus_yleiset", it) }
            SwitchRow(
                stringResource(R.string.asetus_automaattikorjaus),
                stringResource(R.string.asetus_automaattikorjaus_kuvaus),
                checked = state.autoCorrect,
            ) { state.setBoolean("automaattikorjaus", it) }
            LinkRow(stringResource(R.string.opitut_sanat), stringResource(R.string.opitut_sanat_kuvaus), actions.openLearnedWords)
        }

        Section(state, "kaannos", stringResource(R.string.asetukset_kaannos)) {
            LinkRow(stringResource(R.string.kaannoskielet), stringResource(R.string.kaannoskielet_kuvaus), actions.openLanguages)
            SliderRow(
                title = stringResource(R.string.asetus_kaannos_muisti),
                description = stringResource(R.string.asetus_kaannos_muisti_kuvaus),
                value = state.translationMemory,
                range = 5..120,
                unit = "min",
            ) { state.setInt("kaannos_muisti", it) }
        }

        Section(state, "sanelu", stringResource(R.string.asetukset_sanelu)) {
            SliderRow(
                title = stringResource(R.string.asetus_sanelu_hiljaisuus),
                description = stringResource(R.string.asetus_sanelu_hiljaisuus_kuvaus),
                value = state.dictationSilence,
                range = 2..10,
                unit = "s",
            ) { state.setInt("sanelu_hiljaisuus", it) }
            val engineName = engineNames.getOrNull(engineValues.indexOf(state.dictationEngine)) ?: engineNames[0]
            LinkRow(
                title = stringResource(R.string.asetus_sanelu_moottori),
                description = stringResource(R.string.asetus_sanelu_moottori_nykyinen, engineName),
            ) { dialog = Dialog.DICTATION_ENGINE }
            if (state.dictationEngine == "openai") {
                LinkRow(
                    title = stringResource(R.string.asetus_sanelu_malli),
                    description = stringResource(
                        R.string.asetus_malli_nykyinen_openai,
                        state.dictationModel ?: OpenAiDictation.DEFAULT_MODEL,
                    ),
                    onClick = actions.pickDictationModel,
                )
            }
        }

        Section(state, "tekoaly", stringResource(R.string.asetukset_tekoaly)) {
            NoteRow(stringResource(R.string.asetus_ai_avain_ohje), stringResource(R.string.asetus_ai_avain_ohje_kuvaus))
            val chatgpt = state.aiService == "chatgpt"
            val serviceName = if (chatgpt && state.planSignedIn) {
                stringResource(R.string.ai_palvelu_chatgpt_tilaus)
            } else {
                serviceNames.getOrNull(serviceValues.indexOf(state.aiService)) ?: serviceNames[0]
            }
            LinkRow(
                title = stringResource(R.string.asetus_ai_palvelu),
                description = stringResource(
                    if (state.aiServiceHasKey()) R.string.asetus_ai_palvelu_nykyinen else R.string.asetus_ai_palvelu_ei_avainta,
                    serviceName,
                ),
            ) { dialog = Dialog.AI_SERVICE }
            LinkRow(
                title = stringResource(R.string.asetus_api_avain),
                description = keyDescription(state.claudeKeySaved, stringResource(R.string.asetus_api_avain_kuvaus)),
            ) { dialog = Dialog.CLAUDE_KEY }
            if (!chatgpt) {
                LinkRow(
                    title = stringResource(R.string.asetus_malli),
                    description = stringResource(R.string.asetus_malli_nykyinen, state.claudeModel ?: AiRequests.MODEL),
                    onClick = actions.pickClaudeModel,
                )
            }
            LinkRow(
                title = stringResource(R.string.asetus_chatgpt_kirjautuminen),
                description = if (state.planSignedIn) {
                    stringResource(R.string.asetus_chatgpt_kirjautunut, state.planEmail.orEmpty())
                } else {
                    stringResource(R.string.asetus_chatgpt_kirjautuminen_kuvaus)
                },
            ) { if (state.planSignedIn) dialog = Dialog.SIGN_OUT else actions.openChatGptLogin() }
            LinkRow(
                title = stringResource(R.string.asetus_openai_avain),
                description = keyDescription(state.openAiKeySaved, stringResource(R.string.asetus_openai_avain_kuvaus)),
            ) { dialog = Dialog.OPENAI_KEY }
            if (chatgpt) {
                LinkRow(
                    title = stringResource(R.string.asetus_openai_malli),
                    description = if (state.planSignedIn) {
                        stringResource(
                            R.string.asetus_malli_nykyinen_chatgpt_tilaus,
                            state.planModel ?: stringResource(R.string.asetus_malli_tilaus_oletus),
                        )
                    } else {
                        stringResource(R.string.asetus_malli_nykyinen_openai, state.openAiModel ?: AiRequests.OPENAI_MODEL)
                    },
                    onClick = actions.pickOpenAiModel,
                )
            }
        }

        Section(state, "varmuuskopio", stringResource(R.string.asetukset_varmuuskopio)) {
            LinkRow(stringResource(R.string.varmuuskopio_vie), stringResource(R.string.varmuuskopio_vie_kuvaus), actions.exportBackup)
            LinkRow(stringResource(R.string.varmuuskopio_tuo), stringResource(R.string.varmuuskopio_tuo_kuvaus), actions.importBackup)
        }

        SupportSection(onOpen = actions.openUrl)

        Text(
            text = versionLabel,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
        Text(
            text = stringResource(R.string.versio_avoin),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .clip(RoundedCornerShape(12.dp))
                .clickable(role = Role.Button) { actions.openUrl(SupportLinks.REPO_URL) }
                .padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }

    when (dialog) {
        Dialog.THEME -> {
            val options = themeValues.indices
                .filter { state.dynamicAvailable || themeValues[it] != ThemeChoice.DYNAMIC.id }
                .map { themeValues[it] to themeNames[it] }
            ChoiceDialog(
                title = stringResource(R.string.asetus_teema),
                options = options,
                selected = state.theme,
                onSelect = { state.setString(KeyboardTheme.PREF_KEY, it); dialog = null },
                onDismiss = { dialog = null },
            )
        }
        Dialog.AI_SERVICE -> ChoiceDialog(
            title = stringResource(R.string.asetus_ai_palvelu),
            options = serviceValues.indices.map { serviceValues[it] to serviceNames[it] },
            selected = state.aiService,
            onSelect = { state.setString("ai_palvelu", it); dialog = null },
            onDismiss = { dialog = null },
        )
        Dialog.DICTATION_ENGINE -> ChoiceDialog(
            title = stringResource(R.string.asetus_sanelu_moottori),
            options = engineValues.indices.map { engineValues[it] to engineNames[it] },
            selected = state.dictationEngine,
            onSelect = { value ->
                state.setString("sanelu_moottori", value)
                // OpenAI-tunnistus vaatii avaimen; muuten sanelu putoaa laitteen tunnistukseen.
                if (value == "openai" && !state.openAiKeySaved) actions.showToast(R.string.sanelu_openai_ei_avainta)
                dialog = null
            },
            onDismiss = { dialog = null },
        )
        Dialog.CLAUDE_KEY -> SecretDialog(
            title = stringResource(R.string.asetus_api_avain),
            onSave = { actions.saveClaudeKey(it); dialog = null },
            onDismiss = { dialog = null },
        )
        Dialog.OPENAI_KEY -> SecretDialog(
            title = stringResource(R.string.asetus_openai_avain),
            onSave = { actions.saveOpenAiKey(it); dialog = null },
            onDismiss = { dialog = null },
        )
        Dialog.SIGN_OUT -> ConfirmDialog(
            message = stringResource(R.string.chatgpt_uloskirjautuminen_varmistus),
            confirmLabel = stringResource(R.string.chatgpt_kirjaudu_ulos),
            onConfirm = { actions.signOutChatGpt(); dialog = null },
            onDismiss = { dialog = null },
        )
        null -> Unit
    }
    if (modelPicker != null) {
        ChoiceDialog(
            title = modelPicker.title,
            options = modelPicker.options,
            selected = modelPicker.selected,
            onSelect = modelPicker.onSelect,
            onDismiss = onDismissModelPicker,
        )
    }
}

/** Osiokortti, jonka taitettu tila luetaan ja tallennetaan asetuksiin. */
@Composable
private fun Section(
    state: SettingsState,
    id: String,
    title: String,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    SettingsCard(
        title = title,
        collapsed = id in state.collapsed,
        onToggle = { state.setCollapsed(id, it) },
        content = content,
    )
}

/** Avainrivin selite: tila ensin, sitten mihin avainta käytetään. Avainta itseään ei näytetä. */
@Composable
private fun keyDescription(saved: Boolean, purpose: String): String {
    val status = stringResource(if (saved) R.string.avain_tallennettu else R.string.avain_ei_asetettu)
    return "$status – $purpose"
}
