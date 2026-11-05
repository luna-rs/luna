package game.combat.npcHooks

import api.combat.npc.NpcCombatHandler.combat
import api.predef.*
import io.luna.game.model.LocalProjectile
import io.luna.game.model.mob.Mob

/**
 * The revision 377 tzhaar_spine_attack projectile.
 */
val SPINES: (Mob, Mob) -> LocalProjectile = { npc, other ->
    LocalProjectile.followEntity(ctx)
        .setSourceEntity(npc)
        .setTargetEntity(other)
        .setId(443)
        .setTicksToEnd(44)
        .setTicksToStart(3)
        .setStartHeight(36)
        .setEndHeight(36)
        .setInitialSlope(8)
        .build()
}

// Fight Pit Tok-Xil. The Fight Cave's have their own hook, and Jad is deliberately excluded.
combat(2631, 2632) {
    attack {
        if (npc.isWithinDistance(other, 1) && randBoolean()) {
            melee(animationId = 2628)
        } else {
            ranged(null, SPINES, animationId = 2633, range = 6)
        }
    }
}
