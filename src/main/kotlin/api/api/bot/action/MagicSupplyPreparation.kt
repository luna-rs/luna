package api.bot.action

import api.bot.action.trading.BotTradeIntentions
import api.predef.ext.totalItemAmount
import io.luna.game.model.item.Item
import io.luna.game.model.def.EquipmentDefinition
import io.luna.game.model.mob.bot.Bot
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.isActive
import kotlin.coroutines.coroutineContext

/**
 * Obtains small Magic supply baskets through owned stock, the Item Market, ordinary traders and NPC shops.
 * A single deadline survives shopping children and repeated initialization; partial purchases remain owned.
 *
 * @author lare96
 */
internal class MagicSupplyPreparation(private val bot: Bot) {
    var deadline = 0L
    var interruptedAt = 0L

    /**
     * Requests the desired batch while accepting a smaller usable minimum after a bounded purchase attempt.
     * Existing stock is counted across containers, and every purchase retains the shared acquisition checks.
     */
    suspend fun obtain(targets: Map<Int, Int>, minimum: Map<Int, Int> = targets): Boolean {
        require(targets.values.all { it > 0 } && minimum.all { (id, amount) ->
            amount > 0 && amount <= (targets[id] ?: 0)
        })
        fun ready() = minimum.all { (id, amount) -> bot.totalItemAmount(id) >= amount }
        val gear = targets.keys.filter { EquipmentDefinition.ALL.get(it).isPresent }.toSet()
        BotTradeIntentions.recordSupplies(bot, targets.map { (id, amount) -> Item(id, amount) }, tools = gear)
        targets.forEach { (id, amount) -> bot.preferences.raiseWantedItemTarget(id, amount) }
        if (ready()) {
            deadline = 0L
            interruptedAt = 0L
            return true
        }
        val now = System.currentTimeMillis()
        if (interruptedAt != 0L) {
            deadline = now + (deadline - interruptedAt).coerceAtLeast(0L)
            interruptedAt = 0L
        }
        if (deadline == 0L) deadline = now + 600_000L
        if (now >= deadline) return false
        try {
            withTimeoutOrNull(deadline - now) {
                do {
                    val position = bot.position
                    val stock = targets.mapValues { (id, _) -> bot.totalItemAmount(id) }
                    bot.actionHandler.shop.obtainWantedItems(targets)
                    // The shared attempt can time out during a long shop journey. Continue only if it made progress.
                    val progressed = bot.position != position || stock.any { (id, amount) ->
                        bot.totalItemAmount(id) > amount
                    }
                } while (!ready() && progressed)
            }
        } finally {
            // Shopping children cancel the parent's coroutine. Their travel must not consume its purchase budget.
            if (!coroutineContext.isActive) interruptedAt = System.currentTimeMillis()
        }
        return ready().also { if (it) deadline = 0L }
    }
}
