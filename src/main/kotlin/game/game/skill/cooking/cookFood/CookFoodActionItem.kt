package game.skill.cooking.cookFood

import api.predef.*
import api.predef.ext.*
import io.luna.game.action.impl.ItemContainerAction.InventoryAction
import io.luna.game.model.Position
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.`object`.GameObject
import game.player.Animations
import game.player.Sounds
import game.skill.Skills

/**
 * An [InventoryAction] that cooks food.
 *
 * @author lare96
 */
class CookFoodActionItem(plr: Player,
                         private val cookObj: GameObject,
                         val food: Food,
                         val usingFire: Boolean,
                         amount: Int) : InventoryAction(plr, true, 4, amount) {

    companion object {

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
        mob.animation(if (usingFire) Animations.FIRE_COOKING else Animations.RANGE_COOKING)

        if (experience != null) {
            mob.sendMessage("You successfully cook a ${food.formattedName}.")
            mob.cooking.addExperience(experience!!)
        } else {
            mob.sendMessage("You accidentally burn the ${food.formattedName}.")
        }
        mob.playSound(Sounds.COOK_FOOD)
    }

    override fun remove() = listOf(food.rawItem)

    override fun add(): List<Item> =
        if (computeFoodBurnt()) {
            experience = null
            listOf(food.burntItem)
        } else {
            experience = food.exp
            listOf(food.cookedItem)
        }

    /**
     * Determines if the food will be burnt this action cycle.
     */
    private fun computeFoodBurnt(): Boolean {

        // Cooking gauntlets decreases burn chance.
        val level = mob.cooking.level
        var burnLevel = food.burnStopLvl
        if (mob.equipment.contains(775)) {
            burnLevel = when (food) {
                Food.LOBSTER -> 64
                Food.SWORDFISH -> 80
                Food.MONKFISH -> 86
                Food.SHARK -> 94
                else -> burnLevel
            }
        }

        // Using the Lumbridge range decreases burn chance.
        if (!usingFire && cookObj.position.equals(RANGE_O_MATIC_POSITION)) {
            burnLevel = when (food) {
                Food.BREAD -> 34
                Food.BEEF -> 31
                Food.BEAR_MEAT -> 31
                Food.CHICKEN -> 31
                Food.RAT_MEAT -> 31
                Food.SHRIMP -> 31
                Food.ANCHOVIES -> 31
                Food.SARDINE -> 34
                Food.HERRING -> 38
                Food.MACKEREL -> 41
                Food.REDBERRY_PIE -> 42
                Food.TROUT -> 45
                Food.COD -> 46
                Food.PIKE -> 49
                Food.MEAT_PIE -> 49
                Food.SALMON -> 55
                else -> burnLevel
            }
        }
        return if (level >= burnLevel) false
        else !Skills.success(chance = food.chance, level = level, modifier = { if (usingFire) it else it + 5.0 })
    }
}