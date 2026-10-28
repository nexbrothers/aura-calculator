package app.aurio.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.aurio.data.NotCountedReason
import app.aurio.domain.AppUsage
import app.aurio.domain.AuraScoring
import app.aurio.domain.LimitStatus
import app.aurio.domain.LineKind
import app.aurio.domain.ScoreLine
import app.aurio.ui.theme.AurioColors
import app.aurio.ui.theme.AurioRadius
import app.aurio.ui.theme.AurioSpacing
import app.aurio.ui.theme.AurioType

// ---------- Buttons ----------

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    container: Color = AurioColors.Ink,
    contentColor: Color = Color.White,
    leading: (@Composable RowScope.() -> Unit)? = null,
) {
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().heightIn(min = AurioSpacing.buttonHeight),
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(containerColor = container, contentColor = contentColor),
    ) {
        if (leading != null) { leading(); Spacer(Modifier.width(8.dp)) }
        Text(text, style = AurioType.button)
    }
}

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, container: Color = AurioColors.Surface) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().heightIn(min = AurioSpacing.buttonHeight),
        shape = CircleShape,
        colors = ButtonDefaults.outlinedButtonColors(containerColor = container, contentColor = AurioColors.Ink),
    ) { Text(text, style = AurioType.button) }
}

// ---------- Pills ----------

enum class PillTone { GAIN, LOSS, UNKNOWN, NEUTRAL, PROJECTED, ON_AURA }

@Composable
fun Pill(text: String, tone: PillTone, modifier: Modifier = Modifier, minWidth: Dp = 0.dp) {
    val (fg, bg) = when (tone) {
        PillTone.GAIN -> AurioColors.GainText to AurioColors.GainBg
        PillTone.LOSS -> AurioColors.LossText to AurioColors.LossBg
        PillTone.UNKNOWN -> AurioColors.Unknown to AurioColors.UnknownBg
        PillTone.NEUTRAL -> AurioColors.Neutral to AurioColors.NeutralBg
        PillTone.PROJECTED -> AurioColors.GainText to AurioColors.GainBg
        PillTone.ON_AURA -> AurioColors.Ink to Color.White.copy(alpha = 0.75f)
    }
    var m = modifier.widthIn(min = minWidth).height(26.dp).clip(CircleShape).background(bg)
    // Projected values get a dashed-feeling outline so state is never shown by colour alone.
    if (tone == PillTone.PROJECTED) m = m.border(1.dp, AurioColors.Gain.copy(alpha = 0.45f), CircleShape)
    Box(m.padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
        Text(text, style = AurioType.label.copy(fontSize = AurioType.caption.fontSize), color = fg, textAlign = TextAlign.Center)
    }
}

/** Formats a signed aura amount with a real minus sign. */
fun signed(n: Int): String = when {
    n > 0 -> "+$n"
    n < 0 -> "−${-n}"
    else -> "0"
}

// ---------- Progress ----------

@Composable
fun ProgressBar(fraction: Float, modifier: Modifier = Modifier, color: Color = AurioColors.Ink, track: Color = AurioColors.Track, height: Dp = 6.dp) {
    Box(modifier.fillMaxWidth().height(height).clip(CircleShape).background(track)) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(height).clip(CircleShape).background(color))
    }
}

// ---------- App tile ----------

private val tilePastels = listOf(
    Color(0xFFFDE3EE), Color(0xFFFFE1DC), Color(0xFFFFF6C9), Color(0xFFE4EEFB),
    Color(0xFFE2F6EC), Color(0xFFECE8F5), Color(0xFFFFE7D6),
)

/**
 * An app as the user knows it: its own icon from this phone when [packageName] is given and
 * installed, otherwise a pastel rounded square with its initial.
 */
@Composable
fun AppTile(label: String, modifier: Modifier = Modifier, size: Dp = 40.dp, packageName: String? = null) {
    val icon = rememberAppIcon(packageName, size)
    if (icon != null) {
        Image(icon, contentDescription = label, modifier = modifier.size(size))
        return
    }
    val bg = tilePastels[(label.hashCode() and 0x7fffffff) % tilePastels.size]
    Box(
        modifier.size(size).clip(RoundedCornerShape(AurioRadius.tile)).background(bg).semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { Text(label.take(1), style = AurioType.bodyStrong.copy(fontWeight = FontWeight.SemiBold)) }
}

// ---------- Rows ----------

@Composable
fun LimitRow(app: AppUsage, modifier: Modifier = Modifier, notCounted: NotCountedReason? = null, measured: Int? = null) {
    val status = AuraScoring.limitStatus(app)
    val used = app.usedMinutes
    val (pillText, tone) = when (status) {
        is LimitStatus.Under -> "${status.minutesLeft} min left" to PillTone.NEUTRAL
        is LimitStatus.Over -> "${status.minutesOver} min over" to PillTone.LOSS
        LimitStatus.Unknown -> NotCounted.pill(notCounted) to PillTone.UNKNOWN
        LimitStatus.NotScored -> "Never changes aura" to PillTone.NEUTRAL
    }
    val over = status is LimitStatus.Over
    Row(modifier.fillMaxWidth().padding(vertical = AurioSpacing.md), verticalAlignment = Alignment.CenterVertically) {
        AppTile(app.label, packageName = app.packageName)
        Spacer(Modifier.width(AurioSpacing.md))
        Column(Modifier.weight(1f)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(app.label, style = AurioType.bodyStrong)
                Pill(pillText, tone)
            }
            Spacer(Modifier.height(7.dp))
            // On a day that is not counted, time measured since access came back is still shown,
            // in grey, so the row never looks broken; it earns no bonus and costs nothing.
            val shownMinutes = used ?: measured
            ProgressBar(
                fraction = if (shownMinutes == null) 0f else shownMinutes.toFloat() / app.limitMinutes,
                color = when {
                    over -> AurioColors.Loss
                    used == null -> AurioColors.InkSubtle
                    else -> AurioColors.Ink
                },
                track = if (over) AurioColors.LossBg else AurioColors.Track,
            )
            Spacer(Modifier.height(5.dp))
            Text(
                limitCaption(used, measured, app.limitMinutes),
                style = AurioType.caption, color = AurioColors.InkSubtle,
            )
        }
    }
}

/** The line under a limit's bar. The pill and Home's notice say why a day is not counted. */
internal fun limitCaption(used: Int?, measured: Int?, limit: Int): String = when {
    used != null -> "$used of $limit min used"
    measured != null -> "$measured of $limit min used, not counted today"
    else -> "$limit min limit"
}

@Composable
fun ScoreChangeRow(line: ScoreLine, modifier: Modifier = Modifier) {
    val tone = when {
        line.kind == LineKind.UNKNOWN -> PillTone.UNKNOWN
        line.projected -> PillTone.PROJECTED
        line.amount >= 0 -> PillTone.GAIN
        else -> PillTone.LOSS
    }
    Row(modifier.fillMaxWidth().padding(vertical = AurioSpacing.md), verticalAlignment = Alignment.CenterVertically) {
        Pill(signed(line.amount), tone, minWidth = 56.dp)
        Spacer(Modifier.width(AurioSpacing.md))
        Column(Modifier.weight(1f)) {
            Text(line.title, style = AurioType.bodyStrong.copy(fontSize = 14.sp))
            Text(line.detail, style = AurioType.caption, color = AurioColors.InkSubtle)
        }
    }
}

/** White grouped list with hairline dividers between rows (instead of one card per row). */
@Composable
fun GroupedList(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(AurioRadius.card)).background(AurioColors.Surface)
            .padding(horizontal = AurioSpacing.lg, vertical = AurioSpacing.xs),
    ) { content() }
}

@Composable
fun RowDivider() = HorizontalDivider(thickness = 1.dp, color = AurioColors.Divider)
