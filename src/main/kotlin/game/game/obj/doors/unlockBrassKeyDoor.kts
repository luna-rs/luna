package game.obj.doors

import api.predef.*

object1(BrassKeyDoor.DOOR) { BrassKeyDoor.open(plr) }

useItem(BrassKeyDoor.KEY).onObject(BrassKeyDoor.DOOR) { BrassKeyDoor.unlock(plr, gameObject) }
