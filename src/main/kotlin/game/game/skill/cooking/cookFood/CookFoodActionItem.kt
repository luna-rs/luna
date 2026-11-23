package game.skill.cooking.cookFood

import api.predef.*
import api.predef.ext.*
import game.content.randomEvents.RandomEvents
import game.player.Animations
import game.player.Sound
import io.luna.game.action.impl.ItemContainerAction.InventoryAction
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.`object`.GameObject
import io.luna.util.RandomUtils

/**
 * Cooks food through the native inventory action, preserving burn rolls and recipe experience.
 * A ready general random event can interrupt the initial attempt before any food is consumed.
 *
 * @author lare96
 */
class CookFoodActionItem(plr: Player,
                         private val cookObj: GameObject,
                         val food: Food,
                         val usingFire: Boolean,
                         amount: Int) : InventoryAction(plr, true, 4, amount) {

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

            start && RandomEvents.offerProduction(mob, remove()) -> false
            else -> true
        }

    override fun execute() {
        mob.face(cookObj.position)
        mob.animation(if (usingFire) Animations.FIRE_COOKING else Animations.RANGE_COOKING)

        if (experience != null) {
            mob.sendMessage("You successfully cook a ${food.formattedName}.")
            mob.cooking.addExperience(experience!!)
        } else {
            mob.sendMessage("You accidentally burn the ${food.formattedName}.")
        }
        mob.playSound(Sound.FRY)
    }

    override fun remove() = listOf(food.rawItem)

    override fun add(): List<Item> =
        if (computeFoodBurnt()) {
            experience = null
            listOfNotNull(food.burntItem, food.extraItem)
        } else {
            experience = food.exp
            listOfNotNull(food.cookedItem, food.extraItem)
        }

    /**
     * Determines if the food will be burnt this action cycle.
     */
    private fun computeFoodBurnt(): Boolean {
        val rate = CookingSuccess.successRate(food, mob.cooking.level, usingFire,
            mob.equipment.contains(775), cookObj.position)
        if (food == Food.KARAMBWAN) return !rand(rate)
        return rate < 1.0 && rate <= RandomUtils.nextDouble()
    }
}
