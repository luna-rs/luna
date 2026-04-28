package game.combat.npcHooks.dragons

import api.predef.*
import engine.combat.prayer.CombatPrayer
import game.item.consumable.potion.PotionEffect.Companion.hasAntiFire
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
import java.lang.Double.max
import kotlin.math.floor

/**
 * A dragonfire-based [MagicCombatAttack] used by dragons.
 *
 * Protection against this attack is calculated from a combination of:
 * - An anti-dragon shield
 * - An antifire potion
 * - Protect from Magic
 *
 * The total protection value is then used to reduce both the attack's effective accuracy and maximum damage.
 *
 * @param attacker The dragon performing the attack.
 * @param victim The target of the attack.
 * @param maxHit The maximum damage before protection is applied.
 */
class DragonFireCombatAttack(
    attacker: Npc,
    victim: Mob,
    private val maxHit: Int = 50
) : MagicCombatAttack<Npc>(
    attacker,
    victim,
    DRAGON_FIRE_CAST_ANIMATION,
    DRAGON_FIRE_START_GRAPHIC,
    null,
    null,
    Sound.DRAGONBREATH,
    null,
    attacker.combatDef().attackSpeed,
    1
) {

    companion object {

        /**
         * The standard anti-dragon shield item id.
         */
        val DRAGON_FIRE_SHIELD = 1540

        /**
         * The starting graphic displayed for the dragonfire attack.
         */
        val DRAGON_FIRE_START_GRAPHIC = Graphic(1, 100, 0)

        /**
         * The cast animation used when the dragon breathes fire.
         */
        val DRAGON_FIRE_CAST_ANIMATION = Animation(81)
    }

    /**
     * The total protection currently applied against this dragonfire attack.
     *
     * This value is built from the victim's active defensive sources during damage calculation.
     */
    private var protection = 0.0

    /**
     * Calculates the dragonfire damage against the supplied target.
     *
     * If the target is a player, the following protection values are added:
     * - Anti-dragon shield: 80%
     * - Antifire potion: 20%
     * - Protect from Magic: 40%
     *
     * The final protection value is used to reduce:
     * - Effective accuracy
     * - Resulting maximum hit
     *
     * @param other The target being evaluated for damage.
     * @return The resolved combat damage for this attack.
     */
    override fun calculateDamage(other: Mob?): CombatDamage {
        if (other is Player) {
            protection += if (other.equipment.shield?.id == DRAGON_FIRE_SHIELD) 0.80 else 0.0
            protection += if (other.hasAntiFire()) 0.20 else 0.0
            protection += if (other.combat.prayers.isActive(CombatPrayer.PROTECT_FROM_MAGIC)) 0.40 else 0.0
        }

        return CombatDamageRequest.Builder(attacker, victim, CombatDamageType.MAGIC)
            .setFlatBonusAccuracy(max(1.0 - protection, 0.0))
            .setBaseMaxHit((maxHit - floor(maxHit * protection).toInt()).coerceAtLeast(0))
            .ignoreProtectionPrayers()
            .build()
            .resolve()
    }

    override fun onDamageApplied(damage: CombatDamage?) {
        if (damage != null && damage.amount.isPresent && victim is Player) {
            if (protection >= 0.8) {
                victim.sendMessage("Your shield absorbs most of the dragon fire!")
            } else if (protection <= 0.2) {
                victim.sendMessage("You're horribly burnt by the dragon fire!")
            } else {
                victim.sendMessage("You manage to resist some of the dragon fire!")
            }
        }
    }
}