package game.obj.doors

import api.predef.*

for (door in TaverleyJailDoor.values()) {
    object1(door.closed) { door.open(plr, gameObject) }
    useItem(door.key).onObject(door.closed) { door.unlock(plr, gameObject) }
}
