package org.jarsi.ark.settings.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.jarsi.ark.R
import org.jarsi.ark.settings.ImeStatus

/**
 * Tilakortti sivun ylälaidassa: kertoo, onko näppäimistö otettu käyttöön
 * ja valittu, ja tarjoaa seuraavan askeleen yhdellä napilla. Valmis tila
 * on korostusvärinen, keskeneräinen tertiäärivärinen.
 */
@Composable
internal fun ImeStatusCard(
    status: ImeStatus,
    onEnable: () -> Unit,
    onSelect: () -> Unit,
    onIntro: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val colors = LocalSettingsColors.current
    val ready = status == ImeStatus.READY
    val container = if (ready) scheme.primaryContainer else scheme.tertiaryContainer
    val onContainer = if (ready) colors.onHeroPrimary else colors.onHeroTertiary
    val title = stringResource(
        when (status) {
            ImeStatus.NOT_ENABLED -> R.string.ota_kayttoon
            ImeStatus.NOT_SELECTED -> R.string.vaihda_nappaimistoksi
            ImeStatus.READY -> R.string.tila_valmis
        }
    )
    val body = stringResource(
        when (status) {
            ImeStatus.NOT_ENABLED -> R.string.ota_kayttoon_kuvaus
            ImeStatus.NOT_SELECTED -> R.string.vaihda_nappaimistoksi_kuvaus
            ImeStatus.READY -> R.string.tila_valmis_kuvaus
        }
    )
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = container,
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.semantics(mergeDescendants = true) {},
            ) {
                if (ready) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .background(scheme.primary, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_check),
                            contentDescription = null,
                            tint = scheme.onPrimary,
                            modifier = Modifier.size(30.dp),
                        )
                    }
                } else {
                    Icon(
                        painter = painterResource(
                            if (status == ImeStatus.NOT_ENABLED) R.drawable.ic_keyboard else R.drawable.ic_swap
                        ),
                        contentDescription = null,
                        tint = onContainer,
                        modifier = Modifier.size(40.dp),
                    )
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        color = onContainer,
                    )
                    Text(
                        text = body,
                        style = MaterialTheme.typography.bodyMedium,
                        color = onContainer,
                    )
                }
            }
            FlowRow(
                modifier = Modifier.padding(top = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                val primaryColors = ButtonDefaults.buttonColors(
                    containerColor = scheme.surface,
                    contentColor = onContainer,
                )
                when (status) {
                    ImeStatus.NOT_ENABLED -> Button(onClick = onEnable, colors = primaryColors) {
                        Text(stringResource(R.string.tila_ota_kayttoon_nappi))
                    }
                    ImeStatus.NOT_SELECTED -> Button(onClick = onSelect, colors = primaryColors) {
                        Text(stringResource(R.string.vaihda_nappaimistoksi))
                    }
                    ImeStatus.READY -> Button(onClick = onIntro, colors = primaryColors) {
                        Text(stringResource(R.string.esittely))
                    }
                }
                if (!ready) {
                    TextButton(
                        onClick = onIntro,
                        colors = ButtonDefaults.textButtonColors(contentColor = onContainer),
                    ) {
                        Text(stringResource(R.string.esittely))
                    }
                }
            }
        }
    }
}
