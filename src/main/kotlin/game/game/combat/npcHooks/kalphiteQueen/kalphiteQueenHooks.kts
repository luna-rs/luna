package game.combat.npcHooks.kalphiteQueen

import api.combat.death.DeathHookHandler
import api.combat.npc.NpcCombatHandler.combat
import api.predef.*
import api.predef.ext.*
import game.player.Sound
import io.luna.game.model.LocalSound
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.mob.block.Animation.AnimationPriority

for (id in listOf(1158, 1160)) {
    combat(id) {
        attack {
            when (rand().nextInt(if (npc.isWithinDistance(other, 1)) 3 else 2)) {
                0 -> KalphiteQueenAttack(npc, other, true)
                1 -> KalphiteQueenAttack(npc, other, false)
                else -> melee(animationId = (if (npc.id == 1158) 1184 else 1177) + rand().nextInt(2))
            }
        }
        defend {
            // The head icons represent defence bonuses, not complete immunity to those attack styles.
            if ((npc.id == 1160 || npc.health - (damage?.rawAmount ?: 0) < 175) && rand().nextInt(20) == 0) {
                KalphiteQueen.hatch(npc)
            }
        }
    }
}

DeathHookHandler.addNpcHook(1158) {
    preDeath(reset = false) {
        victim.combat.damageStack.clear()
        victim.animation(Animation(1187, AnimationPriority.IMMUTABLE))
        LocalSound.of(victim, Sound.KALPHITE_CRACK_OPEN).display()
    }
    // The first form produces neither loot nor a Slayer kill. Transform only after the last death stage,
    // so the default second-form death hook cannot consume the first form's POST_DEATH callback.
    death { }
    postDeath(reset = false) {
        val shell = Npc(victim.context, 1159, victim.position)
        shell.respawnTicks = -1
        victim.world.npcs.add(shell)
        shell.animation(Animation(1187))
        victim.world.scheduleOnce(3) { victim.world.npcs.remove(shell) }
        KalphiteQueen.rise(victim)
    }
}
