package api.bot.action.trading

import api.bot.Suspendable.waitFor
import api.bot.script.BotScript.ActionSafety
import api.predef.ext.*
import engine.trade.ConfirmTradeInterface
import engine.trade.OfferTradeInterface
import engine.trade.tradingWith
import io.luna.game.model.EntityState
import io.luna.game.model.item.Item
import io.luna.game.model.mob.bot.Bot
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

/**
 * Exchanges agreed rune-running baskets through each participant's own native trade actions.
 * Empty payment is permitted for a crafter-funded first load; two empty baskets never form a trade.
 *
 * @author lare96
 */
object RuneRunningTrade {

    /**
     * Counts real item ids without merging notes with their unnoted counterparts or overflowing quantities.
     */
    private fun quantities(items: Collection<Item?>): Map<Int, Long>? {
        val result = mutableMapOf<Int, Long>()
        for (item in items.filterNotNull()) {
            if (item.amount <= 0 || item.isDynamic) return null
            val total = (result[item.id] ?: 0L) + item.amount.toLong()
            if (total > Int.MAX_VALUE) return null
            result[item.id] = total
        }
        return result
    }

    /**
     * Requires exact ids and quantities, including an empty basket for an explicitly agreed starter gift.
     */
    internal fun matches(items: Collection<Item?>, expected: List<Item>): Boolean {
        val actual = quantities(items) ?: return false
        val target = quantities(expected) ?: return false
        return actual == target
    }

    /**
     * Offers a fixed basket and validates both participants again on each acceptance screen.
     * Calls run independently in the two owning bot coroutines; no partner's actions are performed here.
     * Unanswered requests, interrupted sessions and changed baskets close only this participant's trade.
     */
    suspend fun exchange(bot: Bot, other: Bot, give: List<Item>, receive: List<Item>,
                         canContinue: () -> Boolean): Boolean {
        if (bot === other || give.isEmpty() && receive.isEmpty()) return false
        val outgoing = quantities(give) ?: return false
        if (quantities(receive) == null || outgoing.any { (id, amount) ->
                bot.inventory.computeAmountForId(id).toLong() < amount
            }) return false
        fun ready() = bot.state == EntityState.ACTIVE && other.state == EntityState.ACTIVE &&
            ActionSafety.NON_COMBAT.allows(bot) && ActionSafety.NON_COMBAT.allows(other) && canContinue()
        var success = false
        try {
            success = withTimeoutOrNull(75.seconds) {
                if (!ready()) return@withTimeoutOrNull false
                val stage = BotTradeRequestStage(bot, other).sendAndAwait(::ready)
                    ?: return@withTimeoutOrNull false
                val screen = bot.overlays[OfferTradeInterface::class]
                    ?: return@withTimeoutOrNull false
                if (screen.other !== other || screen.items.filterNotNull().isNotEmpty()) {
                    return@withTimeoutOrNull false
                }
                screen.samplesMarketPrices = false
                if (!stage.offerAll(give)) return@withTimeoutOrNull false
                fun valid(): Boolean = ready() && matches(screen.items.filterNotNull(), give) &&
                    screen.otherOffer.other === bot && matches(screen.otherOffer.items.filterNotNull(), receive)
                if (!waitFor(20.seconds) {
                        !ready() || bot.overlays[OfferTradeInterface::class] !== screen || valid()
                    } || !valid() || bot.overlays[OfferTradeInterface::class] !== screen) {
                    return@withTimeoutOrNull false
                }
                val confirmation = stage.offerAndAwait(::valid) ?: return@withTimeoutOrNull false
                val own = bot.overlays[ConfirmTradeInterface::class]
                val partner = other.overlays[ConfirmTradeInterface::class]
                if (own?.offer !== screen || partner?.offer !== screen.otherOffer || !valid()) {
                    return@withTimeoutOrNull false
                }
                confirmation.confirmAndAwait()
            } == true
            return success
        } finally {
            if (!success) BotTradeStage(bot, other).decline()
            if (bot.tradingWith == other.index) bot.tradingWith = -1
        }
    }
}
