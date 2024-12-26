package world.player.skill.crafting.potteryCrafting

import api.predef.*
import io.luna.game.action.Action
import io.luna.game.action.ItemContainerAction.InventoryAction
import io.luna.game.model.item.Item
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.mob.Player

/**
 * An [InventoryAction] that shapes soft clay into [Unfired] pottery materials.
 */
class PotteryWheelActionItem(val plr: Player, val unfired: Unfired, amount: Int) : InventoryAction(plr, true, 2, amount) {

    override fun executeIf(start: Boolean): Boolean =
        when {
            mob.crafting.level < unfired.level -> {
                plr.sendMessage("You need a Crafting level of ${unfired.level} to make this.")
                false
            }

            else -> true
        }

    override fun execute() {
        mob.animation(Animation(894))
        mob.crafting.addExperience(unfired.shapingExp)
        plr.sendMessage("You mold the clay into ${articleItemName(unfired.firedId)}.")
    }

    override fun add() = listOf(Item(unfired.unfiredId))
    override fun remove() = listOf(Item(1761))

    override fun ignoreIf(other: Action<*>?): Boolean =
        when (other) {
            is PotteryWheelActionItem -> unfired == other.unfired
            else -> false
        }
}