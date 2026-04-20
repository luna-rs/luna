package api.combat.npc.dsl

import io.luna.game.model.mob.Mob
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.combat.AmmoType
import io.luna.game.model.mob.combat.CombatSpell
import io.luna.game.model.mob.combat.CombatStyle
import io.luna.game.model.mob.combat.attack.CombatAttack
import io.luna.game.model.mob.combat.attack.MagicCombatAttack
import io.luna.game.model.mob.combat.attack.MeleeCombatAttack
import io.luna.game.model.mob.combat.attack.RangedCombatAttack
import io.luna.game.model.mob.combat.damage.CombatDamage
import io.luna.game.model.mob.combat.damage.CombatDamageRequest
import io.luna.game.model.mob.combat.damage.CombatDamageType

/**
 * DSL receiver used to build combat attacks for an NPC combat hook.
 *
 * This provides convenient access to the attacking NPC, the victim, and commonly used combat-related definitions so
 * hook implementations can build attacks with minimal boilerplate.
 *
 * @param npc The NPC performing the attack.
 * @param other The current combat target.
 * @author lare96
 */
class NpcAttackCombatHookReceiver(npc: Npc, other: Mob) : NpcCombatHookReceiver(npc, other) {

    /**
     * Creates a magic combat attack using the specified spell.
     *
     * The attack speed defaults to the attacker's configured combat attack speed.
     *
     * @param spell The spell to cast.
     * @param speed The attack delay in ticks.
     * @return The configured magic combat attack.
     */
    fun magic(
        spell: CombatSpell,
        speed: Int = npc.combatDef().attackSpeed
    ) = MagicCombatAttack(npc, other, spell, speed)

    /**
     * Creates a melee combat attack with optional overrides for the attack animation, maximum hit, attack range,
     * and attack speed.
     *
     * Damage is rolled using melee accuracy against the current victim and is capped by the supplied maximum hit.
     *
     * @param animationId The attack animation to play.
     * @param maxHit The maximum damage this attack can deal.
     * @param range The maximum melee distance allowed for the attack.
     * @param speed The attack delay in ticks.
     * @param launch The listener applied when the melee attack is actually launched.
     * @return The configured melee combat attack.
     */
    fun melee(
        animationId: Int = npc.combatDef().attackAnimation,
        maxHit: Int = npc.combatDef().maximumHit,
        range: Int = 1,
        speed: Int = npc.combatDef().attackSpeed,
        launch: MeleeCombatAttack<Npc>.(CombatDamage?) -> CombatDamage? = { it },
    ) = object : MeleeCombatAttack<Npc>(npc, other, animationId, range, speed) {

        /**
         * Computes melee damage against the current victim using standard accuracy and the supplied maximum hit.
         *
         * @param other The target passed by the combat system.
         * @return The computed melee damage result.
         */
        override fun calculateDamage(other: Mob): CombatDamage {
            return CombatDamageRequest.Builder(attacker, other, CombatDamageType.MELEE)
                .setBaseMaxHit(maxHit).build().resolve()
        }

        override fun onAttack(damage: CombatDamage?): CombatDamage? {
            return launch(this, damage)
        }
    }

    /**
     * Creates a ranged combat attack using the specified combat style and ammunition.
     *
     * @param style The ranged combat style to use.
     * @param ammo The ammunition type to fire.
     * @return The configured ranged combat attack.
     */
    fun ranged(style: CombatStyle, ammo: AmmoType) =
        RangedCombatAttack(npc, other, style, ammo)

    /**
     * Gets the attacker's default combat attack against the current victim.
     *
     * @return The default combat attack produced by the NPC combat context.
     */
    fun default(): CombatAttack<Npc> = npc.combat.getDefaultAttack(other)
}