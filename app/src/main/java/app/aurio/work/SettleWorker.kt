package app.aurio.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.aurio.AurioApplication
import app.aurio.data.UsageAccess
import app.aurio.domain.SystemAurioClock

/** Freezes finished days even when the app was not opened. The app also catches up on launch. */
class SettleWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as AurioApplication
        if (!UsageAccess.isGranted(applicationContext)) app.repository.markAccessOff(SystemAurioClock.today())
        app.repository.reconcileFocus()
        app.repository.settlePastDays { date -> UsageAccess.minutesForDate(applicationContext, date) }
        return Result.success()
    }
}
