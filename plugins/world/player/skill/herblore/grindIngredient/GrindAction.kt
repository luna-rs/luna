package world.player.skill.herblore.grindIngredient

import api.predef.*
import io.luna.game.action.Action
import io.luna.game.action.InventoryAction
import io.luna.game.model.mob.Animation
import io.luna.game.model.mob.Player

/**
 * An [InventoryAction] that will grind all ingredients in an inventory.
 */
class GrindAction(plr: Player,
                  val ingredient: Ingredient,
                  makeTimes: Int) : InventoryAction(plr, true, 2, makeTimes) {

    companion object {

        /**
         * The grinding animation.
         */
        val ANIMATION = Animation(364)
    }

    override fun executeIf(start: Boolean) =
        when {
            !mob.inventory.contains(Ingredient.PESTLE_AND_MORTAR) -> false
            else -> true
        }


    override fun execute() {
        val oldName = itemDef(ingredient.id).name
        val newName = itemDef(ingredient.newId).name
        val nextWord = if (ingredient == Ingredient.CRUSHED_NEST) "a" else "some"
        mob.sendMessage("You grind the $oldName into $nextWord $newName.")

        mob.animation(ANIMATION)
    }

    override fun remove() = listOf(ingredient.oldItem)

    override fun add() = listOf(ingredient.newItem)

    override fun ignoreIf(other: Action<*>) =
        when (other) {
            is GrindAction -> ingredient == other.ingredient
            else -> false
        }
}