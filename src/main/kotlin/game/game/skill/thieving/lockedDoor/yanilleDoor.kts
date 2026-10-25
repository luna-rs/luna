package game.skill.thieving.lockedDoor

import api.predef.*

// Open is registered by the door registry and dispatched through Doors.toggle.
object2(2559) { plr.submitAction(YanilleDoorAction(plr, gameObject, true)) }
useItem(1523).onObject(2559) { plr.submitAction(YanilleDoorAction(plr, gameObject, true)) }
