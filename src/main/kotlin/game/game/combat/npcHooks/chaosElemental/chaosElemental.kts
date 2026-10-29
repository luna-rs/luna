package game.combat.npcHooks.chaosElemental

import api.combat.npc.NpcCombatHandler.combat
import api.predef.*
import game.combat.npcHooks.chaosElemental.ChaosElementalAttack.Companion.CHAOS_ELEMENTAL
import game.player.Sound
import io.luna.game.model.mob.Player

combat(CHAOS_ELEMENTAL) {
    attack {
        ChaosElementalAttack(npc, other, ChaosElementalAttack.choose(other, rand()))
    }
    defend {
        // It has no block animation. Sending its -1 would cut short the attack animation it's playing.
        animationId = null
        // The player landing the hit hears the boss: its death cry on the killing blow, otherwise its hit sound.
        val player = other as? Player ?: return@defend
        val amount = damage?.rawAmount ?: 0
        when {
            amount >= npc.health -> player.playSound(Sound.CHAOS_ELEMENTAL_DEATH)
            amount > 0 -> player.playSound(Sound.CHAOS_ELEMENTAL_HIT)
        }
    }
}
