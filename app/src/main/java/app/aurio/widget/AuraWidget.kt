package app.aurio.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import app.aurio.MainActivity
import app.aurio.data.AurioState
import app.aurio.data.AurioStore
import app.aurio.data.NotCountedReason
import app.aurio.ui.components.NotCounted
import app.aurio.domain.AppUsage
import app.aurio.domain.AuraScoring
import app.aurio.domain.LimitStatus
import app.aurio.domain.WidgetPicks
import app.aurio.ui.aura.AuraPainter
import app.aurio.ui.components.signed
import app.aurio.ui.theme.AuraPalettes
import app.aurio.ui.theme.AurioColors
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

// Home-screen widgets. They show the same numbers as Home because they read the same state.
// The 2x2 shows only the aura and score; the 4x1 and 4x2 also show limited apps' time today,
// which the design brief asks for, and nothing else.

private val ink = ColorProvider(AurioColors.Ink)
private val subtle = ColorProvider(AurioColors.InkSubtle)

/** The app's state once loaded, or null if it is not ready in time (the widget then says so). */
private suspend fun loadState(): AurioState? = withTimeoutOrNull(5_000) { AurioStore.state.first { it.loaded } }

private fun openApp(context: Context, destination: String? = null) = actionStartActivity(
    Intent(context, MainActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        .apply { if (destination != null) putExtra(MainActivity.EXTRA_OPEN, destination) },
)

private fun limitText(app: AppUsage, notCounted: NotCountedReason?): String = when (val s = AuraScoring.limitStatus(app)) {
    is LimitStatus.Under -> "${s.minutesLeft} min left"
    is LimitStatus.Over -> "${s.minutesOver} min over"
    LimitStatus.Unknown -> NotCounted.pill(notCounted)
    LimitStatus.NotScored -> "Not scored"
}

private fun limitColor(app: AppUsage): ColorProvider =
    if (AuraScoring.limitStatus(app) is LimitStatus.Over) ColorProvider(AurioColors.LossText) else subtle

private fun todayColor(change: Int) = ColorProvider(
    when {
        change > 0 -> AurioColors.GainText
        change < 0 -> AurioColors.LossText
        else -> AurioColors.InkSubtle
    },
)

/** Usage as Home shows it: Unknown when access is off. */
private fun AurioState.visibleApps(): List<AppUsage> = if (usageAccess) apps else apps.map { it.copy(usedMinutes = null) }

@Composable
private fun NotReady(context: Context) {
    Box(
        GlanceModifier.fillMaxSize().cornerRadius(28.dp).background(AurioColors.Surface).padding(14.dp)
            .clickable(openApp(context)),
        contentAlignment = Alignment.Center,
    ) { Text("Open Aurio to load your aura", style = TextStyle(color = ink, fontSize = 13.sp)) }
}

// ---------- 2x2: aura, level, score ----------

class AuraWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val s = loadState()
        val aura = s?.let { AuraPainter.bitmap(AuraPalettes.of(it.level), 360, 360) }
        provideContent {
            if (s == null || aura == null) NotReady(context) else {
                Box(GlanceModifier.fillMaxSize().cornerRadius(28.dp).clickable(openApp(context))) {
                    Image(ImageProvider(aura), contentDescription = null, contentScale = ContentScale.Crop, modifier = GlanceModifier.fillMaxSize())
                    Column(GlanceModifier.fillMaxSize().padding(14.dp)) {
                        Text(s.level.label, style = TextStyle(color = ink, fontSize = 13.sp, fontWeight = FontWeight.Medium))
                        Spacer(GlanceModifier.defaultWeight())
                        Text("${s.score}", style = TextStyle(color = ink, fontSize = 48.sp, fontWeight = FontWeight.Bold))
                        Text("${signed(s.daily.total)} today", style = TextStyle(color = todayColor(s.daily.total), fontSize = 12.sp, fontWeight = FontWeight.Medium))
                    }
                }
            }
        }
    }
}

// ---------- 4x1: closest limit and Start focus ----------

class LimitWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val s = loadState()
        val aura = s?.let { AuraPainter.bitmap(AuraPalettes.of(it.level), 160, 160) }
        provideContent {
            if (s == null || aura == null) NotReady(context) else {
                val closest = WidgetPicks.closestLimit(s.visibleApps())
                Row(
                    GlanceModifier.fillMaxSize().cornerRadius(28.dp).background(AurioColors.Surface).padding(horizontal = 12.dp, vertical = 8.dp)
                        .clickable(openApp(context)),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(GlanceModifier.width(44.dp).height(44.dp).cornerRadius(22.dp), contentAlignment = Alignment.Center) {
                        Image(ImageProvider(aura), contentDescription = null, contentScale = ContentScale.Crop, modifier = GlanceModifier.fillMaxSize())
                        Text("${s.score}", style = TextStyle(color = ink, fontSize = 13.sp, fontWeight = FontWeight.Bold))
                    }
                    Spacer(GlanceModifier.width(10.dp))
                    Column(GlanceModifier.defaultWeight()) {
                        if (closest == null) {
                            Text("No limits set", style = TextStyle(color = ink, fontSize = 14.sp, fontWeight = FontWeight.Medium))
                            Text("Choose apps in Aurio", style = TextStyle(color = subtle, fontSize = 12.sp))
                        } else {
                            Text(closest.label, style = TextStyle(color = ink, fontSize = 14.sp, fontWeight = FontWeight.Medium), maxLines = 1)
                            Text(limitText(closest, s.notCounted), style = TextStyle(color = limitColor(closest), fontSize = 12.sp))
                        }
                    }
                    Box(
                        GlanceModifier.height(40.dp).cornerRadius(20.dp).background(AurioColors.Ink).padding(horizontal = 14.dp)
                            .clickable(openApp(context, MainActivity.OPEN_FOCUS)),
                        contentAlignment = Alignment.Center,
                    ) { Text("Start focus", style = TextStyle(color = ColorProvider(Color.White), fontSize = 13.sp, fontWeight = FontWeight.Medium)) }
                }
            }
        }
    }
}

// ---------- 4x2: score and today's limits ----------

class LimitsWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val s = loadState()
        val aura = s?.let { AuraPalettes.of(it.level) }?.let { AuraPainter.bitmap(it, 300, 360) }
        provideContent {
            if (s == null || aura == null) NotReady(context) else {
                val limits = WidgetPicks.limitsByUrgency(s.visibleApps()).take(3)
                Row(GlanceModifier.fillMaxSize().cornerRadius(28.dp).background(AurioColors.Surface).clickable(openApp(context))) {
                    Box(GlanceModifier.width(120.dp).fillMaxHeight()) {
                        Image(ImageProvider(aura), contentDescription = null, contentScale = ContentScale.Crop, modifier = GlanceModifier.fillMaxSize())
                        Column(GlanceModifier.fillMaxSize().padding(12.dp)) {
                            Text(s.level.label, style = TextStyle(color = ink, fontSize = 12.sp, fontWeight = FontWeight.Medium))
                            Spacer(GlanceModifier.defaultWeight())
                            Text("${s.score}", style = TextStyle(color = ink, fontSize = 36.sp, fontWeight = FontWeight.Bold))
                            Text("${signed(s.daily.total)} today", style = TextStyle(color = todayColor(s.daily.total), fontSize = 11.sp, fontWeight = FontWeight.Medium))
                        }
                    }
                    Column(GlanceModifier.defaultWeight().fillMaxHeight().padding(horizontal = 14.dp, vertical = 12.dp)) {
                        Text("Today's limits", style = TextStyle(color = ink, fontSize = 13.sp, fontWeight = FontWeight.Bold))
                        Spacer(GlanceModifier.height(6.dp))
                        if (limits.isEmpty()) {
                            Text("No limits set. Choose apps in Aurio.", style = TextStyle(color = subtle, fontSize = 12.sp))
                        }
                        limits.forEach { app ->
                            Row(GlanceModifier.fillMaxWidth().padding(vertical = 3.dp)) {
                                Text(app.label, style = TextStyle(color = ink, fontSize = 13.sp), maxLines = 1, modifier = GlanceModifier.defaultWeight())
                                Text(limitText(app, s.notCounted), style = TextStyle(color = limitColor(app), fontSize = 12.sp, fontWeight = FontWeight.Medium))
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------- Receivers and refresh ----------

/**
 * The periodic widget update (about every 30 minutes) also reads usage, so widgets stay current
 * when Aurio is closed. Refreshes after that come from [Widgets.refreshAll], not from here.
 */
abstract class AurioWidgetReceiver : GlanceAppWidgetReceiver() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        AurioStore.sync(context.applicationContext)
        super.onUpdate(context, appWidgetManager, appWidgetIds)
    }
}

class AuraWidgetReceiver : AurioWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = AuraWidget()
}

class LimitWidgetReceiver : AurioWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = LimitWidget()
}

class LimitsWidgetReceiver : AurioWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = LimitsWidget()
}

object Widgets {
    /** Redraws every placed Aurio widget. Cheap when none are placed. */
    suspend fun refreshAll(context: Context) {
        try {
            AuraWidget().updateAll(context)
            LimitWidget().updateAll(context)
            LimitsWidget().updateAll(context)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("Widgets", "Widget refresh failed", e)
        }
    }
}
