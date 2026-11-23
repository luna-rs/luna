package game.item.weaponPoison

import api.predef.*

// Pour Karamjan rum onto silk to make a cleaning cloth.
useItem(CleaningCloth.KARAMJAN_RUM).onItem(CleaningCloth.SILK) {
    if (usedItemId == CleaningCloth.KARAMJAN_RUM) {
        CleaningCloth.make(plr, usedItemIndex, targetItemIndex)
    } else {
        CleaningCloth.make(plr, targetItemIndex, usedItemIndex)
    }
}

// Use the cloth on any poisoned weapon to clean it.
for (poisonedId in PoisonableWeapon.POISONED_TO_WEAPON.keys) {
    useItem(CleaningCloth.CLOTH).onItem(poisonedId) {
        CleaningCloth.clean(plr, if (usedItemId == poisonedId) usedItemIndex else targetItemIndex, poisonedId)
    }
}
