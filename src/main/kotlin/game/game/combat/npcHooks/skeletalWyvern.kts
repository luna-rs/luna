package game.combat.npcHooks

import api.combat.magic.ImmobilizationAction
import api.combat.npc.NpcCombatHandler.combat
import api.predef.*
import api.predef.ext.*
import game.player.Sound
import io.luna.game.model.LocalProjectile
import io.luna.game.model.mob.Mob
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.mob.block.Graphic
import io.luna.game.model.mob.combat.attack.CombatAttack
import io.luna.game.model.mob.combat.attack.MagicCombatAttack
import io.luna.game.model.mob.combat.attack.RangedCombatAttack
import io.luna.game.model.mob.combat.damage.CombatDamage
import io.luna.game.model.mob.combat.damage.CombatDamageAction
import io.luna.game.model.mob.combat.damage.CombatDamageRequest
import io.luna.game.model.mob.combat.damage.CombatDamageType

/**
 * The shield item id that protects players from the full effect of skeletal wyvern icy breath.
 */
val ICY_BREATH_SHIELD = 2890

/**
 * The start graphic displayed when a skeletal wyvern uses icy breath.
 */
val ICY_BREATH_START_GRAPHIC = Graphic(501, 0, 0)

/**
 * The cast animation used for skeletal wyvern icy breath.
 */
val ICY_BREATH_CAST_ANIMATION = Animation(2988)

/**
 * The end graphic displayed when skeletal wyvern icy breath lands on the victim.
 */
val ICY_BREATH_END_GRAPHIC = Graphic(502, 100, 0)

/**
 * The start graphic displayed when a skeletal wyvern uses its ranged attack.
 */
val RANGED_START_GRAPHIC = Graphic(499, 100, 0)

/**
 * Builds the skeletal wyvern ranged projectile that travels from the attacker to the target.
 */
val RANGED_PROJECTILE: (Mob, Mob) -> LocalProjectile = { mob, other ->
    LocalProjectile.followEntity(ctx)
        .setSourceEntity(mob)
        .setTargetEntity(other)
        .setId(500)
        .setTicksToStart(44)
        .setTicksToEnd(3)
        .setStartHeight(43)
        .setEndHeight(31)
        .setInitialSlope(0)
        .build()
}

/**
 * The cast animation used for skeletal wyvern ranged attacks.
 */
val RANGED_CAST_ANIMATION = 2989

/**
 * A skeletal wyvern ranged combat attack.
 *
 * @param npc The skeletal wyvern performing the attack.
 * @param other The target being attacked.
 * @author lare96
 */
class WyvernRangedAttack(npc: Npc, other: Mob) : RangedCombatAttack<Npc>(
    npc,
    other,
    RANGED_CAST_ANIMATION,
    RANGED_START_GRAPHIC,
    RANGED_PROJECTILE,
    null,
    6,
    npc.combatDef().attackSpeed
)

/**
 * A skeletal wyvern icy breath magic attack.
 *
 * This attack checks whether the victim is protected by the correct shield. Unprotected victims take higher damage
 * and are always frozen, while protected victims take reduced damage and only have a small chance to be frozen.
 *
 * @param npc The skeletal wyvern performing the attack.
 * @param other The target being attacked.
 * @author lare96
 */
class WyvernIcyAttack(npc: Npc, other: Mob) : MagicCombatAttack<Npc>(
    npc,
    other,
    ICY_BREATH_CAST_ANIMATION,
    ICY_BREATH_START_GRAPHIC,
    null,
    ICY_BREATH_END_GRAPHIC,
    Sound.ICE_BURST_IMPACT,
    null,
    npc.combatDef().attackSpeed,
    1
) {

    /**
     * Tracks whether the current victim is protected from the full icy breath effect.
     */
    private var protected = other is Player && other.equipment.shield?.id == ICY_BREATH_SHIELD

    override fun calculateDamage(other: Mob?): CombatDamage {
        // Build the damage request based on protection.
        val request = CombatDamageRequest.Builder(attacker, victim, CombatDamageType.MAGIC)
        if (!protected) {
            // Unprotected: 40 max hit and 50% accuracy bonus.
            request.setBaseMaxHit(50).setFlatBonusAccuracy(0.50)
        } else {
            // Protected: 10 max hit.
            request.setBaseMaxHit(10)
        }
        return request.build().resolve()
    }

    override fun onDamageApplied(damage: CombatDamage?) {
        val chance = if (protected) 1 of 7 else ALWAYS
        val freeze = if (protected) 5 else 10

        if (rand(chance)) {
            // Disable the victim's combat once the freeze starts.
            victim.combat.isDisabled = true
            if (victim is Player) {
                victim.sendMessage("You are unable to attack.")
            }
            victim.actions.submit(object : ImmobilizationAction(victim, freeze) {
                override fun onFinished() {
                    // Re-enables the victim's combat once the freeze expires.
                    victim.combat.isDisabled = false
                }
            })
        }

        victim.submitAction(CombatDamageAction(nextDamage, this, true))
    }
}

/*
 * Registers skeletal wyvern combat behavior.
 *
 * Wyverns may choose between melee, icy breath, and ranged attacks depending on distance:
 *
 * - Within 2 tiles: melee, icy breath, or ranged
 * - Within 6 tiles: icy breath or ranged
 * - Beyond 6 tiles: ranged only
 */
combat(3068, 3069, 3070, 3071) {
    attack {
        val attacks = ArrayList<CombatAttack<Npc>>(3)

        if (npc.isWithinDistance(other, 2)) {
            // Can use all attacks.
            attacks += melee(
                animationId = if (rand().nextBoolean()) 2985 else 2986,
                maxHit = 13
            )
            attacks += WyvernIcyAttack(npc, other)
            attacks += WyvernRangedAttack(npc, other)
        } else if (npc.isWithinDistance(other, 6)) {
            // Can use ranged and icy breath attacks.
            attacks += WyvernIcyAttack(npc, other)
            attacks += WyvernRangedAttack(npc, other)
        } else {
            // Can use ranged attack only.
            attacks += WyvernRangedAttack(npc, other)
        }

        attacks.random()
    }
}