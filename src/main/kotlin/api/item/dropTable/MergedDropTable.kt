package api.item.dropTable

import com.google.common.collect.Iterators
import io.luna.game.model.Entity
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Mob

/**
 * A drop table that merges multiple sub-tables into a single logical drop source.
 * Useful for NPCs that should have several independent or conditional drop rolls.
 *
 * Each sub-table is independently evaluated and rolled if [DropTable.canRollOnTable] passes.
 *
 * @author lare96
 */
class MergedDropTable(val tableList: List<DropTable>) : DropTable() {

    /**
     * Use sparingly, can be an expensive operation.
     */
    override fun iterator(): Iterator<DropTableItem> {
        return Iterators.concat(tableList.map { it.iterator() }.iterator())
    }

    override fun toString(): String {
        return Iterators.toString(iterator())
    }

    override fun roll(mob: Mob?, source: Entity?): MutableList<Item> {
        val itemList = ArrayList<Item>()
        if (tableList.isEmpty()) {
            return itemList
        }
        for (table in tableList) {
            itemList += table.roll(mob, source)
        }
        return itemList
    }

    /**
     * Not used in [roll] for this implementation. An aggregation of [computeTable] for all inner tables.
     */
    override fun computeTable(mob: Mob?, source: Entity?): DropTableItemList {
        return DropTableHandler.createList {
            for(table in tableList) {
                items += table.computeTable(mob, source)
            }
        }
    }

    override fun computePossibleItems(): DropTableItemList {
        return DropTableHandler.createList {
            for (drops in tableList) {
                items += drops.computeTable(null, null)
            }
        }
    }
}