package engine.spawn

import api.predef.*
import api.predef.ext.*
import io.luna.game.model.Position
import io.luna.game.model.chunk.ChunkUpdatableView
import io.luna.game.model.item.GroundItem
import io.luna.game.model.mob.Player

/**
 * A [GroundItem] implementation that never expires and respawns once it's picked up.
 *
 * @author lare96
 */
class PersistentGroundItem(id: Int, amount: Int = 1, position: Position, val respawnTicks: Int = DEFAULT_RESPAWN_TICKS) :
    GroundItem(ctx, id, amount, position, ChunkUpdatableView.globalView()) {

    companion object {
        const val DEFAULT_RESPAWN_TICKS = 100
    }

    /**
     * Confirmed collector of this exact spawn, retained only while a caller still observes the removed item.
     * A deletion or disappearance without a native pickup leaves this unset.
     */
    var collectedBy: String? = null
        private set

    override fun onPickedUp(player: Player) {
        collectedBy = player.username
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
