package api.predef

import api.item.LootTable
import api.item.LootTableReceiver

/**
 * Initializes a new [LootTableReceiver] and returns the created loot table. Here's an example of how it should be
 * used
 *
 * ```
 * lootTable {
 *     rarity(COMMON) {
 *         "Tinderbox" x 1
 *         "Hammer" x 1
 *     }
 *
 *     rarity(UNCOMMON) {
 *         "Rune kiteshield" x 1..3
 *         "Dragon dagger" x 1..2
 *     }
 *
 *     rarity(VERY_RARE) {
 *         "Abyssal whip" x 1
 *         "Coins" x 100_000..200_000
 *         "Death rune" x 1000..2000
 *
 *         noted {
 *             "Iron ore" x 25..50
 *             "Gold ore" x 5..10
 *         }
 *     }
 * }
 * ```
 */
// TODO Move example for above into wiki
fun lootTable(init: LootTableReceiver.() -> Unit): LootTable {
    val builder = LootTableReceiver()
    init(builder)
    return builder.toLootTable()
}