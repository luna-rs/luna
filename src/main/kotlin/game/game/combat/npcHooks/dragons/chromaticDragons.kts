package game.combat.npcHooks.dragons

import api.combat.npc.NpcCombatHandler.combat
import api.predef.*

// Chromatic dragons have a small chance to deal dragonfire.
combat(941, 55, 53, 54) {
    attack {
        if (rand(5) == 0) {
            DragonFireCombatAttack(npc, other)
        } else {
            melee(animationId = if (rand(2) == 0) 91 else 80)
        }
    }
}
