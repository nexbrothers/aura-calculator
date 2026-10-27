package app.aurio.ui.apps

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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aurio.data.AurioStore
import app.aurio.domain.AppCategory
import app.aurio.domain.AppImpact
import app.aurio.domain.AuraScoring
import app.aurio.domain.DayBar
import app.aurio.domain.History
import app.aurio.domain.LimitStatus
import app.aurio.domain.SystemAurioClock
import app.aurio.ui.components.AppTile
import app.aurio.ui.components.LimitRow
import app.aurio.ui.components.NotCounted
import app.aurio.data.NotCountedReason
import app.aurio.ui.components.Pill
import app.aurio.ui.components.PillTone
import app.aurio.ui.components.signed
import app.aurio.ui.components.LimitStepper
import app.aurio.ui.tabs.tomorrowNote
import app.aurio.ui.theme.AurioColors
import app.aurio.ui.theme.AurioRadius
import app.aurio.ui.theme.AurioSpacing
import app.aurio.ui.theme.AurioTheme
import app.aurio.ui.theme.AurioType
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/**
 * One app: today against its limit, the last 7 days, the limit and category settings, and what
 * the app did to the aura. Works for an app removed today too (it is still scored today).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDetailScreen(packageName: String, onBack: () -> Unit) {
    val state by AurioStore.state.collectAsStateWithLifecycle()
    val records by remember(packageName) { AurioStore.appWeek(packageName) }.collectAsStateWithLifecycle(emptyList())
    val points by remember(packageName) { AurioStore.appPointsLastWeek(packageName) }.collectAsStateWithLifecycle(emptyList())

    val todayUsage = state.apps.firstOrNull { it.packageName == packageName }
        ?.let { if (state.usageAccess) it else it.copy(usedMinutes = null) }
    val saved = state.tracked[packageName]
    val label = todayUsage?.label ?: saved?.label ?: records.lastOrNull { it.terms != null }?.terms?.label ?: packageName
    val week = History.week(SystemAurioClock.today(), records, todayUsage, saved)
    val note = todayUsage?.let { tomorrowNote(it, saved) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding()
            .padding(horizontal = AurioSpacing.xl, vertical = AurioSpacing.lg),
    ) {
        TextButton(onClick = onBack, modifier = Modifier.padding(start = 0.dp)) {
            Text("Back", style = AurioType.label, color = AurioColors.Ink)
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = AurioSpacing.sm)) {
            AppTile(label, size = 56.dp, packageName = packageName)
            Spacer(Modifier.width(AurioSpacing.md))
            Column {
                Text(label, style = AurioType.display)
                Text(categoryText(todayUsage?.category ?: saved?.category), style = AurioType.caption, color = AurioColors.InkSubtle)
            }
        }

        if (todayUsage != null) {
            Card(Modifier.padding(top = AurioSpacing.xl)) {
                Text("Today", style = AurioType.caption, color = AurioColors.InkSubtle)
                LimitRow(todayUsage, notCounted = state.notCounted, measured = state.measuredMinutes[packageName])
                note?.let { Text(it, style = AurioType.caption, color = AurioColors.InkSubtle) }
            }
        }

        Card(Modifier.padding(top = AurioSpacing.md)) {
            Text("Last 7 days", style = AurioType.bodyStrong)
            Spacer(Modifier.height(AurioSpacing.md))
            UsageBars(week)
            Spacer(Modifier.height(AurioSpacing.sm))
            val pastDays = week.dropLast(1).count { it.state != DayBar.State.NO_DATA }
            Text(
                if (pastDays == 0) "Bars fill in one day at a time from when you added the app. The line is that day's limit."
                else "The line on each day is the limit that applied then. ? means that day was not tracked.",
                style = AurioType.caption, color = AurioColors.InkSubtle,
            )
        }

        if (saved != null) {
            Text("Settings", style = AurioType.title, modifier = Modifier.padding(top = AurioSpacing.xxl, bottom = AurioSpacing.sm))
            Card {
                val savedLimit = saved.limitMinutes
                if (saved.category == AppCategory.LIMITED && savedLimit != null) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Daily limit", style = AurioType.bodyStrong)
                        LimitStepper(savedLimit, label,
                            onDec = { AurioStore.setLimit(packageName, savedLimit - 5) },
                            onInc = { AurioStore.setLimit(packageName, savedLimit + 5) })
                    }
                    Spacer(Modifier.height(AurioSpacing.md))
                }
                Text("Category", style = AurioType.bodyStrong)
                Spacer(Modifier.height(AurioSpacing.sm))
                val cats = listOf(AppCategory.LIMITED to "Limited", AppCategory.NEUTRAL to "Neutral", AppCategory.ESSENTIAL to "Essential")
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    cats.forEachIndexed { i, (cat, text) ->
                        SegmentedButton(
                            selected = saved.category == cat,
                            onClick = { if (saved.category != cat) AurioStore.setCategory(packageName, cat) },
                            shape = SegmentedButtonDefaults.itemShape(i, cats.size),
                            colors = SegmentedButtonDefaults.colors(activeContainerColor = AurioColors.Ink, activeContentColor = AurioColors.Surface),
                        ) { Text(text, style = AurioType.label) }
                    }
                }
                Spacer(Modifier.height(AurioSpacing.sm))
                Text(
                    "Neutral and essential apps never change aura. A higher limit or a new category starts tomorrow; a lower limit starts now.",
                    style = AurioType.caption, color = AurioColors.InkSubtle,
                )
            }
        }

        Text("Aura impact", style = AurioType.title, modifier = Modifier.padding(top = AurioSpacing.xxl, bottom = AurioSpacing.sm))
        ImpactCard(History.impact(points), todayUsage?.let { AuraScoring.limitStatus(it) }, state.notCounted)
        Spacer(Modifier.height(AurioSpacing.xxl))
    }
}

private fun categoryText(category: AppCategory?): String = when (category) {
    AppCategory.LIMITED -> "Limited app"
    AppCategory.NEUTRAL -> "Neutral app, never changes aura"
    AppCategory.ESSENTIAL -> "Essential app, never changes aura"
    null -> "Not tracked"
}

@Composable
private fun Card(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(AurioRadius.card)).background(AurioColors.Surface).padding(18.dp),
    ) { content() }
}

@Composable
private fun ImpactCard(impact: AppImpact, today: LimitStatus?, notCounted: NotCountedReason? = null) {
    Card {
        Text("Last 7 days, settled", style = AurioType.caption, color = AurioColors.InkSubtle)
        Text(signed(impact.total), style = AurioType.scoreLarge)
        Text(
            when {
                impact.daysUnder + impact.daysOver == 0 -> "No settled days yet. Days are added after midnight."
                else -> "${days(impact.daysUnder)} under the limit, ${days(impact.daysOver)} over"
            },
            style = AurioType.body, color = AurioColors.InkMuted,
        )
        Spacer(Modifier.height(AurioSpacing.md))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Today", style = AurioType.bodyStrong, modifier = Modifier.weight(1f))
            when (today) {
                is LimitStatus.Under -> Pill("+${today.projectedBonus} at midnight", PillTone.PROJECTED)
                is LimitStatus.Over -> Pill("${signed(-today.penalty)} today", PillTone.LOSS)
                LimitStatus.Unknown -> Pill(NotCounted.pill(notCounted), PillTone.UNKNOWN)
                LimitStatus.NotScored -> Pill("Never changes aura", PillTone.NEUTRAL)
                null -> Pill("Not scored today", PillTone.NEUTRAL)
            }
        }
    }
}

private fun days(n: Int) = if (n == 1) "1 day" else "$n days"

private val BAR_AREA: Dp = 120.dp

/** Minutes per day as bars, with each day's limit as a line, so over and under read without colour. */
@Composable
private fun UsageBars(week: List<DayBar>) {
    val scale = week.maxOf { maxOf(it.minutes ?: 0, it.limitMinutes ?: 0) }.coerceAtLeast(1)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        week.forEachIndexed { i, bar ->
            val isToday = i == week.lastIndex
            val dayName = if (isToday) "Today" else bar.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
            Column(
                Modifier.weight(1f).clearAndSetSemantics { contentDescription = barDescription(dayName, bar) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(barValue(bar), style = AurioType.caption, color = barTextColor(bar), textAlign = TextAlign.Center)
                Spacer(Modifier.height(AurioSpacing.xs))
                Box(Modifier.fillMaxWidth().height(BAR_AREA), contentAlignment = Alignment.BottomCenter) {
                    val minutes = bar.minutes
                    if (minutes != null && bar.state != DayBar.State.NO_DATA) {
                        Box(
                            Modifier.fillMaxWidth().height((BAR_AREA * (minutes.toFloat() / scale)).coerceAtLeast(4.dp))
                                .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp)).background(barColor(bar)),
                        )
                    } else {
                        Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(AurioColors.Track))
                    }
                    bar.limitMinutes?.let { limit ->
                        Box(
                            Modifier.align(Alignment.BottomCenter).padding(bottom = BAR_AREA * (limit.toFloat() / scale))
                                .fillMaxWidth().height(2.dp).background(AurioColors.Ink),
                        )
                    }
                }
                Spacer(Modifier.height(AurioSpacing.xs))
                Text(dayName, style = AurioType.caption, color = if (isToday) AurioColors.Ink else AurioColors.InkSubtle, maxLines = 1)
            }
        }
    }
}

private fun barValue(bar: DayBar): String = when (bar.state) {
    DayBar.State.NO_DATA -> "–"
    DayBar.State.UNKNOWN -> "?"
    else -> "${bar.minutes}"
}

private fun barColor(bar: DayBar): Color = when (bar.state) {
    DayBar.State.OVER -> AurioColors.Loss
    DayBar.State.UNDER -> AurioColors.Ink
    else -> AurioColors.InkSubtle.copy(alpha = 0.45f)
}

private fun barTextColor(bar: DayBar): Color = if (bar.state == DayBar.State.OVER) AurioColors.LossText else AurioColors.InkSubtle

internal fun barDescription(day: String, bar: DayBar): String = when (bar.state) {
    DayBar.State.NO_DATA -> "$day, no data"
    DayBar.State.UNKNOWN -> "$day, not tracked, usage access was off"
    DayBar.State.NOT_SCORED -> "$day, ${bar.minutes} min, not scored"
    DayBar.State.UNDER -> "$day, ${bar.minutes} of ${bar.limitMinutes} min, under the limit"
    DayBar.State.OVER -> "$day, ${bar.minutes} of ${bar.limitMinutes} min, ${(bar.minutes ?: 0) - (bar.limitMinutes ?: 0)} over"
}

@Preview(widthDp = 360, heightDp = 400, showBackground = true)
@Composable
private fun UsageBarsPreview() {
    val today = LocalDate.parse("2026-10-26")
    val minutes = listOf(22, 47, null, 30, 12, 64, 18)
    val week = minutes.mapIndexed { i, m ->
        val state = when {
            m == null -> DayBar.State.UNKNOWN
            m > 30 -> DayBar.State.OVER
            else -> DayBar.State.UNDER
        }
        DayBar(today.minusDays(6L - i), m, 30, state)
    }
    AurioTheme {
        Column(Modifier.background(AurioColors.Paper).padding(AurioSpacing.xl)) {
            Card { UsageBars(week) }
            Spacer(Modifier.height(AurioSpacing.md))
            ImpactCard(AppImpact(total = 23, daysUnder = 2, daysOver = 1), LimitStatus.Under(12, 20))
        }
    }
}
