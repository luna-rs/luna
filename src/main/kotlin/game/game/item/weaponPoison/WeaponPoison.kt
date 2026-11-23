package game.item.weaponPoison

import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player

/**
 * The weapon poisons that can be used on a [PoisonableWeapon].
 *
 * @property id The vial of poison.
 * @property poisonedForm The weapon this poison makes from an unpoisoned weapon.
 * @author TheLining
 */
enum class WeaponPoison(val id: Int, val poisonedForm: (PoisonableWeapon) -> Int) {
    WEAPON_POISON(187, { it.poisoned }),
    EXTRA_STRONG(5937, { it.extraStrong }),
    SUPER_STRONG(5940, { it.superStrong });

    companion object {

        /**
         * The most ammunition one vial poisons.
         */
        const val AMMUNITION_PER_VIAL = 5

        /**
         * The vial left once the poison is used.
         */
        const val EMPTY_VIAL = 229

        /**
         * Uses the poison in inventory slot [poisonIndex] on the weapon in slot [weaponIndex]. One vial poisons one
         * dagger or spear, or up to [AMMUNITION_PER_VIAL] from a stack of ammunition, and leaves an empty vial.
         */
        fun apply(plr: Player, poison: WeaponPoison, poisonIndex: Int, weapon: PoisonableWeapon, weaponIndex: Int) {
            val inventory = plr.inventory
            val weaponItem = inventory[weaponIndex]
            if (inventory[poisonIndex]?.id != poison.id || weaponItem?.id != weapon.base) {
                return
            }
            val product = poison.poisonedForm(weapon)
            if (weapon.type.ammunition) {
                val amount = minOf(weaponItem.amount, AMMUNITION_PER_VIAL)
                val poisonedItem = Item(product, amount)
                if (amount < weaponItem.amount && !inventory.hasSpaceFor(poisonedItem)) {
                    plr.sendMessage("You don't have enough inventory space.")
                    return
                }
                inventory.set(poisonIndex, Item(EMPTY_VIAL))
                inventory.remove(weaponIndex, Item(weapon.base, amount))
                inventory.add(poisonedItem)
            } else {
                inventory.set(weaponIndex, Item(product))
                inventory.set(poisonIndex, Item(EMPTY_VIAL))
            }
            plr.sendMessage(weapon.type.poisonMessage)
        }
    }
}
