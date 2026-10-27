package app.aurio.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.aurio.ui.theme.AurioColors
import app.aurio.ui.theme.AurioElevation
import app.aurio.ui.theme.AurioRadius
import app.aurio.ui.theme.AurioSpacing
import app.aurio.ui.theme.AurioType

/** A white card with a soft shadow: the standard surface for a group of content. */
@Composable
fun AurioCard(
    modifier: Modifier = Modifier,
    padding: Dp = AurioSpacing.lg,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(AurioRadius.card)
    Column(
        modifier.fillMaxWidth()
            .shadow(AurioElevation.card, shape, ambientColor = AurioColors.Shadow.copy(alpha = 0.04f), spotColor = AurioColors.Shadow.copy(alpha = 0.10f))
            .background(AurioColors.Surface, shape)
            .padding(padding),
        verticalArrangement = verticalArrangement,
        content = content,
    )
}

/** A card's heading with an optional text action on the right ("Edit"). */
@Composable
fun CardHeader(title: String, actionLabel: String? = null, onAction: () -> Unit = {}) {
    // The action keeps its 48dp touch target, but a plain heading takes only its own height.
    Row(
        Modifier.fillMaxWidth().padding(bottom = if (actionLabel == null) AurioSpacing.sm else 0.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(title, style = AurioType.title)
        if (actionLabel != null) {
            TextButton(onClick = onAction, modifier = Modifier.heightIn(min = AurioSpacing.touchMin)) {
                Text(actionLabel, style = AurioType.label, color = AurioColors.InkSubtle)
            }
        }
    }
}

/** A 24-unit stroke icon from an SVG path, drawn like the design prototype's icons. */
@Composable
fun StrokeIcon(path: String, contentDescription: String?, modifier: Modifier = Modifier, tint: Color = AurioColors.Ink, size: Dp = 20.dp) {
    Icon(strokeVector(path), contentDescription = contentDescription, modifier = modifier.size(size), tint = tint)
}

private fun strokeVector(path: String): ImageVector =
    ImageVector.Builder(defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
        .addPath(
            pathData = PathParser().parsePathString(path).toNodes(),
            stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
        ).build()

/** Stroke icon paths shared across screens. */
object AurioIcons {
    const val Flame = "M12 3c1 4 5 6 5 11a5 5 0 0 1-10 0c0-3 2-4 2-7 2 1 3 3 3 5"
    const val Timer = "M12 21a8 8 0 1 0 0-16a8 8 0 0 0 0 16zM12 9v4l2 2M10 2h4"
    const val ChevronDown = "M6 9l6 6 6-6"
    const val ChevronRight = "M9 6l6 6-6 6"
    const val Info = "M12 21a9 9 0 1 0 0-18a9 9 0 0 0 0 18zM12 11v5M12 8h.01"
    const val ArrowUp = "M12 19V5M5 12l7-7 7 7"
}
