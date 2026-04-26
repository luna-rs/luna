package game.combat.npcHooks

import api.combat.npc.NpcCombatHandler.combat
import io.luna.Luna
import io.luna.game.action.impl.LockedAction
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.mob.block.Animation.AnimationPriority
import io.luna.game.model.mob.block.Graphic
import io.luna.game.model.mob.combat.damage.CombatDamageRequest

//Ice cooler -> Same concept as bag of salt for desert lizards under 5hp

if (Luna.settings().skills().slayerEquipmentNeeded()) {

    val HITPOINTS_THRESHOLD = 5
    val ICE_COOLER = 6696
    val ANIMATION = Animation(1574)
    val GRAPHIC = Graphic(456, 0, 0)

    combat(2804, 2805, 2806) {
        defend {
            val damageAmount = damage?.rawAmount ?: 0
            if (other is Player && npc.health - damageAmount <= HITPOINTS_THRESHOLD) {
                npc.combat.prepareForExecution()
                if (other.inventory.remove(ICE_COOLER)) {
                    damage = null
                    other.submitAction(object : LockedAction(other, false, 1) {
                        override fun run(): Boolean {
                            if (executions == 0) {
                                other.animation(ANIMATION)
                                npc.graphic(GRAPHIC)
                                other.sendMessage("You throw some water onto the desert lizard.")
                                npc.animation(Animation(npc.combatDef().deathAnimation, AnimationPriority.HIGH))
                            } else if (executions == 1) {
                                npc.health = 0
                                return true
                            }
                            return false
                        }
                    })
                } else {
                    damage = CombatDamageRequest.zero(other, npc).resolve()
                }
            }
        }
    }
}

