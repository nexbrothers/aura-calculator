package app.aurio.ui.insights

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.aurio.data.db.DailyScoreEntity
import app.aurio.domain.AuraLevel
import app.aurio.domain.History
import app.aurio.ui.components.AurioCard
import app.aurio.ui.components.signed
import app.aurio.ui.theme.AuraPalettes
import app.aurio.ui.theme.AurioColors
import app.aurio.ui.theme.AurioRadius
import app.aurio.ui.theme.AurioSpacing
import app.aurio.ui.theme.AurioTheme
import app.aurio.ui.theme.AurioType
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

/**
 * A month of settled days, each tinted with the aura of the level it ended on. Colour is never
 * the only signal: tapping a day spells out its change, score and level, and every cell reads
 * that out to screen readers. Today is outlined and stays blank until it settles at midnight.
 */
@Composable
fun AuraCalendar(history: List<DailyScoreEntity>, today: LocalDate, modifier: Modifier = Modifier, startDate: LocalDate? = null) {
    val byDate = history.associateBy { LocalDate.parse(it.date) }
    val thisMonth = YearMonth.from(today)
    val firstMonth = (history.map { YearMonth.from(LocalDate.parse(it.date)) } + listOfNotNull(startDate?.let(YearMonth::from))).minOrNull() ?: thisMonth
    var month by rememberSaveable { mutableStateOf(thisMonth.toString()) }
    val shown = YearMonth.parse(month)
    var selected by rememberSaveable { mutableStateOf(history.maxByOrNull { it.date }?.date) }
    val locale = Locale.getDefault()
    val firstDay = WeekFields.of(locale).firstDayOfWeek

    AurioCard(modifier, padding = 18.dp) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                shown.format(DateTimeFormatter.ofPattern("LLLL yyyy", locale)),
                style = AurioType.bodyStrong, modifier = Modifier.weight(1f),
            )
            // Arrows only once there is more than one month to show.
            if (firstMonth < thisMonth) {
                MonthButton("‹", "Previous month", enabled = shown > firstMonth) { month = shown.minusMonths(1).toString() }
                MonthButton("›", "Next month", enabled = shown < thisMonth) { month = shown.plusMonths(1).toString() }
            }
        }
        Spacer(Modifier.height(AurioSpacing.sm))
        Row(Modifier.fillMaxWidth().clearAndSetSemantics { }) {
            (0L until 7L).forEach { i ->
                Text(
                    firstDay.plus(i).getDisplayName(TextStyle.NARROW, locale),
                    style = AurioType.caption, color = AurioColors.InkSubtle, textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.height(AurioSpacing.xs))
        History.monthGrid(shown, firstDay).forEach { week ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                week.forEach { date ->
                    Box(Modifier.weight(1f).aspectRatio(1f).padding(vertical = 2.dp)) {
                        if (date != null) {
                            DayCell(date, byDate[date], isToday = date == today, isFuture = date.isAfter(today) || (startDate != null && date.isBefore(startDate)), isSelected = selected == date.toString()) {
                                selected = date.toString()
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(AurioSpacing.md))
        Text(
            selected?.let { dayDetail(LocalDate.parse(it), byDate[LocalDate.parse(it)], today, locale) } ?: "Days fill in after each midnight.",
            style = AurioType.body, color = AurioColors.InkMuted,
        )
        Spacer(Modifier.height(AurioSpacing.md))
        Legend()
    }
}

@Composable
private fun DayCell(date: LocalDate, day: DailyScoreEntity?, isToday: Boolean, isFuture: Boolean, isSelected: Boolean, onSelect: () -> Unit) {
    // Settled days carry their aura; past days without a score are grey; days to come, and days
    // before Aurio was installed, are blank.
    val fill = day?.let { AuraPalettes.of(AuraLevel.of(it.totalAfter)).c1 }
        ?: if (isFuture) AurioColors.Surface else AurioColors.Track.copy(alpha = 0.5f)
    val ring = when {
        isSelected -> Modifier.border(2.dp, AurioColors.Ink, RoundedCornerShape(AurioRadius.tile))
        isToday -> Modifier.border(1.dp, AurioColors.InkSubtle, RoundedCornerShape(AurioRadius.tile))
        else -> Modifier
    }
    Box(
        Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(AurioRadius.tile)).background(fill).then(ring)
            .selectable(isSelected, role = Role.RadioButton, onClick = onSelect)
            .semantics { contentDescription = cellDescription(date, day, isToday) },
        contentAlignment = Alignment.Center,
    ) {
        Text("${date.dayOfMonth}", style = AurioType.caption, color = if (day != null) AurioColors.Ink else AurioColors.InkSubtle)
    }
}

@Composable
private fun MonthButton(symbol: String, label: String, enabled: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.semantics { contentDescription = label }) {
        Text(symbol, style = AurioType.title, color = if (enabled) AurioColors.Ink else AurioColors.InkSubtle.copy(alpha = 0.4f))
    }
}

/** The five levels and their tints, named, so the colours can be read. */
@Composable
private fun Legend() {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        AuraLevel.entries.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(AurioSpacing.md)) {
                row.forEach { level ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(12.dp).clip(CircleShape).background(AuraPalettes.of(level).c1))
                        Spacer(Modifier.width(6.dp))
                        Text(level.label, style = AurioType.caption, color = AurioColors.InkSubtle)
                    }
                }
            }
        }
    }
}

internal fun dayDetail(date: LocalDate, day: DailyScoreEntity?, today: LocalDate, locale: Locale): String {
    val name = date.format(DateTimeFormatter.ofPattern("EEE d MMM", locale))
    return when {
        day != null -> "$name: ${signed(day.delta)}, aura ${day.totalAfter}, ${AuraLevel.of(day.totalAfter).label}" +
            if (day.partial) ". Partial day: this day was not counted in full." else ""
        date == today -> "$name: today settles at midnight."
        date.isAfter(today) -> "$name: still to come."
        else -> "$name: no score. Waura was not tracking yet, or the day has not settled."
    }
}

private fun cellDescription(date: LocalDate, day: DailyScoreEntity?, isToday: Boolean): String {
    val name = date.format(DateTimeFormatter.ofPattern("d MMMM", Locale.getDefault()))
    return when {
        day != null -> "$name, ${signed(day.delta)}, ${AuraLevel.of(day.totalAfter).label}"
        isToday -> "$name, today, not settled yet"
        else -> "$name, no score"
    }
}

@Preview(widthDp = 360, heightDp = 640, showBackground = true)
@Composable
private fun AuraCalendarPreview() {
    val today = LocalDate.parse("2026-10-26")
    var total = 120
    val history = (25 downTo 1).map { back ->
        val delta = listOf(45, -12, 60, 20, -30, 80, 35)[back % 7]
        total = (total + delta).coerceIn(0, 1000)
        DailyScoreEntity(today.minusDays(back.toLong()).toString(), delta, total, partial = back == 9, streakAfter = 0, settledAt = 0)
    }
    AurioTheme {
        Column(Modifier.background(AurioColors.Paper).padding(AurioSpacing.xl)) { AuraCalendar(history, today) }
    }
}
