package game.combat.npcHooks

import api.combat.npc.NpcCombatHandler.combat
import api.predef.*
import game.skill.slayer.Slayer.drainCombatSkills
import io.luna.Luna
import io.luna.game.model.LocalProjectile
import io.luna.game.model.mob.Mob
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.combat.CombatFormula
import io.luna.game.model.mob.combat.attack.MeleeCombatAttack
import io.luna.game.model.mob.combat.damage.CombatDamage
import io.luna.game.model.mob.combat.damage.CombatDamageRequest
import io.luna.game.model.mob.combat.damage.CombatDamageType
import io.luna.game.model.mob.interact.InteractionPolicy
import io.luna.game.model.mob.interact.InteractionType

/**
 * Whether missing mirror shields enable the native gaze penalty. Ordinary damage styles remain active in either mode.
 */
val EQUIPMENT_NEEDED = Luna.settings().skills().slayerEquipmentNeeded()

/**
 * The wearable mirror shield item id.
 */
val MIRROR_SHIELD = 4156

/**
 * Native mirror-shield monsters. Unprotected players receive the source's five-tile gaze projectile, fixed damage
 * and immediate skill drains. Protected cockatrice strikes check magic defence; protected basilisks use slash.
 * Gaze damage keeps melee protection and the native zero-delay player damage queue despite its visual projectile.
 *
 * @author lare96
 */
combat(1616, 1617, 1620, 1621) {
    attack {
        object : MeleeCombatAttack<Npc>(npc, other, npc.combatDef().attackAnimation, 1, 4) {
            private val basilisk = npc.id == 1616 || npc.id == 1617
            private val gazePolicy = InteractionPolicy(InteractionType.LINE_OF_SIGHT, 5)
            private var gazing = false

            private fun lacksShield(): Boolean = EQUIPMENT_NEEDED && victim is Player &&
                victim.equipment.shield?.id != MIRROR_SHIELD

            override fun getInteractionPolicy(): InteractionPolicy =
                if (lacksShield()) gazePolicy else super.getInteractionPolicy()

            override fun calculateDamage(other: Mob): CombatDamage {
                // Capture protection when the attack commits, after pursuit has finished.
                gazing = lacksShield()
                val request = CombatDamageRequest.Builder(attacker, victim, CombatDamageType.MELEE)
                if (gazing) {
                    val player = victim as Player
                    request.setBaseMaxHit(0).setBaseAccuracy(1.0).setFlatBonusAccuracy(1.0)
                        .setFlatBonusDamage(player.hitpoints.staticLevel * (if (basilisk) 13 else 12) / 100)
                } else if (!basilisk) {
                    request.setBaseAccuracy(CombatFormula.calculateMagicDefendedMeleeHitChance(attacker, victim))
                }
                return request.build().resolve()
            }

            override fun onAttack(damage: CombatDamage): CombatDamage {
                if (gazing) {
                    val player = victim as Player
                    player.drainCombatSkills(if (basilisk) 60 else 40, if (basilisk) 30 else 20)
                    player.sendMessage("You have been weakened.")
                }
                return damage
            }

            override fun getNpcAttackSound(): Int =
                if (gazing && basilisk) 843 else super.getNpcAttackSound()

            override fun attack() {
                if (gazing) {
                    LocalProjectile.followEntity(attacker.context).setSourceEntity(attacker).setTargetEntity(victim)
                        .setId(if (basilisk) 324 else 75).setStartHeight(if (basilisk) 16 else 23)
                        .setEndHeight(32).setTiming(0, attacker.position.computeLongestDistance(victim.position) * 2)
                        .setInitialSlope(0).setLaunchOffset(16).build().display()
                }
                super.attack()
            }
        }
    }
}
