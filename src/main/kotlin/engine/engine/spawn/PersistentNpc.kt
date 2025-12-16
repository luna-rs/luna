package engine.spawn

import api.predef.*
import io.luna.game.model.Position
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.wandering.WanderingFrequency

/**
 * An [Npc] implementation that always respawns at some point after death. Default is `30` seconds.
 *
 * @author lare96
 */
class PersistentNpc(id: Int, position: Position,
                    private val respawnAfter: Int? = null,
                    private val wanderingRadius: Int? = null,
                    private val wanderingFrequency: WanderingFrequency? = null) : Npc(ctx, id, position) {

    override fun onActive() {
        if (wanderingRadius != null && wanderingFrequency != null) {
            startWandering(wanderingRadius, wanderingFrequency);
        } else if (definition.name.equals("Imp")) {
            startWandering(1500, WanderingFrequency.NORMAL);
        }
        if (respawnAfter != null) {
            respawnTicks = respawnAfter
        }
    }
}