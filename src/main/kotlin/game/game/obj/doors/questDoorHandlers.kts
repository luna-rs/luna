package game.obj.doors

import api.predef.*

for (id in QuestDoors.doors.keys) {
    object1(id) { QuestDoors.cross(plr, gameObject) }
}

for (id in LockedDoors.messages.keys) {
    object1(id) { LockedDoors.tryOpen(plr, gameObject.id) }
}
