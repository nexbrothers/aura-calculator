package app.aurio

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import app.aurio.data.AurioRepository
import app.aurio.data.AurioStore
import app.aurio.data.db.AurioDatabase
import app.aurio.data.settings.SettingsStore
import app.aurio.domain.SystemAurioClock
import app.aurio.work.SettleWorker
import java.util.concurrent.TimeUnit

/** Simple manual wiring; a DI library is not worth it for an app this size. */
class AurioApplication : Application() {
    val database by lazy { AurioDatabase.build(this) }
    val settings by lazy { SettingsStore(this) }
    val repository by lazy { AurioRepository(database, SystemAurioClock) }

    override fun onCreate() {
        super.onCreate()
        AurioStore.init(this)
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "settle-days", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<SettleWorker>(6, TimeUnit.HOURS).build(),
        )
    }
}
