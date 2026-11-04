package game.combat.npcHooks.tzhaar

import api.combat.npc.NpcCombatHandler.combat
import api.predef.*
import io.luna.game.model.LocalProjectile

/**
 * The Fight Cave's Tok-Xil punches a player beside one of its edges and fires its spines from up to 15 tiles away
 * otherwise. The Fight Pit's Tok-Xil keep the shared hook.
 *
 * @author TheLining
 */
combat(2739, 2740) {
    attack {
        if (JadAttack.besideEdge(npc, other)) {
            melee(animationId = 2628)
        } else {
            ranged(null, { npc, other ->
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
            }, animationId = 2633, range = 15)
        }
    }
}
