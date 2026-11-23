package game.item.weaponPoison

import api.predef.*

// Use any weapon poison on any unpoisoned piercing weapon, either way round.
for (poison in WeaponPoison.entries) {
    for (weapon in PoisonableWeapon.entries) {
        useItem(poison.id).onItem(weapon.base) {
            if (usedItemId == poison.id) {
                WeaponPoison.apply(plr, poison, usedItemIndex, weapon, targetItemIndex)
            } else {
                WeaponPoison.apply(plr, poison, targetItemIndex, weapon, usedItemIndex)
            }
        }
    }
}
