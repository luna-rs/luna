package game.combat.npcHooks.dragon

import api.predef.*
import engine.combat.prayer.CombatPrayer
import game.player.Sound
import io.luna.game.model.item.Equipment.EquipmentBonus
import io.luna.game.model.mob.Mob
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.mob.block.Graphic
import io.luna.game.model.mob.combat.CombatFormula
import io.luna.game.model.mob.combat.attack.MagicCombatAttack
import io.luna.game.model.mob.combat.damage.CombatDamage
import io.luna.game.model.mob.combat.damage.CombatDamageRequest
import io.luna.game.model.mob.combat.damage.CombatDamageType

/**
 * Shared ordinary dragonfire from Lost City's `npc/scripts/dragon.rs2`, used by adult chromatic and metal dragons.
 * An anti-dragon shield limits damage to five; otherwise Protect from Magic limits it to ten. Antifire subtracts
 * fifteen after that ordered selection. Unprotected magic offence is checked against slash defence to select a
 * fifty or thirty maximum; even a resisted breath delivers numeric damage rather than splashing.
 *
 * The source displays fire immediately and delivers its hit through the native damage action, without a projectile.
 * Protection and messages are captured per launch, so reusing an attack cannot accumulate old protection values.
 *
 * @param attacker The dragon performing the attack.
 * @param victim The target of the attack.
 * @param maxHit The unprotected maximum; ordinary dragons use fifty and a resisted maximum twenty lower.
 * @param range The permitted breath reach; metal fire mode uses its native five-tile range.
 * @param commit Optional native mode update, invoked only once the prepared attack launches.
 * @author lare96
 */
open class DragonFireCombatAttack(
    attacker: Npc,
    victim: Mob,
    private val maxHit: Int = 50,
    range: Int = 1,
    private val commit: () -> Unit = {}
) : MagicCombatAttack<Npc>(
    attacker, victim, DRAGON_FIRE_CAST_ANIMATION, DRAGON_FIRE_START_GRAPHIC, null, null,
    null, null, 4, range
) {
    companion object {

        /**
         * The standard anti-dragon shield item id.
         */
        val DRAGON_FIRE_SHIELD = 1540

        /**
         * The source's firebreath_attack spot animation at height ninety-two.
         */
        val DRAGON_FIRE_START_GRAPHIC = Graphic(1, 92, 0)

        /**
         * Ordinary dragons use dragon_firebreath_middle_attack, distinct from KBD's other breath animations.
         */
        val DRAGON_FIRE_CAST_ANIMATION = Animation(84)

        /**
         * Applies ordered shield/prayer protection and then antifire. Unprotected accuracy selects the normal or
         * resisted maximum, while protected caps apply directly without a second accuracy roll.
         */
        internal fun maximumHit(shield: Boolean, prayer: Boolean, antifire: Boolean, accurate: Boolean,
                                unprotectedMaximum: Int = 50): Int {
            val maximum = when {
                shield -> 5
                prayer -> 10
                accurate -> unprotectedMaximum
                else -> unprotectedMaximum - 20
            }
            return (maximum - if (antifire) 15 else 0).coerceAtLeast(0)
        }
    }

    /**
     * The source's launch-time message, based on the same protection and accuracy used to prepare this hit.
     */
    private var message: String? = null

    override fun calculateDamage(other: Mob): CombatDamage {
        val player = other as? Player
        val shield = player?.equipment?.shield?.id == DRAGON_FIRE_SHIELD
        val prayer = player?.combat?.prayers?.isActive(CombatPrayer.PROTECT_FROM_MAGIC) == true
        val accurate = !shield && !prayer && rand().nextDouble() <
            CombatFormula.calculatePhysicalDefendedMagicHitChance(attacker, other, EquipmentBonus.SLASH_DEFENCE)
        message = when {
            shield -> "Your shield absorbs most of the dragon fire!"
            prayer -> null
            accurate -> "You're horribly burnt by the dragon fire!"
            else -> "You manage to resist some of the dragon fire!"
        }
        val maximum = maximumHit(shield, prayer, player?.status?.isAntiFire() == true, accurate, maxHit)
        return CombatDamageRequest.damageIgnoringPrayers(attacker, other, CombatDamageType.MAGIC, maximum).resolve()
    }

    override fun onAttack(damage: CombatDamage?): CombatDamage? {
        if (damage != null) commit()
        return damage
    }

    override fun attack() {
        if (victim is Player) {
            victim.overlays.closeWindows()
            victim.playSound(Sound.DRAGONBREATH, 1)
            message?.let { victim.sendMessage(it) }
        }
        super.attack()
    }

    override fun scheduleHit() {
        onProjectileReached()
        onProjectileFinished(false)
    }
}
