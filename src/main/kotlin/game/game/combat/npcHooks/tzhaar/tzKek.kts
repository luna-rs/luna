package game.combat.npcHooks.tzhaar

import api.combat.death.DeathHookHandler
import api.combat.npc.NpcCombatHandler.combat
import api.predef.*
import io.luna.game.action.impl.NpcRetreatAction.RetreatPolicy
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.combat.damage.CombatDamageType

/** The level 45 Tz-Kek. */
val TZ_KEK = listOf(2736, 2737)

/** The level 22 Tz-Kek that a level 45 one splits into. */
val TZ_KEK_SPLIT = 2738

/**
 * A level 45 Tz-Kek hits whoever strikes it with melee for 1, whatever the hit did. The split ones don't.
 *
 * @author TheLining
 */
combat(*TZ_KEK.toIntArray()) {
    defend {
        val attacker = other
        if (damage?.type == CombatDamageType.MELEE && attacker.isAlive) attacker.damage(1)
    }
}

/**
 * As a level 45 Tz-Kek disappears it splits into two level 22 ones on its tile, which go for its killer. The rest
 * of its death is the default one.
 */
for (id in TZ_KEK) {
    DeathHookHandler.addNpcHook(id) {
        death {
            val killer = source
            repeat(2) {
                val split = Npc(victim.context, TZ_KEK_SPLIT, victim.position)
                split.respawnTicks = -1
                split.combat.retreatPolicy = RetreatPolicy.NONE
                if (victim.world.npcs.add(split) && killer != null && killer.isAlive) split.combat.attack(killer)
            }
        }
        DeathHookHandler.defaultNpcHook?.invoke(this)
    }
}
