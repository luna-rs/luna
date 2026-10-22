package game.content.barrows

import api.predef.ext.schedule
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.NpcAggressionProfile
import io.luna.game.model.mob.Player

/**
 * A non-respawning door monster with Lost City's 500-tick lifetime.
 * It initially targets the player opening the door, but ordinary tunnel monsters are not owned fights.
 *
 * @author lare96
 */
class BarrowsTunnelNpc(private val opener: Player, monster: BarrowsMonster, position: Position) :
    Npc(opener.context, monster.npcId, position) {
    init {
        respawnTicks = -1
    }

    override fun getAggressionProfile(): NpcAggressionProfile? = null

    override fun onSpawn() {
        combat.attack(opener)
        world.schedule(500) { task ->
            if (state == EntityState.ACTIVE) world.npcs.remove(this)
            task.cancel()
        }
    }
}
