package world.player.skill.herblore.grindIngredient

import api.predef.itemName
import io.luna.game.action.impl.ItemContainerAction.InventoryAction
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Animation

/**
 * An [InventoryAction] that will grind all ingredients in an inventory.
 */
class GrindActionItem(
    plr: Player,
    val ingredient: Ingredient,
    makeTimes: Int
) : InventoryAction(plr, true, 2, makeTimes) {

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
        val oldName = itemName(ingredient.id)
        val newName = itemName(ingredient.newId)
        val nextWord = if (ingredient == Ingredient.CRUSHED_NEST) "a" else "some"
        mob.sendMessage("You grind the $oldName into $nextWord $newName.")

        mob.animation(ANIMATION)
    }

    override fun remove() = listOf(ingredient.oldItem)

    override fun add() = listOf(ingredient.newItem)
}