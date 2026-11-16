package engine.bot.coordinator

import api.bot.action.PkLoadoutSelection
import api.bot.action.PkLoadoutPreparation
import api.bot.action.trading.BotMarketExperience
import api.bot.action.trading.BotTradeIntentions
import api.bot.action.trading.BotTradePrice
import api.bot.script.BotScript
import api.bot.script.DynamicBotScript
import api.bot.script.ZonedBotScript
import engine.bot.coordinator.skill.SkillingCoordinator
import engine.controllers.Controllers.inWilderness
import io.luna.game.model.EntityState
import io.luna.game.model.item.Item
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.bot.brain.BotActivity
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext
import kotlin.time.Duration.Companion.minutes

/**
 * Obtains a restricted PK account's missing supplies through the shared wanted-item path and earns its funding first.
 * Physical stock is reassessed on each decision; no coins, runes or experience are granted.
 *
 * @author lare96
 */
internal object PkRuneRestocking {
    /**
     * Returns one shared acquisition visit or an ordinary non-combat profit activity for the requested owned targets.
     * The shared helper owns trading, shop access and purchase-time rechecks.
     */
    fun getScript(bot: Bot, required: Map<Int, Int>): BotScript? {
        if (bot.state != EntityState.ACTIVE || bot.inWilderness() || !BotScript.ActionSafety.NON_COMBAT.allows(bot)) return null
        if (required.isEmpty()) return profitScript(bot)
        BotTradeIntentions.recordSupplies(bot, required.map { (id, amount) -> Item(id, amount) })
        required.forEach { (id, amount) ->
            bot.preferences.raiseWantedItemTarget(id, amount)
        }
        val missing = missingTargets(bot, required)
        if (missing.isEmpty() || fundingNeeded(bot, required)) return profitScript(bot)
        return ObtainSupplies(bot, required.filterKeys { it in missing })
    }

    /**
     * Excludes physically owned supplies before selecting acquisition or funding work.
     */
    internal fun missingTargets(bot: Bot, required: Map<Int, Int>): Map<Int, Int> {
        val selection = PkLoadoutSelection(bot)
        return required.mapValues { (id, amount) ->
            (amount - selection.owned(id)).coerceAtLeast(0)
        }.filterValues { it > 0 }
    }

    /**
     * Compares known supply costs with owned coins without imposing a market-only reserve on NPC purchases.
     * Affordable stocked shops remain usable even when market guide prices are missing or more expensive.
     * Unknown costs leave the shared acquisition helper in charge of trying its available channels.
     */
    internal fun fundingNeeded(bot: Bot, required: Map<Int, Int>): Boolean {
        val missing = missingTargets(bot, required)
        if (missing.isEmpty()) return false
        val coins = PkLoadoutSelection(bot).owned(995).toLong()
        if (coins == 0L) return true
        val shopCost = shopCost(bot, missing)
        if (shopCost != null && coins >= shopCost) return false
        val strategy = BotTradePrice.computePriceStrategy(bot)
        var marketCost = 0L
        for ((id, amount) in missing) {
            val guide = bot.world.economy.getPrice(id)
            if (guide <= 0L) return shopCost != null && coins < shopCost
            val normal = BotMarketExperience.buy(bot, id, strategy.applyBuy(guide))
            if (amount.toLong() > (Long.MAX_VALUE - marketCost) / normal) return true
            marketCost += normal * amount
        }
        return coins < minOf(marketCost, shopCost ?: Long.MAX_VALUE)
    }

    /**
     * Quotes actual quantities across accessible GP shop stock, leaving purchase-time rechecks to the shared helper.
     */
    private fun shopCost(bot: Bot, missing: Map<Int, Int>): Long? {
        var total = 0L
        for ((id, amount) in missing) {
            var remaining = amount
            val shops = bot.world.shops.values().filter {
                it.currency == Currency.COINS && id in it.itemIds && it.botAccess.apply(bot)
            }.sortedBy { it.computeBuyValue(id, 1).orElse(Int.MAX_VALUE) }
            for (shop in shops) {
                val quantity = minOf(remaining, shop.items.computeAmountForId(id))
                if (quantity <= 0) continue
                val quote = shop.computeBuyValue(id, quantity).orElse(-1)
                if (quote < 0) continue
                total += quote
                remaining -= quantity
                if (remaining == 0) break
            }
            if (remaining > 0) return null
        }
        return total
    }

    /**
     * Uses ordinary wanted-item acquisition, retaining its current target when a market child pauses the visit.
     * Actual ownership and account eligibility are rechecked after each suspension and before another purchase.
     *
     * @author lare96
     */
    private class ObtainSupplies(bot: Bot, required: Map<Int, Int>) : DynamicBotScript(bot) {
        private val supplies = required.entries.map { it.key to it.value }
        private var index = 0

        override suspend fun run(): Boolean {
            val preparation = PkLoadoutPreparation(bot, handler)
            while (index < supplies.size && isActionSafe()) {
                if (!preparation.magicRestockRequired()) return true
                val (id, amount) = supplies[index]
                if (missingTargets(bot, mapOf(id to amount)).isEmpty()) {
                    index++
                    continue
                }
                handler.shop.obtainWantedItem(id, amount)
                coroutineContext.ensureActive()
                if (bot.scriptStack.current() !== this) return false
                if (!isActionSafe()) return true
                if (PkLoadoutSelection(bot).owned(id) < amount) return true
                index++
            }
            return true
        }
    }

    /**
     * Selects only General Profit or Profit Skills, respecting their native build and skill eligibility.
     * Timed funding activities last at most ten minutes before ordinary completion and a fresh PK decision.
     * A supply need does not change the account's saved activity interests.
     */
    private fun profitScript(bot: Bot): BotScript? {
        val activities = listOf(BotActivity.GENERAL_PROFIT, BotActivity.PROFIT_SKILLS)
            .filter { bot.preferences.canChooseActivity(it) }
            .sortedByDescending { bot.preferences.activities[it]?.takeIf { weight -> weight.isFinite() } ?: 0.0 }
        for (activity in activities) {
            val script = if (activity == BotActivity.GENERAL_PROFIT) GeneralProfitCoordinator.getScript(bot)
                else (activity.coordinator as SkillingCoordinator).getScript(bot)
            if (script != null) {
                if (script is ZonedBotScript) script.duration = minOf(script.duration, 10.minutes)
                return script
            }
        }
        return null
    }

}
