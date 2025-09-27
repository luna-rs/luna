package world.npc.globalSpawn

import api.predef.*
import io.luna.game.model.Position
import io.luna.game.model.mob.Npc

/**
 * An [Npc] implementation that always respawns at some point after death. Default is `30` seconds.
 */
class PersistentNpc(id: Int, position: Position, val respawnTicks: Int = 50,
                    private val wanderRadius: Int = 0) : Npc(ctx, id, position) {

    override fun onActive() {
        setRespawning()
        setWandering(wanderRadius)
    }
}