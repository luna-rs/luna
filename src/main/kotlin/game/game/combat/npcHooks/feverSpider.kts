package game.combat.npcHooks

import api.combat.npc.NpcCombatHandler.combat
import api.predef.*
import io.luna.Luna
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.combat.damage.CombatDamageRequest
import io.luna.game.model.mob.combat.damage.CombatDamageType
import kotlin.math.floor

if (Luna.settings().skills().slayerEquipmentNeeded()) {

    /**
     * The slayer gloves item id.
     */
    val SLAYER_GLOVES = 6708

    /**
     * Applies the fever spider glove penalty.
     *
     * If the victim is a player and is not wearing slayer gloves, the spider:
     * - Poisons the player if they are not already poisoned
     * - Adds bonus damage equal to 12.5% of the player's current hitpoints
     * - Forces the hit to use perfect base accuracy
     *
     * Players wearing slayer gloves use the normal melee damage request unchanged.
     */
    combat(2850) {
        attack {
            melee {
                if (other is Player && other.equipment.hands?.id != SLAYER_GLOVES) {
                    if (other.combat.poisonSeverity == 0) {
                        other.combat.poisonSeverity = 20
                    }

                    CombatDamageRequest.Builder(attacker, victim, CombatDamageType.MELEE)
                        .setBaseMaxHit(5)
                        .setFlatBonusDamage(floor(0.125 * other.health).toInt())
                        .setBaseAccuracy(1.0)
                        .build().resolve()
                } else {
                    it
                }
            }
        }
    }
}