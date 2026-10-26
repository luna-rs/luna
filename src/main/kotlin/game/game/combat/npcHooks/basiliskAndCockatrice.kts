package game.combat.npcHooks

import api.combat.npc.NpcCombatHandler.combat
import api.predef.*
import game.skill.slayer.Slayer.drainCombatSkills
import io.luna.Luna
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.combat.damage.CombatDamageRequest
import io.luna.game.model.mob.combat.damage.CombatDamageType

if (Luna.settings().skills().slayerEquipmentNeeded()) {

    /**
     * The mirror shield item id.
     */
    val MIRROR_SHIELD = 4156

    // Basilisk and cockatrice variants require a mirror shield; kurasks use their own combat hook.
    combat(1616, 1617, 1620, 1621) {
        attack {
            melee {
                if (other is Player && other.equipment.shield?.id != MIRROR_SHIELD) {
                    val basilisk = npc.id == 1616 || npc.id == 1617
                    val damagePercent = if (basilisk) 13 else 12
                    // Resolve prayer protection before draining prayer points, as in the reference gaze scripts.
                    val gazeHit = CombatDamageRequest.Builder(attacker, victim, CombatDamageType.MELEE)
                        .setBaseMaxHit(0)
                        .setBaseAccuracy(1.0)
                        .setFlatBonusAccuracy(1.0)
                        .setFlatBonusDamage(other.hitpoints.staticLevel * damagePercent / 100)
                        .build().resolve()
                    other.drainCombatSkills(if (basilisk) 60 else 40, if (basilisk) 30 else 20)
                    other.sendMessage("You have been weakened.")
                    gazeHit
                } else {
                    // Has mirror shield, return the original damage amount.
                    it
                }

            }
        }
    }
}
