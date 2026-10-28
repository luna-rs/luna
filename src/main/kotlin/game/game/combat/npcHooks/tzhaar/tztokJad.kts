package game.combat.npcHooks.tzhaar

import api.combat.npc.NpcCombatHandler.combat
import api.predef.*

/** TzTok-Jad. */
val JAD = 2745

/** Jad's melee attack: only when the player stands next to him, and it comes every 4 ticks. */
val MELEE_ANIMATION = 2655
val MELEE_MAX_HIT = 97
val MELEE_SPEED = 4

combat(JAD) {
    attack {
        // Next to him he picks any of the three styles; further away only magic or ranged.
        when (rand().nextInt(if (JadAttack.besideEdge(npc, other)) 3 else 2)) {
            0 -> JadAttack(npc, other, true)
            1 -> JadAttack(npc, other, false)
            // Melee reach is measured from his south-west tile, so it spans his whole body; melee is only picked
            // when the player is beside one of his edges.
            else -> melee(animationId = MELEE_ANIMATION, maxHit = MELEE_MAX_HIT, range = npc.size(),
                speed = MELEE_SPEED)
        }
    }
    defend {
        JadHealers.onJadHit(npc, other, damage?.rawAmount ?: 0)
    }
}

combat(JadHealers.HEALER) {
    defend {
        JadHealers.onHealerHit(npc, other, damage?.rawAmount ?: 0)
    }
}
