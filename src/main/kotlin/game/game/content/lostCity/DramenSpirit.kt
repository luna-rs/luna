/*
 * Adapted from LostCityRS/Content scripts/quests/quest_zanaris and areas/area_lumbridge,
 * revision 44eab09081ef0f6c5f3eaabd1897a588ad7d9e50.
 */
package game.content.lostCity

import api.predef.ext.schedule
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.mob.Mob
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.NpcAggressionProfile
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.combat.CombatRestriction

/**
 * The Dramen tree's level 101 guardian. Leaving, logout or timeout requires another fight, while a victory persists.
 *
 * @author lare96
 */
class DramenSpirit(val owner: Player, position: Position) : Npc(owner.context, LostCity.SPIRIT, position), CombatRestriction {
    init { respawnTicks = -1 }

    override fun getAggressionProfile(): NpcAggressionProfile? = null

    override fun canFight(opponent: Mob): Boolean = opponent === owner && owner.state == EntityState.ACTIVE &&
        owner.isAlive && state == EntityState.ACTIVE && LostCity.stage(owner) == LostCity.SPOKEN_SHAMUS &&
        owner.z == z && owner.position.isWithinDistance(LostCity.SPIRIT_SPAWN, 15)

    override fun onSpawn() {
        var age = 0
        world.schedule(1) { task ->
            if (state != EntityState.ACTIVE) task.cancel()
            else if (!canFight(owner) || ++age >= 1000) {
                world.npcs.remove(this)
                task.cancel()
            }
        }
    }

    override fun onInactive() { LostCity.removed(this) }
}
