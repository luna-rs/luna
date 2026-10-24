package game.combat.npcHooks

import api.combat.npc.NpcCombatHandler.combat
import api.predef.*
import api.predef.ext.*
import io.luna.game.model.mob.combat.AmmoType

// Barbarians in Barbarian Village
combat(3246, 3247, 3248, 3249, 3250, 3251, 3252, 3253, 3255, 3256, 3257, 3258, 3260, 3261, 3262, 3263) {
    attack {
        melee {
            if (rand(8 of 37)) {
                npc.speak("YYEEEEEAAARRRRGGHHHH!!!");
            }
            it
        }
    }
}

// Lost City 377 all.npc [fai_barbarian_14]: ranged_style, human_bow,
// iron_arrow_launch and iron_arrow_travel. Keep Luna's standard ranged attack policy.
combat(3259) {
    attack {
        ranged(AmmoType.IRON_ARROW, animationId = 426) {
            if (rand(8 of 37)) {
                npc.speak("YYEEEEEAAARRRRGGHHHH!!!")
            }
            it
        }
    }
}
