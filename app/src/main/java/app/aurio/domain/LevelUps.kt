package app.aurio.domain

/** One saved level change, as settlement wrote it. */
data class LevelChange(val id: Long, val from: AuraLevel, val to: AuraLevel)

/** Which level-up moment to show. Pure: no Android, no clock. */
object LevelUps {

    /**
     * The level to celebrate among changes the user has not seen ([unseen], any order), given the
     * level their settled score holds now ([current]). Only rises are celebrated, never a drop.
     * If several days settled at once, the highest level reached is shown once, and only if it is
     * still held: a level that was reached and then lost again is not celebrated after the fact.
     */
    fun toCelebrate(unseen: List<LevelChange>, current: AuraLevel): AuraLevel? =
        unseen.filter { it.to > it.from }.map { it.to }.filter { it <= current }.maxOrNull()
}
