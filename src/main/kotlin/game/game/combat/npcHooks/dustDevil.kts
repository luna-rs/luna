package game.combat.npcHooks

import api.combat.npc.NpcCombatHandler.combat
import api.predef.*
import io.luna.Luna
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.combat.damage.CombatDamageRequest
import io.luna.game.model.mob.combat.damage.CombatDamageType

if (Luna.settings().skills().slayerEquipmentNeeded()) {

    /**
     * The wearable facemask item id.
     */
    val FACEMASK = 4166

    combat(1624, 1625) {
        attack {
            // Weaken the player on the first attack.
            if (other is Player && other.equipment.head?.id != FACEMASK) {
                melee {
                    // Fully drain some skills, drain others by half.
                    other.attack.level = 1
                    other.strength.level = 1
                    other.ranged.level = 1
                    other.magic.level = 1
                    val agility = other.agility.weaken(other.agility.level / 2)
                    val defence = other.defence.weaken(other.defence.level / 2)
                    val prayer = other.prayer.weaken(other.prayer.level / 2)
                    if (agility && defence && prayer) {
                        // Only send message on first weaken.
                        other.sendMessage("You have been weakened.")
                    }

                    // Always deal 16 damage.
                    CombatDamageRequest.builder(npc, other, CombatDamageType.MELEE)
                        .setBaseAccuracy(1.0)
                        .setFlatBonusAccuracy(1.0)
                        .setBaseMaxHit(0)
                        .setFlatBonusDamage(16)
                        .ignoreProtectionPrayers()
                        .build().resolve()
                }
            } else {
                default()
            }
        }
    }
}
