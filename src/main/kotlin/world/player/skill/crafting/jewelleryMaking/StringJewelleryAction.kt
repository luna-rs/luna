package world.player.skill.crafting.jewelleryMaking

import api.predef.*
import io.luna.game.action.Action
import io.luna.game.action.impl.ItemContainerAction.InventoryAction
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player

/**
 * An [InventoryAction] implementation that strings jewellery.
 */
class StringJewelleryAction(plr: Player, times: Int,
                            private val usedId: Int,
                            private val targetId: Int,
                            private val newId: Int) : InventoryAction(plr, true, 2, times) {

    override fun executeIf(start: Boolean): Boolean = true
    override fun execute() {
        mob.crafting.addExperience(4.0)
    }
    override fun add() = listOf(Item(newId))
    override fun remove() = listOf(Item(usedId), Item(targetId))
}