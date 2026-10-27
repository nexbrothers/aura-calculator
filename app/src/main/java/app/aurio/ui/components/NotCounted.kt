package app.aurio.ui.components

import app.aurio.data.NotCountedReason

/**
 * Plain words for the "Unknown" state: what it means for the user and what happens next,
 * never just "Unknown". Used by Home, Limits, app detail and the widgets so they all agree.
 */
object NotCounted {

    /** Short status for a limit pill. */
    fun pill(reason: NotCountedReason?): String = when (reason) {
        NotCountedReason.OFF_EARLIER_TODAY -> "Counts tomorrow"
        else -> "Not tracked"
    }
}
