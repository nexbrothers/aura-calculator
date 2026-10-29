package app.aurio.ui.limits

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.ContextCompat
import app.aurio.ui.theme.AurioTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aurio.data.AurioState
import app.aurio.data.AurioStore
import app.aurio.domain.AppCategory
import app.aurio.domain.AppUsage
import app.aurio.domain.AuraScoring
import app.aurio.domain.LimitStatus
import app.aurio.ui.components.AppTile
import app.aurio.ui.components.AurioCard
import app.aurio.ui.components.LimitStepper
import app.aurio.ui.components.NotCounted
import app.aurio.ui.components.NotCountedNotice
import app.aurio.ui.components.Pill
import app.aurio.ui.components.PillTabs
import app.aurio.ui.components.PillTone
import app.aurio.ui.components.ProgressBar
import app.aurio.ui.components.RowDivider
import app.aurio.ui.components.SecondaryButton
import app.aurio.ui.components.limitCaption
import app.aurio.ui.components.signed
import app.aurio.ui.tabs.tomorrowNote
import app.aurio.ui.theme.AurioColors
import app.aurio.ui.theme.AurioElevation
import app.aurio.ui.theme.AurioRadius
import app.aurio.ui.theme.AurioSpacing
import app.aurio.ui.theme.AurioType

private val categories = listOf(AppCategory.LIMITED to "Limited", AppCategory.NEUTRAL to "Neutral", AppCategory.ESSENTIAL to "Essential")

/**
 * Limits, as the approved design lays it out: one card per limited app (icon, today's use, one
 * status pill, the bar, then the daily limit stepper), neutral and essential apps as a quiet list,
 * and how limits score at the end.
 */
@Composable
fun LimitsScreen(onManageApps: () -> Unit, onOpenApp: (String) -> Unit = {}, onTurnOnAccess: () -> Unit = {}) {
    val state by AurioStore.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val category = categories[tab].first
    val apps = state.apps.filter { it.category == category }

    Column(
        Modifier.fillMaxSize().background(AurioColors.Paper).statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(start = AurioSpacing.xl, end = AurioSpacing.xl, top = AurioSpacing.md, bottom = AurioSpacing.xxl),
        verticalArrangement = Arrangement.spacedBy(AurioSpacing.lg),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Limits", style = AurioType.display, modifier = Modifier.weight(1f).semantics { heading() })
            ManageButton(onManageApps)
        }
        PillTabs(categories.map { it.second }, tab, onSelect = { tab = it })
        if (category == AppCategory.LIMITED) state.notCounted?.let { NotCountedNotice(it, onTurnOnAccess) }
        LimitWarningPrompt(
            visible = category == AppCategory.LIMITED && apps.isNotEmpty() && state.usageAccess &&
                state.settings.limitWarnings && !state.settings.limitPromptSeen,
        )

        when {
            apps.isEmpty() -> EmptyCard(categories[tab].second, onManageApps)
            category == AppCategory.LIMITED -> AurioCard(padding = AurioSpacing.lg) {
                // One card for all limited apps, rows split by hairlines, like the other tabs.
                apps.forEachIndexed { i, app ->
                    LimitItem(app, state, onOpenApp)
                    if (i < apps.lastIndex) RowDivider()
                }
            }
            else -> OtherAppsCard(apps, state, onOpenApp)
        }
        RulesCard()
    }
}

/**
 * Asked once, the first time Limits shows limited apps, when the reason is obvious. Android 13+
 * only; older versions allow notifications by default. Answering either way hides it for good.
 */
@Composable
private fun LimitWarningPrompt(visible: Boolean) {
    val context = LocalContext.current
    val needed = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { AurioStore.setLimitPromptSeen() }
    if (!visible || !needed) return
    LimitWarningPromptCard(
        onTurnOn = { ask.launch(Manifest.permission.POST_NOTIFICATIONS) },
        onNotNow = { AurioStore.setLimitPromptSeen() },
    )
}

@Composable
private fun LimitWarningPromptCard(onTurnOn: () -> Unit, onNotNow: () -> Unit) {
    AurioCard {
        Text("Know when a limit is close", style = AurioType.bodyStrong)
        Spacer(Modifier.height(AurioSpacing.xs))
        Text(
            "Waura can tell you when a limited app has 5 minutes left today, and when its limit is reached. Worked out on this phone.",
            style = AurioType.caption, color = AurioColors.InkSubtle,
        )
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
private fun LimitWarningPromptCardPreview() {
    AurioTheme { LimitWarningPromptCard(onTurnOn = {}, onNotNow = {}) }
}

@Composable
private fun ManageButton(onClick: () -> Unit) {
    Row(
        Modifier.heightIn(min = 40.dp)
            .shadow(AurioElevation.card / 3, CircleShape)
            .clip(CircleShape).background(AurioColors.Surface)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = AurioSpacing.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) { Text("Manage apps", style = AurioType.label) }
}

/** What a limited app will do to today's aura, as one pill: the one status the card shows. */
@Composable
private fun ImpactPill(usage: AppUsage, state: AurioState) {
    when (val s = AuraScoring.limitStatus(usage)) {
        is LimitStatus.Under -> Pill("+${s.projectedBonus} at midnight", PillTone.PROJECTED)
        is LimitStatus.Over -> Pill("${signed(-s.penalty)} today", PillTone.LOSS)
        LimitStatus.NotScored -> Pill("Never changes aura", PillTone.NEUTRAL)
        LimitStatus.Unknown -> Pill(NotCounted.pill(state.notCounted), PillTone.UNKNOWN)
    }
}

@Composable
private fun LimitItem(app: AppUsage, state: AurioState, onOpenApp: (String) -> Unit) {
    val usage = if (state.usageAccess) app else app.copy(usedMinutes = null)
    val saved = state.tracked[app.packageName]
    val measured = state.measuredMinutes[app.packageName]
    val used = usage.usedMinutes ?: measured
    val over = AuraScoring.limitStatus(usage) is LimitStatus.Over

    Column(Modifier.fillMaxWidth().padding(vertical = AurioSpacing.md)) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(AurioRadius.tile))
                .clickable(role = Role.Button, onClickLabel = "Open ${app.label} details") { onOpenApp(app.packageName) },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppTile(app.label, packageName = app.packageName)
            Spacer(Modifier.width(AurioSpacing.md))
            Column(Modifier.weight(1f)) {
                Text(app.label, style = AurioType.bodyStrong, maxLines = 1)
                Text(limitCaption(usage.usedMinutes, measured, app.limitMinutes), style = AurioType.caption, color = AurioColors.InkSubtle)
            }
            Spacer(Modifier.width(AurioSpacing.sm))
            ImpactPill(usage, state)
        }
        Spacer(Modifier.height(AurioSpacing.md))
        ProgressBar(
            fraction = (used ?: 0).toFloat() / app.limitMinutes,
            color = when {
                over -> AurioColors.Loss
                usage.usedMinutes == null -> AurioColors.InkSubtle
                else -> AurioColors.Ink
            },
            track = if (over) AurioColors.LossBg else AurioColors.Track,
        )
        val savedLimit = saved?.limitMinutes
        if (saved?.category == AppCategory.LIMITED && savedLimit != null) {
            Spacer(Modifier.height(AurioSpacing.sm))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Daily limit", style = AurioType.label, color = AurioColors.InkSubtle, modifier = Modifier.weight(1f))
                LimitStepper(
                    savedLimit, app.label,
                    onDec = { AurioStore.setLimit(app.packageName, savedLimit - 5) },
                    onInc = { AurioStore.setLimit(app.packageName, savedLimit + 5) },
                )
            }
        }
        tomorrowNote(app, saved)?.let { Text(it, style = AurioType.caption, color = AurioColors.InkSubtle) }
    }
}

/** Neutral and essential apps: their time today, and the reminder that they never change aura. */
@Composable
private fun OtherAppsCard(apps: List<AppUsage>, state: AurioState, onOpenApp: (String) -> Unit) {
    AurioCard(padding = AurioSpacing.lg) {
        apps.forEachIndexed { i, app ->
            val minutes = (if (state.usageAccess) app.usedMinutes else null) ?: state.measuredMinutes[app.packageName]
            Row(
                Modifier.fillMaxWidth()
                    .clickable(role = Role.Button, onClickLabel = "Open ${app.label} details") { onOpenApp(app.packageName) }
                    .padding(vertical = AurioSpacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppTile(app.label, packageName = app.packageName)
                Spacer(Modifier.width(AurioSpacing.md))
                Column(Modifier.weight(1f)) {
                    Text(app.label, style = AurioType.bodyStrong, maxLines = 1)
                    Text(
                        if (minutes != null) "$minutes min today" else NotCounted.pill(state.notCounted),
                        style = AurioType.caption, color = AurioColors.InkSubtle,
                    )
                }
                Pill("Never changes aura", PillTone.NEUTRAL)
            }
            tomorrowNote(app, state.tracked[app.packageName])?.let {
                Text(it, style = AurioType.caption, color = AurioColors.InkSubtle, modifier = Modifier.padding(bottom = AurioSpacing.sm))
            }
            if (i < apps.lastIndex) RowDivider()
        }
    }
}

@Composable
private fun EmptyCard(categoryLabel: String, onManageApps: () -> Unit) {
    AurioCard {
        Text(
            when (categoryLabel) {
                "Limited" -> "No limited apps yet. Pick the apps you want a daily limit on; each one you stay under earns +20 aura at midnight."
                else -> "No ${categoryLabel.lowercase()} apps yet. Apps here are tracked but never change aura."
            },
            style = AurioType.body, color = AurioColors.InkMuted,
        )
        Spacer(Modifier.height(AurioSpacing.md))
        SecondaryButton("Choose apps", onManageApps, container = AurioColors.SurfaceSunken)
    }
}

@Composable
private fun RulesCard() {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(AurioRadius.card)).background(AurioColors.SurfaceSunken).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(AurioSpacing.sm),
    ) {
        Text("How limits score", style = AurioType.bodyStrong)
        RuleRow("+20", PillTone.GAIN, "Day ends under an app's limit")
        RuleRow("−1", PillTone.LOSS, "Per minute over a limit, up to −60 for each app")
        RuleRow("0", PillTone.NEUTRAL, "Neutral and essential apps")
        RuleRow("0", PillTone.UNKNOWN, "Not counted while usage access is off")
    }
}

@Composable
private fun RuleRow(amount: String, tone: PillTone, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Pill(amount, tone, minWidth = 48.dp)
        Spacer(Modifier.width(AurioSpacing.sm))
        Text(text, style = AurioType.caption)
    }
}
