package world.player.skill.cooking.cookFood

import api.predef.cooking
import api.predef.rand
import io.luna.game.action.impl.ItemContainerAction.InventoryAction
import io.luna.game.model.Position
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.`object`.GameObject

/**
 * An [InventoryAction] that cooks food.
 */
class CookFoodActionItem(
    plr: Player,
    private val cookObj: GameObject,
    val food: Food,
    val usingFire: Boolean,
    amount: Int
) : InventoryAction(plr, true, 4, amount) {

    companion object {

        /**
         * The fire cooking animation.
         */
        private val FIRE_ANIMATION = Animation(897)

        /**
         * The range cooking animation.
         */
        private val RANGE_ANIMATION = Animation(896)

        /**
         * The position of the lumbridge range.
         */
        private val RANGE_O_MATIC_POSITION = Position(3212, 3215)
    }

    /**
     * The experience.
     */
    private var experience: Double? = null

    override fun executeIf(start: Boolean): Boolean =
        when {
            mob.cooking.level < food.lvl -> {
                mob.sendMessage("You need a Cooking level of ${food.lvl} to cook this.")
                false
            }

            else -> true
        }

    override fun execute() {
        mob.animation(if (usingFire) FIRE_ANIMATION else RANGE_ANIMATION)

        if (experience != null) {
            mob.sendMessage("You successfully cook a ${food.formattedName}.")
            mob.cooking.addExperience(experience!!)
        } else {
            mob.sendMessage("You accidentally burn the ${food.formattedName}.")
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

    /**
     * Determines if the food will be burnt this action cycle.
     */
    private fun computeIsBurnt(): Boolean {

        // Cooking gauntlets decreases burn chance.
        var baseBurnStopLevel = food.burnStopLvl
        if (mob.equipment.contains(775)) {
            when (food) {
                Food.LOBSTER -> baseBurnStopLevel = 64
                Food.SWORDFISH -> baseBurnStopLevel = 80
                Food.MONKFISH -> baseBurnStopLevel = 86
                Food.SHARK -> baseBurnStopLevel = 94
                else -> {}
            }
        }

        // Using the Lumbridge range decreases burn chance.
        if (!usingFire && cookObj.position.equals(RANGE_O_MATIC_POSITION)) {
            baseBurnStopLevel -= rand(3, 6)
        }

        if (mob.cooking.level >= baseBurnStopLevel) {
            return false
        } else {
            val rangeBonus = if (usingFire) 0.0 else 5.0
            val baseChance = 55.0 - rangeBonus
            val burnLvlFactor = baseBurnStopLevel - food.lvl
            val reqLvlFactor = mob.cooking.level - food.lvl
            val burnChance = baseChance - (reqLvlFactor * (baseChance / burnLvlFactor))

            return burnChance >= (rand().nextDouble() * 100.0)
        }
    }
}