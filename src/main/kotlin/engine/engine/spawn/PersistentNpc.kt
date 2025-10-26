package engine.spawn

import api.predef.*
import io.luna.game.model.Position
import io.luna.game.model.mob.Npc

/**
 * An [Npc] implementation that always respawns at some point after death. Default is `30` seconds.
 *
 * @author lare96
 */
class PersistentNpc(id: Int, position: Position, val respawnAfter: Int? = null,
                    private val wanderRadius: Int = 0) : Npc(ctx, id, position) {

    override fun onActive() {
        setRespawning()
        setWandering(wanderRadius)
        if (respawnAfter != null) {
            respawnTicks = respawnAfter
        } else {
            respawnTicks = combatDef.map { it.respawnTime }.filter { it > 0 }.orElse(50)
        }
    }
}