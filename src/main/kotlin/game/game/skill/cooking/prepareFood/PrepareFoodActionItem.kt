package game.skill.cooking.prepareFood

import api.predef.*
import game.obj.resource.fillable.WaterResource
import io.luna.game.action.impl.ItemContainerAction.InventoryAction
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player

/**
 * An [InventoryAction] that prepares an [IncompleteFood] type.
 * Consumed water, milk, and flour normally return their empty containers. Tea and stew prepared in a bowl
 * retain that bowl in the product; pouring nettle tea into a cup returns the emptied bowl instead.
 *
 * @author lare96
 */
class PrepareFoodActionItem(plr: Player,
                            val food: IncompleteFood,
                            private val removeIds: MutableSet<Int>,
                            amount: Int) :
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
        val name = food.name
        val str =
            if (name.endsWith("DOUGH")) "You make some ${itemName(food.id)}."
            else if (food != IncompleteFood.PINEAPPLE_RING) "You make ${articleItemName(food.id)}."
            else "You make some ${itemName(food.id)}s."
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

    /**
     * Returns the emptied container for a consumed ingredient when the product does not retain it.
     * Bowl-based tea and stew keep their source bowl; cup preparation empties its tea bowl.
     */
    private fun computeReplacedItems(id: Int): Int? {
        if (id == 1921 && (food == IncompleteFood.NETTLE_WATER ||
                food == IncompleteFood.INCOMPLETE_STEW_WITH_POTATO ||
                food == IncompleteFood.INCOMPLETE_STEW_WITH_MEAT)) return null
        if (id == 4239 && food == IncompleteFood.CUP_OF_NETTLE_TEA) return 1923
        // Handle all water containers dynamically.
        val inverseFillables = WaterResource.FILLABLES.inverse()
        val emptyId = inverseFillables[id]
        if (emptyId != null) {
            return emptyId
        }
        return when (id) {
            1933 -> 1931 // Pot of flour
            1927 -> 1925 // Bucket of milk
            else -> null
        }
    }
}
