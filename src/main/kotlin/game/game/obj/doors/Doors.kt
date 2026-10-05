package game.obj.doors

import api.predef.*
import api.predef.ext.*
import io.luna.game.model.*
import io.luna.game.model.mob.*
import io.luna.game.model.`object`.*
import java.util.*

object Doors {

    /**
     * Maps both the closed id and the open id of every door to its [DoorType].
     */
    private val byId: HashMap<Int, DoorType> = HashMap()

    /**
     * Every loaded [DoorType].
     */
    var all: List<DoorType> = emptyList()
        private set

    /**
     * Doors that are currently away from their home state, mapped to the object they replaced. A door only reverts
     * on its own if it is still in this map when its timer expires.
     */
    private val displaced: IdentityHashMap<GameObject, GameObject> = IdentityHashMap()

    /**
     * Loads all door definitions, replacing any previously loaded ones.
     *
     * @param types The door pairs to load.
     */
    fun load(types: Array<DoorType>) {
        byId.clear()
        for (type in types) {
            require(byId.putIfAbsent(type.closed, type) == null) { "Duplicate door id ${type.closed} in doors.json." }
            require(byId.putIfAbsent(type.open, type) == null) { "Duplicate door id ${type.open} in doors.json." }
        }
        all = types.toList()
    }

    /**
     * @return The [DoorType] that [id] belongs to, or `null` if it is not a known door.
     */
    fun typeOf(id: Int): DoorType? = byId[id]

    /**
     * Opens [door] if it is closed, or closes it if it is open.
     *
     * An opening door moves one tile and turns a quarter turn clockwise. A closing door does the exact inverse, so
     * the position and direction of the original door can always be recovered from the door that was clicked. A
     * door that was moved away from its home state reverts on its own after [DoorType.durationOrDefault] ticks.
     *
     * @param world The world.
     * @param plr The player that clicked the door.
     * @param door The door that was clicked.
     */
    fun toggle(world: World, plr: Player, door: GameObject) {
        val type = typeOf(door.id) ?: return
        if (door.objectType != ObjectType.STRAIGHT_WALL) {
            return
        }

        val opening = door.id == type.closed
        val offset = if (opening) openOffset(door.direction) else closeOffset(door.direction)
        val newDirection = rotate(door.direction, if (opening) 1 else 3)
        val newId = if (opening) type.open else type.closed

        // Fails if the door was already replaced, so a stale click cannot spawn a duplicate.
        if (!world.removeObject(door)) {
            return
        }
        val newDoor = world.addObject(newId,
                                      door.position.translate(offset.first, offset.second),
                                      door.objectType,
                                      newDirection)
        plr.playSound(if (opening) type.openSoundOrDefault else type.closeSoundOrDefault)

        if (displaced.remove(door) != null) {
            // The clicked door was away from home, so it is home again and has nothing left to revert.
            return
        }
        displaced[newDoor] = door
        world.scheduleOnce(type.durationOrDefault) {
            if (displaced.remove(newDoor) != null && world.removeObject(newDoor)) {
                world.addObject(door.id, door.position, door.objectType, door.direction)
            }
        }
    }

    /**
     * The tile offset applied when a straight wall door opens.
     */
    private fun openOffset(direction: ObjectDirection): Pair<Int, Int> {
        return when (direction) {
            ObjectDirection.WEST -> Pair(-1, 0)
            ObjectDirection.NORTH -> Pair(0, 1)
            ObjectDirection.EAST -> Pair(1, 0)
            ObjectDirection.SOUTH -> Pair(0, -1)
        }
    }

    /**
     * The tile offset applied when a straight wall door closes. This is the inverse of [openOffset] after the
     * direction has been rotated.
     */
    private fun closeOffset(direction: ObjectDirection): Pair<Int, Int> {
        return when (direction) {
            ObjectDirection.WEST -> Pair(0, 1)
            ObjectDirection.NORTH -> Pair(1, 0)
            ObjectDirection.EAST -> Pair(0, -1)
            ObjectDirection.SOUTH -> Pair(-1, 0)
        }
    }

    /**
     * Rotates [direction] by [quarterTurns] quarter turns, in the order west, north, east, south.
     */
    private fun rotate(direction: ObjectDirection, quarterTurns: Int): ObjectDirection {
        return ObjectDirection.ALL[(direction.id + quarterTurns) % 4]!!
    }
}
