package world.player.`object`.resource

import io.luna.game.action.Action
import io.luna.game.action.ProducingAction
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player

/**
 * A [ProducingAction] that fills [emptyId] with a [Resource].
 *
 * @author lare96
 */
class FillAction(plr: Player,
                 val emptyId: Item,
                 val objectId: Int?,
                 val filledId: Item,
                 val resource: Resource,
                 amount: Int) : ProducingAction(plr, true, 2, amount) {

    override fun remove() = arrayOf(emptyId)
    override fun add() = arrayOf(filledId)
    override fun onProduce() {
        resource.onFill(mob)
    }

    override fun isEqual(other: Action<*>): Boolean {
        return when (other) {
            is FillAction -> resource == other.resource &&
                    objectId == other.objectId &&
                    emptyId == other.emptyId &&
                    filledId == other.filledId
            else -> false
        }
    }
}