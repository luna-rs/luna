package game.obj.doors

import api.predef.*
import game.content.druidicRitual.CauldronArmour

for (id in QuestDoors.doors.keys) {
    object1(id) {
        if (id in QuestDoors.cauldronDoors) CauldronArmour.rattle(plr, gameObject)
        QuestDoors.cross(plr, gameObject)
    }
}

for (id in LockedDoors.messages.keys) {
    object1(id) { LockedDoors.tryOpen(plr, gameObject.id) }
}
