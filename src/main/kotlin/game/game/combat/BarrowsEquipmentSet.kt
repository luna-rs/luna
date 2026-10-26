package game.combat

import io.luna.game.model.item.Equipment
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.Skill
import io.luna.game.model.mob.block.Graphic
import io.luna.game.model.mob.combat.damage.CombatDamage
import io.luna.game.model.mob.combat.damage.CombatDamageRequest
import io.luna.game.model.mob.combat.damage.CombatDamageType
import io.luna.util.RandomUtils
import io.luna.util.Rational

/**
 * The six original player equipment set bonuses. Item and graphic identities use Lost City's 377 pack;
 * proc rates and scaling reconstruct the documented pre-EoC/Old School effects where 377 scripts are absent.
 * A set is captured at attack time and retained until impact. Later amulet bonuses are not included.
 *
 * @author lare96
 */
enum class BarrowsEquipmentSet(
    private val head: Int,
    private val weapon: Int,
    private val body: Int,
    private val legs: Int,
    private val degradedHead: Int,
    private val damageType: CombatDamageType,
    private val graphicId: Int = -1
) {
    AHRIM(4708, 4710, 4712, 4714, 4856, CombatDamageType.MAGIC, 400),
    DHAROK(4716, 4718, 4720, 4722, 4880, CombatDamageType.MELEE),
    GUTHAN(4724, 4726, 4728, 4730, 4904, CombatDamageType.MELEE, 398),
    KARIL(4732, 4734, 4736, 4738, 4928, CombatDamageType.RANGED, 401),
    TORAG(4745, 4747, 4749, 4751, 4952, CombatDamageType.MELEE, 399),
    VERAC(4753, 4755, 4757, 4759, 4976, CombatDamageType.MELEE);

    companion object {
        private val procChance = Rational(1, 4)

        /**
         * Finds a complete set in its actual equipment slots. Mixed durability levels are valid.
         */
        @JvmStatic
        fun find(player: Player): BarrowsEquipmentSet? = entries.firstOrNull { it.isWorn(player.equipment) }

        /**
         * The extra damage fraction, applied after the damage roll rather than to the max-hit roll.
         */
        fun dharokBonus(maxHealth: Int, health: Int): Double =
            maxOf(0, maxHealth).toDouble() * maxOf(0, maxHealth - maxOf(0, health)) / 10000.0
    }

    /**
     * Checks base pieces and the four usable degradation states; broken items and bank notes do not match.
     */
    fun isWorn(equipment: Equipment): Boolean =
        matches(equipment.computeIdForIndex(Equipment.HEAD), head, degradedHead) &&
                matches(equipment.computeIdForIndex(Equipment.WEAPON), weapon, degradedHead + 6) &&
                matches(equipment.computeIdForIndex(Equipment.CHEST), body, degradedHead + 12) &&
                matches(equipment.computeIdForIndex(Equipment.LEGS), legs, degradedHead + 18)

    private fun matches(id: Int, base: Int, degraded: Int): Boolean = id == base || id in degraded..degraded + 3

    /**
     * Modifies a normal melee request before accuracy and damage are rolled. Defensive hooks still run at impact.
     */
    fun modifyMeleeRequest(player: Player, request: CombatDamageRequest.Builder) {
        when (this) {
            DHAROK -> request.setPercentBonusDamage(dharokBonus(player.totalHealth, player.health))
            VERAC -> if (RandomUtils.roll(procChance)) {
                request.setBaseAccuracy(1.0).ignoreProtectionPrayers().setFlatBonusDamage(1)
            }
            else -> Unit
        }
    }

    /**
     * Applies a captured set's effect only after an accepted, accurate hit. The supplied damage is already capped
     * to the victim's remaining health. This also supports accurate zero hits and successful effect-only spells.
     */
    fun onDamageApplied(damage: CombatDamage) {
        val player = damage.attacker as? Player ?: return
        if (damage.type != damageType || !damage.isAccurate || !player.isAlive) {
            return
        }
        val target = damage.victim
        if (this == DHAROK || this == VERAC ||
            (this != GUTHAN && !target.isAlive) ||
            ((this == KARIL || this == TORAG) && target !is Player) ||
            !RandomUtils.roll(procChance)) {
            return
        }
        when (this) {
            AHRIM -> {
                val strength = target.skill(Skill.STRENGTH)
                strength.level = maxOf(1, strength.level - 5)
            }
            GUTHAN -> {
                val healing = minOf(maxOf(0, damage.rawAmount), maxOf(0, player.totalHealth - player.health))
                if (healing > 0) {
                    player.addHealth(healing)
                }
            }
            KARIL -> {
                val agility = target.skill(Skill.AGILITY)
                agility.level = maxOf(1, agility.level - agility.level / 5)
            }
            TORAG -> (target as Player).runEnergy *= 0.8
            else -> return
        }
        target.graphic(Graphic(graphicId))
    }
}
