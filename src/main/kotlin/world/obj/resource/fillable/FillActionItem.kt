package world.obj.resource.fillable

import io.luna.game.action.Action
import io.luna.game.action.ItemContainerAction.InventoryAction
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.`object`.GameObject

/**
 * An [InventoryAction] that fills [emptyId] with a [FillableResource].
 *
 * @author lare96
 */
class FillActionItem(plr: Player,
                     val emptyId: Item,
                     val filledId: Item,
                     val resourceObject: GameObject,
                     val resource: FillableResource,
                     amount: Int) : InventoryAction(plr, true, 2, amount) {

    override fun remove() = listOf(emptyId)
    override fun add() = listOf(filledId)
    override fun execute() {
        resource.onFill(mob)
    }

    override fun ignoreIf(other: Action<*>): Boolean {
        return when (other) {
            is FillActionItem -> resource == other.resource &&
                    resourceObject == other.resourceObject &&
                    emptyId == other.emptyId &&
                    filledId == other.filledId
            else -> false
        }
    }
}