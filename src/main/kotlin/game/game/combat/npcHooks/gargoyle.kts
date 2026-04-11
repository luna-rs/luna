package game.combat.npcHooks

import api.combat.npc.NpcCombatHandler.combat
import api.combat.player.PlayerCombatHandler.playerAttack
import io.luna.Luna
import io.luna.game.action.impl.LockedAction
import io.luna.game.model.mob.Mob
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.mob.block.Animation.AnimationPriority
import io.luna.game.model.mob.combat.attack.CombatAttack
import io.luna.game.model.mob.combat.damage.CombatDamage
import io.luna.game.model.mob.combat.damage.CombatDamageAction
import io.luna.game.model.mob.combat.damage.CombatDamageRequest
import io.luna.game.model.mob.combat.damage.CombatDamageType
import io.luna.game.model.mob.interact.InteractionPolicy
import io.luna.game.model.mob.interact.InteractionType

// Only register hooks if slayer equipment is required.
if (Luna.settings().skills().slayerEquipmentNeeded()) {

    /**
     * If the hitpoints fall below this amount, the gargoyle will turn to stone.
     */
    val HITPOINTS_THRESHOLD = 9

    /**
     * The rock hammer item ID.
     */
    val ROCK_HAMMER = 4162

    /**
     * A [CombatAttack] implementation that makes the player's next attack perform a smash animation.
     *
     * @author lare96
     */
    class GargoyleSmashAttack(player: Player, victim: Mob) :
        CombatAttack<Player>(player, victim, InteractionPolicy(InteractionType.SIZE, 1), 1) {

        override fun attack() {
            attacker.submitAction(object : LockedAction(attacker, false, 2) {
                override fun run(): Boolean {
                    attacker.animation(Animation(1755, AnimationPriority.HIGH))
                    attacker.sendMessage("You smash the Gargoyle into pieces using your Rock hammer.")
                    victim.submitAction(CombatDamageAction(nextDamage, this@GargoyleSmashAttack, true))
                    return true
                }
            })
        }

        override fun calculateDamage(other: Mob): CombatDamage? {
            return CombatDamageRequest.Builder(attacker, other, CombatDamageType.MELEE).setBaseMaxHit(0).build()
                .resolve()
        }
    }

    // Smash the gargoyle instead of doing our regular attack.
    playerAttack { other is Npc && other.id == 1611 }.then {
        if (ROCK_HAMMER in player.inventory) {
            GargoyleSmashAttack(player, other)
        } else {
            player.sendMessage("I need a Rock hammer to finish this creature off.")
            player.combat.target = null
            null
        }
    }

    // Regular gargoyle fell to low HP, turn to stone (transform -> 1611).
    combat(1610) {
        defend {
            val amount = damage?.rawAmount ?: 0
            if (other is Player && npc.health - amount <= HITPOINTS_THRESHOLD) {
                damage = null
                npc.walking.isLocked = true
                npc.combat.isDisabled = true
                npc.interact(null)
                npc.lock(2)
                npc.transform(1611)
                npc.health = 1
            }
        }
    }

    // If the attack is a GargoyleSmashAttack, kill NPC.
    combat(1611) {
        defend {
            if (source is GargoyleSmashAttack) {
                damage = null
                npc.health = 0
            }
        }
    }
}