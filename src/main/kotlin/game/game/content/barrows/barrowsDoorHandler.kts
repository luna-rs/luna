package game.content.barrows

import api.predef.*

for (door in BarrowsDoor.entries) {
    for (id in listOf(door.leftId, door.rightId)) {
        object1(id) { BarrowsDoors.cross(plr, gameObject, door) }
    }
}
