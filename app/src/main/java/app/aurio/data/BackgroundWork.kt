package app.aurio.data

import kotlinx.coroutines.sync.Mutex

/**
 * One lock for background notification work (limit checks, the daily recap) and Delete all
 * data, so a check already running can never write rows or book alarms after the wipe.
 */
object BackgroundWork {
    val lock = Mutex()
}
