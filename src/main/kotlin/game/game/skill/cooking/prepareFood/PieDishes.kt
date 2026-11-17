package game.skill.cooking.prepareFood

import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player

/**
 * Recovers the real dish retained by an unnoted burnt pie through its native inventory option.
 *
 * @author lare96
 */
object PieDishes {
    const val BURNT_PIE = 2329
    const val DISH = 2313

    /**
     * Empties only the clicked, unchanged pie slot. The same-slot replacement also works in a full inventory.
     */
    fun empty(plr: Player, index: Int): Boolean {
        val inventory = plr.inventory
        if (index !in 0 until inventory.capacity()) return false
        val pie = inventory[index] ?: return false
        if (pie.id != BURNT_PIE || pie.amount != 1 || pie.isDynamic || pie.itemDef.isNoted) return false
        inventory[index] = Item(DISH)
        plr.sendMessage("You empty the burnt pie out of the dish.")
        return true
    }
}
