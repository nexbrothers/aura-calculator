package app.aurio.ui.insights

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aurio.data.AurioStore
import app.aurio.domain.SystemAurioClock
import app.aurio.ui.components.AurioCard
import app.aurio.ui.components.CardHeader
import app.aurio.ui.components.RowDivider
import app.aurio.ui.components.ScoreChangeRow
import app.aurio.ui.components.signed
import app.aurio.ui.theme.AurioColors
import app.aurio.ui.theme.AurioSpacing
import app.aurio.ui.theme.AurioTheme
import app.aurio.ui.theme.AurioType
import java.time.format.TextStyle
import java.util.Locale

/**
 * Insights, as the approved design lays it out: the last 7 days as bars with their values (today
 * striped, still projected), best day and focus time, today line by line, then a month of aura.
 */
@Composable
fun InsightsScreen() {
    val state by AurioStore.state.collectAsStateWithLifecycle()
    val focusMinutes by remember { AurioStore.focusMinutesLastWeek() }.collectAsStateWithLifecycle(0)
    val startDate by remember { AurioStore.startDate() }.collectAsStateWithLifecycle(null)

    val today = SystemAurioClock.today()
    val byDate = state.history.associate { it.date to it.delta }
    val dates = (6L downTo 0L).map { today.minusDays(it) }
    // Settled days come from history; today is live and projected; days without a score are null.
    val values = dates.map { d -> if (d == today) state.daily.total else byDate[d.toString()] }
    val labels = dates.map { d -> if (d == today) "Today" else d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()) }

    Column(
        Modifier.fillMaxSize().background(AurioColors.Paper).statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(start = AurioSpacing.xl, end = AurioSpacing.xl, top = AurioSpacing.md, bottom = AurioSpacing.xxl),
        verticalArrangement = Arrangement.spacedBy(AurioSpacing.lg),
    ) {
        Text("Insights", style = AurioType.display, modifier = Modifier.semantics { heading() })

        AurioCard(padding = 18.dp) {
            Text("Last 7 days", style = AurioType.label, color = AurioColors.InkSubtle)
            val total = values.sumOf { it ?: 0 }
            Text(signed(total), style = AurioType.scoreLarge, color = signColor(total))
            Spacer(Modifier.height(AurioSpacing.lg))
            WeekChart(values, labels)
            Spacer(Modifier.height(AurioSpacing.sm))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).stripes(AurioColors.Gain))
                Spacer(Modifier.width(6.dp))
                Text("Today, projected until midnight", style = AurioType.caption, color = AurioColors.InkSubtle)
            }
            Spacer(Modifier.height(AurioSpacing.md))
            RowDivider()
            Spacer(Modifier.height(AurioSpacing.md))
            Row {
                Stat("Best day", bestDay(values, labels) ?: "–", Modifier.weight(1f))
                Stat("Focus time", focusTime(focusMinutes), Modifier.weight(1f))
            }
        }

        AurioCard(padding = AurioSpacing.lg) {
            CardHeader("Today, line by line")
            if (state.daily.lines.isEmpty()) {
                Text(
                    "Nothing yet today. Finish a focus session or keep your limits to earn aura.",
                    style = AurioType.body, color = AurioColors.InkMuted, modifier = Modifier.padding(bottom = AurioSpacing.sm),
                )
                RowDivider()
            }
            state.daily.lines.forEach { ScoreChangeRow(it); RowDivider() }
            Row(Modifier.fillMaxWidth().padding(top = AurioSpacing.md), verticalAlignment = Alignment.CenterVertically) {
                Text("Total", style = AurioType.bodyStrong, modifier = Modifier.weight(1f))
                Text(signed(state.daily.total), style = AurioType.title, color = signColor(state.daily.total))
            }
        }

        Text("Your aura by day", style = AurioType.title, modifier = Modifier.semantics { heading() })
        AuraCalendar(state.history, today, startDate = startDate)
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier) {
    Column(modifier) {
        Text(label, style = AurioType.caption, color = AurioColors.InkSubtle)
        Text(value, style = AurioType.title)
    }
}

private fun signColor(n: Int): Color = when {
    n > 0 -> AurioColors.GainText
    n < 0 -> AurioColors.LossText
    else -> AurioColors.Ink
}

/** "Wed, +45": the highest day among those with a score, the latest one on a tie. */
internal fun bestDay(values: List<Int?>, labels: List<String>): String? {
    val best = values.indices.filter { values[it] != null }.maxWithOrNull(compareBy({ values[it]!! }, { it })) ?: return null
    return "${labels[best]}, ${signed(values[best]!!)}"
}

/** "25 min", "2 h", "4 h 12 min". */
internal fun focusTime(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h == 0 -> "$m min"
        m == 0 -> "$h h"
        else -> "$h h $m min"
    }
}

private val CHART_HEIGHT: Dp = 132.dp

/**
 * Seven bars around a zero line, each with its value written on it so colour is never the only
 * signal. Room below the line appears only when there is a loss to show. Days without a score
 * show "–" and no bar; today is striped because it is projected until midnight.
 */
@Composable
private fun WeekChart(values: List<Int?>, labels: List<String>) {
    val maxGain = values.maxOf { maxOf(it ?: 0, 0) }
    val maxLoss = values.maxOf { maxOf(-(it ?: 0), 0) }
    val range = (maxGain + maxLoss).coerceAtLeast(1)
    val gainArea = if (maxLoss == 0) CHART_HEIGHT else CHART_HEIGHT * (maxGain.toFloat() / range)
    val lossArea = CHART_HEIGHT - gainArea

    Row(Modifier.fillMaxWidth()) {
        values.forEachIndexed { i, v ->
            val isToday = i == values.lastIndex
            Column(
                Modifier.weight(1f).clearAndSetSemantics { contentDescription = "${labels[i]}, ${v?.let { signed(it) + " aura" } ?: "no score"}" },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Above the zero line: the value, then the bar growing up to it.
                Box(Modifier.fillMaxWidth().height(gainArea + 18.dp), contentAlignment = Alignment.BottomCenter) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (v == null || v >= 0) ValueLabel(v)
                        if (v != null && v > 0) Bar(gainArea * (v.toFloat() / maxGain.coerceAtLeast(1)), AurioColors.Gain, isToday)
                    }
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(AurioColors.Divider))
                if (lossArea > 0.dp) {
                    Box(Modifier.fillMaxWidth().height(lossArea + 18.dp), contentAlignment = Alignment.TopCenter) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            if (v != null && v < 0) {
                                Bar(lossArea * (-v.toFloat() / maxLoss.coerceAtLeast(1)), AurioColors.Loss, isToday)
                                ValueLabel(v)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(AurioSpacing.xs))
                Text(
                    labels[i], style = AurioType.caption, maxLines = 1,
                    color = if (isToday) AurioColors.Ink else AurioColors.InkSubtle,
                )
            }
        }
    }
}

@Composable
private fun ValueLabel(v: Int?) {
    Text(
        v?.let { signed(it) } ?: "–", style = AurioType.caption, textAlign = TextAlign.Center,
        color = when {
            v == null || v == 0 -> AurioColors.InkSubtle
            v > 0 -> AurioColors.GainText
            else -> AurioColors.LossText
        },
        modifier = Modifier.padding(vertical = 2.dp),
    )
}

@Composable
private fun Bar(height: Dp, color: Color, projected: Boolean) {
    val m = Modifier.padding(horizontal = 5.dp).fillMaxWidth().height(height.coerceAtLeast(4.dp)).clip(RoundedCornerShape(6.dp))
    Box(if (projected) m.stripes(color) else m.background(color))
}

/** Diagonal stripes: the design's mark for a value that is projected, not yet settled. */
private fun Modifier.stripes(color: Color): Modifier = drawBehind {
    drawRect(color.copy(alpha = 0.35f))
    val step = 6.dp.toPx()
    clipRect {
        var x = -size.height
        while (x < size.width) {
            drawLine(color, Offset(x, size.height), Offset(x + size.height, 0f), strokeWidth = 3.dp.toPx())
            x += step
        }
    }
}

@Preview(widthDp = 360, heightDp = 360, showBackground = true)
@Composable
private fun WeekChartPreview() {
    AurioTheme {
        Column(Modifier.background(AurioColors.Paper).padding(AurioSpacing.xl)) {
            AurioCard { WeekChart(listOf(null, 45, -17, 0, 72, 30, 60), listOf("Thu", "Fri", "Sat", "Sun", "Mon", "Tue", "Today")) }
        }
    }
}
