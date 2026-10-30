package game.content.barrows

import engine.combat.prayer.CombatPrayer
import io.luna.game.model.mob.Mob
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.Skill
import io.luna.game.model.mob.block.Graphic
import io.luna.game.model.mob.combat.AmmoType
import io.luna.game.model.mob.combat.CombatSpell
import io.luna.game.model.mob.combat.attack.CombatAttack
import io.luna.game.model.mob.combat.attack.MagicCombatAttack
import io.luna.game.model.mob.combat.attack.MeleeCombatAttack
import io.luna.game.model.mob.combat.attack.RangedCombatAttack
import io.luna.game.model.mob.combat.damage.CombatDamage
import io.luna.game.model.mob.combat.damage.CombatDamageRequest
import io.luna.game.model.mob.combat.damage.CombatDamageType
import kotlin.random.Random

/**
 * Reconstructs the brothers' early effects using contemporary guides and the 377 effect assets.
 * The one-in-four proc rate and exact scaling use later documented behavior where early evidence
 * is incomplete. These are NPC effects; player equipment set bonuses are separate content.
 *
 * @author lare96
 */
object BarrowsCombat {
    /**
     * Confuse, Weaken and Curse are documented alongside Fire Wave. Each gets one of sixteen slots;
     * this weighting is an approximation. Already lowered skills fall back to Fire Wave.
     */
    fun ahrimSpell(target: Mob, random: Random): CombatSpell {
        val (spell, skillId) = when (random.nextInt(16)) {
            0 -> CombatSpell.CONFUSE to Skill.ATTACK
            1 -> CombatSpell.WEAKEN to Skill.STRENGTH
            2 -> CombatSpell.CURSE to Skill.DEFENCE
            else -> return CombatSpell.FIRE_WAVE
        }
        val skill = target.skill(skillId)
        return if (skill.level >= skill.staticLevel) spell else CombatSpell.FIRE_WAVE
    }

    fun dharokMaximum(base: Int, health: Int, total: Int): Int =
        base + (base.toLong() * (total - health).coerceIn(0, total) * total / 10000).toInt()

    fun attack(npc: Npc, target: Mob, brother: BarrowsBrother,
               random: Random = Random.Default): CombatAttack<Npc> = when (brother) {
        BarrowsBrother.AHRIM -> object : MagicCombatAttack<Npc>(npc, target, ahrimSpell(target, random), 6) {
            override fun getNpcAttackSound(): Int {
                val sound = attacker.combatDef()?.attackSound ?: -1
                return if (sound >= 0) sound else super.getNpcAttackSound()
            }

            override fun onDamageApplied(damage: CombatDamage) {
                super.onDamageApplied(damage)
                effect(npc, brother, damage, random)
            }
        }
        BarrowsBrother.KARIL -> {
            val ammo = AmmoType.BOLT_RACK.def
            object : RangedCombatAttack<Npc>(npc, target, npc.combat.getAttackAnimation(CombatDamageType.RANGED),
                                             ammo.startGraphic, ammo.projectile, null, 4, 7) {
                override fun onDamageApplied(damage: CombatDamage) {
                    effect(npc, brother, damage, random)
                }
            }
        }
        else -> object : MeleeCombatAttack<Npc>(npc, target, npc.combat.getAttackAnimation(CombatDamageType.MELEE),
                                                1, npc.combatDef().attackSpeed) {
            override fun calculateDamage(other: Mob): CombatDamage {
                var maximum = npc.combatDef().maximumHit
                if (brother == BarrowsBrother.DHAROK) maximum = dharokMaximum(maximum, npc.health, npc.totalHealth)
                if (brother == BarrowsBrother.VERAC && random.nextInt(4) == 0) {
                    if (other is Player && other.combat.prayers.isActive(CombatPrayer.PROTECT_FROM_MELEE)) {
                        maximum = minOf(maximum, 15)
                    }
                    return CombatDamageRequest.damageIgnoringPrayers(npc, other, CombatDamageType.MELEE, maximum).resolve()
                }
                return CombatDamageRequest.Builder(npc, other, CombatDamageType.MELEE)
                    .setBaseMaxHit(maximum).build().resolve()
            }

            override fun onDamageApplied(damage: CombatDamage) {
                effect(npc, brother, damage, random)
            }
        }
    }

    fun effect(npc: Npc, brother: BarrowsBrother, damage: CombatDamage, random: Random = Random.Default) {
        if (!npc.isAlive || damage.rawAmount <= 0 || brother.effectGraphicId == null ||
            random.nextInt(4) != 0) return
        val target = damage.victim
        when (brother) {
            BarrowsBrother.AHRIM -> {
                val strength = target.skill(Skill.STRENGTH)
                strength.level = (strength.level - 5).coerceAtLeast(0)
            }
            BarrowsBrother.GUTHAN -> npc.health = minOf(npc.totalHealth, npc.health + damage.rawAmount)
            BarrowsBrother.KARIL -> {
                val agility = target.skill(Skill.AGILITY)
                agility.level = agility.level - agility.level / 5
            }
            BarrowsBrother.TORAG -> if (target is Player) target.runEnergy = (target.runEnergy * 0.8).coerceAtLeast(0.0)
            else -> return
        }
        target.graphic(Graphic(brother.effectGraphicId))
    }
}
