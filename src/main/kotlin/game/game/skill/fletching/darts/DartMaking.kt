package game.skill.fletching.darts

import api.predef.*
import game.player.Messages
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player

/**
 * Adds feathers to dart tips. Each use makes one batch of up to [Dart.BATCH_AMOUNT] darts on the spot, with no menu
 * and no animation.
 */
object DartMaking {

    /**
     * Makes one batch of [dart] for [plr] from the feathers and tips in their inventory.
     *
     * @return `true` if any darts were made.
     */
    fun make(plr: Player, dart: Dart): Boolean {
        if (plr.fletching.level < dart.level) {
            plr.sendMessage("You need a Fletching level of ${dart.level} to attach this.")
            return false
        }
        val inventory = plr.inventory
        val featherCount = inventory.computeAmountForId(Dart.FEATHER)
        val tipCount = inventory.computeAmountForId(dart.tip)
        val amount = minOf(featherCount, tipCount, Dart.BATCH_AMOUNT)
        if (amount == 0) {
            return false
        }
        val freed = (if (featherCount == amount) 1 else 0) + (if (tipCount == amount) 1 else 0)
        val needed = if (inventory.contains(dart.id)) 0 else 1
        if (needed > inventory.computeRemainingSize() + freed) {
            plr.sendMessage(Messages.inventoryFull())
            return false
        }
        inventory.removeAll(listOf(Item(Dart.FEATHER, amount), Item(dart.tip, amount)))
        inventory.add(Item(dart.id, amount))
        plr.sendMessage("You finish making $amount darts.")
        plr.fletching.addExperience(dart.exp * amount)
        return true
    }
}
