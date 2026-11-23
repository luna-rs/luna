package game.skill.cooking.prepareFood

import api.predef.*
import io.luna.game.action.impl.ItemContainerAction.InventoryAction
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player

/**
 * An [InventoryAction] that cuts fruit into slices or chunks with a knife. The knife is kept.
 */
class CutFruitActionItem(plr: Player, private val fruit: Int, private val product: Int, amount: Int) :
    InventoryAction(plr, true, 1, amount) {

    override fun executeIf(start: Boolean) = mob.inventory.contains(Mix.KNIFE)

    override fun execute() {
        val cut = itemName(product).substringAfterLast(' ').lowercase()
        mob.sendMessage("You cut the ${itemName(fruit).lowercase()} into $cut.")
    }

    override fun remove() = listOf(Item(fruit))

    override fun add() = listOf(Item(product))
}
