package game.content.seasonalEvents

import api.predef.ctx
import io.luna.game.model.Position
import io.luna.game.model.chunk.ChunkUpdatableView
import io.luna.game.model.item.GroundItem
import io.luna.game.model.mob.Player

/**
 * A globally visible ground item spawned as part of a seasonal event.
 *
 * Picking up this item increments the seasonal event's global found-item counter.
 *
 * @property id The item ID.
 * @property amount The amount of the item on the ground.
 * @param position The position where the item is spawned.
 * @author lare96
 */
class SeasonalEventGroundItem(val id: Int, val amount: Int, position: Position) :
    GroundItem(ctx, id, amount, position, ChunkUpdatableView.globalView()) {

    override fun onPickedUp(player: Player) {
        // Increment seasonal event quota when picked up by a player.
        SeasonalEventScheduler.incrementFound()
    }
}