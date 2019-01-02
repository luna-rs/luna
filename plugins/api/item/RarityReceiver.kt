package api.item

import io.luna.util.Rational

/**
 * A model representing the [LootTableReceiver.rarity] receiver.
 *
 * @author lare96
 */
class RarityReceiver(private val lootTable: LootTableReceiver,
                     private val chance: Rational,
                     private val noted: Boolean) {

    /**
     * Returns an instance of this receiver that works with noted items.
     */
    fun noted(func: RarityReceiver.() -> Unit) = func(RarityReceiver(lootTable, chance, true))

    /**
     * Adds an item to the backing loot table.
     */
    infix fun String.x(amount: IntRange) {
        lootTable.items += LootTableItem(this, amount, chance, noted)
    }

    /**
     * Adds an item to the backing loot table.
     */
    infix fun Int.x(amount: IntRange) {
        lootTable.items += LootTableItem(this, amount, chance)
    }

    /**
     * Adds an item to the backing loot table.
     */
    infix fun String.x(amount: Int) = x(amount..amount)

    /**
     * Adds an item to the backing loot table.
     */
    infix fun Int.x(amount: Int) = x(amount..amount)
}