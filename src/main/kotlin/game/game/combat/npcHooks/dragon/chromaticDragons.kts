package game.combat.npcHooks.dragon

import api.combat.npc.NpcCombatHandler.combat
import api.predef.*

// Green, blue, red, and black adult dragons.
combat(941, 55, 53, 54) {
    attack {
        if (rand(5) == 0) {
            DragonFireCombatAttack(npc, other)
        } else {
            melee(animationId = if (rand(2) == 0) 91 else 80)
        }
    }
}
