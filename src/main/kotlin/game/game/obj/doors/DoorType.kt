package game.obj.doors

import game.player.Sound

/**
 * A single closed/open door pair, as defined in `data/game/world/doors.json`.
 *
 * Fields that are missing from the JSON are deserialized as `null`, so optional values are exposed through the
 * `*OrDefault` properties rather than constructor defaults (Gson does not run Kotlin default arguments).
 *
 * @param closed The id of the closed door.
 * @param open The id of the open door.
 * @param openSound The sound played on opening, or `null` for [Sound.DOOR_OPEN].
 * @param closeSound The sound played on closing, or `null` for [Sound.DOOR_CLOSE].
 * @param duration The ticks before the door reverts on its own, or `null` for [DEFAULT_DURATION].
 */
class DoorType(val closed: Int,
               val open: Int,
               private val openSound: Sound?,
               private val closeSound: Sound?,
               private val duration: Int?) {

    companion object {

        /**
         * The default number of ticks an opened (or closed) door stays changed before it reverts.
         */
        const val DEFAULT_DURATION = 500
    }

    /**
     * The sound played when this door is opened.
     */
    val openSoundOrDefault: Sound get() = openSound ?: Sound.DOOR_OPEN

    /**
     * The sound played when this door is closed.
     */
    val closeSoundOrDefault: Sound get() = closeSound ?: Sound.DOOR_CLOSE

    /**
     * The ticks before this door reverts on its own.
     */
    val durationOrDefault: Int get() = duration ?: DEFAULT_DURATION
}
