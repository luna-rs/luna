package world.obj.resource.fillable

import io.luna.game.action.impl.ItemContainerAction.InventoryAction
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player

/**
 * An [InventoryAction] that fills [emptyId] with a [FillableResource].
 *
 * @author lare96
 */
class FillActionItem(
    plr: Player,
    val emptyId: Item,
    val filledId: Item,
    val resource: FillableResource,
    amount: Int
) : InventoryAction(plr, true, 2, amount) {

    override fun remove() = listOf(emptyId)
    override fun add() = listOf(filledId)
    override fun execute() {
        resource.onFill(mob)
    }
}