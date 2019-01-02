package world.player.item.fill

import io.luna.game.action.Action
import io.luna.game.action.ProducingAction
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player

class FillAction(plr: Player,
                 val objectId: Int?,
                 val emptyId: Item,
                 val filledId: Item,
                 val source: Source,
                 var amount: Int) : ProducingAction(plr, true, 2) {

    override fun remove() = arrayOf(emptyId)
    override fun add() = arrayOf(filledId)
    override fun onProduce() {
        source.onFill(mob)
        amount--
        if (amount == 0) {
            interrupt()
        }
    }

    override fun isEqual(other: Action<*>): Boolean {
        return when (other) {
            is FillAction -> source == other.source &&
                    objectId == other.objectId &&
                    emptyId == other.emptyId &&
                    filledId == other.filledId
            else -> false
        }
    }
}