package api.item

import io.luna.game.model.def.ItemDefinition
import io.luna.game.model.item.Item
import io.luna.util.Rational

/**
 * A model representing an item within a [LootTable].
 *
 * @author lare96
 */
class LootTableItem(val id: Int, val amount: IntRange, val chance: Rational) {

    companion object {

        /**
         * Computes the [id] for an item with [name].
         */
        private fun computeId(name: String, noted: Boolean) =
            ItemDefinition.ALL.lookup { it.name == name && it.isNoted == noted }
                .orElseThrow { NoSuchElementException("Item with $name not found.") }.id
    }

    /**
     * A constructor that takes [name] instead of [id].
     */
    constructor(name: String, amount: IntRange, chance: Rational, noted: Boolean = false) :
            this(computeId(name, noted), amount, chance)

    /**
     * Returns this as an item, with a random value within its [amount].
     */
    fun getItem() = Item(id, amount.random())
}