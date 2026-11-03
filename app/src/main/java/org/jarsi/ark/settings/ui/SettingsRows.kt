package org.jarsi.ark.settings.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jarsi.ark.R
import kotlin.math.roundToInt

private val CardShape = RoundedCornerShape(28.dp)
private val RowShape = RoundedCornerShape(20.dp)

/**
 * Osiokortti: otsikkorivi taittaa sisällön piiloon, kun [onToggle] on
 * annettu. Taitto animoituu, ellei käyttäjä ole poistanut animaatioita.
 */
@Composable
internal fun SettingsCard(
    title: String,
    collapsed: Boolean = false,
    onToggle: ((Boolean) -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LocalSettingsColors.current
    Surface(
        shape = CardShape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
    ) {
        Column(Modifier.padding(6.dp)) {
            val stateText = stringResource(
                if (collapsed) R.string.osio_taitettu else R.string.osio_avattu
            )
            val headerModifier = if (onToggle != null) {
                Modifier
                    .clip(RowShape)
                    .clickable(role = Role.Button) { onToggle(!collapsed) }
                    .semantics { heading(); stateDescription = stateText }
            } else {
                Modifier.semantics { heading() }
            }
            Row(
                modifier = headerModifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.onCard,
                    modifier = Modifier.weight(1f),
                )
                if (onToggle != null) {
                    val reduced = LocalReducedMotion.current
                    val target = if (collapsed) 0f else 180f
                    val rotation by animateFloatAsState(
                        targetValue = target,
                        animationSpec = if (reduced) androidx.compose.animation.core.snap() else MaterialTheme.motionScheme.fastSpatialSpec(),
                        label = "taitto",
                    )
                    Icon(
                        painter = painterResource(R.drawable.ic_chevron_down),
                        contentDescription = null,
                        tint = colors.onCardVariant,
                        modifier = Modifier.size(24.dp).rotate(rotation),
                    )
                }
            }
            val reduced = LocalReducedMotion.current
            AnimatedVisibility(
                visible = !collapsed,
                enter = if (reduced) EnterTransition.None else expandVertically() + fadeIn(),
                exit = if (reduced) ExitTransition.None else shrinkVertically() + fadeOut(),
            ) {
                Column(content = content)
            }
        }
    }
}

/** Otsikko ja mahdollinen selite; koko rivi vaihtaa kytkimen tilaa. */
@Composable
internal fun SwitchRow(
    title: String,
    description: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val colors = LocalSettingsColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RowShape)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .heightIn(min = 52.dp)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowTexts(title, description, Modifier.weight(1f))
        Spacer(Modifier.width(14.dp))
        Switch(
            checked = checked,
            onCheckedChange = null,
            thumbContent = if (checked) {
                {
                    Icon(
                        painter = painterResource(R.drawable.ic_check),
                        contentDescription = null,
                        modifier = Modifier.size(SwitchDefaults.IconSize),
                    )
                }
            } else {
                null
            },
            colors = SwitchDefaults.colors(
                checkedTrackColor = colors.control,
                uncheckedBorderColor = colors.controlOff,
                uncheckedThumbColor = colors.controlOff,
            ),
        )
    }
}

/** Rivi, joka avaa alisivun tai dialogin; nuoli kertoo siirtymästä. */
@Composable
internal fun LinkRow(
    title: String,
    description: String? = null,
    onClick: () -> Unit,
) {
    val colors = LocalSettingsColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RowShape)
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 52.dp)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowTexts(title, description, Modifier.weight(1f))
        Spacer(Modifier.width(14.dp))
        Icon(
            painter = painterResource(R.drawable.ic_chevron_right),
            contentDescription = null,
            tint = colors.onCardVariant,
            modifier = Modifier.size(24.dp),
        )
    }
}

/**
 * Kokonaislukuasetus liukusäätimellä. Arvo näkyy pillerissä liikkeen
 * aikana ja tallentuu, kun kahva irrotetaan.
 */
@Composable
internal fun SliderRow(
    title: String,
    description: String? = null,
    value: Int,
    range: IntRange,
    unit: String,
    onValueChangeFinished: (Int) -> Unit,
) {
    val colors = LocalSettingsColors.current
    var current by remember(value) { mutableIntStateOf(value) }
    val stateText = "$current $unit"
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .semantics(mergeDescendants = true) { stateDescription = stateText },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RowTexts(title, description, Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = colors.heroReady.background,
            ) {
                Text(
                    text = stateText,
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.heroReady.text,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                )
            }
        }
        Slider(
            value = current.toFloat(),
            onValueChange = { current = it.roundToInt().coerceIn(range) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = (range.last - range.first - 1).coerceAtLeast(0),
            onValueChangeFinished = { onValueChangeFinished(current) },
            colors = SliderDefaults.colors(
                thumbColor = colors.control,
                activeTrackColor = colors.control,
                inactiveTrackColor = MaterialTheme.colorScheme.secondaryContainer,
                activeTickColor = colors.control,
                inactiveTickColor = MaterialTheme.colorScheme.secondaryContainer,
            ),
        )
    }
}

/** Pitkä tiedote: otsikko ja kaksi riviä, napautus näyttää kaiken. */
@Composable
internal fun NoteRow(title: String, text: String) {
    val colors = LocalSettingsColors.current
    var expanded by remember { mutableStateOf(false) }
    val stateText = stringResource(
        if (expanded) R.string.osio_avattu else R.string.osio_taitettu
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RowShape)
            .clickable(role = Role.Button) { expanded = !expanded }
            .semantics(mergeDescendants = true) { stateDescription = stateText }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = colors.onCard,
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onCardVariant,
            maxLines = if (expanded) Int.MAX_VALUE else 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun RowTexts(title: String, description: String?, modifier: Modifier) {
    val colors = LocalSettingsColors.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = colors.onCard,
        )
        if (description != null) {
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onCardVariant,
            )
        }
    }
}
