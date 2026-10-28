package app.aurio

import android.app.Application
import app.aurio.data.AurioRepository
import app.aurio.data.AurioStore
import app.aurio.data.db.AurioDatabase
import app.aurio.data.settings.SettingsStore
import app.aurio.domain.SystemAurioClock
import app.aurio.focus.FocusNotifications
import app.aurio.limits.AccessNotice
import app.aurio.limits.LimitNotifications
import app.aurio.recap.RecapAlarm
import app.aurio.recap.RecapNotifications
import app.aurio.work.SettleWorker

/** Simple manual wiring; a DI library is not worth it for an app this size. */
class AurioApplication : Application() {
    val database by lazy { AurioDatabase.build(this) }
    val settings by lazy { SettingsStore(this) }
    val repository by lazy { AurioRepository(database, SystemAurioClock) }

    override fun onCreate() {
        super.onCreate()
        AurioStore.init(this)
        FocusNotifications.createChannel(this)
        LimitNotifications.createChannel(this)
        RecapNotifications.createChannel(this)
        AccessNotice.createChannel(this)
        SettleWorker.schedule(this)
        RecapAlarm.scheduleAsync(this)
    }
}
