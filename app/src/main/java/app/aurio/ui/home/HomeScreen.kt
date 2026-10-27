package app.aurio.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aurio.data.AurioState
import app.aurio.data.AurioStore
import app.aurio.domain.AuraLevel
import app.aurio.domain.SystemAurioClock
import app.aurio.ui.components.AuraField
import app.aurio.ui.components.AurioCard
import app.aurio.ui.components.AurioIcons
import app.aurio.ui.components.CardHeader
import app.aurio.ui.components.LimitRow
import app.aurio.ui.components.Pill
import app.aurio.ui.components.PillTone
import app.aurio.ui.components.PrimaryButton
import app.aurio.ui.components.ProgressBar
import app.aurio.ui.components.RowDivider
import app.aurio.ui.components.ScoreChangeRow
import app.aurio.ui.components.SecondaryButton
import app.aurio.ui.components.StrokeIcon
import app.aurio.ui.components.signed
import app.aurio.ui.theme.AuraPalettes
import app.aurio.ui.theme.AurioColors
import app.aurio.ui.theme.AurioElevation
import app.aurio.ui.theme.AurioRadius
import app.aurio.ui.theme.AurioSpacing
import app.aurio.ui.theme.AurioTheme
import app.aurio.ui.theme.AurioType
import java.time.format.DateTimeFormatter

/**
 * Home, laid out as the approved prototype: greeting, the aura card (the one bold thing),
 * streak and focus at a glance, the main action, then today's limits and what changed.
 * Everything below the status bar scrolls; nothing slides under the clock.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onStartFocus: () -> Unit, onEditLimits: () -> Unit, onProfile: () -> Unit, onTurnOnAccess: () -> Unit, onOpenApp: (String) -> Unit = {}) {
    val state by AurioStore.state.collectAsStateWithLifecycle()
    var showLevels by rememberSaveable { mutableStateOf(false) }

    // Score micro-interaction: tick from the last shown value to the new one; brighten on gain.
    val shown = remember { Animatable(state.score.toFloat()) }
    var gainFlash by remember { mutableStateOf(false) }
    LaunchedEffect(state.score) {
        gainFlash = state.score > shown.value
        shown.animateTo(state.score.toFloat(), tween(1100))
        gainFlash = false
    }
    val brightness by animateFloatAsState(if (gainFlash) 1.12f else 1f, tween(600), label = "glow")
    val displayScore = shown.value.toInt()
    val level = AuraLevel.of(displayScore)

    Column(
        Modifier.fillMaxSize().background(AurioColors.Paper).statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(start = AurioSpacing.xl, end = AurioSpacing.xl, top = AurioSpacing.md, bottom = AurioSpacing.xxl),
        verticalArrangement = Arrangement.spacedBy(AurioSpacing.lg),
    ) {
        Greeting(state, level, onProfile)
        AuraCard(state, displayScore, level, brightness, onLevelClick = { showLevels = true })
        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(AurioSpacing.sm)) {
            StreakCard(state, Modifier.weight(1f).fillMaxHeight())
            FocusCard(state, Modifier.weight(1f).fillMaxHeight())
        }
        PrimaryButton("Start focus session", onStartFocus, leading = {
            StrokeIcon(AurioIcons.Timer, contentDescription = null, tint = Color.White, size = 18.dp)
        })
        if (!state.usageAccess) AccessOffCard(onTurnOnAccess)
        LimitsCard(state, onEditLimits, onOpenApp)
        ChangesCard(state)
    }

    if (showLevels) {
        ModalBottomSheet(onDismissRequest = { showLevels = false }, containerColor = AurioColors.Surface) {
            LevelSheet(current = level)
        }
    }
}

internal fun greeting(name: String, hour: Int): String {
    val part = when {
        hour < 5 -> "Good night"
        hour < 12 -> "Good morning"
        hour < 17 -> "Good afternoon"
        else -> "Good evening"
    }
    // First name only: "Good evening, Aman", as the design shows.
    val first = name.trim().substringBefore(' ')
    return if (first.isBlank()) part else "$part, $first"
}

@Composable
private fun Greeting(state: AurioState, level: AuraLevel, onProfile: () -> Unit) {
    val hour = java.time.Instant.ofEpochMilli(SystemAurioClock.nowMillis()).atZone(SystemAurioClock.zone()).hour
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(greeting(state.userName, hour), style = AurioType.heading, modifier = Modifier.semantics { heading() })
            Text(
                SystemAurioClock.today().format(DateTimeFormatter.ofPattern("EEEE, d MMMM")),
                style = AurioType.label, color = AurioColors.InkSubtle,
            )
        }
        Spacer(Modifier.width(AurioSpacing.md))
        // The avatar wears the user's aura, so even the corner of Home says which level they are on.
        Box(
            Modifier.size(AurioSpacing.touchMin).clip(CircleShape)
                .clickable(role = Role.Button, onClickLabel = "Open profile", onClick = onProfile),
            contentAlignment = Alignment.Center,
        ) {
            AuraField(AuraPalettes.of(level), Modifier.fillMaxSize(), animate = false)
            Text(state.userName.trim().take(1).uppercase().ifEmpty { "A" }, style = AurioType.bodyStrong)
        }
    }
}

@Composable
private fun AuraCard(state: AurioState, score: Int, level: AuraLevel, brightness: Float, onLevelClick: () -> Unit) {
    val shape = RoundedCornerShape(AurioRadius.hero)
    val palette = AuraPalettes.of(level)
    Box(
        Modifier.fillMaxWidth().height(296.dp)
            .shadow(AurioElevation.hero, shape, ambientColor = palette.c1, spotColor = palette.c1)
            .clip(shape),
    ) {
        AuraField(palette, Modifier.fillMaxSize(), brightness = brightness, animate = !state.settings.reduceMotion)
        Column(Modifier.fillMaxSize().padding(22.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier.heightIn(min = 36.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.6f))
                        .clickable(role = Role.Button, onClickLabel = "Show aura levels", onClick = onLevelClick)
                        .padding(start = 14.dp, end = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(level.label, style = AurioType.label)
                    Spacer(Modifier.width(AurioSpacing.xs))
                    StrokeIcon(AurioIcons.ChevronDown, contentDescription = null, size = 16.dp)
                }
                Spacer(Modifier.weight(1f))
                Pill("${signed(state.daily.total)} today", PillTone.ON_AURA)
            }
            Spacer(Modifier.height(AurioSpacing.xl))
            Text(
                "$score", style = AurioType.scoreCard,
                modifier = Modifier.clearAndSetSemantics { contentDescription = "Aura score $score, level ${level.label}" },
            )
            Text("aura points", style = AurioType.label, color = AurioColors.Ink.copy(alpha = 0.72f))
            Spacer(Modifier.weight(1f))
            val next = level.next
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(if (next != null) "${next.range.first - score} to ${next.label}" else "Top level reached", style = AurioType.label)
                Text("${next?.range?.first ?: 1000}", style = AurioType.label, color = AurioColors.Ink.copy(alpha = 0.72f))
            }
            Spacer(Modifier.height(AurioSpacing.sm))
            ProgressBar(
                fraction = (score - level.range.first) / (level.range.last + 1 - level.range.first).toFloat(),
                track = Color.White.copy(alpha = 0.6f), height = 8.dp,
            )
        }
    }
}

@Composable
private fun StreakCard(state: AurioState, modifier: Modifier) {
    AurioCard(modifier, padding = AurioSpacing.md, verticalArrangement = Arrangement.Center) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StrokeIcon(AurioIcons.Flame, contentDescription = null, tint = AurioColors.Streak)
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    if (state.streakDays == 0) "No streak yet" else "${state.streakDays}-day streak",
                    style = AurioType.bodyStrong,
                )
                Text("Best: ${days(state.bestStreak)}", style = AurioType.caption, color = AurioColors.InkSubtle)
            }
        }
    }
}

@Composable
private fun FocusCard(state: AurioState, modifier: Modifier) {
    AurioCard(modifier, padding = AurioSpacing.md) {
        Text("${state.daily.focusAura} of 100", style = AurioType.bodyStrong)
        Text("focus aura today", style = AurioType.caption, color = AurioColors.InkSubtle)
        Spacer(Modifier.height(6.dp))
        ProgressBar(state.daily.focusAura / 100f, color = AurioColors.Gain, track = AurioColors.GainBg)
    }
}

private fun days(n: Int) = if (n == 1) "1 day" else "$n days"

@Composable
private fun AccessOffCard(onTurnOn: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(AurioRadius.card)).background(AurioColors.UnknownBg)
            .padding(start = AurioSpacing.lg, end = AurioSpacing.sm, top = AurioSpacing.md, bottom = AurioSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StrokeIcon(AurioIcons.Info, contentDescription = null, tint = AurioColors.Unknown)
        Spacer(Modifier.width(AurioSpacing.md))
        Column(Modifier.weight(1f)) {
            Text("Usage access is off", style = AurioType.bodyStrong)
            Text("Limits show as Unknown until it is on.", style = AurioType.caption, color = AurioColors.InkSubtle)
        }
        Spacer(Modifier.width(AurioSpacing.sm))
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

@Composable
private fun LimitsCard(state: AurioState, onEditLimits: () -> Unit, onOpenApp: (String) -> Unit) {
    AurioCard(padding = AurioSpacing.lg) {
        CardHeader("Today's limits", actionLabel = if (state.limitedApps.isEmpty()) null else "Edit", onAction = onEditLimits)
        if (state.limitedApps.isEmpty()) {
            Text(
                "Pick the apps you want a daily limit on. Each one you stay under earns +20 aura at midnight.",
                style = AurioType.body, color = AurioColors.InkMuted,
            )
            Spacer(Modifier.height(AurioSpacing.md))
            SecondaryButton("Choose apps", onEditLimits, container = AurioColors.SurfaceSunken)
        } else {
            state.limitedApps.forEachIndexed { i, app ->
                LimitRow(
                    if (state.usageAccess) app else app.copy(usedMinutes = null),
                    Modifier.clickable(role = Role.Button, onClickLabel = "Open ${app.label} details") { onOpenApp(app.packageName) },
                )
                if (i < state.limitedApps.lastIndex) RowDivider()
            }
        }
    }
}

@Composable
private fun ChangesCard(state: AurioState) {
    AurioCard(padding = AurioSpacing.lg) {
        CardHeader("What changed today")
        if (state.daily.lines.isEmpty()) {
            Text(
                "Nothing yet today. Finish a focus session to earn your first aura.",
                style = AurioType.body, color = AurioColors.InkMuted, modifier = Modifier.padding(bottom = AurioSpacing.sm),
            )
        }
        state.daily.lines.forEach { ScoreChangeRow(it); RowDivider() }
        if (state.daily.lines.isEmpty()) RowDivider()
        Row(Modifier.fillMaxWidth().padding(top = AurioSpacing.md), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Total today", style = AurioType.bodyStrong)
            val total = state.daily.total
            Text(
                signed(total), style = AurioType.title,
                color = when {
                    total > 0 -> AurioColors.GainText
                    total < 0 -> AurioColors.LossText
                    else -> AurioColors.Ink
                },
            )
        }
    }
}

@Composable
private fun LevelSheet(current: AuraLevel) {
    Column(Modifier.padding(start = AurioSpacing.xl, end = AurioSpacing.xl, bottom = AurioSpacing.xxxl)) {
        Text("Aura levels", style = AurioType.heading)
        Spacer(Modifier.height(AurioSpacing.lg))
        AuraLevel.entries.forEach { lv ->
            val isCurrent = lv == current
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                    .background(if (isCurrent) AurioColors.Paper else Color.Transparent).padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AuraField(AuraPalettes.of(lv), Modifier.size(44.dp).clip(RoundedCornerShape(AurioRadius.tile)), animate = false)
                Spacer(Modifier.width(AurioSpacing.md))
                Column(Modifier.weight(1f)) {
                    Text(lv.label, style = AurioType.bodyStrong)
                    Text("${lv.range.first} to ${lv.range.last}", style = AurioType.caption, color = AurioColors.InkSubtle)
                }
                if (isCurrent) Pill("You", PillTone.NEUTRAL)
            }
        }
    }
}

@Preview(widthDp = 360, heightDp = 360, showBackground = true)
@Composable
private fun AccessOffCardPreview() {
    AurioTheme { Column(Modifier.background(AurioColors.Paper).padding(AurioSpacing.xl)) { AccessOffCard(onTurnOn = {}) } }
}
