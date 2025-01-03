package world.player.skill.cooking.prepareFood

import api.predef.*
import io.luna.game.action.Action
import io.luna.game.action.ItemContainerAction.InventoryAction
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import world.obj.resource.fillable.WaterResource

/**
 * An [InventoryAction] that prepares an [IncompleteFood] type.
 */
class PrepareFoodActionItem(plr: Player, val food: IncompleteFood, private val removeIds: MutableSet<Int>, amount: Int) :
        InventoryAction(plr, true, 1, amount) {

    override fun executeIf(start: Boolean): Boolean =
        when {
            mob.cooking.level < food.lvl -> {
                mob.sendMessage("You need a Cooking level of ${food.lvl} to make this.")
                false
            }

            else -> true
        }

    override fun execute() {
        if (food.exp > 0.0) {
            mob.cooking.addExperience(food.exp)
        }
        val str = if (food != IncompleteFood.PINEAPPLE_RING) "You make ${articleItemName(food.id)}." else
            "You make some ${itemName(food.id)}s."
        mob.sendMessage(str)
    }

    override fun add(): List<Item> {
        val addItems = ArrayList<Item>()
        addItems += when (food) {
            // Using knife with pineapple gives 4 rings.
            IncompleteFood.PINEAPPLE_RING -> Item(food.id, 4)
            // All other food preparation.
            else -> Item(food.id)
        }
        if (currentRemove != null) {
            // Replace items with empty counterparts. Empty buckets, jugs, pots, etc.
            for (item in currentRemove) {
                val replaceId = computeReplacedItems(item.id)
                if (replaceId != null) {
                    addItems += Item(replaceId, item.amount)
                }
            }
        }
        return addItems
    }

    override fun remove() = when {
        // Uncooked cake requires all ingredients at once.
        food == IncompleteFood.UNCOOKED_CAKE -> listOf(*food.otherIngredients.map { Item(it) }.toTypedArray())
        // Making uncooked curry requires 3 leaves.
        food == IncompleteFood.UNCOOKED_CURRY -> {
            removeIds.map {
                if (it == 5970) {
                    Item(it, 3)
                } else {
                    Item(it)
                }
            }
        }
        // Don't remove knife when cutting pineapple.
        food == IncompleteFood.PINEAPPLE_RING -> listOf(Item(2114))

        else -> removeIds.map { Item(it) }
    }


    override fun ignoreIf(other: Action<*>?): Boolean =
        when (other) {
            is PrepareFoodActionItem -> other.food == food
            else -> false
        }

    /**
     * Determines if and what a removed item will be replaced by. Used to empty containers.
     */
    private fun computeReplacedItems(id: Int): Int? {
        // Handle all water containers dynamically.
        val inverseFillables = WaterResource.FILLABLES.inverse()
        val emptyId = inverseFillables[id]
        if (emptyId != null) {
            return emptyId
        }
        return when (id) {
            1933 -> 1932 // Pot of flour
            1927 -> 1925 // Bucket of milk
            else -> null
        }
    }
}