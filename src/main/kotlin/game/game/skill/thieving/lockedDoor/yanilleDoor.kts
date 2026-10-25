package game.skill.thieving.lockedDoor

import api.predef.*

object1(2559) { plr.submitAction(YanilleDoorAction(plr, gameObject, false)) }
object2(2559) { plr.submitAction(YanilleDoorAction(plr, gameObject, true)) }
useItem(1523).onObject(2559) { plr.submitAction(YanilleDoorAction(plr, gameObject, true)) }
