package game.content.barrows

import api.combat.death.DeathHookHandler
import io.luna.game.model.EntityState
import io.luna.game.model.mob.Player

// Lost City's barrows_catacombs_monsters.rs2 increments progress before the normal NPC death.
// Luna supplies the highest-damage attacker as source. Restrict credit to an active player in Barrows.
for (monster in BarrowsMonster.entries) {
    DeathHookHandler.addNpcHook(monster.npcId) {
        death {
            val killer = source
            if (killer is Player && killer.state == EntityState.ACTIVE &&
                BarrowsAreaListener.inside(killer.position) && BarrowsAreaListener.inside(victim.position)) {
                BarrowsProgress.recordMonsterKill(killer, monster)
            }
        }
        DeathHookHandler.defaultNpcHook?.invoke(this)
    }
}
