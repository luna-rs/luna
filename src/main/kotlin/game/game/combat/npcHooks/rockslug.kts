package game.combat.npcHooks

import api.combat.npc.NpcCombatHandler.combat
import io.luna.Luna
import io.luna.game.action.impl.LockedAction
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.mob.block.Animation.AnimationPriority
import io.luna.game.model.mob.block.Graphic
import io.luna.game.model.mob.combat.damage.CombatDamageRequest

if (Luna.settings().skills().slayerEquipmentNeeded()) {

    /**
     * The lowest health a rockslug can be reduced to through normal damage.
     */
    val HITPOINTS_THRESHOLD = 5

    /**
     * The bag of salt item id.
     */
    val BAG_OF_SALT = 4161

    /**
     * The animation played when a player uses salt on a weakened rockslug.
     */
    val SALT_ANIMATION = Animation(1574)

    /**
     * The graphic displayed when salt is used on a weakened rockslug.
     */
    val SALT_GRAPHIC = Graphic(327, 0, 0)

    /*
     * Handles rockslug finishing behaviour.
     *
     * When an incoming hit would reduce a rockslug to or below the finishing threshold, the normal damage is cancelled and
     * the slug is left at 1 hitpoint. If the attacker is a player carrying a bag of salt, the player performs the
     * salting sequence and the slug is killed shortly after.
     *
     * If the player does not have salt, they will always hit 0.
     */
    combat(1622, 1623) {
        defend {
            val damageAmount = damage?.rawAmount ?: 0
            if (other is Player && npc.health - damageAmount <= HITPOINTS_THRESHOLD) {

                if (other.inventory.remove(BAG_OF_SALT)) {
                    damage = null
                    npc.combat.isDisabled = true
                    npc.walking.isLocked = true
                    npc.health = 1
                    other.combat.target = null
                    other.submitAction(object : LockedAction(other, false, 1) {
                        override fun run(): Boolean {
                            if (executions == 0) {
                                other.animation(SALT_ANIMATION)
                                npc.graphic(SALT_GRAPHIC)
                                other.sendMessage("You pour some salt on the Rockslug.")
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