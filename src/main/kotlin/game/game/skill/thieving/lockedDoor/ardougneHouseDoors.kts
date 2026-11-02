package game.skill.thieving.lockedDoor

import api.predef.*

for (lock in ArdougneHouseDoorAction.Lock.entries) {
    object1(lock.door) { plr.submitAction(ArdougneHouseDoorAction(plr, gameObject, lock, false)) }
    object2(lock.door) { plr.submitAction(ArdougneHouseDoorAction(plr, gameObject, lock, true)) }
    if (lock.requiresLockpick) {
        useItem(1523).onObject(lock.door) {
            if (plr.inventory[usedItemIndex]?.id == 1523) {
                plr.submitAction(ArdougneHouseDoorAction(plr, gameObject, lock, true))
            }
        }
    }
}
