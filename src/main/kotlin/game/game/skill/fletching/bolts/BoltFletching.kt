package game.skill.fletching.bolts

import api.predef.*
import game.player.Messages
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player

/**
 * Shapes bolt tips with a chisel and attaches them to plain bolts. Each use does one gem, pearl or set on the spot,
 * with no menu.
 *
 * @author TheLining
 */
object BoltFletching {

    /**
     * Shapes one [tip] source in [plr]'s inventory into bolt tips.
     *
     * @return `true` if tips were made.
     */
    fun shapeTips(plr: Player, tip: BoltTip): Boolean {
        if (plr.fletching.level < tip.level) {
            plr.sendMessage("You need a Fletching level of ${tip.level} to cut this.")
            return false
        }
        val inventory = plr.inventory
        if (!inventory.remove(Item(tip.source))) {
            return false
        }
        inventory.add(Item(tip.tip, tip.amount))
        tip.animation?.let { plr.animation(it) }
        plr.fletching.addExperience(tip.exp)
        return true
    }

    /**
     * Attaches one set of [bolt] tips to the plain bolts in [plr]'s inventory.
     *
     * @return `true` if any bolts were made.
     */
    fun attach(plr: Player, bolt: Bolt): Boolean {
        if (plr.fletching.level < bolt.level) {
            plr.sendMessage("You need a Fletching level of ${bolt.level} to attach this.")
            return false
        }
        val inventory = plr.inventory
        val boltCount = inventory.computeAmountForId(Bolt.BOLTS)
        val tipCount = inventory.computeAmountForId(bolt.tip)
        val amount = minOf(boltCount, tipCount, Bolt.SET_AMOUNT)
        if (amount == 0) {
            return false
        }
        val freed = (if (boltCount == amount) 1 else 0) + (if (tipCount == amount) 1 else 0)
        val needed = if (inventory.contains(bolt.id)) 0 else 1
        if (needed > inventory.computeRemainingSize() + freed) {
            plr.sendMessage(Messages.inventoryFull())
            return false
        }
        inventory.removeAll(listOf(Item(bolt.tip, amount), Item(Bolt.BOLTS, amount)))
        inventory.add(Item(bolt.id, amount))
        plr.sendMessage("You fletch $amount bolts.")
        plr.fletching.addExperience(bolt.exp * amount)
        return true
    }
}
