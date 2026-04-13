package game.combat.npcHooks

import api.combat.npc.NpcCombatHandler.combat
import api.predef.*
import io.luna.Luna
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.combat.damage.CombatDamageRequest
import io.luna.game.model.mob.combat.damage.CombatDamageType
import io.luna.util.RandomUtils

if (Luna.settings().skills().slayerEquipmentNeeded()) {

    /**
     * Basilisk NPC ids.
     */
    val BASILISK = setOf(1616, 1617)

    /**
     * Cockatrice NPC ids.
     */
    val COCKATRICE = setOf(1608, 1609)

    /**
     * The mirror shield item id.
     */
    val MIRROR_SHIELD = 4156

    for (id in BASILISK + COCKATRICE) {
        /**
         * Handles basilisk and cockatrice attack effects.
         *
         * When a player is attacked without a mirror shield equipped, the NPC has a chance to apply stat drains to one
         * or more combat skills, notify the player that they have been weakened, and deal a much more accurate attack.
         */
        combat(id) {
            attack {
                melee {
                    if (other is Player && other.equipment.shield?.id != MIRROR_SHIELD) {
                        if (RandomUtils.random()) {
                            if (RandomUtils.random()) {
                                other.attack.adjustLevel(-rand(10, 20))
                            }
                            if (RandomUtils.random()) {
                                other.strength.adjustLevel(-rand(10, 20))
                            }
                            if (RandomUtils.random()) {
                                other.defence.adjustLevel(-rand(10, 20))
                            }
                            if (RandomUtils.random()) {
                                other.ranged.adjustLevel(-rand(10, 20))
                            }
                            if (RandomUtils.random()) {
                                other.magic.adjustLevel(-rand(10, 20))
                            }
                            other.sendMessage("You have been weakened.")
                        }
                        CombatDamageRequest.Builder(attacker, victim, CombatDamageType.MELEE)
                            .setBaseMaxHit(11)
                            .setBaseAccuracy(0.9)
                            .build().resolve()
                    } else {
                        it
                    }

                }
            }
        }
    }
}