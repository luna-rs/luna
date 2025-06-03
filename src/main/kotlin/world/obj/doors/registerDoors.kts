package world.obj.doors

import api.predef.*
import api.predef.ext.*

val doors: IntArray = intArrayOf(1533, 1534, 1519, 1516, 1530, 1531, 1536, )

doors.forEach {
    val doorID = it
    object1(doorID) {
        world.removeObject(gameObject.position, filter = {id == gameObject.id})
    }
}