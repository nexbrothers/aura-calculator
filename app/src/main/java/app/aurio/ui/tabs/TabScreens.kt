package app.aurio.ui.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.content.Intent
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextField
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import kotlinx.coroutines.launch
import app.aurio.ui.components.SecondaryButton
import app.aurio.data.AurioStore
import app.aurio.domain.AppCategory
import app.aurio.domain.AppTerms
import app.aurio.ui.theme.AurioTheme
import androidx.compose.ui.tooling.preview.Preview
import app.aurio.domain.AppUsage
import app.aurio.domain.AuraScoring
import app.aurio.domain.LimitStatus
import app.aurio.ui.components.AuraField
import app.aurio.ui.components.GroupedList
import app.aurio.ui.components.LimitRow
import app.aurio.ui.components.NotCounted
import app.aurio.ui.components.Pill
import app.aurio.ui.components.PillTone
import app.aurio.ui.components.RowDivider
import app.aurio.ui.components.ScoreChangeRow
import app.aurio.ui.components.signed
import app.aurio.ui.theme.AuraPalettes
import app.aurio.ui.theme.AurioColors
import app.aurio.ui.theme.AurioRadius
import app.aurio.ui.theme.AurioSpacing
import app.aurio.ui.theme.AurioType

// ---------------- Limits (shared) ----------------

/**
 * What changes tomorrow when the saved settings differ from the terms scoring today:
 * a raised limit, a new category or a removed app only apply from the next day.
 */
internal fun tomorrowNote(today: AppUsage, saved: AppTerms?): String? = when {
    saved == null -> "Stops being tracked tomorrow"
    saved.category != today.category -> "Moves to ${saved.category.name.lowercase()} tomorrow"
    saved.category == AppCategory.LIMITED && saved.limitMinutes != today.limitMinutes ->
        "Limit is ${today.limitMinutes} min today, ${saved.limitMinutes} min from tomorrow"
    else -> null
}
