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

if (Luna.settings().skills().slayerEquipmentNeeded()) {

    /**
     * The highest remaining health eligible for a hammer finish, inclusive.
     */
    val HITPOINTS_THRESHOLD = 8

    /**
     * The rock hammer item ID.
     */
    val ROCK_HAMMER = 4162

    /**
     * A [CombatAttack] implementation that makes the player's next attack perform a smash animation.
     * The delayed smash only starts while both participants are alive and active.
     *
     * @author lare96
     */
    class GargoyleSmashAttack(player: Player, victim: Mob) :
        CombatAttack<Player>(player, victim, InteractionPolicy(InteractionType.SIZE, 1), 1) {

        override fun attack() {
            attacker.submitAction(object : LockedAction(attacker, false, 2) {
                override fun run(): Boolean {
                    if (!attacker.isAlive || !victim.isAlive) {
                        return true
                    }
                    attacker.animation(Animation(1755, AnimationPriority.HIGH))
                    attacker.sendMessage("You smash the Gargoyle into pieces using your Rock hammer.")
                    victim.submitAction(CombatDamageAction(nextDamage, this@GargoyleSmashAttack, true))
                    return true
                }
            })
        }

        override fun calculateDamage(other: Mob): CombatDamage? {
            return CombatDamageRequest.effect(attacker, victim).resolve()
        }
    }

    // Smash the gargoyle instead of doing our regular attack.
    playerAttack { other is Npc && other.id == 1611 }.then {
        if (ROCK_HAMMER in player.inventory) {
            GargoyleSmashAttack(player, other)
        } else {
            player.sendMessage("I need a Rock hammer to finish this creature off.")
            player.combat.target = null
            nothing()
        }
    }

    /**
     * Turns a weakened gargoyle to stone while preserving its health before the pending hit is applied.
     * Transforming reloads the NPC's combat skills, including full hitpoints; restoring the captured health
     * prevents healing and duplicate damage credit. The capped triggering hit is still delivered normally.
     */
    combat(1610) {
        poisonCanKill = false
        defend {
            val healthBeforeTransform = npc.health
            val amount = damage?.rawAmount ?: 0
            if (other is Player && healthBeforeTransform - amount <= HITPOINTS_THRESHOLD) {
                damage = damage?.cappedAt(healthBeforeTransform - 1)
                npc.combat.prepareForExecution()
                npc.transform(1611)
                npc.health = healthBeforeTransform
            }
        }
    }

    /**
     * Keeps the stone gargoyle alive until a validated hammer smash delivers the finishing blow.
     * Ordinary hits already queued before transformation may still arrive, but must leave at least one hitpoint.
     * The smash delivers attributed damage without re-entering this finishing hook.
     */
    combat(1611) {
        poisonCanKill = false
        defend {
            if (source is GargoyleSmashAttack) {
                damage = null
                npc.combat.finishExecution(source)
            } else {
                damage = damage?.cappedAt(npc.health - 1)
            }
        }
    }
}
