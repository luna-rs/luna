package game.obj.doors

import game.player.Sound

/**
 * A closed/open pair of objects that make up a door. Which kind of door it is depends on the file it is defined in, all
 * of which are in `data/game/world/doors/`:
 *
 * - `doors.json`: a single door.
 * - `double_doors.json`: one leaf of a double door.
 * - `gates.json`: one leaf of a gate.
 * - `curtains.json`: a curtain.
 *
 * Fields that are missing from the JSON are deserialized as `null`, so optional values are exposed through the
 * `*OrDefault` properties rather than constructor defaults (Gson does not run Kotlin default arguments).
 *
 * @param closed The id of the closed door.
 * @param open The id of the open door.
 * @param side Which leaf of a double door or gate this is, or `null` for a single door or curtain.
 * @param openSound The sound played on opening, or `null` for [Sound.DOOR_OPEN].
 * @param closeSound The sound played on closing, or `null` for [Sound.DOOR_CLOSE].
 * @param duration The ticks before the door reverts on its own, or `null` for [DEFAULT_DURATION].
 */
class DoorType(val closed: Int,
               val open: Int,
               val side: DoorSide?,
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
