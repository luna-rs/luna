package game.content.barrows

import api.predef.ext.schedule
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.mob.Mob
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.NpcAggressionProfile
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.combat.CombatRestriction

/**
 * One player's non-respawning brother encounter. Ownership uses object identity, not a reusable player index.
 * Lost City's brother timer removes encounters on logout, death or loss of range; lifetime is 1,000 ticks.
 *
 * @author lare96
 */
class BarrowsBrotherNpc(val owner: Player, val brother: BarrowsBrother, position: Position) :
    Npc(owner.context, brother.npcId, position), CombatRestriction {

    init {
        respawnTicks = -1
    }

    override fun getAggressionProfile(): NpcAggressionProfile? = null

    override fun canFight(opponent: Mob): Boolean = opponent === owner && owner.state == EntityState.ACTIVE &&
        owner.isAlive && state == EntityState.ACTIVE && BarrowsAreaListener.inside(owner.position) &&
        position.isWithinDistance(owner.position, Position.VIEWING_DISTANCE)

    override fun onSpawn() {
        var elapsed = 0
        world.schedule(1) { task ->
            if (state != EntityState.ACTIVE) {
                task.cancel()
            } else if (isAlive && (!canFight(owner) || ++elapsed >= 1000)) {
                if (elapsed >= 1000 && owner.state == EntityState.ACTIVE) {
                    owner.sendMessage("We'll finish this later...")
                }
                world.npcs.remove(this)
                task.cancel()
            } else if (isAlive && combat.target == null) {
                combat.attack(owner)
            }
        }
    }

    override fun onInactive() {
        BarrowsEncounters.finished(this)
    }
}
