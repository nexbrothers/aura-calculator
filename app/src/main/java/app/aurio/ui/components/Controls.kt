package app.aurio.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.clickable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.aurio.ui.theme.AurioColors
import app.aurio.ui.theme.AurioSpacing
import app.aurio.ui.theme.AurioType

/**
 * A soft segmented switcher: equal pill tabs on a sunken track, the chosen one lifted in white.
 * Quieter than Material's outlined segmented buttons, as the design asks.
 */
@Composable
fun PillTabs(labels: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().clip(CircleShape).background(AurioColors.SurfaceSunken).padding(AurioSpacing.xs).selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(AurioSpacing.xs),
    ) {
        labels.forEachIndexed { i, label ->
            val isSelected = i == selected
            Box(
                Modifier.weight(1f).height(40.dp)
                    .then(if (isSelected) Modifier.shadow(2.dp, CircleShape) else Modifier)
                    .clip(CircleShape)
                    .background(if (isSelected) AurioColors.Surface else AurioColors.SurfaceSunken)
                    .selectable(isSelected, role = Role.Tab) { onSelect(i) },
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = AurioType.label, color = if (isSelected) AurioColors.Ink else AurioColors.InkMuted)
            }
        }
    }
}

/** A daily-limit stepper: soft round − and + around the value, each a 48dp target. */
@Composable
fun LimitStepper(value: Int, appLabel: String, onDec: () -> Unit, onInc: () -> Unit, modifier: Modifier = Modifier) =
    ValueStepper("$value min", "$appLabel limit", onDec, onInc, modifier)

/** − value + with 48dp targets. [what] names the value for TalkBack ("Decrease quiet hours start"). */
@Composable
fun ValueStepper(text: String, what: String, onDec: () -> Unit, onInc: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        StepButton("−", "Decrease $what", onDec)
        Text(text, style = AurioType.title, textAlign = TextAlign.Center, modifier = Modifier.widthIn(min = 76.dp))
        StepButton("+", "Increase $what", onInc)
    }
}

@Composable
private fun StepButton(symbol: String, label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(AurioSpacing.touchMin).clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(36.dp).clip(CircleShape).background(AurioColors.SurfaceSunken), contentAlignment = Alignment.Center) {
            Text(symbol, style = AurioType.title)
        }
    }
}
