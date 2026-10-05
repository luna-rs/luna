package world.obj.doors

import api.predef.*
import api.predef.ext.*
import io.luna.game.model.*
import io.luna.game.model.chunk.*
import io.luna.game.model.`object`.*

object Doors {

    /**
     * Map of closed door ids mapped to their corresponding open door
     */
    val closedToOpen: Map<Int, Int> = mapOf(
        1533 to 1534,
        1530 to 1531,
        1551 to 1551,
        1553 to 1553,
    )

    /**
     * Map of open door ids mapped to their corresponding closed door
     */
    val openToClosed: Map<Int, Int> = closedToOpen.entries.associate { (key, value) ->
        value to key
    }

    /**
     * Map of activated doors mapped by their position, each entry is a pair of the initial door static object and the activated door dynamic object.
     */
    val activeDoors: MutableMap<Position, Pair<GameObject, GameObject>> = mutableMapOf()

    fun activateDoor(world: World, inactiveDoor: GameObject) {
        var nextDoorId: Int = -1
        if (closedToOpen.contains(inactiveDoor.id)) {
            nextDoorId = closedToOpen.get(inactiveDoor.id)!!
        } else if (openToClosed.contains(inactiveDoor.id)) {
            nextDoorId = openToClosed.get(inactiveDoor.id)!!
        }
        val activeDoorObj = GameObject.createDynamic(
            ctx,
            if (nextDoorId != -1) nextDoorId else inactiveDoor.id,
            inactiveDoor.position,
            ObjectType.STRAIGHT_WALL,
            calcNewDirection(inactiveDoor.direction),
            ChunkUpdatableView.globalView())
        world.addObject(activeDoorObj)
        activeDoors.put(inactiveDoor.position, Pair(inactiveDoor, activeDoorObj))
    }

    fun closeDoor(world: World, openDoor: GameObject) {
        if (!activeDoors.contains(openDoor.position)) {
            return
        }
        val doorEntry = activeDoors.get(openDoor.position)!!
        val closedObj = doorEntry.first

        val closedDoorDynamicObj = GameObject.createDynamic(ctx, closedObj.id, closedObj.position, closedObj.objectType, closedObj.direction, ChunkUpdatableView.globalView())
        world.addObject(closedDoorDynamicObj)
        activeDoors.remove(openDoor.position)
    }

    fun calcNewDirection(initialDirection: ObjectDirection): ObjectDirection {
        return when (initialDirection) {
            ObjectDirection.SOUTH -> ObjectDirection.EAST
            ObjectDirection.NORTH -> ObjectDirection.EAST
            ObjectDirection.EAST -> ObjectDirection.SOUTH
            ObjectDirection.WEST -> ObjectDirection.SOUTH
            else -> ObjectDirection.EAST
        }
    }
}