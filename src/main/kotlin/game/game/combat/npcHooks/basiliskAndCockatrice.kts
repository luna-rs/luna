package game.combat.npcHooks

import api.combat.npc.NpcCombatHandler.combat
import api.predef.*
import io.luna.Luna
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.combat.damage.CombatDamageRequest
import io.luna.game.model.mob.combat.damage.CombatDamageType

if (Luna.settings().skills().slayerEquipmentNeeded()) {

    /**
     * The mirror shield item id.
     */
    val MIRROR_SHIELD = 4156

    combat(1608, 1609, 1616, 1617) {
        attack {
            melee {
                if (other is Player && other.equipment.shield?.id != MIRROR_SHIELD) {
                    // No mirror shield, weaken player and generate stronger attack.
                    if (randBoolean()) {
                        other.attack.adjustLevel(-rand(10, 20))
                    }
                    if (randBoolean()) {
                        other.strength.adjustLevel(-rand(10, 20))
                    }
                    if (randBoolean()) {
                        other.defence.adjustLevel(-rand(10, 20))
                    }
                    if (randBoolean()) {
                        other.ranged.adjustLevel(-rand(10, 20))
                    }
                    if (randBoolean()) {
                        other.magic.adjustLevel(-rand(10, 20))
                    }
                    other.sendMessage("You have been weakened.")
                    CombatDamageRequest.Builder(attacker, victim, CombatDamageType.MELEE)
                        .setBaseMaxHit(11)
                        .setBaseAccuracy(0.9)
                        .build().resolve()
                } else {
                    // Has mirror shield, return the original damage amount.
                    it
                }

            }
        }
    }
}