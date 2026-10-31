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
        soundId = null
        // It has no block animation. Sending its -1 would cut short the attack animation it's playing.
        animationId = null
        // The death cry belongs to the shared death sequence after the hit has committed.
        val player = other as? Player ?: return@defend
        val amount = damage?.rawAmount ?: 0
        if (amount > 0 && amount < npc.health) player.playSound(Sound.CHAOS_ELEMENTAL_HIT)
    }
}
