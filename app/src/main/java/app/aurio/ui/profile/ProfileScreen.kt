package app.aurio.ui.profile

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.text.format.DateFormat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.compose.runtime.mutableIntStateOf
import app.aurio.ui.components.ValueStepper
import app.aurio.domain.DailyRecap
import java.util.Calendar
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aurio.data.AurioStore
import app.aurio.ui.components.AuraField
import app.aurio.ui.components.AurioCard
import app.aurio.ui.components.AurioDialog
import app.aurio.ui.components.AurioIcons
import app.aurio.ui.components.StrokeIcon
import app.aurio.ui.components.CardHeader
import app.aurio.ui.components.Pill
import app.aurio.ui.components.PillTabs
import app.aurio.ui.components.PillTone
import app.aurio.ui.components.RowDivider
import app.aurio.ui.theme.AuraPalettes
import app.aurio.ui.theme.AurioColors
import app.aurio.ui.theme.AurioRadius
import app.aurio.ui.theme.AurioSpacing
import app.aurio.ui.theme.AurioType
import kotlinx.coroutines.launch

private val sessionLengths = listOf(25, 50, 90)

/**
 * Profile, as the approved design lays it out: who you are and your aura, privacy first (usage
 * access, what Aurio sees and never sees), preferences, then your data.
 */
@Composable
fun ProfileScreen(onOpenUsageSettings: () -> Unit, onDeveloperTools: () -> Unit = {}) {
    val state by AurioStore.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var editName by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val debuggable = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    val version = remember { context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty() }
    var editQuiet by remember { mutableStateOf(false) }

    // Notification and exact-alarm permissions change in Android settings, so re-read them on return.
    var resumes by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        resumes++
        onPauseOrDispose { }
    }
    val notificationsOn = remember(resumes) { NotificationManagerCompat.from(context).areNotificationsEnabled() }
    val exactOn = remember(resumes) {
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
    }

    Column(
        Modifier.fillMaxSize().background(AurioColors.Paper).statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(start = AurioSpacing.xl, end = AurioSpacing.xl, top = AurioSpacing.md, bottom = AurioSpacing.xxl),
        verticalArrangement = Arrangement.spacedBy(AurioSpacing.lg),
    ) {
        Text("Profile", style = AurioType.display, modifier = Modifier.semantics { heading() })

        // Who you are: your aura, name, level and score.
        Row(verticalAlignment = Alignment.CenterVertically) {
            AuraField(AuraPalettes.of(state.level), Modifier.size(56.dp).clip(CircleShape), animate = false)
            Spacer(Modifier.width(AurioSpacing.md))
            Column(Modifier.weight(1f)) {
                Text(state.userName.ifBlank { "Add your name" }, style = AurioType.heading, maxLines = 1)
                Text("${state.level.label}, ${state.score} aura", style = AurioType.label, color = AurioColors.InkSubtle)
            }
            TextButton(onClick = { editName = true }, modifier = Modifier.heightIn(min = AurioSpacing.touchMin)) {
                Text("Edit", style = AurioType.label, color = AurioColors.InkSubtle)
            }
        }

        AurioCard(padding = AurioSpacing.lg) {
            CardHeader("Privacy")
            NavRow(
                title = "Usage access",
                subtitle = if (state.usageAccess) "Waura can see how long apps are open" else "Your limits are not being tracked",
                trailing = { Pill(if (state.usageAccess) "On" else "Off", if (state.usageAccess) PillTone.GAIN else PillTone.UNKNOWN) },
                onClickLabel = "Change usage access in Settings",
                onClick = onOpenUsageSettings,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(AurioSpacing.sm)) {
            SeesCard("Waura sees", "Which app is open\nFor how long\nYour focus sessions", AurioColors.GainBg, AurioColors.GainText, Modifier.weight(1f))
            SeesCard("Never sees", "Messages\nPhotos\nWhat you watch or post", AurioColors.UnknownBg, AurioColors.Ink, Modifier.weight(1f))
        }

        AurioCard(padding = AurioSpacing.lg) {
            CardHeader("Preferences")
            Text("Default focus length", style = AurioType.bodyStrong)
            Spacer(Modifier.height(AurioSpacing.sm))
            PillTabs(
                sessionLengths.map { "$it min" },
                selected = sessionLengths.indexOf(state.settings.defaultFocusMinutes).coerceAtLeast(0),
                onSelect = { AurioStore.setDefaultFocusMinutes(sessionLengths[it]) },
            )
            Spacer(Modifier.height(AurioSpacing.sm))
            RowDivider()
            ToggleRow("Reduce motion", "Stops the moving aura and celebrations", state.settings.reduceMotion) { AurioStore.setReduceMotion(it) }
        }

        AurioCard(padding = AurioSpacing.lg) {
            CardHeader("Notifications")
            if (!notificationsOn) {
                NavRow(
                    "Notifications are off", "Nothing below can reach you until you turn them on in Android settings",
                    trailing = { Pill("Off", PillTone.UNKNOWN) },
                    onClickLabel = "Open notification settings",
                    onClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                        )
                    },
                )
                RowDivider()
            }
            ToggleRow("Session complete", "When a focus session ends", state.settings.focusReminders) { AurioStore.setFocusReminders(it) }
            RowDivider()
            ToggleRow("Limit warnings", "When a limited app has 5 min left, and when its limit is reached", state.settings.limitWarnings) {
                AurioStore.setLimitWarnings(it)
            }
            RowDivider()
            val recapAt = clockText(context, DailyRecap.minuteOfDay(state.settings.quietStartMinute, state.settings.quietEndMinute))
            ToggleRow("Daily recap", "Yesterday's aura, each morning after $recapAt", state.settings.dailyRecap) {
                AurioStore.setDailyRecap(it)
            }
            RowDivider()
            NavRow(
                "Quiet hours", quietHoursText(context, state.settings.quietStartMinute, state.settings.quietEndMinute),
                onClickLabel = "Change quiet hours", onClick = { editQuiet = true },
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                RowDivider()
                NavRow(
                    "Precise timing",
                    if (exactOn) "Alerts arrive on the minute" else "Alerts may arrive a few minutes late",
                    trailing = { Pill(if (exactOn) "On" else "Off", if (exactOn) PillTone.GAIN else PillTone.UNKNOWN) },
                    onClickLabel = "Change precise timing in Settings",
                    onClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")),
                        )
                    },
                )
            }
        }

        AurioCard(padding = AurioSpacing.lg) {
            CardHeader("Your data")
            NavRow("Export my data", "Scores, tracked apps, and focus sessions. You choose where it goes.", onClickLabel = "Export my data", onClick = {
                scope.launch {
                    val json = AurioStore.exportJson()
                    val send = Intent(Intent.ACTION_SEND).setType("text/plain")
                        .putExtra(Intent.EXTRA_SUBJECT, "Waura data export")
                        .putExtra(Intent.EXTRA_TEXT, json)
                    context.startActivity(Intent.createChooser(send, "Export Waura data"))
                }
            })
            if (debuggable) {
                RowDivider()
                NavRow("Developer tools", "Debug builds only", onClickLabel = "Open developer tools", onClick = onDeveloperTools)
            }
            RowDivider()
            NavRow(
                "Delete all data", "Erases everything from this phone", titleColor = AurioColors.LossText,
                onClickLabel = "Delete all data", onClick = { confirmDelete = true },
            )
        }

        Text(
            "Waura $version. Everything stays on this phone.",
            style = AurioType.caption, color = AurioColors.InkSubtle, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
        )
    }

    if (editName) {
        var draft by remember { mutableStateOf(state.userName) }
        AurioDialog(
            title = "Your name",
            body = "Only used on this phone.",
            confirmLabel = "Save",
            onConfirm = { AurioStore.setName(draft); editName = false },
            dismissLabel = "Cancel",
            onDismiss = { editName = false },
            content = {
                TextField(
                    draft, { draft = it.take(30) }, singleLine = true,
                    shape = RoundedCornerShape(AurioRadius.tile),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = AurioColors.SurfaceSunken, unfocusedContainerColor = AurioColors.SurfaceSunken,
                        focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            },
        )
    }
    if (editQuiet) {
        var start by remember { mutableIntStateOf(state.settings.quietStartMinute) }
        var end by remember { mutableIntStateOf(state.settings.quietEndMinute) }
        AurioDialog(
            title = "Quiet hours",
            body = "Limit warnings arrive without sound between these times. Set both to the same time to turn quiet hours off.",
            confirmLabel = "Save",
            onConfirm = { AurioStore.setQuietHours(start, end); editQuiet = false },
            dismissLabel = "Cancel",
            onDismiss = { editQuiet = false },
            content = {
                Column {
                    QuietTimeRow("Starts", clockText(context, start), "quiet hours start", { start = stepMinute(start, -1) }, { start = stepMinute(start, 1) })
                    QuietTimeRow("Ends", clockText(context, end), "quiet hours end", { end = stepMinute(end, -1) }, { end = stepMinute(end, 1) })
                }
            },
        )
    }
    if (confirmDelete) {
        AurioDialog(
            title = "Delete all data?",
            body = "This erases your aura, history, apps and settings from this phone. It cannot be undone.",
            confirmLabel = "Delete everything",
            onConfirm = { confirmDelete = false; AurioStore.deleteAllData() },
            dismissLabel = "Keep my data",
            onDismiss = { confirmDelete = false },
            confirmContainer = AurioColors.Loss,
        )
    }
}

/** A tappable settings row: the whole row is the target, with a chevron (or [trailing]) on the right. */
@Composable
private fun NavRow(
    title: String,
    subtitle: String? = null,
    titleColor: Color = AurioColors.Ink,
    trailing: (@Composable () -> Unit)? = null,
    onClickLabel: String,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = AurioSpacing.touchMin).clip(RoundedCornerShape(AurioRadius.tile))
            .clickable(role = Role.Button, onClickLabel = onClickLabel, onClick = onClick)
            .padding(vertical = AurioSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = AurioType.bodyStrong, color = titleColor)
            if (subtitle != null) Text(subtitle, style = AurioType.caption, color = AurioColors.InkSubtle)
        }
        if (trailing != null) {
            Spacer(Modifier.width(AurioSpacing.sm))
            trailing()
        }
        Spacer(Modifier.width(AurioSpacing.sm))
        StrokeIcon(AurioIcons.ChevronRight, contentDescription = null, tint = AurioColors.InkSubtle, size = 18.dp)
    }
}

/** A switch row: tapping anywhere on the row toggles it, and the off state is clearly drawn. */
@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().toggleable(checked, role = Role.Switch, onValueChange = onChange).padding(vertical = AurioSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = AurioType.bodyStrong)
            Text(subtitle, style = AurioType.caption, color = AurioColors.InkSubtle)
        }
        Spacer(Modifier.width(AurioSpacing.sm))
        Switch(
            checked, onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedTrackColor = AurioColors.Ink, checkedThumbColor = AurioColors.Surface,
                uncheckedTrackColor = AurioColors.SurfaceSunken, uncheckedThumbColor = AurioColors.InkSubtle,
                uncheckedBorderColor = AurioColors.InkSubtle,
            ),
        )
    }
}

@Composable
private fun QuietTimeRow(label: String, time: String, what: String, onDec: () -> Unit, onInc: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = AurioType.bodyStrong, modifier = Modifier.weight(1f))
        ValueStepper(time, what, onDec, onInc)
    }
}

/** Quiet hours move in half-hour steps and wrap around midnight. */
private fun stepMinute(minute: Int, steps: Int): Int = (minute + steps * 30).mod(24 * 60)

/** A minute of the day in the phone's own 12- or 24-hour format. */
private fun clockText(context: Context, minuteOfDay: Int): String {
    val at = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, minuteOfDay / 60)
        set(Calendar.MINUTE, minuteOfDay % 60)
    }
    return DateFormat.getTimeFormat(context).format(at.time)
}

private fun quietHoursText(context: Context, start: Int, end: Int): String =
    if (start == end) "Off" else "Silent from ${clockText(context, start)} to ${clockText(context, end)}"

@Composable
private fun SeesCard(title: String, body: String, bg: Color, titleColor: Color, modifier: Modifier) {
    Box(modifier.clip(RoundedCornerShape(AurioRadius.card)).background(bg).padding(AurioSpacing.lg)) {
        Column {
            Text(title, style = AurioType.bodyStrong, color = titleColor)
            Spacer(Modifier.height(AurioSpacing.sm))
            Text(body, style = AurioType.caption.copy(lineHeight = AurioType.body.lineHeight), color = AurioColors.Ink)
        }
    }
}
