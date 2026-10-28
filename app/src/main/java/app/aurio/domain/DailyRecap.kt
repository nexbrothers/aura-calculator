package app.aurio.domain

import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/**
 * The morning recap: yesterday's settled aura, sent once quiet hours end (or at 8:00 with quiet
 * hours off). It reports final numbers only, never a projection. Pure: callers pass time and zone.
 */
object DailyRecap {
    /** When the recap goes out if quiet hours are off. */
    const val DEFAULT_MINUTE = 8 * 60

    data class Message(val title: String, val text: String)

    /** The minute of the day the recap goes out: the end of quiet hours, or [DEFAULT_MINUTE]. */
    fun minuteOfDay(quietStartMinute: Int, quietEndMinute: Int): Int =
        if (quietStartMinute == quietEndMinute) DEFAULT_MINUTE else quietEndMinute

    /** Milliseconds from [nowMillis] to the next [minuteOfDay] in [zone], always in the future. */
    fun millisUntilNext(nowMillis: Long, zone: ZoneId, minuteOfDay: Int): Long {
        val now = Instant.ofEpochMilli(nowMillis).atZone(zone)
        val time = LocalTime.of(minuteOfDay / 60, minuteOfDay % 60)
        val today = now.toLocalDate().atTime(time).atZone(zone)
        val next = if (today.isAfter(now)) today else now.toLocalDate().plusDays(1).atTime(time).atZone(zone)
        return Duration.between(now, next).toMillis()
    }

    /**
     * "Yesterday: +45" / "Your aura is 412, Glow. You reached Glow." [previousTotal] is the aura
     * before that day, so a new level can be named. Only what was measured: no praise, no blame.
     */
    fun message(delta: Int, totalAfter: Int, partial: Boolean, previousTotal: Int): Message {
        val level = AuraLevel.of(totalAfter)
        val text = buildString {
            append("Your aura is $totalAfter, ${level.label}.")
            if (level.ordinal > AuraLevel.of(previousTotal).ordinal) append(" You reached ${level.label}.")
            if (partial) append(" Part of the day was not counted.")
        }
        return Message("Yesterday: ${signed(delta)}", text)
    }

    private fun signed(n: Int) = when {
        n > 0 -> "+$n"
        n < 0 -> "−${-n}"
        else -> "0"
    }
}
