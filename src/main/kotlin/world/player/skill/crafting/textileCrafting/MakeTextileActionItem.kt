package world.player.skill.crafting.textileCrafting

import api.predef.articleItemName
import api.predef.crafting
import io.luna.game.action.impl.ItemContainerAction.InventoryAction
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Animation

/**
 * An [InventoryAction] implementation that makes textiles.
 */
class MakeTextileActionItem(val plr: Player, val textile: Textile, amount: Int) :
    InventoryAction(plr, true, 2, amount) {

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
}