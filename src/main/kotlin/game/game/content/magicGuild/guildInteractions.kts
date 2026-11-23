package game.content.magicGuild

import api.predef.*

object1(MagicGuild.LEFT_DOOR) {
    MagicGuild.openDoor(plr, gameObject)
}

object1(MagicGuild.RIGHT_DOOR) {
    MagicGuild.openDoor(plr, gameObject)
}

MagicPortal.values().forEach { portal ->
    object1(portal.id) {
        portal.enter(plr)
    }
}
