package app.aurio.ui.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.content.Intent
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextField
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import kotlinx.coroutines.launch
import app.aurio.ui.components.SecondaryButton
import app.aurio.data.AurioStore
import app.aurio.domain.AppCategory
import app.aurio.domain.AppTerms
import app.aurio.ui.theme.AurioTheme
import androidx.compose.ui.tooling.preview.Preview
import app.aurio.domain.AppUsage
import app.aurio.domain.AuraScoring
import app.aurio.domain.LimitStatus
import app.aurio.ui.components.AuraField
import app.aurio.ui.components.GroupedList
import app.aurio.ui.components.LimitRow
import app.aurio.ui.components.Pill
import app.aurio.ui.components.PillTone
import app.aurio.ui.components.RowDivider
import app.aurio.ui.components.ScoreChangeRow
import app.aurio.ui.components.signed
import app.aurio.ui.theme.AuraPalettes
import app.aurio.ui.theme.AurioColors
import app.aurio.ui.theme.AurioRadius
import app.aurio.ui.theme.AurioSpacing
import app.aurio.ui.theme.AurioType

@Composable
private fun TabPage(title: String, action: (@Composable () -> Unit)? = null, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding()
            .padding(horizontal = AurioSpacing.xl, vertical = AurioSpacing.xxl),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = AurioType.display)
            action?.invoke()
        }
        Spacer(Modifier.height(AurioSpacing.xl))
        content()
    }
}

// ---------------- Limits ----------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LimitsScreen(onManageApps: () -> Unit) {
    val state by AurioStore.state.collectAsStateWithLifecycle()
    var seg by rememberSaveable { mutableIntStateOf(0) }
    val cats = listOf(AppCategory.LIMITED to "Limited", AppCategory.NEUTRAL to "Neutral", AppCategory.ESSENTIAL to "Essential")

    TabPage("Limits", action = { TextButton(onClick = onManageApps) { Text("Manage apps", style = AurioType.label) } }) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            cats.forEachIndexed { i, (_, label) ->
                SegmentedButton(
                    selected = seg == i, onClick = { seg = i },
                    shape = SegmentedButtonDefaults.itemShape(i, cats.size),
                    colors = SegmentedButtonDefaults.colors(activeContainerColor = AurioColors.Ink, activeContentColor = AurioColors.Surface),
                ) { Text(label, style = AurioType.label) }
            }
        }
        Spacer(Modifier.height(AurioSpacing.lg))
        val apps = state.apps.filter { it.category == cats[seg].first }
        if (apps.isEmpty()) {
            Text(
                "No ${cats[seg].second.lowercase()} apps yet. Use Manage apps to add some.",
                style = AurioType.body, color = AurioColors.InkMuted,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(AurioRadius.card)).background(AurioColors.Surface).padding(AurioSpacing.lg),
            )
        } else if (cats[seg].first == AppCategory.LIMITED) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                apps.forEach { app ->
                    val usage = if (state.usageAccess) app else app.copy(usedMinutes = null)
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(AurioRadius.card)).background(AurioColors.Surface).padding(horizontal = AurioSpacing.lg)) {
                        val saved = state.tracked[app.packageName]
                        LimitRow(usage)
                        Row(Modifier.fillMaxWidth().padding(bottom = AurioSpacing.md), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            when (val s = AuraScoring.limitStatus(usage)) {
                                is LimitStatus.Under -> Pill("+${s.projectedBonus} at midnight", PillTone.PROJECTED)
                                is LimitStatus.Over -> Pill("${signed(-s.penalty)} today", PillTone.LOSS)
                                else -> Pill("Unknown", PillTone.UNKNOWN)
                            }
                            val savedLimit = saved?.limitMinutes
                            if (saved?.category == AppCategory.LIMITED && savedLimit != null) {
                                Stepper(savedLimit, app.label,
                                    onDec = { AurioStore.setLimit(app.packageName, savedLimit - 5) },
                                    onInc = { AurioStore.setLimit(app.packageName, savedLimit + 5) })
                            }
                        }
                        tomorrowNote(app, saved)?.let { PendingNote(it) }
                    }
                }
            }
        } else {
            GroupedList {
                apps.forEachIndexed { i, app ->
                    LimitRow(if (state.usageAccess) app else app.copy(usedMinutes = null))
                    tomorrowNote(app, state.tracked[app.packageName])?.let { PendingNote(it) }
                    if (i < apps.lastIndex) RowDivider()
                }
            }
        }
        Spacer(Modifier.height(AurioSpacing.lg))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(AurioRadius.card)).background(AurioColors.SurfaceSunken).padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("How limits score", style = AurioType.bodyStrong)
            RuleRow("+20", PillTone.GAIN, "Day ends under an app's limit")
            RuleRow("−1", PillTone.LOSS, "Per minute over, up to −60 an app")
            RuleRow("0", PillTone.NEUTRAL, "Neutral and essential apps")
            RuleRow("?", PillTone.UNKNOWN, "Unknown when usage access is off")
        }
    }
}

/**
 * What changes tomorrow when the saved settings differ from the terms scoring today:
 * a raised limit, a new category or a removed app only apply from the next day.
 */
internal fun tomorrowNote(today: AppUsage, saved: AppTerms?): String? = when {
    saved == null -> "Stops being tracked tomorrow"
    saved.category != today.category -> "Moves to ${saved.category.name.lowercase()} tomorrow"
    saved.category == AppCategory.LIMITED && saved.limitMinutes != today.limitMinutes ->
        "Limit is ${today.limitMinutes} min today, ${saved.limitMinutes} min from tomorrow"
    else -> null
}

@Composable
private fun PendingNote(text: String) {
    Text(text, style = AurioType.caption, color = AurioColors.InkSubtle, modifier = Modifier.padding(bottom = AurioSpacing.md))
}

@Preview
@Composable
private fun PendingNotePreview() {
    AurioTheme { PendingNote("Limit is 30 min today, 45 min from tomorrow") }
}

@Composable
private fun RuleRow(amount: String, tone: PillTone, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Pill(amount, tone, minWidth = 48.dp)
        Spacer(Modifier.width(8.dp))
        Text(text, style = AurioType.caption.copy(fontSize = 13.sp))
    }
}

@Composable
private fun Stepper(value: Int, appLabel: String, onDec: () -> Unit, onInc: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        StepButton("−", "Decrease $appLabel limit", onDec)
        Text("$value min", style = AurioType.title.copy(fontSize = 18.sp), textAlign = TextAlign.Center, modifier = Modifier.widthIn(min = 72.dp))
        StepButton("+", "Increase $appLabel limit", onInc)
    }
}

@Composable
private fun StepButton(symbol: String, label: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.semantics { contentDescription = label }) {
        Box(Modifier.size(40.dp).clip(CircleShape).border(1.dp, AurioColors.Track, CircleShape), contentAlignment = Alignment.Center) {
            Text(symbol, style = AurioType.title)
        }
    }
}

// ---------------- Insights ----------------

@Composable
fun InsightsScreen() {
    val state by AurioStore.state.collectAsStateWithLifecycle()
    val today = app.aurio.domain.SystemAurioClock.today()
    val byDate = state.history.associate { it.date to it.delta }
    val dates = (6 downTo 0).map { today.minusDays(it.toLong()) }
    // Past days are frozen at midnight; today is live and still projected.
    val week = dates.map { d -> if (d == today) state.daily.total else byDate[d.toString()] ?: 0 }
    val days = dates.map { it.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault()) }
    val settledDays = dates.count { it != today && byDate.containsKey(it.toString()) }

    TabPage("Insights") {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(AurioRadius.card)).background(AurioColors.Surface).padding(18.dp)) {
            Text("Last 7 days", style = AurioType.caption.copy(fontSize = 13.sp), color = AurioColors.InkSubtle)
            Text(signed(week.sum()), style = AurioType.scoreLarge.copy(fontSize = 56.sp))
            Spacer(Modifier.height(AurioSpacing.lg))
            WeekBars(week, days)
            Spacer(Modifier.height(6.dp))
            Text(
                if (settledDays == 0) "Past days appear here after each midnight. Today is projected until then."
                else "Today is projected until midnight",
                style = AurioType.caption, color = AurioColors.InkSubtle,
            )
        }
        Row(Modifier.fillMaxWidth().padding(top = AurioSpacing.md), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard("Best streak", if (state.bestStreak == 1) "1 day" else "${state.bestStreak} days", Modifier.weight(1f))
            StatCard("Days tracked", "${state.history.size}", Modifier.weight(1f))
        }
        Text("Today, line by line", style = AurioType.title, modifier = Modifier.padding(top = AurioSpacing.xxl, bottom = 10.dp))
        GroupedList {
            if (state.daily.lines.isEmpty()) {
                Text("Nothing yet today.", style = AurioType.body, color = AurioColors.InkMuted, modifier = Modifier.padding(vertical = 14.dp))
                RowDivider()
            }
            state.daily.lines.forEach { ScoreChangeRow(it); RowDivider() }
            Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Total", style = AurioType.bodyStrong)
                Text(signed(state.daily.total), style = AurioType.title.copy(fontSize = 22.sp))
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(AurioRadius.card)).background(AurioColors.Surface).padding(AurioSpacing.lg)) {
        Text(label, style = AurioType.caption, color = AurioColors.InkSubtle)
        Text(value, style = AurioType.title)
    }
}

@Composable
private fun WeekBars(values: List<Int>, days: List<String>) {
    val maxAbs = (values.maxOf { kotlin.math.abs(it) }).coerceAtLeast(1)
    val half = 70.dp
    Row(Modifier.fillMaxWidth().height(half * 2 + 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        values.forEachIndexed { i, v ->
            val today = i == values.lastIndex
            val barH = half * (kotlin.math.abs(v).toFloat() / maxAbs)
            Column(Modifier.weight(1f).semantics { contentDescription = "${days[i]}, ${signed(v)} aura" }, horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.height(half).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                    if (v >= 0) Bar(barH, if (today) AurioColors.Gain.copy(alpha = 0.55f) else AurioColors.Gain)
                }
                Box(Modifier.height(1.dp).fillMaxWidth().background(AurioColors.Track))
                Box(Modifier.height(half - 1.dp).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                    if (v < 0) Bar(barH, AurioColors.Loss)
                }
                Text(days[i], style = AurioType.caption.copy(fontSize = 12.sp), color = AurioColors.InkSubtle)
            }
        }
    }
}

@Composable
private fun Bar(h: androidx.compose.ui.unit.Dp, color: androidx.compose.ui.graphics.Color) =
    Box(Modifier.padding(horizontal = 4.dp).fillMaxWidth().height(h.coerceAtLeast(4.dp)).clip(RoundedCornerShape(8.dp)).background(color))

// ---------------- Profile ----------------

@Composable
fun ProfileScreen(onOpenUsageSettings: () -> Unit, onDeveloperTools: () -> Unit = {}) {
    val state by AurioStore.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var editName by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    TabPage("Profile") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AuraField(AuraPalettes.of(state.level), Modifier.size(56.dp).clip(CircleShape), animate = false)
            Spacer(Modifier.width(AurioSpacing.md))
            Column(Modifier.weight(1f)) {
                Text(state.userName.ifBlank { "Your name" }, style = AurioType.title.copy(fontSize = 24.sp))
                Text("${state.level.label}, ${state.score} aura", style = AurioType.caption.copy(fontSize = 13.sp), color = AurioColors.InkSubtle)
            }
            TextButton(onClick = { editName = true }) { Text("Edit name", style = AurioType.label) }
        }

        Text("Focus", style = AurioType.title, modifier = Modifier.padding(top = 28.dp, bottom = 10.dp))
        GroupedList {
            Column(Modifier.fillMaxWidth().padding(vertical = 14.dp)) {
                Text("Default session length", style = AurioType.bodyStrong)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(25, 50, 90).forEach { m ->
                        val sel = state.settings.defaultFocusMinutes == m
                        Text(
                            "$m min", style = AurioType.label, color = if (sel) Color.White else AurioColors.Ink,
                            modifier = Modifier.heightIn(min = AurioSpacing.touchMin).clip(CircleShape)
                                .background(if (sel) AurioColors.Ink else AurioColors.SurfaceSunken)
                                .selectable(sel, role = Role.RadioButton) { AurioStore.setDefaultFocusMinutes(m) }
                                .padding(horizontal = 16.dp, vertical = 13.dp),
                        )
                    }
                }
            }
            RowDivider()
            ToggleRow("Session complete", "A notification when a focus session ends", state.settings.focusReminders) { AurioStore.setFocusReminders(it) }
        }

        Text("Look", style = AurioType.title, modifier = Modifier.padding(top = 28.dp, bottom = 10.dp))
        GroupedList {
            ToggleRow("Reduce motion", "Stills the moving aura background", state.settings.reduceMotion) { AurioStore.setReduceMotion(it) }
        }

        Text("Privacy", style = AurioType.title, modifier = Modifier.padding(top = 28.dp, bottom = 10.dp))
        GroupedList {
            ToggleRow("Usage access", if (state.usageAccess) "On. Tap to change in Settings" else "Off. Limits show as Unknown", state.usageAccess) { onOpenUsageSettings() }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SeesCard("Aurio sees", "Which app is open\nFor how long\nYour focus sessions", AurioColors.GainBg, AurioColors.GainText, Modifier.weight(1f))
            SeesCard("Never sees", "Messages\nPhotos\nWhat you watch or post", AurioColors.UnknownBg, AurioColors.Ink, Modifier.weight(1f))
        }

        Text("Your data", style = AurioType.title, modifier = Modifier.padding(top = 28.dp, bottom = 10.dp))
        SecondaryButton("Export my data", {
            scope.launch {
                val json = AurioStore.exportJson()
                val send = Intent(Intent.ACTION_SEND).setType("text/plain")
                    .putExtra(Intent.EXTRA_SUBJECT, "Aurio data export")
                    .putExtra(Intent.EXTRA_TEXT, json)
                context.startActivity(Intent.createChooser(send, "Export Aurio data"))
            }
        })
        Spacer(Modifier.height(10.dp))
        TextButton(
            onClick = { confirmDelete = true },
            modifier = Modifier.fillMaxWidth().height(AurioSpacing.buttonHeight).clip(CircleShape).background(AurioColors.LossBg),
        ) { Text("Delete all data", style = AurioType.button, color = AurioColors.LossText) }
        val debuggable = (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
        if (debuggable) {
            Spacer(Modifier.height(10.dp))
            SecondaryButton("Developer tools", onDeveloperTools)
        }
        Text("Aurio 0.1.0", style = AurioType.caption, color = AurioColors.InkSubtle, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = AurioSpacing.lg))
    }

    if (editName) {
        var draft by remember { mutableStateOf(state.userName) }
        AlertDialog(
            onDismissRequest = { editName = false },
            title = { Text("Your name") },
            text = { TextField(draft, { draft = it.take(30) }, singleLine = true) },
            confirmButton = { TextButton({ AurioStore.setName(draft); editName = false }) { Text("Save") } },
            dismissButton = { TextButton({ editName = false }) { Text("Cancel") } },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete all data?") },
            text = { Text("This erases your aura, history, apps and settings from this phone. It cannot be undone.") },
            confirmButton = { TextButton({ confirmDelete = false; AurioStore.deleteAllData() }) { Text("Delete everything", color = AurioColors.LossText) } },
            dismissButton = { TextButton({ confirmDelete = false }) { Text("Keep my data") } },
        )
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = AurioType.bodyStrong)
            Text(subtitle, style = AurioType.caption, color = AurioColors.InkSubtle)
        }
        Switch(checked, onChange, colors = SwitchDefaults.colors(checkedTrackColor = AurioColors.Ink))
    }
}

@Composable
private fun SeesCard(title: String, body: String, bg: androidx.compose.ui.graphics.Color, titleColor: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(AurioRadius.card)).background(bg).padding(AurioSpacing.lg)) {
        Text(title, style = AurioType.bodyStrong, color = titleColor)
        Spacer(Modifier.height(8.dp))
        Text(body, style = AurioType.caption.copy(fontSize = 13.sp, lineHeight = 22.sp))
    }
}
