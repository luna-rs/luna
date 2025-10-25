package api.drops

import api.predef.*
import io.luna.game.model.Entity
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Mob
import io.luna.util.RandomUtils
import io.luna.util.Rational

/**
 * Represents a drop table with a fixed chance to roll for item drops. This is the base class for all drop table types
 * and ensures immutability for safe reuse across NPCs or drop sources.
 *
 * Drop tables can include:
 * - Always-dropped items.
 * - Probabilistic drops calculated using rational odds.
 *
 * Subclasses determine how the drop table is computed dynamically based on the context.
 *
 * @author lare96
 */
abstract class DropTable(private val chance: Rational = ALWAYS) : Iterable<DropTableItem> {

    /**
     * Rolls on the drop table based on the provided context [mob] (killer) and [source] (victim).
     *
     * @return A mutable list of all items successfully rolled from the drop table.
     */
   open fun roll(mob: Mob?, source: Entity?): MutableList<Item> {
        val allItems = mutableListOf<Item>()
        if (RandomUtils.roll(chance) && canRollOnTable(mob, source)) {
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
            val pickedItem = rollOnTable(items)
            if (pickedItem != null) {
                allItems += pickedItem
            }
            return allItems
        }
        return allItems
    }

    /**
     * Performs the core drop logic for probabilistic items, creating a rational table that may include empty slots to
     * simulate odds.
     */
    private fun rollOnTable(items: DropTableItemList): Item? {
        return when {
            items.isEmpty() -> null
            items.size == 1 -> {
                val item = items.first()
                if (RandomUtils.roll(item.chance)) item.toItem() else null
            }

            else -> RationalTable(items.map { it.chance to it }).roll()?.toItem()
        }
    }

    /**
     * Determines if the drop table is eligible for rolling based on the external context. This is independent of
     * the [chance] roll. Override this for dynamic eligibility logic.
     */
    open fun canRollOnTable(mob: Mob?, source: Entity?): Boolean {
        return true
    }

    /**
     * Dynamically computes the drop list that will be evaluated for this instance.
     */
    abstract fun computeTable(mob: Mob?, source: Entity?): DropTableItemList

    /**
     * Returns all potential drops regardless of context (used for previewing drop tables).
     */
    abstract fun computePossibleItems(): DropTableItemList

    override fun iterator(): Iterator<DropTableItem> = computePossibleItems().iterator()
}