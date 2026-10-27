package app.aurio.domain

/** Five aura levels across 0–1000 points. */
enum class AuraLevel(val label: String, val range: IntRange) {
    STATIC("Static", 0..199),
    FLICKER("Flicker", 200..399),
    GLOW("Glow", 400..599),
    RADIANT("Radiant", 600..799),
    HALO("Halo", 800..1000);

    val next: AuraLevel? get() = entries.getOrNull(ordinal + 1)

    companion object {
        fun of(score: Int): AuraLevel {
            val s = score.coerceIn(0, 1000)
            return entries.last { s >= it.range.first }
        }
    }
}

enum class AppCategory { LIMITED, NEUTRAL, ESSENTIAL }

/**
 * Time-only usage for one app today. [usedMinutes] is null when Usage Access is off:
 * the app is then "Unknown" and never gains or loses aura.
 */
data class AppUsage(
    val packageName: String,
    val label: String,
    val category: AppCategory,
    val usedMinutes: Int?,
    val limitMinutes: Int,
)

sealed interface LimitStatus {
    data object Unknown : LimitStatus
    data object NotScored : LimitStatus // neutral and essential apps
    data class Under(val minutesLeft: Int, val projectedBonus: Int) : LimitStatus
    data class Over(val minutesOver: Int, val penalty: Int) : LimitStatus
}

enum class LineKind { FOCUS, LIMIT_BONUS, LIMIT_PENALTY, UNKNOWN }

/** One row of "What changed today". [projected] lines are only added at midnight. */
data class ScoreLine(
    val kind: LineKind,
    val amount: Int,
    val title: String,
    val detail: String,
    val projected: Boolean = false,
)

data class DailyScore(val lines: List<ScoreLine>, val focusAura: Int) {
    /** Includes projected lines, as shown in the UI. */
    val total: Int get() = lines.sumOf { it.amount }
}

/** Pure scoring rules. Keep this file free of Android imports so it is unit-testable. */
object AuraScoring {
    const val FOCUS_PER_MINUTE = 1
    const val MAX_FOCUS_PER_DAY = 100
    const val UNDER_LIMIT_BONUS = 20
    const val PENALTY_PER_MINUTE = 1
    const val MAX_PENALTY_PER_APP = 60
    const val MAX_SCORE = 1000

    /** Aura earned by each completed session, in order, respecting the daily focus cap. */
    fun focusEarnings(sessionMinutes: List<Int>): List<Int> {
        var total = 0
        return sessionMinutes.map { minutes ->
            val earned = (minutes * FOCUS_PER_MINUTE).coerceAtMost(MAX_FOCUS_PER_DAY - total).coerceAtLeast(0)
            total += earned
            earned
        }
    }

    /**
     * Aura the session at [index] earned among a day's completed sessions in start order, the same
     * order settlement credits them in; 0 if it is not among them.
     */
    fun earnedBy(sessionMinutes: List<Int>, index: Int): Int = focusEarnings(sessionMinutes).getOrNull(index) ?: 0

    /** What a new session of [minutes] would add, given sessions already completed today. */
    fun potentialFocus(sessionMinutes: List<Int>, minutes: Int): Int {
        val already = focusEarnings(sessionMinutes).sum()
        return minOf(minutes * FOCUS_PER_MINUTE, MAX_FOCUS_PER_DAY - already).coerceAtLeast(0)
    }

    fun limitStatus(app: AppUsage): LimitStatus {
        if (app.category != AppCategory.LIMITED) return LimitStatus.NotScored
        val used = app.usedMinutes ?: return LimitStatus.Unknown
        return if (used <= app.limitMinutes) {
            LimitStatus.Under(app.limitMinutes - used, UNDER_LIMIT_BONUS)
        } else {
            val over = used - app.limitMinutes
            LimitStatus.Over(over, (over * PENALTY_PER_MINUTE).coerceAtMost(MAX_PENALTY_PER_APP))
        }
    }

    fun today(sessions: List<FocusSession>, apps: List<AppUsage>): DailyScore {
        val lines = mutableListOf<ScoreLine>()
        val earnings = focusEarnings(sessions.map { it.minutes })
        sessions.zip(earnings).asReversed().forEach { (session, earned) ->
            if (earned > 0) lines += ScoreLine(LineKind.FOCUS, earned, "Focus session", "${session.minutes} min, ${session.timeLabel}")
        }

        val limited = apps.filter { it.category == AppCategory.LIMITED }
        val statuses = limited.associateWith { limitStatus(it) }
        val under = statuses.values.filterIsInstance<LimitStatus.Under>()
        if (statuses.values.any { it is LimitStatus.Unknown }) {
            lines += ScoreLine(LineKind.UNKNOWN, 0, "Limits not counted today", "Usage access was off, so no bonus or penalty")
        }
        if (under.isNotEmpty()) {
            val n = under.size
            lines += ScoreLine(
                LineKind.LIMIT_BONUS, n * UNDER_LIMIT_BONUS,
                "$n ${if (n == 1) "app" else "apps"} under limit", "Projected, added at midnight", projected = true,
            )
        }
        statuses.forEach { (app, status) ->
            if (status is LimitStatus.Over) {
                lines += ScoreLine(LineKind.LIMIT_PENALTY, -status.penalty, "${app.label} over limit", "${app.usedMinutes} of ${app.limitMinutes} min")
            }
        }
        return DailyScore(lines, earnings.sum())
    }
}

data class FocusSession(val minutes: Int, val timeLabel: String)
