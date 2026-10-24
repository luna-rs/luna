package game.combat.npcHooks

import api.combat.npc.NpcCombatHandler.combat
import api.predef.*
import io.luna.game.model.LocalProjectile
import io.luna.game.model.mob.Mob

/**
 * The revision 377 horror_spines_travel projectile used by ordinary ranged dagannoths.
 */
val SPINES: (Mob, Mob) -> LocalProjectile = { npc, other ->
    LocalProjectile.followEntity(ctx)
        .setSourceEntity(npc)
        .setTargetEntity(other)
        .setId(294)
        .setTicksToStart(44)
        .setTicksToEnd(3)
        .setStartHeight(36)
        .setEndHeight(36)
        .setInitialSlope(8)
        .build()
}

// Lighthouse level 74 and Waterbirth level 88 variants only. No Dagannoth Kings.
combat(1338, 1339, 1340, 2456, 2887) {
    attack { ranged(null, SPINES, animationId = 1343, range = 6) }
}
