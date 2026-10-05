package world.obj.doors

import api.predef.*
import io.luna.game.model.mob.*
import io.luna.game.model.`object`.*

Doors.closedToOpen.forEach(
    {(key, value) ->
        object1(key) {
            handleDoorClick(gameObject, plr)
        }
        if (key != value) {
            object1(value) {
                handleDoorClick(gameObject, plr)
            }
        }
    }
)

fun handleDoorClick(gameObject: GameObject, plr: Player) {
    System.out.println("Clicked door: "+gameObject+" "+gameObject.direction)
    if (Doors.activeDoors.contains(gameObject.position)) {
        Doors.closeDoor(world, gameObject)
        plr.sendMessage("Ran closeDoor()")
    } else {
        Doors.activateDoor(world, gameObject)
        plr.sendMessage("Ran openDoor()")
    }
}