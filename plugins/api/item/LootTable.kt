package api.item

import api.predef.*
import io.luna.game.model.item.Item

/**
 * A model representing a loot table. Loot tables are collections of items that can select items to be picked based
 * on their rarity.
 *
 * @author lare96
 */
class LootTable(private val items: List<LootTableItem>) : Iterable<LootTableItem> {

    override fun iterator(): Iterator<LootTableItem> = items.iterator()

    /**
     * Rolls on one item from this loot table.
     */
    fun pick(): Item? {
        val all = pickAll()
        return when (all.isEmpty()) {
            true -> null
            false -> all.random()
        }
    }

    /**
     * Rolls on all items from this loot table.
     */
    fun pickAll(): List<Item> {
        val lootItems = ArrayList<Item>(items.size)
        for (loot in items) {
            if (roll(loot)) {
                lootItems += loot.getItem()
            }
        }
        return lootItems
    }

    /**
     * Determines if [loot] will be picked based on its rarity.
     */
    private fun roll(loot: LootTableItem): Boolean {
        val chance = loot.chance
        return when {
            chance.numerator <= 0 -> false
            chance.numerator >= chance.denominator -> true
            rand(0, chance.denominator) <= chance.numerator -> true
            else -> false
        }
    }
}
