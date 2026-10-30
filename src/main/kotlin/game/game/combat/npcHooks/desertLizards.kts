package game.combat.npcHooks

import api.combat.npc.NpcCombatHandler.combat
import io.luna.Luna
import io.luna.game.action.impl.LockedAction
import io.luna.game.model.item.Equipment.EquipmentBonus
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.mob.block.Animation.AnimationPriority
import io.luna.game.model.mob.block.Graphic

//Ice cooler -> Same concept as bag of salt for desert lizards under 5hp

/**
 * Whether the native Slayer-item restriction is enabled; the creature's outgoing attack style is always retained.
 */
val EQUIPMENT_NEEDED = Luna.settings().skills().slayerEquipmentNeeded()

/**
 * The highest remaining health eligible for an ice-cooler finish, inclusive.
 */
val HITPOINTS_THRESHOLD = 4
val ICE_COOLER = 6696
val ANIMATION = Animation(1574)
val GRAPHIC = Graphic(456, 0, 0)

/**
 * Starts the cooling sequence only while both participants are alive and active.
 * Once the water has been thrown, its delayed finish may still land after the player's death.
 */
combat(2803, 2804, 2805, 2806, 2807, 2808) {
    attack {
        combat.setAttackBonusType(EquipmentBonus.CRUSH_ATTACK)
        melee()
    }
    poisonCanKill = !EQUIPMENT_NEEDED
    defend {
        val damageAmount = damage?.rawAmount ?: 0
        if (EQUIPMENT_NEEDED && other is Player && npc.health - damageAmount <= HITPOINTS_THRESHOLD) {
            damage = damage?.cappedAt(npc.health - 1)
            npc.combat.prepareForExecution()
            if (!npc.combat.isExecutionPending && other.inventory.remove(ICE_COOLER)) {
                val finishingAttack = source
                val finishingAction = object : LockedAction(other, false, 1) {
                    override fun onUnlock() {
                        npc.combat.clearExecution(this)
                    }

                    override fun run(): Boolean {
                        if (!npc.isAlive || (executions == 0 && !other.isAlive)) {
                            return true
                        }
                        if (executions == 0) {
                            other.animation(ANIMATION)
                            npc.graphic(GRAPHIC)
                            other.sendMessage("You throw some water onto the desert lizard.")
                            npc.animation(Animation(npc.combatDef().deathAnimation, AnimationPriority.HIGH))
                        } else if (executions == 1) {
                            npc.combat.finishExecution(finishingAttack)
                            return true
                        }
                        return false
                    }
                }
                npc.combat.trackExecution(finishingAction)
                other.submitAction(finishingAction)
            }
        }
    }
}
