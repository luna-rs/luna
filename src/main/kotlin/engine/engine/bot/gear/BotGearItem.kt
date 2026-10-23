package engine.bot.gear

import api.predef.*
import io.luna.game.model.def.ItemDefinition
import io.luna.game.model.def.WeaponDefinition
import io.luna.game.model.item.Equipment.SHIELD
import io.luna.game.model.item.Equipment.WEAPON
import io.luna.game.model.mob.combat.Weapon

/**
 * A single equipment item that can be considered by the bot gear system.
 *
 * Each gear item is tied to one equipment slot through [index], one concrete item id through [id], and one or more
 * [purposes] that describe when the item is useful. The [priority] value is used as an extra manual weight when the
 * automatic equipment score is not enough on its own.
 *
 * Equality is based only on [id], because two gear entries that point to the same item should be treated as the same
 * gear item even if they were declared with different metadata.
 *
 * @property index The equipment slot index this item belongs to.
 * @property id The item id.
 * @property purposes The gear purposes this item can satisfy.
 * @property priority A manual ranking bonus used when calculating the item's usefulness.
 * @author lare96
 */
class BotGearItem(
    val index: Int,
    val id: Int,
    val purposes: Set<BotGearPurpose>,
    val priority: Int
) {

    /**
     * The equipment definition for this item.
     *
     * This is loaded lazily so declaring gear items does not immediately force all equipment definitions to be resolved.
     */
    val def = lazyVal { equipDef(id) }

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other !is BotGearItem) {
            return false
        }
        return id == other.id
    }

    override fun hashCode(): Int {
        return id
    }

    /**
     * Calculates a rough usefulness score for this gear item.
     *
     * The score is intentionally simple and heuristic-based. It starts with the item's manual [priority], then adds a
     * bonus based on the item's highest equipment requirement. Extra bonuses are applied for weapon and shield types
     * that are generally more desirable for bots.
     *
     * This score is used only to choose between multiple valid candidates in the same equipment slot. It is not intended
     * to be a perfect combat formula.
     *
     * @return The calculated equipment score.
     */
    fun calculateScore(): Int {
        // todo SCORE MODIFIER INTERFACE?
        // fun modifiers(): List<BotGearItem.() -> Int>
        val weaponType = if (index == WEAPON) {
            WeaponDefinition.ALL[id].orElse(null)?.type
        } else {
            null
        }

        val shieldType = if (index == SHIELD) {
            ItemDefinition.ALL[id].orElse(null)
        } else {
            null
        }

        var score = 0
        score += if (priority < 1) 100 else priority * 100
        score += def.value.highestRequirement * 100

        score += when (weaponType) {
            Weapon.WHIP -> 200

            Weapon.SCIMITAR -> 100

            // todo Handle ranged, magic, and skilling separately, or at least filter by purpose.
            Weapon.DART,
            Weapon.KNIFE -> 75

            Weapon.LONGSWORD,
            Weapon.BATTLEAXE,
            Weapon.TWO_HANDED_SWORD,
            Weapon.CLAWS -> 50

            Weapon.SWORD,
            Weapon.DAGGER,
            Weapon.MACE -> 25

            else -> 0
        }

        score += if (shieldType != null) {
            when {
                shieldType.name.contains("crystal", true) -> 250
                shieldType.name.contains("kiteshield", true) -> 100
                else -> 0
            }
        } else {
            0
        }

        return score
    }
}