package app.aurio.ui.apps

import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.aurio.domain.AppCategory
import app.aurio.ui.components.AppTile
import app.aurio.ui.components.PrimaryButton
import app.aurio.ui.theme.AurioColors
import app.aurio.ui.theme.AurioRadius
import app.aurio.ui.theme.AurioSpacing
import app.aurio.ui.theme.AurioType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class Installed(val pkg: String, val label: String)

private suspend fun loadInstalled(pm: PackageManager, self: String): List<Installed> = withContext(Dispatchers.Default) {
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    pm.queryIntentActivities(intent, 0)
        .map { Installed(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
        .filter { it.pkg != self }
        .distinctBy { it.pkg }
        .sortedBy { it.label.lowercase() }
}

private val choices = listOf(
    AppCategory.LIMITED to "Limited",
    AppCategory.NEUTRAL to "Neutral",
    AppCategory.ESSENTIAL to "Essential",
)

/**
 * Pick which installed apps Aurio tracks and how each one counts.
 * Limited: scored against a daily limit. Neutral and essential: shown, never change aura.
 */
@Composable
fun AppPicker(
    initial: Map<String, Pair<String, AppCategory>>,
    confirmText: String,
    onConfirm: (Map<String, Pair<String, AppCategory>>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var installed by remember { mutableStateOf<List<Installed>?>(null) }
    var query by remember { mutableStateOf("") }
    val picked = remember { mutableStateMapOf<String, Pair<String, AppCategory>>().apply { putAll(initial) } }

    LaunchedEffect(Unit) { installed = loadInstalled(context.packageManager, context.packageName) }

    Column(modifier) {
        TextField(
            value = query, onValueChange = { query = it }, singleLine = true,
            placeholder = { Text("Search apps", style = AurioType.body, color = AurioColors.InkSubtle) },
            shape = RoundedCornerShape(AurioRadius.tile),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = AurioColors.Surface, unfocusedContainerColor = AurioColors.Surface,
                focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(AurioSpacing.md))
        val list = installed
        if (list == null) {
            Text("Loading your apps…", style = AurioType.body, color = AurioColors.InkMuted, modifier = Modifier.weight(1f))
        } else {
            val shown = list.filter { it.label.contains(query.trim(), ignoreCase = true) }
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(shown, key = { it.pkg }) { app ->
                    val current = picked[app.pkg]?.second
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(AurioRadius.card))
                            .background(AurioColors.Surface).padding(AurioSpacing.md),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AppTile(app.label, packageName = app.pkg)
                            Spacer(Modifier.width(AurioSpacing.md))
                            Text(app.label, style = AurioType.bodyStrong, modifier = Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(AurioSpacing.sm))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            choices.forEach { (category, label) ->
                                val selected = current == category
                                Text(
                                    label, style = AurioType.label,
                                    color = if (selected) Color.White else AurioColors.Ink,
                                    modifier = Modifier
                                        .heightIn(min = AurioSpacing.touchMin)
                                        .clip(CircleShape)
                                        .background(if (selected) AurioColors.Ink else AurioColors.SurfaceSunken)
                                        .selectable(selected, role = Role.RadioButton) {
                                            // Tapping the chosen option again stops tracking the app.
                                            if (selected) picked.remove(app.pkg) else picked[app.pkg] = app.label to category
                                        }
                                        .padding(horizontal = 14.dp, vertical = 13.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(AurioSpacing.md))
        val limited = picked.values.count { it.second == AppCategory.LIMITED }
        Text(
            if (picked.isEmpty()) "Choose at least the apps you want a limit on."
            else "${picked.size} tracked, $limited with a limit",
            style = AurioType.caption, color = AurioColors.InkSubtle,
        )
        Spacer(Modifier.height(AurioSpacing.sm))
        PrimaryButton(confirmText, { onConfirm(picked.toMap()) })
    }
}
