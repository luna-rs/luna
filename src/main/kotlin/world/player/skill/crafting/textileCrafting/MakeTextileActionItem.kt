package world.player.skill.crafting.textileCrafting

import api.predef.*
import io.luna.game.action.Action
import io.luna.game.action.ItemContainerAction.InventoryAction
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.mob.Player

/**
 * An [InventoryAction] implementation that makes textiles.
 */
class MakeTextileActionItem(val plr: Player, val textile: Textile, amount: Int) : InventoryAction(plr, true, 2, amount) {

    override fun executeIf(start: Boolean): Boolean =
        when {
            mob.crafting.level < textile.level -> {
                plr.sendMessage("You need a Crafting level of ${textile.level} to make this.")
                false
            }

            else -> true
        }

    override fun execute() {
        mob.animation(Animation(894))
        mob.crafting.addExperience(textile.exp)
        plr.sendMessage("You make ${articleItemName(textile.processedItem.id)}.")
    }

    override fun add() = listOf(textile.processedItem)
    override fun remove() = listOf(textile.rawItem)

    override fun ignoreIf(other: Action<*>?): Boolean =
        when (other) {
            is MakeTextileActionItem -> textile == other.textile
            else -> false
        }
}