package game.combat.npcHooks

import api.combat.npc.NpcCombatHandler.combat
import api.predef.*
import api.predef.ext.*
import game.player.Sound
import io.luna.Luna
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Graphic

/**
 * The wearable earmuffs item id.
 */
val EARMUFFS = 4166

/**
 * The banshee scream graphic.
 */
val GRAPHIC = Graphic(337, 150, 0) // TODO Projectile looks a bit weird.

combat(1612) {
    attack {
        if (rand(1 of 5)) {
            // 1/3 chance of scream attack.
            npc.graphic(GRAPHIC)
            if (other is Player && other.equipment.head?.id != EARMUFFS && Luna.settings().skills().slayerEquipmentNeeded()) {
                // We're wearing earmuffs, use scream attack with max hit of 8 that also drains stats.
                other.playSound(Sound.BANSHEE_ATTACK_NORMAL)
                melee(animationId = -1,
                      maxHit = 8) {
                    other.attack.level = 1
                    other.strength.level = 1
                    other.defence.level = 1
                    other.ranged.level = 1
                    other.magic.level = 1
                    other.agility.level = 1
                    other.sendMessage("You have been weakened.")
                    it
                }
            } else {
                // We're wearing earmuffs, use scream attack with max hit of 2.
                if (other is Player) {
                    other.playSound(Sound.BANSHEE_ATTACK_EARMUFFS)
                }
                melee(animationId = -1,
                      maxHit = 2)
            }
        } else {
            // Use the default attack.
            default()
        }
    }
}