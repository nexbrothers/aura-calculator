package app.aurio

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app.aurio.data.AurioStore
import app.aurio.data.UsageAccess
import app.aurio.ui.AurioApp
import app.aurio.ui.theme.AurioTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AurioTheme {
                AurioApp(onOpenUsageSettings = { startActivity(UsageAccess.settingsIntent()) })
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
}
