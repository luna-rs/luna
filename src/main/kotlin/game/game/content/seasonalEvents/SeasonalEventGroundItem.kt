package game.content.seasonalEvents

import api.predef.ctx
import api.predef.ext.toTicks
import io.luna.game.model.Position
import io.luna.game.model.chunk.ChunkUpdatableView
import io.luna.game.model.item.GroundItem
import io.luna.game.model.item.GroundItemList
import io.luna.game.model.mob.Player
import kotlin.math.floor
import kotlin.time.Duration.Companion.minutes

/**
 * A globally visible ground item spawned as part of a [SeasonalEvent].
 *
 * Seasonal event items remain on the ground longer than standard global ground items. When a non-consumable
 * seasonal item is picked up, the pickup count for its associated [event] is incremented.
 *
 * @property id The item ID.
 * @property amount The amount of the item on the ground.
 * @param position The world position where the item is spawned.
 * @property event The seasonal event that spawned this item.
 *
 * @author lare96
 */
class SeasonalEventGroundItem(
    val id: Int,
    val amount: Int,
    position: Position,
    val event: SeasonalEvent
) : GroundItem(ctx, id, amount, position, ChunkUpdatableView.globalView()) {

    companion object {

        /**
         * The amount of time seasonal event items remain globally visible before they can expire.
         */
        private val SEASONAL_ITEM_EXPIRE_TICKS = 30.minutes.toTicks()
    }

    /**
     * The number of normal global expiration intervals that must pass before this item is allowed to expire.
     */
    private var skip = floor(SEASONAL_ITEM_EXPIRE_TICKS.toDouble() / GroundItemList.GLOBAL_TICKS).toInt()

    override fun onGlobalExpiration(): Boolean {
        // Skip expiration if needed. Seasonal ground items persist longer than regular ones.
        return --skip <= 0
    }

    override fun onPickedUp(player: Player) {
            if (id !in SeasonalEvent.CONSUMABLES) {
                // Increment seasonal event quota when picked up by a player.
            SeasonalEventPersistence.incrementPickups(event)
        }
    }
}