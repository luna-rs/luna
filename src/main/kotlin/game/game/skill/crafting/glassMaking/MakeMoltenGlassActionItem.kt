package game.skill.crafting.glassMaking

import api.predef.*
import io.luna.game.action.impl.ItemContainerAction.InventoryAction
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Animation

/**
 * Combines one soda ash and one bucket of sand per cycle, producing molten glass and an empty bucket.
 *
 * [InventoryAction] validates both inputs and space for both outputs before changing the inventory.
 * Returning the bucket in the same conversion permits full fourteen-pair batches without losing containers.
 * Each successful conversion retains the existing three-tick cadence and awards twenty Crafting experience.
 *
 * @author lare96
 */
class MakeMoltenGlassActionItem(plr: Player, amount: Int) : InventoryAction(plr, true, 3, amount) {

    override fun executeIf(start: Boolean): Boolean = true
    override fun execute() {
        mob.animation(Animation(899))
        mob.crafting.addExperience(20.0)
        mob.sendMessage("You smelt the materials together and get molten glass.")
    }

    override fun add() = listOf(Item(1775), Item(1925))
    override fun remove() = listOf(Item(1781), Item(1783))
}