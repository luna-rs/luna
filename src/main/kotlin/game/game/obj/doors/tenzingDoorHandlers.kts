package game.obj.doors

import api.predef.*

TenzingDoors.IDS.forEach { id ->
    object1(id) {
        TenzingDoors.cross(plr, gameObject)
    }
}
