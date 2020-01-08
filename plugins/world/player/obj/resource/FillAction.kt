package world.player.obj.resource

import io.luna.game.action.Action
import io.luna.game.action.InventoryAction
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player

/**
 * An [InventoryAction] that fills [emptyId] with a [Resource].
 *
 * @author lare96
 */
class FillAction(plr: Player,
                 val emptyId: Item,
                 val objectId: Int?,
                 val filledId: Item,
                 val resource: Resource,
                 amount: Int) : InventoryAction(plr, true, 2, amount) {

    override fun remove() = listOf(emptyId)
    override fun add() = listOf(filledId)
    override fun execute() {
        resource.onFill(mob)
    }

    override fun ignoreIf(other: Action<*>): Boolean {
        return when (other) {
            is FillAction -> resource == other.resource &&
                    objectId == other.objectId &&
                    emptyId == other.emptyId &&
                    filledId == other.filledId
            else -> false
        }
    }
}