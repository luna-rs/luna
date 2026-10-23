package api.bot.script

import io.luna.game.model.item.Item
import io.luna.game.model.mob.bot.Bot

/**
 * Calculates a balanced bank withdrawal for recipes with non-stackable inputs and non-stackable tools.
 *
 * Supplies come from the bank after the existing inventory script deposits the old inventory. This helper has no
 * script lifecycle, interaction, retry state, or persistence. Recipes must not increase occupied inventory slots.
 */
fun Bot.productionBatch(materials: List<Item>, tools: Set<Int> = emptySet()): List<Item> {
    require(materials.isNotEmpty() && materials.all { it.amount > 0 })
    require(materials.map { it.id }.distinct().size == materials.size)
    require(materials.none { it.id in tools })
    if (tools.any { bank.computeAmountForId(it) < 1 }) return emptyList()
    val slots = inventory.capacity() - tools.size
    val batches = minOf(slots / materials.sumOf { it.amount },
        materials.minOf { bank.computeAmountForId(it.id) / it.amount })
    if (batches < 1) return emptyList()
    return tools.map { Item(it) } + materials.map { Item(it.id, it.amount * batches) }
}

/** Checks the minimum recipe supplies across inventory and bank, including tools held in the inventory. */
fun Bot.ownsProductionSupplies(materials: List<Item>, tools: Set<Int> = emptySet()): Boolean {
    fun owned(id: Int) = bank.computeAmountForId(id).toLong() + inventory.computeAmountForId(id)
    return materials.all { owned(it.id) >= it.amount } && tools.all { owned(it) > 0 }
}
