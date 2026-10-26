package app.aurio.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import app.aurio.MainActivity
import app.aurio.data.AurioStore
import kotlinx.coroutines.flow.first
import app.aurio.domain.AuraLevel
import app.aurio.ui.components.signed
import app.aurio.ui.theme.AuraPalettes
import app.aurio.ui.theme.AurioColors

/**
 * 2x2 home-screen widget: level, score and today's change. Score data only, no usage details.
 * TODO(widget): render the grainy aura gradient as a bitmap ImageProvider, and add the 4x1 and 4x2 widgets.
 */
class AuraWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val s = AurioStore.state.first { it.loaded }
        provideContent { AuraWidgetContent(s.score, s.level, s.daily.total) }
    }
}

@Composable
private fun AuraWidgetContent(score: Int, level: AuraLevel, today: Int) {
    val palette = AuraPalettes.of(level)
    val ink = ColorProvider(AurioColors.Ink)
    Column(
        GlanceModifier.fillMaxSize().cornerRadius(28.dp).background(palette.base).padding(14.dp)
            .clickable(actionStartActivity<MainActivity>()),
    ) {
        Text(level.label, style = TextStyle(color = ink, fontSize = 13.sp, fontWeight = FontWeight.Medium))
        Spacer(GlanceModifier.defaultWeight())
        Text("$score", style = TextStyle(color = ink, fontSize = 52.sp, fontWeight = FontWeight.Bold))
        Text(
            "${signed(today)} today",
            style = TextStyle(color = ColorProvider(if (today >= 0) AurioColors.GainText else AurioColors.LossText), fontSize = 12.sp, fontWeight = FontWeight.Medium),
        )
    }
}

class AuraWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = AuraWidget()
}
