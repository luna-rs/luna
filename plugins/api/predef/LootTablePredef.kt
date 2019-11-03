/**
 * Initializes a new [LootTableReceiver] and returns the created loot table.
 */
fun lootTable(init: LootTableReceiver.() -> Unit): LootTable {
    val builder = LootTableReceiver()
    init(builder)
    return builder.toLootTable()
}
