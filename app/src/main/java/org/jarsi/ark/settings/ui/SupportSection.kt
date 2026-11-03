package org.jarsi.ark.settings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.jarsi.ark.R
import org.jarsi.ark.settings.SupportLinks

/**
 * Tukikortti version yläpuolella: otsikko, yksi lause ja kolme
 * sävytettyä nappia. Tarkoituksella hiljainen: ei muistutuksia muualla,
 * ei sovelluksen sisäistä maksua, ei logoja.
 */
@Composable
internal fun SupportSection(onOpen: (String) -> Unit) {
    val colors = LocalSettingsColors.current
    SettingsCard(title = stringResource(R.string.tuki_otsikko)) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
            Text(
                text = stringResource(R.string.tuki_teksti),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onCardVariant,
            )
            val buttonColors = ButtonDefaults.filledTonalButtonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = colors.onTonalButton,
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                FilledTonalButton(onClick = { onOpen(SupportLinks.SPONSOR_URL) }, colors = buttonColors) {
                    Text(stringResource(R.string.tuki_github))
                }
                FilledTonalButton(onClick = { onOpen(SupportLinks.COFFEE_URL) }, colors = buttonColors) {
                    Text(stringResource(R.string.tuki_kahvi))
                }
                FilledTonalButton(onClick = { onOpen(SupportLinks.PAYPAL_URL) }, colors = buttonColors) {
                    Text(stringResource(R.string.tuki_paypal))
                }
            }
        }
    }
}
