package api.item

import api.predef.*
import io.luna.util.Rational

/**
 * A model representing the primary [lootTable] receiver.
 *
 * @author lare96
 */
class LootTableReceiver {

    /**
     * The items to initialize the loot table with.
     */
    val items = mutableListOf<LootTableItem>()

    /**
     * Creates a set of [LootTableItem]s with [chance].
     */
    fun rarity(chance: Rational, func: RarityReceiver.() -> Unit) =
        func(RarityReceiver(this, chance, false))

    /**
     * Create the loot table!
     */
    fun toLootTable() = LootTable(items)
}


