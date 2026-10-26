package app.aurio.domain

import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/** When the midnight settlement should run. Pure: the caller passes the time and zone. */
object SettleSchedule {

    /** A few minutes after midnight, so the day's last usage events have been written. */
    val AFTER_MIDNIGHT: Duration = Duration.ofMinutes(5)

    /**
     * Milliseconds from [nowMillis] until the next run: [AFTER_MIDNIGHT] past the next local
     * midnight in [zone]. Daylight-saving days are 23 or 25 hours long; the run still lands just
     * after midnight. If it is already past midnight but before the run time, that run is next.
     */
    fun millisUntilNextRun(nowMillis: Long, zone: ZoneId): Long {
        val now = Instant.ofEpochMilli(nowMillis).atZone(zone)
        val today = now.toLocalDate().atStartOfDay(zone).plus(AFTER_MIDNIGHT)
        val next = if (now.isBefore(today)) today else now.toLocalDate().plusDays(1).atStartOfDay(zone).plus(AFTER_MIDNIGHT)
        return Duration.between(now, next).toMillis()
    }
}
