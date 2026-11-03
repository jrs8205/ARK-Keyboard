package org.jarsi.ark.settings.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import org.jarsi.ark.R

/** Yhden valinnan lista; valinta tallentuu heti ja sulkee dialogin. */
@Composable
internal fun ChoiceDialog(
    title: String,
    options: List<Pair<String, String>>,
    selected: String?,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalSettingsColors.current
    AlertDialog(
        onDismissRequest = onDismiss,
        titleContentColor = colors.onDialog,
        textContentColor = colors.onDialogVariant,
        title = { CappedFontScale { Text(title) } },
        text = {
            CappedFontScale {
            Column(Modifier.selectableGroup().verticalScroll(rememberScrollState())) {
                options.forEach { (value, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .selectable(
                                selected = value == selected,
                                role = Role.RadioButton,
                                onClick = { onSelect(value) },
                            )
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = value == selected, onClick = null)
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyLarge,
                            color = colors.onDialog,
                            modifier = Modifier.padding(start = 12.dp),
                        )
                    }
                }
            }
            }
        },
        confirmButton = {},
        dismissButton = { DialogButton(stringResource(android.R.string.cancel), onDismiss) },
    )
}

/** Dialogin tekstinappi vahdin värillä ja fonttikatolla. */
@Composable
private fun DialogButton(label: String, onClick: () -> Unit) {
    val colors = LocalSettingsColors.current
    CappedFontScale {
        TextButton(
            onClick = onClick,
            colors = ButtonDefaults.textButtonColors(contentColor = colors.dialogAction),
        ) { Text(label) }
    }
}

/**
 * Salainen teksti (API-avain): kirjoitetaan piilotettuna kuten salasana.
 * Tyhjä teksti poistaa tallennetun avaimen.
 */
@Composable
internal fun SecretDialog(
    title: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf("") }
    val colors = LocalSettingsColors.current
    AlertDialog(
        onDismissRequest = onDismiss,
        titleContentColor = colors.onDialog,
        textContentColor = colors.onDialogVariant,
        title = { CappedFontScale { Text(title) } },
        text = {
            CappedFontScale {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { DialogButton(stringResource(R.string.tallenna)) { onSave(text.trim()) } },
        dismissButton = { DialogButton(stringResource(android.R.string.cancel), onDismiss) },
    )
}

/** Vahvistus ennen peruuttamatonta toimea, esim. uloskirjautumista. */
@Composable
internal fun ConfirmDialog(
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalSettingsColors.current
    AlertDialog(
        onDismissRequest = onDismiss,
        textContentColor = colors.onDialog,
        text = { CappedFontScale { Text(message) } },
        confirmButton = { DialogButton(confirmLabel, onConfirm) },
        dismissButton = { DialogButton(stringResource(android.R.string.cancel), onDismiss) },
    )
}
