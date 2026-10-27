package app.aurio.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.aurio.data.NotCountedReason
import app.aurio.ui.theme.AurioColors
import app.aurio.ui.theme.AurioRadius
import app.aurio.ui.theme.AurioSpacing
import app.aurio.ui.theme.AurioTheme
import app.aurio.ui.theme.AurioType

/**
 * Plain words for the "Unknown" state: what it means for the user and what happens next,
 * never just "Unknown". Used by Home, Limits, app detail and the widgets so they all agree.
 */
object NotCounted {

    /** Short status for a limit pill. */
    fun pill(reason: NotCountedReason?): String = when (reason) {
        NotCountedReason.OFF_EARLIER_TODAY -> "Counts tomorrow"
        else -> "Not tracked"
    }
}

/**
 * Why today's limits are not counted, shown once above them (Home, Limits) so each row need not
 * repeat it. With access off it offers to turn it on; otherwise it says when counting resumes.
 */
@Composable
fun NotCountedNotice(reason: NotCountedReason, onTurnOn: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(AurioRadius.card)).background(AurioColors.UnknownBg)
            .padding(start = AurioSpacing.lg, end = AurioSpacing.sm, top = AurioSpacing.md, bottom = AurioSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StrokeIcon(AurioIcons.Info, contentDescription = null, tint = AurioColors.Unknown)
        Spacer(Modifier.width(AurioSpacing.md))
        Column(Modifier.weight(1f).padding(end = AurioSpacing.sm)) {
            when (reason) {
                NotCountedReason.ACCESS_OFF -> {
                    Text("Usage access is off", style = AurioType.bodyStrong)
                    Text("Your limits are not being tracked yet.", style = AurioType.caption, color = AurioColors.InkSubtle)
                }
                NotCountedReason.OFF_EARLIER_TODAY -> {
                    Text("Limits count again from tomorrow", style = AurioType.bodyStrong)
                    Text(
                        "Usage access was off earlier today, so today has no limit bonus or penalty. Focus still counts.",
                        style = AurioType.caption, color = AurioColors.InkSubtle,
                    )
                }
            }
        }
        if (reason == NotCountedReason.ACCESS_OFF) {
            Box(
                Modifier.heightIn(min = AurioSpacing.touchMin).clip(CircleShape)
                    .clickable(role = Role.Button, onClickLabel = "Turn on usage access", onClick = onTurnOn)
                    .padding(horizontal = AurioSpacing.xs),
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.clip(CircleShape).background(AurioColors.Ink).padding(horizontal = 14.dp, vertical = 8.dp)) {
                    Text("Turn on", style = AurioType.label, color = Color.White)
                }
            }
        }
    }
}

@Preview(widthDp = 360, heightDp = 260, showBackground = true)
@Composable
private fun NotCountedNoticePreview() {
    AurioTheme {
        Column(Modifier.background(AurioColors.Paper).padding(AurioSpacing.xl)) {
            NotCountedNotice(NotCountedReason.ACCESS_OFF, onTurnOn = {})
            Spacer(Modifier.width(AurioSpacing.md))
            NotCountedNotice(NotCountedReason.OFF_EARLIER_TODAY, onTurnOn = {}, Modifier.padding(top = AurioSpacing.md))
        }
    }
}
