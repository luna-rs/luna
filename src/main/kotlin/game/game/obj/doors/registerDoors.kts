package game.obj.doors

import api.predef.*
import io.luna.game.model.mob.*
import io.luna.game.model.`object`.*
import io.luna.util.GsonUtils
import java.nio.file.Paths

/**
 * The filesystem path to the door definition file.
 *
 * Read synchronously while this script is evaluated, because a click handler has to be registered for every door id
 * and the ids come from this file.
 */
val PATH = Paths.get("data", "game", "world", "doors.json")

/**
 * The filesystem path to the double door definition file.
 */
val DOUBLE_PATH = Paths.get("data", "game", "world", "double_doors.json")

/**
 * The filesystem path to the gate definition file.
 */
val GATE_PATH = Paths.get("data", "game", "world", "gates.json")

/**
 * The filesystem path to the curtain definition file.
 */
val CURTAIN_PATH = Paths.get("data", "game", "world", "curtains.json")

Doors.load(GsonUtils.readAsType(PATH, Array<DoorType>::class.java),
           GsonUtils.readAsType(DOUBLE_PATH, Array<DoorType>::class.java),
           GsonUtils.readAsType(GATE_PATH, Array<DoorType>::class.java),
           GsonUtils.readAsType(CURTAIN_PATH, Array<DoorType>::class.java))

Doors.all.forEach { type ->
    object1(type.closed) {
        handleDoorClick(gameObject, plr)
    }
    object1(type.open) {
        handleDoorClick(gameObject, plr)
    }
}

fun handleDoorClick(gameObject: GameObject, plr: Player) {
    Doors.toggle(world, plr, gameObject)
}
