package api.predef.ext

import api.predef.itemDef
import engine.bot.gear.BotItemTracker.Companion.itemTracker
import io.luna.game.model.def.WantedItemDefinition
import io.luna.game.model.mob.bot.brain.BotPreference
import kotlin.math.max

/**
 * Checks whether this bot currently owns at least [amount] of the item with the specified [id].
 *
 * If the bot does not have enough, a temporary wanted-item entry is added or updated. The target amount is never lowered;
 * if the bot already wanted more of this item, the higher amount is preserved.
 *
 * The generated wanted item has no timeout, level requirement, price limit, or interest decay.
 *
 * @param id The item id to check for.
 * @param amount The amount required.
 * @return `true` if the bot already has at least [amount], otherwise `false`.
 */
fun BotPreference.requireItem(id: Int, amount: Int): Boolean {
    val notedId = itemDef(id).notedId
    var totalCount = bot.itemTracker.count(id)
    if(notedId.isPresent && notedId.asInt != id) {
        totalCount += bot.itemTracker.count(notedId.asInt)
    }
    if (totalCount < amount) {
        // Create a new temporary wanted item that the bot never loses interest in acquiring.
        val existingAmount = getWantedItem(id)?.target ?: 0
        addWantedItem(id, max(amount, existingAmount))
        return false
    }
    return true
}