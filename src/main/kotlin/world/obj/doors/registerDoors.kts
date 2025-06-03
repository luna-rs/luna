package world.obj.doors

import api.predef.*
import api.predef.ext.*
import io.luna.game.model.`object`.*

val doors: IntArray = intArrayOf(1533, 1534, 1519, 1516, 1530, 1531, 1536, )

doors.forEach {
    val doorID = it
    object1(doorID) {
        world.removeObject(gameObject.position, filter = {id == gameObject.id})
        world.addObject(1534, gameObject.position, type = ObjectType.STRAIGHT_WALL, direction = ObjectDirection.SOUTH)
    }
}