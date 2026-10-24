package api.combat.specialAttack.dsl

import api.combat.specialAttack.SpecialAttackHandler.specialBarClicks
import game.player.Sound
import io.luna.game.model.LocalProjectile
import io.luna.game.model.def.CombatSpellDefinition
import io.luna.game.model.mob.Mob
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.mob.block.Graphic
import io.luna.game.model.mob.combat.CombatSpell
import io.luna.game.model.mob.combat.CombatFormula
import io.luna.game.model.mob.combat.attack.CombatAttack
import io.luna.game.model.mob.combat.attack.PlayerMagicCombatAttack
import io.luna.game.model.mob.combat.attack.PlayerMeleeCombatAttack
import io.luna.game.model.mob.combat.attack.PlayerRangedCombatAttack
import io.luna.game.model.mob.combat.damage.CombatDamage
import io.luna.game.model.mob.combat.damage.CombatDamageRequest
import io.luna.game.model.mob.combat.damage.CombatDamageType
import io.luna.game.model.mob.combat.state.NpcCombatContext
import io.luna.game.model.mob.combat.state.PlayerCombatContext
import java.util.function.BiFunction

/**
 * DSL receiver used to build the concrete combat attack for a special attack.
 *
 * This receiver exposes helper methods for constructing melee, ranged, and magic combat attacks that automatically
 * integrate special attack launch handling, custom damage calculation, and arrival callbacks.
 * Applied damage first runs the base attack's experience and poison handling, then the special arrival callback.
 *
 * The receiver is created for a single attacker, victim, and special attack configuration.
 *
 * @property attacker The player performing the special attack.
 * @property victim The mob targeted by the special attack.
 * @property receiver The special attack data and callback configuration.
 * @author lare96
 */
class SpecialAttackBuilderReceiver(val attacker: Player, val victim: Mob, val receiver: SpecialAttackDataReceiver) {

    /**
     * The attacker's [PlayerCombatContext] instance.
     */
    val atkCombat = attacker.combat

    /**
     * The victim's [NpcCombatContext] instance.
     */
    val defCombat = victim.combat

    /**
     * Creates a melee combat attack for this special attack using explicit animation, range, and speed values.
     *
     * The returned attack automatically:
     * - Runs special attack launch handling through [onAttack].
     * - Uses special attack damage calculation through [calculateDamage].
     * - Runs the configured arrival callback through [attackArrived].
     *
     * @param animationId The attack animation id to use.
     * @param range The attack range.
     * @param speed The attack speed in ticks.
     * @return A configured melee combat attack instance.
     */
    fun melee(animationId: Int = atkCombat.getAttackAnimation(CombatDamageType.MELEE),
              range: Int = atkCombat.weapon.range,
              speed: Int = atkCombat.weapon.speed): PlayerMeleeCombatAttack {
        return object : PlayerMeleeCombatAttack(attacker, victim, animationId, range, speed) {
            override fun onAttack(damage: CombatDamage?): CombatDamage? {
                return onAttack(this, damage) { super.onAttack(it) }
            }

            override fun calculateDamage(other: Mob): CombatDamage {
                return calculateDamage(other, CombatDamageType.MELEE)
            }

            override fun onDamageApplied(damage: CombatDamage) {
                super.onDamageApplied(damage)
                attackArrived(damage, this)
            }
        }
    }

    /**
     * Creates a ranged combat attack for this special attack using explicit projectile and timing data.
     *
     * The returned attack automatically:
     * - Runs special attack launch handling through [onAttack].
     * - Uses special attack damage calculation through [calculateDamage].
     * - Runs the configured arrival callback through [attackArrived].
     *
     * @param animationId The attack animation id to use.
     * @param start The starting graphic shown when the attack is launched.
     * @param projectileFunction Supplies the projectile that travels from attacker to victim.
     * @param end The ending graphic shown when the attack arrives.
     * @param speed The attack speed in ticks.
     * @param range The maximum attack range.
     * @return A configured ranged combat attack instance.
     */
    fun ranged(animationId: Int = atkCombat.getAttackAnimation(CombatDamageType.RANGED),
               start: Graphic = atkCombat.ammoDef.startGraphic,
               projectileFunction: BiFunction<Mob, Mob, LocalProjectile> = atkCombat.ammoDef.projectile,
               end: Graphic? = atkCombat.ammoDef.endGraphic,
               speed: Int = atkCombat.weapon.speed,
               range: Int = atkCombat.weapon.range): PlayerRangedCombatAttack {
        return object : PlayerRangedCombatAttack(attacker, victim, animationId, start, projectileFunction, end,
                                                 speed, range) {
            override fun onAttack(damage: CombatDamage?): CombatDamage? {
                return onAttack(this, damage) { super.onAttack(it) }
            }

            override fun calculateDamage(other: Mob): CombatDamage {
                return calculateDamage(other, CombatDamageType.RANGED)
            }

            override fun onDamageApplied(damage: CombatDamage) {
                super.onDamageApplied(damage)
                attackArrived(damage, this)
            }
        }

    }

    /**
     * Creates a magic combat attack for this special attack using explicit spell visuals and combat data.
     *
     * The returned attack automatically:
     * - Runs special attack launch handling through [onAttack].
     * - Uses special attack damage calculation through [calculateDamage].
     * - Runs the configured arrival callback through [attackArrived].
     *
     * @param cast The cast animation.
     * @param start The starting graphic shown when the spell is cast.
     * @param projectileFunction Supplies the projectile that travels from attacker to victim.
     * @param end The ending graphic shown when the spell lands.
     * @param impactSound The sound played when the spell impacts.
     * @param spellEffect The combat spell logic applied on hit.
     * @param speed The spell travel or attack speed in ticks.
     * @param distance The distance required for interaction.
     * @return A configured magic combat attack instance.
     */
    fun magic(cast: Animation,
              start: Graphic,
              projectileFunction: BiFunction<Mob, Mob, LocalProjectile>,
              end: Graphic,
              impactSound: Sound,
              spellEffect: CombatSpell,
              speed: Int,
              distance: Int): PlayerMagicCombatAttack {
        return object : PlayerMagicCombatAttack(attacker, victim, cast, start, projectileFunction, end, impactSound,
                                                spellEffect, speed, distance) {
            override fun onAttack(damage: CombatDamage?): CombatDamage? {
                return onAttack(this, damage) { super.onAttack(it) }
            }

            override fun calculateDamage(other: Mob): CombatDamage {
                return calculateDamage(other, CombatDamageType.MAGIC)
            }

            override fun onDamageApplied(damage: CombatDamage) {
                super.onDamageApplied(damage)
                attackArrived(damage, this)
            }
        }
    }

    /**
     * Creates a magic combat attack for this special attack from a combat spell definition.
     *
     * @param spell The spell definition that supplies cast animation, graphics, projectile, sound, and spell effect.
     * @return A magic combat attack built from the supplied spell definition.
     */
    fun magic(spell: CombatSpellDefinition): PlayerMagicCombatAttack {
        return magic(spell.castAnimation, spell.startGraphic, spell.projectile, spell.endGraphic, spell.endSound,
                     spell.spell, 4, 10)
    }

    /**
     * Applies shared special attack launch logic around a combat attack's normal attack processing.
     *
     * If the wrapped attack succeeds and the attacker has enough special energy:
     * - Special attack energy is drained.
     * - The launch callback is executed.
     *
     * Non-instant attacks check energy before normal processing can consume ammunition or runes. Instant attacks
     * have already handled their energy cost and clear the forced special selection here.
     *
     * @param attack The combat attack being processed.
     * @param damage The current damage result, if already resolved.
     * @param action The underlying attack-processing function to execute.
     * @return The final processed damage, or `null` if the attack did not produce one.
     */
    private fun onAttack(attack: CombatAttack<Player>,
                         damage: CombatDamage?,
                         action: (CombatDamage?) -> CombatDamage?): CombatDamage? {

        if (!receiver.instant && !attacker.combat.specialBar.checkEnergy(receiver.drain)) {
            return null
        }
        var newDamage = action(damage)
        if (newDamage != null) {
            // Attack can go through, drain special attack energy, run launch listener.
            if(!receiver.instant) {
                attacker.combat.specialBar.drain(receiver.drain, true)
            } else {
                attacker.specialBarClicks = 0
                attacker.combat.specialBar.forceOff()
                attacker.combat.specialBar.isForced = false
            }
            newDamage = receiver.launchedTransformer(SpecialAttackLaunchedReceiver(attacker, victim, attack, newDamage))
            return newDamage
        }
        return null
    }

    /**
     * Resolves combat damage for this special attack using the configured bonuses and optional max-hit override.
     *
     * Damage is calculated using the standard combat damage pipeline, with the special attack's accuracy bonus,
     * optional base max hit, strength bonus to maximum damage, and damage bonus applied on top.
     * An integer offensive roll percentage is applied before hit probability is calculated; flat accuracy is added afterward.
     * Melee specials may override the target's defence bonus without changing the attacker's offensive style.
     *
     * @param other The mob receiving the damage.
     * @param type The combat damage type to resolve.
     * @return The resolved combat damage.
     */
    private fun calculateDamage(other: Mob, type: CombatDamageType): CombatDamage {
        val request = CombatDamageRequest.Builder(attacker, other, type)
            .setFlatBonusAccuracy(receiver.attackBonus)
            .setPercentBonusDamage(receiver.damageBonus)
            .setPercentBonusStrength(receiver.strengthBonus)
        if (receiver.maxHit != null) {
            request.setBaseMaxHit(receiver.maxHit)
        }
        if (receiver.attackRollPercent != 100 || receiver.meleeDefenceBonus != null) {
            request.setBaseAccuracy(CombatFormula.calculateHitChance(attacker, other, type,
                receiver.attackRollPercent, receiver.meleeDefenceBonus))
        }
        return request.build().resolve()
    }

    /**
     * Runs the configured special attack arrival callback for a landed hit.
     *
     * @param damage The resolved damage that was applied to the victim.
     * @param source The source of the damage.
     */
    private fun attackArrived(damage: CombatDamage, source: CombatAttack<Player>) {
        receiver.arrivedConsumer(SpecialAttackArrivedReceiver(attacker, victim, damage, source))
    }
}
