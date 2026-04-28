package game.combat.npcHooks.skeletalWyvern

import api.predef.*
import api.predef.ext.*
import game.player.Sound
import io.luna.game.model.mob.Mob
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.mob.block.Graphic
import io.luna.game.model.mob.combat.attack.MagicCombatAttack
import io.luna.game.model.mob.combat.damage.CombatDamage
import io.luna.game.model.mob.combat.damage.CombatDamageRequest
import io.luna.game.model.mob.combat.damage.CombatDamageType

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
class WyvernIcyBreathAttack(npc: Npc, other: Mob) : MagicCombatAttack<Npc>(
    npc,
    other,
    ICY_BREATH_CAST_ANIMATION,
    ICY_BREATH_START_GRAPHIC,
    null,
    ICY_BREATH_END_GRAPHIC,
    Sound.ICE_BURST_IMPACT,
    null,
    npc.combatDef().attackSpeed,
    1) {

    companion object {

        /**
         * The shield item id that protects players from the full effect of skeletal wyvern icy breath.
         */
        const val ICY_BREATH_SHIELD = 2890

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
    }

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
            // Freeze player and disable their combat.
            victim.status.add(WyvernIcyBreathStatusEffect(victim, freeze.ticks))
        }
    }
}