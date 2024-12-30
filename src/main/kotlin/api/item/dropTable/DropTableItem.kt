package api.item.dropTable

import api.predef.*
import com.google.common.base.MoreObjects
import io.luna.game.model.def.ItemDefinition
import io.luna.game.model.item.Item
import io.luna.util.Rational

/**
 * A model representing an item within a [NpcDropTableSet]. Please keep in mind that [amount] works inclusively both ways.
 *
 * @author lare96
 */
class DropTableItem(val id: Int, val amount: IntRange, val chance: Rational) {

    companion object {

        /**
         * Priority name -> ID mappings to avoid conflicts.
         */
        private val PRIORITY = mapOf(
                "Coins" to 995
        )

        /**
         * Computes the [id] for an item with [name].
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

    /**
     * A constructor that takes [name] instead of [id].
     */
    constructor(name: String, amount: IntRange, chance: Rational, noted: Boolean = false) :
            this(computeId(name, noted), amount, chance)

    /**
     * A constructor that takes [name] instead of [id].
     */
    constructor(name: String, amount: Int, chance: Rational, noted: Boolean = false) :
            this(computeId(name, noted), amount..amount, chance)

    /**
     * A constructor that takes a single [Int] amount value instead of an [IntRange].
     */
    constructor(id: Int, amount: Int, chance: Rational) :
            this(id, amount..amount, chance)

    override fun toString(): String {
        return MoreObjects.toStringHelper(this).add("id", id).add("amount", amount).add("chance", chance).toString();
    }

    /**
     * Returns this as an item, with a random value within its [inclusiveAmount].
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
     * Determines if this item is an empty slot.
     */
    fun isNothing() = id == -1
}