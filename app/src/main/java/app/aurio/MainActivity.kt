package app.aurio

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app.aurio.data.AurioStore
import app.aurio.data.UsageAccess
import app.aurio.ui.AurioApp
import app.aurio.ui.theme.AurioTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    /** Where a widget asked to open (for example [OPEN_FOCUS]); cleared once handled. */
    private val openRequest = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) openRequest.value = intent?.getStringExtra(EXTRA_OPEN)
        setContent {
            val open by openRequest.collectAsStateWithLifecycle()
            AurioTheme {
                AurioApp(
                    onOpenUsageSettings = { startActivity(UsageAccess.settingsIntent()) },
                    openRequest = open,
                    onOpenHandled = { openRequest.value = null },
                )
            }
        }
        // While the app is visible: re-read usage every minute. This also picks up the
        // permission change when the user returns from Settings, and rolls the day over.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                AurioStore.visible = true
                try {
                    while (true) {
                        AurioStore.sync(this@MainActivity)
                        delay(60_000)
                    }
                } finally {
                    AurioStore.visible = false
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(EXTRA_OPEN)?.let { openRequest.value = it }
    }

    companion object {
        const val EXTRA_OPEN = "app.aurio.extra.OPEN"
        const val OPEN_FOCUS = "focus"
    }
}
