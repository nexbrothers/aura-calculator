package app.aurio.domain

import java.time.LocalDate
import java.time.ZoneId

/** Injected time source so days can be simulated in tests and the debug panel. */
interface AurioClock {
    fun nowMillis(): Long
    fun zone(): ZoneId
    fun today(): LocalDate = java.time.Instant.ofEpochMilli(nowMillis()).atZone(zone()).toLocalDate()
}

object SystemAurioClock : AurioClock {
    override fun nowMillis() = System.currentTimeMillis() + DebugTime.offsetMillis
    override fun zone(): ZoneId = ZoneId.systemDefault()
}

/**
 * Developer-only time travel and fake usage, used by the Developer tools screen of debuggable builds.
 * While [enabled], real usage is ignored so a simulated day is fully under your control.
 */
object DebugTime {
    @Volatile var offsetMillis: Long = 0L
    @Volatile var enabled: Boolean = false
    /** Fake foreground minutes: date -> package -> minutes. */
    val usageByDate: MutableMap<LocalDate, MutableMap<String, Int>> = java.util.concurrent.ConcurrentHashMap()

    fun reset() { offsetMillis = 0L; enabled = false; usageByDate.clear() }
}

class FakeClock(var millis: Long, var zoneId: ZoneId = ZoneId.of("UTC")) : AurioClock {
    override fun nowMillis() = millis
    override fun zone() = zoneId
    fun advanceMinutes(m: Long) { millis += m * 60_000 }
    fun advanceDays(d: Long) { millis += d * 86_400_000 }
}
