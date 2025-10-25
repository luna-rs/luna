package game.skill.fletching.attachArrow

import api.predef.*
import io.luna.game.action.impl.ItemContainerAction.InventoryAction
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player

/**
 * An [InventoryAction] that will attach arrowtips to headless arrows.
 *
 * @author lare96
 */
class MakeArrowActionItem(plr: Player,
                          val arrow: Arrow,
                          makeTimes: Int) : InventoryAction(plr, true, 3, makeTimes) {

    /**
     * The amount of arrows to make in this set.
     */
    var setAmount = 0

    override fun add() = listOf(Item(arrow.id, setAmount))
    override fun remove(): List<Item> {
        val tipItem = Item(arrow.tip, setAmount)
        val withItem = Item(arrow.with, setAmount)
        return listOf(tipItem, withItem)
    }

    override fun executeIf(start: Boolean): Boolean {
        return when {

            // Check fletching level.
            mob.fletching.level < arrow.level -> {
                mob.sendMessage("You need a Fletching level of ${arrow.level} to attach this.")
                false
            }

            // Check if there's enough materials.
            else -> {
                val withCount = mob.inventory.computeAmountForId(arrow.with)
                val tipCount = mob.inventory.computeAmountForId(arrow.tip)
                setAmount = Integer.min(withCount, tipCount)
                setAmount = Integer.min(setAmount, Arrow.SET_AMOUNT)

                setAmount != 0
            }
        }
    }

    override fun execute() {
        val withName = itemName(arrow.with)
        val tipName = itemName(arrow.tip)
        mob.sendMessage("You attach the $tipName to the $withName.")

        mob.fletching.addExperience(arrow.exp * setAmount)
    }

}