package game.combat.npcHooks

import api.combat.npc.NpcCombatHandler.combat
import api.predef.*
import api.predef.ext.*

// Barbarians in Barbarian Village
combat(3246, 3247, 3248, 3249, 3250, 3251, 3252, 3253, 3255, 3256, 3257, 3258, 3259, 3260, 3261, 3262, 3263) {
    attack {
        melee {
            if (rand(8 of 37)) {
                npc.speak("YYEEEEEAAARRRRGGHHHH!!!");
            }
            it
        }
    }
}