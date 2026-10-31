package engine.bot.coordinator

import api.bot.action.trading.BotTradePurpose
import api.bot.action.trading.BotTradeManager
import api.bot.action.trading.BotTradePrice
import api.bot.action.trading.BotTradeIntentions
import api.bot.script.BotScript.ActionSafety
import api.predef.*
import api.bot.action.trading.BotMarketplace
import api.predef.ext.*
import game.bot.scripts.trading.BuyFromStoreBotScript
import game.bot.scripts.trading.SellToStoreBotScript
import game.bot.scripts.trading.TradeBotScript
import engine.controllers.Controllers.inWilderness
import io.luna.game.model.EntityState
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.bot.brain.BotActivity
import io.luna.game.model.mob.bot.brain.BotBrain.BotCoordinator
import io.luna.game.model.mob.bot.brain.BotEmotion.EmotionType
import io.luna.game.model.item.shop.Currency

/**
 * Coordinates merchanting activities for bots.
 *
 * Selects economy-related scripts based on the bot's wanted-item stock, merchanting preferences,
 * personality, and emotions. Merchants trade resale stock with players and keep their buying advertisements broad.
 * Ordinary bots buy deliberate supplies and upgrades, or sell their surplus.
 *
 * Trading and shop interactions are performed by the scripts pushed onto the bot's script stack.
 * This coordinator only determines which activity the bot should attempt.
 *
 * @author lare96
 */
object MerchantingCoordinator : BotCoordinator {

    /**
     * Creates a selling-only follow-up for a completed activity without changing merchant preferences.
     * Ordinary accounts sell ordinary surplus. Merchants sell their available resale stock while retaining
     * concrete personal supplies and preferred equipment.
     */
    internal fun getSellingScript(bot: Bot): TradeBotScript? {
        if (bot.state != EntityState.ACTIVE || bot.inWilderness() || !ActionSafety.NON_COMBAT.allows(bot)) return null
        val market = availableMarket(bot) ?: return null
        val purpose = sellingPurpose(bot) ?: return null
        return TradeBotScript(bot, purpose, market)
    }

    /**
     * Resolves a useful selling purpose from actual priced, transferable surplus. Empty and negligible
     * offers skip the visit; a follow-up never accidentally chooses a purchase through the general coordinator.
     */
    internal fun sellingPurpose(bot: Bot): BotTradePurpose? {
        val strategy = BotTradePrice.computePriceStrategy(bot)
        val purposes = if (bot.preferences.isMerchant) listOf(BotTradePurpose.MERCHANT_SELL)
            else listOf(BotTradePurpose.SELL)
        return purposes.firstOrNull { purpose ->
            BotTradeManager.computeOfferItems(bot, strategy, purpose).sumOf { it.totalPrice().toLong() } >=
                BotTradeManager.MINIMUM_OFFER_VALUE
        }
    }

    override fun accept(bot: Bot) {
        if (bot.preferences.isMerchant) {
            val market = availableMarket(bot) ?: return
            val selling = sellingPurpose(bot)
            val canBuy = bot.totalItemAmount(995).toDouble() *
                BotTradeIntentions.budgetFraction(bot, BotTradePurpose.MERCHANT_BUY) >= BotTradeManager.MINIMUM_OFFER_VALUE
            val purpose = when {
                chooseSelling(selling != null, canBuy) -> selling
                canBuy -> BotTradePurpose.MERCHANT_BUY
                else -> null
            }
            if (purpose != null) bot.scriptStack.push(TradeBotScript(bot, purpose, market))
            return
        }
        val selling = sellingPurpose(bot)
        val needs = BotTradeIntentions.purchases(bot)
        val strategy = BotTradePrice.computePriceStrategy(bot)
        val payment = if (needs.isEmpty()) emptyList() else
            BotTradeManager.computeBudgetedOfferItems(bot, strategy, BotTradePurpose.BUY)
        val canMarketBuy = payment.sumOf { it.totalPrice().toLong() } >= BotTradeManager.MINIMUM_OFFER_VALUE &&
            BotTradeManager.computeExpected(bot, strategy, BotTradePurpose.BUY, payment).isNotEmpty()
        val shopBudget = (bot.totalItemAmount(995).toDouble() *
            BotTradeIntentions.budgetFraction(bot, BotTradePurpose.BUY)).toInt()
        val shops = if (shopBudget <= 0 || needs.isEmpty()) emptyList() else world.shops.values()
            .filter { it.currency == Currency.COINS && it.botAccess.apply(bot) }
        val canShopBuy = needs.any { wanted ->
            shops.any { shop ->
                val price = shop.computeBuyValue(wanted.id, 1)
                price.isPresent && price.asInt in 1..shopBudget
            }
        }
        val needsItems = canMarketBuy || canShopBuy
        if (chooseSelling(selling != null, needsItems)) {
            // Start trading script, or sell items to store.
            val purpose = BotTradePurpose.SELL
            val isDumbOrGreedy = bot.personality.isDumb || (bot.personality.isIntelligent && bot.emotions.isFeeling(
                EmotionType.GREEDY))
            val store = if (isDumbOrGreedy) SellToStoreBotScript(bot) else null
            if (store != null && randBoolean() && store.saleCandidates().isNotEmpty()) {
                bot.scriptStack.push(store)
            } else {
                val market = availableMarket(bot) ?: return
                bot.scriptStack.push(TradeBotScript(bot, purpose, market))
            }
        } else if (needsItems) {
            // Start trading script, or buy items from store.
            val purpose = BotTradePurpose.BUY
            val isShopper = bot.personality.isIntelligent && !bot.preferences.hatesActivity(BotActivity.MERCHANTING) ||
                    bot.preferences.isMerchant
            if (canShopBuy && (!canMarketBuy || rand(3) == 0 || isShopper && randBoolean())) {
                bot.scriptStack.push(BuyFromStoreBotScript(bot, shopBudget))
            } else {
                val market = availableMarket(bot) ?: return
                bot.scriptStack.push(TradeBotScript(bot, purpose, market))
            }
        }
    }

    /**
     * Avoids constructing a doomed market visit when every trading pool is full.
     */
    private fun availableMarket(bot: Bot): BotMarketplace? {
        val markets = BotMarketplace.entries.filter { it.hasAvailableTradingSpot(bot) }
        return if (bot.personality.isDextrous) markets.minByOrNull { it.arrival.computeLongestDistance(bot.position) }
            else markets.randomOrNull()
    }

    /**
     * Gives buying and selling equal chances when both are available.
     */
    internal fun chooseSelling(hasSurplus: Boolean, needsItems: Boolean,
                               sellCoinFlip: () -> Boolean = { randBoolean() }): Boolean =
        hasSurplus && (!needsItems || sellCoinFlip())
}
