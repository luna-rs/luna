package world.player.skill.crafting.glassMaking

import api.predef.*
import io.luna.game.action.Action
import io.luna.game.action.ItemContainerAction.InventoryAction
import io.luna.game.model.item.Item
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.mob.Player

/**
 * An [InventoryAction] that combines soda ash and a bucket of sand to make molten glass.
 */
class MakeMoltenGlassActionItem(plr: Player, amount: Int) : InventoryAction(plr, true, 3, amount) {

    override fun executeIf(start: Boolean): Boolean = true
    override fun execute() {
        mob.animation(Animation(899))
        mob.crafting.addExperience(20.0)
        mob.sendMessage("You smelt the materials together and get molten glass.")
    }

    override fun add() = listOf(Item(1775))
    override fun remove() = listOf(Item(1781), Item(1783))

    override fun ignoreIf(other: Action<*>?): Boolean =
        when (other) {
            is MakeMoltenGlassActionItem -> true
            else -> false
        }
}