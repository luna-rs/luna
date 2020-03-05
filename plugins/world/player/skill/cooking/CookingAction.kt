package world.player.skill.cooking

import api.predef.*
import io.luna.game.action.Action
import io.luna.game.action.InventoryAction
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Animation
import io.luna.game.model.mob.Player

/**
 * An [InventoryAction] that cooks food.
 */
class CookingAction(plr: Player,
                    val food: Food,
                    val usingFire: Boolean,
                    amount: Int) : InventoryAction(plr, true, 4, amount) {

    companion object {

        /**
         * The fire cooking animation.
         */
        private val FIRE_ANIMATION = Animation(897)

        /**
         * The range cooking animation.
         */
        private val RANGE_ANIMATION = Animation(896)
    }

    /**
     * The experience.
     */
    private var experience: Double? = null

    override fun executeIf(start: Boolean): Boolean =
        when {
            mob.cooking.level < food.reqLevel -> {
                mob.sendMessage("You need a Cooking level of ${food.reqLevel} to cook this.")
                false
            }
            else -> true
        }

    override fun execute() {
        mob.animation(if (usingFire) FIRE_ANIMATION else RANGE_ANIMATION)

        if (experience != null) {
            mob.sendMessage("You cook the ${food.formattedName}.")
            mob.cooking.addExperience(experience!!)
        } else {
            mob.sendMessage("Oops! You accidentally burn the ${food.formattedName}.")
        }
    }

    override fun remove() = listOf(food.rawItem)

    override fun add(): List<Item> =
        if (computeIsBurnt()) {
            experience = null
            listOf(food.burntItem)
        } else {
            experience = food.exp
            listOf(food.cookedItem)
        }

    override fun ignoreIf(other: Action<*>?): Boolean =
        when (other) {
            is CookingAction -> other.food == food &&
                    other.usingFire == usingFire
            else -> false
        }

    /**
     * Determines if the food will be burnt this action cycle.
     */
    private fun computeIsBurnt(): Boolean =
        if (mob.cooking.level >= food.masterLevel) {
            false
        } else {
            val rangeBonus = if (usingFire) 0.0 else 5.0
            val baseChance = 55.0 - rangeBonus
            val burnLvlFactor = food.masterLevel - food.reqLevel
            val reqLvlFactor = mob.cooking.level - food.reqLevel
            val burnChance = baseChance - (reqLvlFactor * (baseChance / burnLvlFactor))

            burnChance >= (rand().nextDouble() * 100.0)
        }
}