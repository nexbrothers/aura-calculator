package app.aurio.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aurio.data.AurioState
import app.aurio.data.AurioStore
import app.aurio.domain.AuraLevel
import app.aurio.ui.components.AuraField
import app.aurio.ui.components.GroupedList
import app.aurio.ui.components.LimitRow
import app.aurio.ui.components.Pill
import app.aurio.ui.components.PillTone
import app.aurio.ui.components.PrimaryButton
import app.aurio.ui.components.SecondaryButton
import app.aurio.ui.components.ProgressBar
import app.aurio.ui.components.RowDivider
import app.aurio.ui.components.ScoreChangeRow
import app.aurio.ui.components.signed
import app.aurio.ui.theme.AuraPalettes
import app.aurio.ui.theme.AurioColors
import app.aurio.ui.theme.AurioRadius
import app.aurio.ui.theme.AurioSpacing
import app.aurio.ui.theme.AurioType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onStartFocus: () -> Unit, onEditLimits: () -> Unit, onProfile: () -> Unit, onTurnOnAccess: () -> Unit) {
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

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        AuraHeader(state, displayScore, level, brightness, onProfile, onLevelClick = { showLevels = true })
        Column(
            Modifier.offset(y = (-28).dp)
                .clip(RoundedCornerShape(topStart = AurioRadius.hero, topEnd = AurioRadius.hero))
                .background(AurioColors.Paper)
                .padding(horizontal = AurioSpacing.xl, vertical = AurioSpacing.xxl),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            StreakAndFocus(state)
            PrimaryButton("Start focus session", onStartFocus)
            if (!state.usageAccess) AccessOffBanner(onTurnOnAccess)

            Column {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Today's limits", style = AurioType.title)
                    TextButton(onClick = onEditLimits) { Text("Edit", style = AurioType.bodyStrong, color = AurioColors.InkMuted) }
                }
                if (state.limitedApps.isEmpty()) {
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(AurioRadius.card)).background(AurioColors.Surface).padding(AurioSpacing.lg),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text("No limits yet", style = AurioType.bodyStrong)
                        Text("Pick the apps you want a daily limit on. Staying under earns +20 aura each.", style = AurioType.body, color = AurioColors.InkMuted)
                        SecondaryButton("Choose apps", onEditLimits, container = AurioColors.SurfaceSunken)
                    }
                } else {
                    GroupedList {
                        state.limitedApps.forEachIndexed { i, app ->
                            LimitRow(if (state.usageAccess) app else app.copy(usedMinutes = null))
                            if (i < state.limitedApps.lastIndex) RowDivider()
                        }
                    }
                }
            }

            Column {
                Text("What changed today", style = AurioType.title, modifier = Modifier.padding(bottom = 10.dp))
                GroupedList {
                    if (state.daily.lines.isEmpty()) {
                        Text("Nothing yet today. Finish a focus session to earn your first aura.", style = AurioType.body, color = AurioColors.InkMuted, modifier = Modifier.padding(vertical = 14.dp))
                        RowDivider()
                    }
                    state.daily.lines.forEach { ScoreChangeRow(it); RowDivider() }
                    Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Total today", style = AurioType.bodyStrong)
                        Text(signed(state.daily.total), style = AurioType.title.copy(fontSize = 22.sp))
                    }
                }
            }
        }
    }

    if (showLevels) {
        ModalBottomSheet(onDismissRequest = { showLevels = false }, containerColor = AurioColors.Surface) {
            LevelSheet(current = level)
        }
    }
}

private fun greeting(name: String): String {
    val hour = java.time.Instant.ofEpochMilli(app.aurio.domain.SystemAurioClock.nowMillis()).atZone(java.time.ZoneId.systemDefault()).hour
    val part = when {
        hour < 5 -> "Good night"
        hour < 12 -> "Good morning"
        hour < 17 -> "Good afternoon"
        else -> "Good evening"
    }
    return if (name.isBlank()) part else "$part, $name"
}

@Composable
private fun AuraHeader(state: AurioState, score: Int, level: AuraLevel, brightness: Float, onProfile: () -> Unit, onLevelClick: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(440.dp)) {
        AuraField(AuraPalettes.of(level), Modifier.fillMaxSize(), brightness = brightness, animate = !state.settings.reduceMotion)
        Column(Modifier.statusBarsPadding().padding(horizontal = AurioSpacing.xl, vertical = AurioSpacing.md)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text(greeting(state.userName), style = AurioType.bodyStrong)
                    Text(app.aurio.domain.SystemAurioClock.today().format(java.time.format.DateTimeFormatter.ofPattern("EEEE, d MMMM")), style = AurioType.label.copy(fontSize = 13.sp), color = AurioColors.Ink.copy(alpha = 0.75f))
                }
                Box(
                    Modifier.size(44.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.45f))
                        .clickable(role = Role.Button, onClickLabel = "Open profile", onClick = onProfile),
                    contentAlignment = Alignment.Center,
                ) { Text(state.userName.trim().take(1).uppercase().ifEmpty { "A" }, style = AurioType.bodyStrong) }
            }
            Spacer(Modifier.height(40.dp))
            Box(
                Modifier.clip(CircleShape).background(Color.White.copy(alpha = 0.55f))
                    .clickable(role = Role.Button, onClickLabel = "Show aura levels", onClick = onLevelClick)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) { Text(level.label, style = AurioType.label.copy(fontSize = 14.sp)) }
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    "$score", style = AurioType.scoreHero,
                    modifier = Modifier.semantics { contentDescription = "Aura score $score, level ${level.label}" },
                )
                Spacer(Modifier.width(10.dp))
                Pill("${signed(state.daily.total)} today", PillTone.ON_AURA, Modifier.padding(bottom = 14.dp))
            }
            Spacer(Modifier.height(18.dp))
            val next = level.next
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(if (next != null) "${next.range.first - score} to ${next.label}" else "Top level reached", style = AurioType.label)
                Text("${next?.range?.first ?: 1000}", style = AurioType.label, color = AurioColors.Ink.copy(alpha = 0.7f))
            }
            Spacer(Modifier.height(8.dp))
            ProgressBar(
                fraction = (score - level.range.first) / (level.range.last + 1 - level.range.first).toFloat(),
                track = Color.White.copy(alpha = 0.55f), height = 8.dp,
            )
        }
    }
}

@Composable
private fun StreakAndFocus(state: AurioState) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(if (state.streakDays == 1) "1 day" else "${state.streakDays} days", style = AurioType.title)
            Text("streak, best ${state.bestStreak}", style = AurioType.caption, color = AurioColors.InkSubtle)
        }
        Box(Modifier.width(1.dp).height(36.dp).background(Color(0xFFE3E2DD)))
        Spacer(Modifier.width(AurioSpacing.lg))
        Column(Modifier.weight(1.2f)) {
            Text("${state.daily.focusAura} / 100", style = AurioType.title)
            Text("focus aura today", style = AurioType.caption, color = AurioColors.InkSubtle)
            Spacer(Modifier.height(6.dp))
            ProgressBar(state.daily.focusAura / 100f, color = AurioColors.Gain, track = AurioColors.GainBg)
        }
    }
}

@Composable
private fun AccessOffBanner(onTurnOn: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(AurioColors.UnknownBg)
            .clickable(role = Role.Button, onClick = onTurnOn).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Usage access is off, so limits show as unknown.", style = AurioType.caption.copy(fontSize = 13.sp), modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        Text("Turn on", style = AurioType.label)
    }
}

@Composable
private fun LevelSheet(current: AuraLevel) {
    Column(Modifier.padding(start = AurioSpacing.xl, end = AurioSpacing.xl, bottom = AurioSpacing.xxxl)) {
        Text("Aura levels", style = AurioType.title.copy(fontSize = 24.sp))
        Spacer(Modifier.height(AurioSpacing.lg))
        AuraLevel.entries.forEach { lv ->
            val isCurrent = lv == current
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                    .background(if (isCurrent) AurioColors.Paper else Color.Transparent).padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AuraField(AuraPalettes.of(lv), Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)), animate = false)
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
