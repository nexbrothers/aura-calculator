package app.aurio.ui.focus

import app.aurio.ui.theme.AurioTheme
import androidx.core.content.ContextCompat
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.heightIn
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import android.os.Build
import android.content.pm.PackageManager
import android.Manifest
import androidx.compose.foundation.Canvas
import kotlin.math.sin
import kotlin.math.cos
import app.aurio.ui.theme.AurioElevation
import app.aurio.ui.components.StrokeIcon
import app.aurio.ui.components.AurioIcons
import app.aurio.ui.components.AurioCard
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aurio.data.AurioStore
import app.aurio.data.db.FocusSessionEntity
import app.aurio.domain.AuraScoring
import app.aurio.ui.components.AuraField
import app.aurio.ui.components.Pill
import app.aurio.ui.components.PillTone
import app.aurio.ui.components.PrimaryButton
import app.aurio.ui.components.ProgressBar
import app.aurio.ui.components.SecondaryButton
import app.aurio.ui.theme.AuraPalettes
import app.aurio.ui.theme.AurioColors
import app.aurio.ui.theme.AurioRadius
import app.aurio.ui.theme.AurioSpacing
import app.aurio.ui.theme.AurioType
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun FocusSetupScreen(onStarted: () -> Unit) {
    val state by AurioStore.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val presets = listOf(25, 50, 90)
    var minutes by rememberSaveable { mutableIntStateOf(state.settings.defaultFocusMinutes.takeIf { it in presets } ?: 25) }
    var custom by rememberSaveable { mutableStateOf(false) }
    var customMinutes by rememberSaveable { mutableIntStateOf(35) }
    val chosen = if (custom) customMinutes else minutes
    val sessionMinutes = state.sessions.map { it.minutes }
    val capLeft = AuraScoring.MAX_FOCUS_PER_DAY - state.daily.focusAura

    // A session already running (for example after reopening the app) is resumed, not duplicated.
    LaunchedEffect(state.running?.id) { if (state.running != null) onStarted() }

    // The choices scroll; Start stays pinned at the bottom, so nothing is cut off on small phones.
    Column(Modifier.fillMaxSize().background(AurioColors.Paper).statusBarsPadding()) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState())
                .padding(start = AurioSpacing.xl, end = AurioSpacing.xl, top = AurioSpacing.md, bottom = AurioSpacing.lg),
        ) {
            Text("Focus session", style = AurioType.display, modifier = Modifier.semantics { heading() })
            Spacer(Modifier.height(AurioSpacing.sm))
            Text("Put the phone down. Aura is added when the session completes.", style = AurioType.body, color = AurioColors.InkMuted)
            Spacer(Modifier.height(AurioSpacing.xxl))
            Column(verticalArrangement = Arrangement.spacedBy(AurioSpacing.sm)) {
                presets.forEach { m ->
                    DurationCard(m, AuraScoring.potentialFocus(sessionMinutes, m), selected = !custom && m == minutes) { custom = false; minutes = m }
                }
                CustomCard(
                    customMinutes, AuraScoring.potentialFocus(sessionMinutes, customMinutes), selected = custom,
                    onSelect = { custom = true },
                    onDec = { custom = true; customMinutes = (customMinutes - 5).coerceAtLeast(5) },
                    onInc = { custom = true; customMinutes = (customMinutes + 5).coerceAtMost(180) },
                )
            }
            Spacer(Modifier.height(AurioSpacing.md))
            Text(
                if (capLeft > 0) "$capLeft of 100 focus aura left today" else "Daily focus cap reached. Sessions still count as focus time.",
                style = AurioType.caption, color = AurioColors.InkSubtle,
            )
        }
        PrimaryButton(
            "Start $chosen min session", { scope.launch { AurioStore.startFocus(chosen) } },
            Modifier.padding(horizontal = AurioSpacing.xl, vertical = AurioSpacing.lg),
            leading = { StrokeIcon(AurioIcons.Timer, contentDescription = null, tint = Color.White, size = 18.dp) },
        )
    }
}

private fun earnText(earn: Int) = if (earn <= 0) "Cap reached" else "+$earn aura"

/** One duration to choose: a big number, its aura, lifted in ink when chosen. */
@Composable
private fun DurationCard(minutes: Int, earn: Int, selected: Boolean, onSelect: () -> Unit) {
    val shape = RoundedCornerShape(AurioRadius.card)
    Row(
        Modifier.fillMaxWidth().height(84.dp)
            .shadow(if (selected) 0.dp else AurioElevation.card, shape, ambientColor = AurioColors.Shadow.copy(alpha = 0.04f), spotColor = AurioColors.Shadow.copy(alpha = 0.10f))
            .clip(shape).background(if (selected) AurioColors.Ink else AurioColors.Surface)
            .selectable(selected, role = Role.RadioButton, onClick = onSelect)
            .padding(horizontal = AurioSpacing.xl),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val fg = if (selected) Color.White else AurioColors.Ink
        Row(Modifier.weight(1f), verticalAlignment = Alignment.Bottom) {
            Text("$minutes", style = AurioType.display, color = fg)
            Text(" min", style = AurioType.bodyStrong, color = fg, modifier = Modifier.padding(bottom = 4.dp))
        }
        EarnPill(earn, selected)
    }
}

@Composable
private fun EarnPill(earn: Int, onDark: Boolean) {
    Text(
        earnText(earn), style = AurioType.label, color = if (onDark) Color.White else AurioColors.GainText,
        modifier = Modifier.clip(CircleShape).background(if (onDark) Color.White.copy(alpha = 0.16f) else AurioColors.GainBg)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

/** Any length from 5 to 180 minutes, in steps of 5. */
@Composable
private fun CustomCard(minutes: Int, earn: Int, selected: Boolean, onSelect: () -> Unit, onDec: () -> Unit, onInc: () -> Unit) {
    val shape = RoundedCornerShape(AurioRadius.card)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 84.dp)
            .shadow(if (selected) 0.dp else AurioElevation.card, shape, ambientColor = AurioColors.Shadow.copy(alpha = 0.04f), spotColor = AurioColors.Shadow.copy(alpha = 0.10f))
            .clip(shape).background(if (selected) AurioColors.Ink else AurioColors.Surface)
            .selectable(selected, role = Role.RadioButton, onClick = onSelect)
            .padding(start = AurioSpacing.xl, end = AurioSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val fg = if (selected) Color.White else AurioColors.Ink
        Column(Modifier.weight(1f)) {
            Text("Custom", style = AurioType.bodyStrong, color = fg)
            Text(earnText(earn), style = AurioType.caption, color = if (selected) Color.White.copy(alpha = 0.8f) else AurioColors.GainText)
        }
        CustomStep("−", "Shorter by 5 minutes", selected, onDec)
        Text("$minutes min", style = AurioType.title, color = fg, modifier = Modifier.widthIn(min = 76.dp), textAlign = TextAlign.Center)
        CustomStep("+", "Longer by 5 minutes", selected, onInc)
    }
}

@Composable
private fun CustomStep(symbol: String, label: String, onDark: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(AurioSpacing.touchMin).clip(CircleShape)
            .background(if (onDark) Color.White.copy(alpha = 0.16f) else AurioColors.SurfaceSunken)
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(symbol, style = AurioType.title, color = if (onDark) Color.White else AurioColors.Ink) }
}

@Composable
fun FocusRunningScreen(onComplete: (earned: Int) -> Unit, onEndEarly: () -> Unit) {
    val state by AurioStore.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var finished by rememberSaveable { mutableStateOf(false) }
    // Keep showing the session for the moment between "saved as complete" and the screen change.
    var held by remember { mutableStateOf<FocusSessionEntity?>(null) }
    LaunchedEffect(state.running) { state.running?.let { held = it } }
    val running = state.running ?: held

    if (running == null) {
        if (!finished) LaunchedEffect(Unit) { onEndEarly() }
        return
    }

    val total = running.plannedMinutes * 60
    val earn = AuraScoring.potentialFocus(state.sessions.map { it.minutes }, running.plannedMinutes)
    var remaining by remember { mutableIntStateOf(secondsLeft(running.endsAt)) }

    // The end time is saved, so the countdown is always read from the clock. If the app is
    // killed, the session is completed correctly the next time it opens.
    LaunchedEffect(running.id) {
        while (true) {
            remaining = secondsLeft(running.endsAt)
            if (remaining <= 0) break
            delay(250)
        }
        finished = true
        // Correct even if the end-time alarm completed the session a moment earlier. If End won
        // the race the session is not complete, so do not celebrate it.
        val result = AurioStore.completeFocus(running)
        if (result.completed) onComplete(result.earned) else onEndEarly()
    }

    Box(Modifier.fillMaxSize()) {
        AuraField(AuraPalettes.of(state.level), Modifier.fillMaxSize(), animate = !state.settings.reduceMotion)
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = AurioSpacing.xl, vertical = AurioSpacing.xxl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("${running.plannedMinutes} minute focus", style = AurioType.bodyStrong)
            // Equal space above and below centres the ring on any screen height.
            Spacer(Modifier.weight(1f))
            Box(Modifier.size(272.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val stroke = 10.dp.toPx()
                    val inset = stroke / 2
                    val arcSize = Size(size.width - stroke, size.height - stroke)
                    drawCircle(Color.White.copy(alpha = 0.35f))
                    drawArc(Color.White.copy(alpha = 0.6f), 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
                    drawArc(AurioColors.Ink, -90f, 360f * remaining / total, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val mm = remaining / 60
                    val ss = remaining % 60
                    Text(
                        "%02d:%02d".format(mm, ss), style = AurioType.scoreLarge,
                        modifier = Modifier.semantics { contentDescription = "$mm minutes $ss seconds left" },
                    )
                    Text("left to earn +$earn", style = AurioType.body)
                }
            }
            Spacer(Modifier.height(AurioSpacing.xxxl))
            Row(horizontalArrangement = Arrangement.spacedBy(AurioSpacing.sm)) {
                Pill("Counts when complete", PillTone.ON_AURA)
                Pill("No ads", PillTone.ON_AURA)
            }
            Spacer(Modifier.weight(1f))
            Text("Ending early never costs aura", style = AurioType.caption, color = AurioColors.InkMuted)
            Spacer(Modifier.height(AurioSpacing.md))
            SecondaryButton("End session", {
                finished = true
                scope.launch {
                    val result = AurioStore.endFocusEarly(running)
                    if (result.completed) onComplete(result.earned) else onEndEarly()
                }
            }, container = Color.White.copy(alpha = 0.85f))
        }
    }
}

/**
 * Asked once, right after the first completed session, when the reason is obvious. Android 13+
 * only; older versions allow notifications by default. Answering either way hides it for good.
 */
@Composable
private fun NotificationPrompt(visible: Boolean) {
    val context = LocalContext.current
    val needed = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { AurioStore.setNotificationPromptSeen() }
    if (!visible || !needed) return
    NotificationPromptCard(
        onTurnOn = { ask.launch(Manifest.permission.POST_NOTIFICATIONS) },
        onNotNow = { AurioStore.setNotificationPromptSeen() },
    )
}

@Composable
private fun NotificationPromptCard(onTurnOn: () -> Unit, onNotNow: () -> Unit) {
    AurioCard(Modifier.padding(top = AurioSpacing.md), padding = 18.dp) {
        Text("Know when a session ends", style = AurioType.bodyStrong)
        Spacer(Modifier.height(AurioSpacing.xs))
        Text("Aurio can tell you when your timer is done, even with the app closed.", style = AurioType.caption, color = AurioColors.InkSubtle)
        Row(Modifier.fillMaxWidth().padding(top = AurioSpacing.sm), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onNotNow, modifier = Modifier.heightIn(min = AurioSpacing.touchMin)) {
                Text("Not now", style = AurioType.label, color = AurioColors.InkMuted)
            }
            TextButton(onClick = onTurnOn, modifier = Modifier.heightIn(min = AurioSpacing.touchMin)) {
                Text("Turn on notifications", style = AurioType.label, color = AurioColors.Ink)
            }
        }
    }
}

@Preview
@Composable
private fun NotificationPromptCardPreview() {
    AurioTheme { NotificationPromptCard(onTurnOn = {}, onNotNow = {}) }
}

private fun secondsLeft(endsAt: Long): Int = ((endsAt - app.aurio.domain.SystemAurioClock.nowMillis() + 999) / 1000).toInt().coerceAtLeast(0)

@Composable
fun SessionCompleteScreen(earned: Int, onHome: () -> Unit, onAgain: () -> Unit) {
    val state by AurioStore.state.collectAsStateWithLifecycle()
    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = AurioSpacing.xl, vertical = AurioSpacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(0.4f))
        Box(Modifier.size(300.dp), contentAlignment = Alignment.Center) {
            val palette = AuraPalettes.of(state.level)
            if (!state.settings.reduceMotion) ParticleBurst(listOf(palette.c1, palette.c2, palette.c3, palette.c4), Modifier.fillMaxSize())
            AuraField(palette, Modifier.size(240.dp).clip(CircleShape), animate = !state.settings.reduceMotion)
            Text("+$earned", style = AurioType.scoreHero, textAlign = TextAlign.Center)
        }
        Text("Session complete.", style = AurioType.display, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(AurioSpacing.sm))
        Text("Your aura is now ${state.score}", style = AurioType.body, color = AurioColors.InkMuted)
        Spacer(Modifier.height(AurioSpacing.xxl))
        AurioCard(padding = 18.dp) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Focus aura today", style = AurioType.label)
                Text("${state.daily.focusAura} of 100", style = AurioType.label)
            }
            Spacer(Modifier.height(AurioSpacing.sm))
            ProgressBar(state.daily.focusAura / 100f, color = AurioColors.Gain, track = AurioColors.GainBg, height = 8.dp)
            Spacer(Modifier.height(AurioSpacing.sm))
            val left = AuraScoring.MAX_FOCUS_PER_DAY - state.daily.focusAura
            Text(
                if (left > 0) "$left more focus aura available today" else "Today's focus aura is full. Sessions still count as focus time.",
                style = AurioType.caption, color = AurioColors.InkSubtle,
            )
        }
        NotificationPrompt(visible = state.loaded && !state.settings.notificationPromptSeen && state.settings.focusReminders)
        Spacer(Modifier.weight(1f))
        PrimaryButton("Back to home", onHome)
        Spacer(Modifier.height(8.dp))
        SecondaryButton("Start another", onAgain)
    }
}

/**
 * The celebration on session complete: dots in the aura's colours burst out from the orb once and
 * fade. Callers skip it when reduce motion is on.
 */
@Composable
private fun ParticleBurst(colors: List<Color>, modifier: Modifier = Modifier) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, tween(durationMillis = 1100, easing = FastOutSlowInEasing)) }
    val dots = remember {
        val r = kotlin.random.Random(11)
        List(18) { i -> Triple(i * (360f / 18) + r.nextFloat() * 12f, 0.75f + r.nextFloat() * 0.5f, i % 4) }
    }
    Canvas(modifier) {
        val p = progress.value
        val start = 0.30f * size.minDimension
        val travel = 0.22f * size.minDimension
        dots.forEach { (angle, reach, colorIndex) ->
            val rad = Math.toRadians(angle.toDouble())
            val distance = start + travel * reach * p
            val center = Offset(size.width / 2 + (cos(rad) * distance).toFloat(), size.height / 2 + (sin(rad) * distance).toFloat())
            drawCircle(colors[colorIndex].copy(alpha = (1f - p).coerceIn(0f, 1f)), radius = (7.dp.toPx() * (1f - 0.6f * p)), center = center)
        }
    }
}
