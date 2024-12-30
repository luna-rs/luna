package api.item.dropTable

import api.item.dropTable.DropTableHandler.rollSuccess
import api.predef.*
import io.luna.game.model.Entity
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Mob
import io.luna.util.RandomUtils
import io.luna.util.Rational

/**
 * Represents a standard drop table. All drop tables must maintain an immutable internal state to ensure
 * instances can be reused.
 *
 * @author lare96
 */
abstract class DropTable(private val chance: Rational = ALWAYS) : Iterable<DropTableItem> {

    /**
     * Rolls on this drop table and returns the selected items.
     */
    fun roll(mob: Mob?, source: Entity?): MutableList<Item> {
        val allItems = mutableListOf<Item>()
        if (canRollOnTable(mob, source)) {
            val items = computeTable(mob, source).filterNot {
                val alwaysDrop = it.chance == ALWAYS
                if (alwaysDrop) {
                    val alwaysItem = it.toItem()
                    if (alwaysItem != null) {
                        allItems += alwaysItem
                    }
                }
                alwaysDrop
            }
            val pickedItem = rollOnTable(mob, source, items)
            if (pickedItem != null) {
                allItems += pickedItem
            }
            return allItems
        }
        return allItems
    }

    /**
     * Rolls on the table items by building a rational table with empty slots, and returns the rolled on item.
     */
    private fun rollOnTable(mob: Mob?, source: Entity?, items: DropTableItemList): Item? {
        if(items.isEmpty()) {
            return null
        } else if (items.size == 1) {
            // Only one item, so our rational table is just the chance that the single item is dropped.
            val pickedItem = items.first()
            return if (rollSuccess(pickedItem)) pickedItem.toItem() else null
        } else {
            return RationalTable(items.map { it.chance to it }).roll()?.toItem()
        }
    }

    /**
     * Determines if this table can be rolled on. If `false`, `null` will always be returned from [roll].
     */
    open fun canRollOnTable(mob: Mob?, source: Entity?): Boolean {
        return RandomUtils.rollSuccess(chance)
    }

    /**
     * Dynamically computes the table of items that will be rolled on.
     */
    abstract fun computeTable(mob: Mob?, source: Entity?): DropTableItemList

    /**
     * Returns all possible items that can be rolled on.
     */
    abstract fun computePossibleItems(): DropTableItemList

    override fun iterator(): Iterator<DropTableItem> = computePossibleItems().iterator()
}