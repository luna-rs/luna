package world.player.skill.crafting.potteryCrafting

import api.predef.*
import io.luna.game.action.Action
import io.luna.game.action.ItemContainerAction.InventoryAction
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Animation
import world.player.Sounds

/**
 * An [InventoryAction] that fires [Unfired] pottery materials.
 */
class PotteryOvenActionItem(val plr: Player, val unfired: Unfired, amount: Int) :
    InventoryAction(plr, true, 4, amount) {

    override fun executeIf(start: Boolean): Boolean =
        when {
            mob.crafting.level < unfired.level -> {
                plr.sendMessage("You need a Crafting level of ${unfired.level} to make this.")
                false
            }

            else -> true
        }

    override fun execute() {
        mob.playSound(Sounds.SMELTING)
        mob.animation(Animation(899))
        mob.crafting.addExperience(unfired.firingExp)
        plr.sendMessage("You fire the ${itemName(unfired.firedId)} in the oven.")
    }

    override fun add() = listOf(Item(unfired.firedId))
    override fun remove() = listOf(Item(unfired.unfiredId))

    override fun ignoreIf(other: Action<*>?): Boolean =
        when (other) {
            is PotteryOvenActionItem -> unfired == other.unfired
            else -> false
        }
}