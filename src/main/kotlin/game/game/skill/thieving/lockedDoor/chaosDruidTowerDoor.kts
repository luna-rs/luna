package game.skill.thieving.lockedDoor

import api.predef.*

// Open is registered by the door registry and dispatched through Doors.toggle.
object2(ChaosDruidTowerDoorAction.DOOR) { plr.submitAction(ChaosDruidTowerDoorAction(plr, gameObject, true)) }
