package app.aurio.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.aurio.data.AurioStore
import app.aurio.domain.AuraLevel
import app.aurio.ui.apps.AppPicker
import app.aurio.ui.components.AuraField
import app.aurio.ui.components.Pill
import app.aurio.ui.components.PillTone
import app.aurio.ui.components.PrimaryButton
import app.aurio.ui.components.SecondaryButton
import app.aurio.ui.theme.AuraPalettes
import app.aurio.ui.theme.AurioColors
import app.aurio.ui.theme.AurioRadius
import app.aurio.ui.theme.AurioSpacing
import app.aurio.ui.theme.AurioType

private const val LAST_STEP = 4

/** Five steps. The current step is saved, so closing the app never loses progress. */
@Composable
fun OnboardingFlow(step: Int, usageAccess: Boolean, onOpenUsageSettings: () -> Unit) {
    val s = step.coerceIn(0, LAST_STEP)
    BackHandler(enabled = s > 0) { AurioStore.setOnboardingStep(s - 1) }

    Box(Modifier.fillMaxSize().background(AurioColors.Paper)) {
        when (s) {
            0 -> Welcome()
            1 -> HowItWorks()
            2 -> UsageAccessStep(usageAccess, onOpenUsageSettings)
            3 -> PickAppsStep()
            else -> NameStep()
        }
    }
}

@Composable
private fun StepFrame(step: Int, title: String, subtitle: String, content: @Composable (Modifier) -> Unit) {
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding().padding(horizontal = AurioSpacing.xl, vertical = AurioSpacing.xxl)) {
        Text("Step ${step + 1} of 5", style = AurioType.caption, color = AurioColors.InkSubtle)
        Spacer(Modifier.height(AurioSpacing.md))
        Text(title, style = AurioType.display)
        Spacer(Modifier.height(8.dp))
        Text(subtitle, style = AurioType.body, color = AurioColors.InkMuted)
        Spacer(Modifier.height(AurioSpacing.xl))
        content(Modifier.weight(1f))
    }
}

@Composable
private fun Welcome() {
    Column(Modifier.fillMaxSize()) {
        AuraField(AuraPalettes.of(AuraLevel.RADIANT), Modifier.fillMaxWidth().weight(1f))
        Column(Modifier.navigationBarsPadding().padding(AurioSpacing.xl)) {
            Text("Your attention has an aura.", style = AurioType.display)
            Spacer(Modifier.height(10.dp))
            Text(
                "Focus builds it. Staying inside your limits protects it. Everything stays on this phone.",
                style = AurioType.body, color = AurioColors.InkMuted,
            )
            Spacer(Modifier.height(AurioSpacing.xl))
            PrimaryButton("Get started", { AurioStore.setOnboardingStep(1) })
        }
    }
}

@Composable
private fun HowItWorks() {
    StepFrame(1, "How aura works", "A score from 0 to 1000 that moves with how you use your phone.") { m ->
        Column(m, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Rule("+1", PillTone.GAIN, "Focus minute", "Finish a focus session to earn aura, up to +100 a day.")
            Rule("+20", PillTone.GAIN, "Limit kept", "Each limited app that ends the day under its limit.")
            Rule("−1", PillTone.LOSS, "Minute over", "Per minute over a limit, up to −60 for each app.")
            Rule("0", PillTone.NEUTRAL, "Everything else", "Neutral and essential apps never change your score.")
            Spacer(Modifier.weight(1f))
            PrimaryButton("Continue", { AurioStore.setOnboardingStep(2) })
        }
    }
}

@Composable
private fun Rule(amount: String, tone: PillTone, title: String, body: String) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(AurioRadius.card)).background(AurioColors.Surface).padding(AurioSpacing.lg),
        horizontalArrangement = Arrangement.spacedBy(AurioSpacing.md),
    ) {
        Pill(amount, tone, minWidth = 52.dp)
        Column {
            Text(title, style = AurioType.bodyStrong)
            Text(body, style = AurioType.caption, color = AurioColors.InkMuted)
        }
    }
}

@Composable
private fun UsageAccessStep(granted: Boolean, onOpen: () -> Unit) {
    StepFrame(2, "Let Aurio see time, not content", "Android calls this usage access. It reports how long each app was open.") { m ->
        Column(m, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(AurioRadius.card)).background(AurioColors.Surface).padding(AurioSpacing.lg)) {
                Text("Aurio sees", style = AurioType.bodyStrong, color = AurioColors.GainText)
                Text("Which app is open, and for how long.", style = AurioType.body)
                Spacer(Modifier.height(AurioSpacing.md))
                Text("Aurio never sees", style = AurioType.bodyStrong)
                Text("Messages, photos, or what you watch and post. It has no internet permission, so nothing can leave this phone.", style = AurioType.body)
            }
            Pill(if (granted) "Usage access is on" else "Usage access is off", if (granted) PillTone.GAIN else PillTone.UNKNOWN)
            Text(
                if (granted) "You're set." else "Find Aurio in the list and switch it on, then come back. You can skip this. Limits stay uncounted and never cost aura until usage access is on.",
                style = AurioType.caption, color = AurioColors.InkMuted,
            )
            Spacer(Modifier.weight(1f))
            if (!granted) {
                PrimaryButton("Open usage access settings", onOpen)
                SecondaryButton("Skip for now", { AurioStore.setOnboardingStep(3) })
            } else {
                PrimaryButton("Continue", { AurioStore.setOnboardingStep(3) })
            }
        }
    }
}

@Composable
private fun PickAppsStep() {
    StepFrame(3, "Choose your apps", "Mark the apps you want to limit. Neutral and essential apps are shown but never change your score.") { m ->
        AppPicker(
            initial = emptyMap(), confirmText = "Continue",
            onConfirm = { chosen ->
                AurioStore.saveApps(chosen)
                AurioStore.setOnboardingStep(4)
            },
            modifier = m,
        )
    }
}

@Composable
private fun NameStep() {
    var name by rememberSaveable { mutableStateOf("") }
    StepFrame(4, "What should we call you?", "Only used on this phone. You can change it later in Profile.") { m ->
        Column(m) {
            TextField(
                value = name, onValueChange = { name = it.take(30) }, singleLine = true,
                placeholder = { Text("Your name", style = AurioType.body, color = AurioColors.InkSubtle) },
                shape = RoundedCornerShape(AurioRadius.tile),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = AurioColors.Surface, unfocusedContainerColor = AurioColors.Surface,
                    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(AurioSpacing.md))
            Text("You start at 0 aura. Your first day is marked partial while Aurio learns your habits.", style = AurioType.caption, color = AurioColors.InkSubtle)
            Spacer(Modifier.weight(1f))
            PrimaryButton("Finish", { AurioStore.finishOnboarding(name) })
        }
    }
}
