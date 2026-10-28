package app.aurio.ui.debug

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aurio.data.AurioStore
import app.aurio.domain.DebugTime
import app.aurio.domain.SystemAurioClock
import app.aurio.ui.components.SecondaryButton
import app.aurio.ui.theme.AurioColors
import app.aurio.ui.theme.AurioRadius
import app.aurio.ui.theme.AurioSpacing
import app.aurio.ui.theme.AurioType

/** Debug builds only. Lets you test days, limits and midnight scoring without waiting. */
@Composable
fun DebugScreen(onBack: () -> Unit, onPreviewLevelUp: () -> Unit = {}, onPreviewShareCard: () -> Unit = {}) {
    val context = LocalContext.current
    val state by AurioStore.state.collectAsStateWithLifecycle()
    var tick by remember { mutableIntStateOf(0) } // forces the labels below to refresh
    fun change(block: () -> Unit) { block(); tick++; AurioStore.sync(context) }

    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState())
            .padding(horizontal = AurioSpacing.xl, vertical = AurioSpacing.xxl),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Developer tools", style = AurioType.display)
        Text("Debug builds only. Simulated time and usage replace the real ones until you reset.", style = AurioType.body, color = AurioColors.InkMuted)

        Card("Time") {
            val now = SystemAurioClock.today()
            Text("App date: $now${if (DebugTime.enabled) " (simulated)" else ""}  [$tick]", style = AurioType.body)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryButton("+1 hour", { change { DebugTime.enabled = true; DebugTime.offsetMillis += 3_600_000L } }, Modifier.weight(1f), AurioColors.SurfaceSunken)
                SecondaryButton("+1 day", { change { DebugTime.enabled = true; DebugTime.offsetMillis += 86_400_000L } }, Modifier.weight(1f), AurioColors.SurfaceSunken)
            }
            Text("A new day settles the previous one: bonuses, penalties, streak and level-ups are saved.", style = AurioType.caption, color = AurioColors.InkSubtle)
        }

        Card("Fake usage today") {
            if (state.limitedApps.isEmpty()) Text("No limited apps yet.", style = AurioType.body)
            val day = SystemAurioClock.today()
            state.apps.forEach { app ->
                val current = DebugTime.usageByDate[day]?.get(app.packageName) ?: 0
                Text("${app.label}: $current min", style = AurioType.bodyStrong)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    listOf(-10, 10, 30).forEach { d ->
                        SecondaryButton(if (d > 0) "+$d" else "$d", {
                            change {
                                DebugTime.enabled = true
                                val m = DebugTime.usageByDate.getOrPut(day) { mutableMapOf() }
                                m[app.packageName] = (current + d).coerceAtLeast(0)
                            }
                        }, Modifier.weight(1f), AurioColors.SurfaceSunken)
                    }
                }
            }
        }

        Card("Focus") {
            SecondaryButton("Finish running session now", {
                val r = state.running
                if (r != null) change { DebugTime.enabled = true; DebugTime.offsetMillis += (r.endsAt - SystemAurioClock.nowMillis()).coerceAtLeast(0) + 1000 }
            }, container = AurioColors.SurfaceSunken)
        }

        Card("Reset") {
            SecondaryButton("Preview level-up (sample, saves nothing)", onPreviewLevelUp, container = AurioColors.SurfaceSunken)
            SecondaryButton("Preview share card (sample, saves nothing)", onPreviewShareCard, container = AurioColors.SurfaceSunken)
            SecondaryButton("Back to real time and usage", { change { DebugTime.reset() } }, container = AurioColors.SurfaceSunken)
            SecondaryButton("Delete all data and restart onboarding", { DebugTime.reset(); AurioStore.deleteAllData(); onBack() }, container = AurioColors.LossBg)
        }
        SecondaryButton("Back", onBack)
    }
}

@Composable
private fun Card(title: String, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(AurioRadius.card)).background(AurioColors.Surface).padding(AurioSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, style = AurioType.title)
        content()
    }
}
