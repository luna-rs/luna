package api.drops

import com.google.common.base.MoreObjects
import io.luna.game.model.def.ItemDefinition
import io.luna.game.model.item.Item
import io.luna.util.Rational

/**
 * Represents a possible item drop from a drop table, with variable quantity and drop chance.
 *
 * @property id The item ID, or -1 for a "nothing" entry (represents empty space).
 * @property amount The inclusive range of possible amounts.
 * @property chance The probability of dropping this item.
 * @author lare96
 */
class DropTableItem(val id: Int, val amount: IntRange, val chance: Rational) {

    companion object {

        /**
         * A priority lookup for known item name → ID mappings to prevent ambiguity.
         */
        private val PRIORITY = mapOf(
                "Coins" to 995
        )

        /**
         * Resolves an item name to its ID using priority first, then global item definitions.
         */
        fun computeId(name: String, noted: Boolean): Int {
            val priorityValue = PRIORITY[name]
            if (priorityValue != null) {
                return priorityValue
            }
            return ItemDefinition.ALL.lookup { it.name == name && it.isNoted == noted }
                .orElseThrow { NoSuchElementException("Item with name [$name] not found.") }.id
        }
    }


    constructor(name: String, amount: IntRange, chance: Rational, noted: Boolean = false) :
            this(computeId(name, noted), amount, chance)

    constructor(name: String, amount: Int, chance: Rational, noted: Boolean = false) :
            this(computeId(name, noted), amount..amount, chance)

    constructor(id: Int, amount: Int, chance: Rational) :
            this(id, amount..amount, chance)

    override fun toString(): String {
        return MoreObjects.toStringHelper(this).add("id", id).add("amount", amount).add("chance", chance).toString();
    }

    /**
     * Converts this drop definition into an [Item] instance. If [id] is -1, it is treated as a non-drop and returns
     * null.
     */
    fun toItem(): Item? {
        if (id == -1) {
            return null
        }
        if (amount.first == amount.last) {
            return Item(id, amount.first)
        }
        return Item(id, amount.random())
    }

    /**
     * Checks if this item is a placeholder for no drop.
     */
    fun isNothing() = id == -1
}