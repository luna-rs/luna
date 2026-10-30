package game.content.mageArena

import api.predef.ext.schedule
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.mob.Mob
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.NpcAggressionProfile
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.combat.CombatRestriction

/**
 * One player's current Kolodion form, with no loot or automatic respawn.
 * Ownership checks also cover delayed and secondary attacks through CombatRestriction.
 *
 * @author lare96
 */
class MageArenaNpc(val owner: Player, val form: Int, position: Position) :
    Npc(owner.context, 906 + form, position), CombatRestriction {
    init {
        require(form in 1..5)
        respawnTicks = -1
    }

    override fun getAggressionProfile(): NpcAggressionProfile? = null
    override fun canFight(opponent: Mob): Boolean = opponent === owner && MageArena.live(owner) &&
        MageArena.arena(owner.position) && state == EntityState.ACTIVE && MageArena.stage(owner) == form &&
        owner.position.isWithinDistance(position, Position.VIEWING_DISTANCE)
    override fun onSpawn() {
        var ticks = 0
        world.schedule(1) { task ->
            if (state != EntityState.ACTIVE) task.cancel()
            else if (!canFight(owner) || ++ticks >= 1000) { world.npcs.remove(this); task.cancel() }
            else if (isAlive && combat.target == null) combat.attack(owner)
        }
    }
    override fun onInactive() { MageArena.finished(this) }
}
