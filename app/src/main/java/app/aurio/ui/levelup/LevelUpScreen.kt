package app.aurio.ui.levelup

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.aurio.domain.AuraLevel
import app.aurio.ui.components.AuraField
import app.aurio.ui.components.PrimaryButton
import app.aurio.ui.components.SecondaryButton
import app.aurio.ui.theme.AuraPalettes
import app.aurio.ui.theme.AurioColors
import app.aurio.ui.theme.AurioSpacing
import app.aurio.ui.theme.AurioTheme
import app.aurio.ui.theme.AurioType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The level-up moment: a full-screen takeover where the aura morphs from the old level's colours
 * into the new one's. Shown once, for a level reached at a past midnight and still held.
 */
@Composable
fun LevelUpScreen(level: AuraLevel, score: Int, reduceMotion: Boolean, onDone: () -> Unit, canShare: Boolean = true) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val previous = AuraLevel.entries.getOrNull(level.ordinal - 1) ?: level
    // Start on the old colours and let AuraField cross-fade to the new ones; with reduced motion,
    // show the new level at once.
    var shown by remember { mutableStateOf(if (reduceMotion) level else previous) }
    var sharing by remember { mutableStateOf(false) }

    LaunchedEffect(level) {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        if (!reduceMotion) {
            delay(500)
            shown = level
        }
    }

    Box(Modifier.fillMaxSize()) {
        AuraField(AuraPalettes.of(shown), Modifier.fillMaxSize(), animate = !reduceMotion)
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()
                .padding(horizontal = AurioSpacing.xl, vertical = AurioSpacing.xxl),
        ) {
            Text("Level up", style = AurioType.bodyStrong)
            Spacer(Modifier.weight(1f))
            Text("You reached", style = AurioType.title)
            Text(level.label, style = AurioType.scoreLarge, modifier = Modifier.semantics { heading() })
            Spacer(Modifier.height(AurioSpacing.sm))
            Text("Aura $score", style = AurioType.display)
            Spacer(Modifier.height(AurioSpacing.md))
            Text(levelLine(level, score), style = AurioType.body, color = AurioColors.InkMuted)
            Spacer(Modifier.weight(1f))
            if (canShare) {
                PrimaryButton(if (sharing) "Preparing your card" else "Share", {
                    if (sharing) return@PrimaryButton
                    sharing = true
                    scope.launch {
                        val card = withContext(Dispatchers.Default) { ShareCard.render(context, level, score) }
                        ShareCard.share(context, card)
                        sharing = false
                    }
                })
                Spacer(Modifier.height(AurioSpacing.sm))
            }
            SecondaryButton("Continue", onDone, container = AurioColors.Surface.copy(alpha = 0.85f))
            Spacer(Modifier.height(4.dp))
        }
    }
}

/** What the new level means, in measured terms only. */
internal fun levelLine(level: AuraLevel, score: Int): String {
    val next = level.next ?: return "Halo is the top level, ${level.range.first} to ${level.range.last} aura."
    return "${level.label} is ${level.range.first} to ${level.range.last} aura. ${next.range.first - score} more to ${next.label}."
}

@Preview(widthDp = 360, heightDp = 800, showBackground = true)
@Composable
private fun LevelUpPreview() {
    AurioTheme { LevelUpScreen(AuraLevel.HALO, 812, reduceMotion = true, onDone = {}) }
}
