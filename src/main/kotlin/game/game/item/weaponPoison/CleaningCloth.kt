package game.item.weaponPoison

import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player

/**
 * The cleaning cloth, which cleans the poison off a weapon and is kept for further use.
 *
 * @author TheLining
 */
object CleaningCloth {

    /**
     * The cleaning cloth.
     */
    const val CLOTH = 3188

    /**
     * The silk the cloth is made from.
     */
    const val SILK = 950

    /**
     * Karamjan rum, which soaks the silk.
     */
    const val KARAMJAN_RUM = 431

    /**
     * Pours the rum in slot [rumIndex] onto the silk in slot [silkIndex].
     */
    fun make(plr: Player, rumIndex: Int, silkIndex: Int) {
        val inventory = plr.inventory
        if (inventory[rumIndex]?.id != KARAMJAN_RUM || inventory[silkIndex]?.id != SILK) {
            return
        }
        inventory.set(rumIndex, null)
        inventory.set(silkIndex, Item(CLOTH))
        plr.sendMessage("You pour the rum onto the silk and make a cleaning cloth.")
    }

    /**
     * Cleans the poisoned weapon in slot [weaponIndex]. A stack of ammunition is cleaned all at once.
     */
    fun clean(plr: Player, weaponIndex: Int, poisonedId: Int) {
        val inventory = plr.inventory
        val weaponItem = inventory[weaponIndex]
        val weapon = PoisonableWeapon.POISONED_TO_WEAPON[poisonedId]
        if (weapon == null || weaponItem?.id != poisonedId || !inventory.contains(CLOTH)) {
            return
        }
        val cleaned = Item(weapon.base, weaponItem.amount)
        if (weapon.type.ammunition && inventory.contains(weapon.base)) {
            inventory.set(weaponIndex, null)
            inventory.add(cleaned)
        } else {
            inventory.set(weaponIndex, cleaned)
        }
        plr.sendMessage("You clean the poison off the ${weapon.type.noun}.")
    }
}
