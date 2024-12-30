package api.item.dropTable

import com.google.common.collect.Iterators
import io.luna.game.model.Entity
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Mob
import io.luna.game.model.mob.Npc

/**
 * Represents a complete [Npc] drop table set which is made up of internal [DropTable] instances.
 *
 * @author lare96
 */
class NpcDropTableSet(private val tables: List<DropTable>) : Iterable<DropTableItem> {

    override fun iterator(): Iterator<DropTableItem> {
        val items = arrayListOf<DropTableItem>()
        tables.forEach { items.addAll(it.computePossibleItems()) }
        return items.iterator()
    }

    override fun toString(): String {
        return Iterators.toString(iterator())
    }

    /**
     * Performs a roll on all the internal tables, and returns a list containing the combined result.
     */
    fun rollAll(mob: Mob?, source: Entity?): List<Item> {
        val dropItems = ArrayList<Item>()
        if (tables.isNotEmpty()) {
            for (table in tables) {
                dropItems += table.roll(mob, source)
            }
        }
        return dropItems
    }
}