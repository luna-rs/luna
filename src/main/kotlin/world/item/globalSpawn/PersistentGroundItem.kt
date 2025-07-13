package world.item.globalSpawn

import api.predef.ctx
import api.predef.ext.addItem
import api.predef.ext.scheduleOnce
import io.luna.game.model.Position
import io.luna.game.model.chunk.ChunkUpdatableView
import io.luna.game.model.item.GroundItem

/**
 * A [GroundItem] implementation that never expires and respawns once it's picked up.
 */
class PersistentGroundItem(
    id: Int,
    amount: Int = 1,
    position: Position,
    val respawnTicks: Int = DEFAULT_RESPAWN_TICKS
) :
    GroundItem(ctx, id, amount, position, ChunkUpdatableView.globalView()) {

    companion object {
        const val DEFAULT_RESPAWN_TICKS = 100
    }

    override fun onActive() {
        // Never expires.
        setExpire(false)
    }

    override fun onInactive() {
        // Respawn if item is deleted, picked up, etc.
        world.scheduleOnce(respawnTicks) {
            world.addItem(PersistentGroundItem(id, amount, position, respawnTicks))
        }
    }
}