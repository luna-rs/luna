package engine.bot.coordinator

import api.bot.action.trading.BotTradePurpose
import api.bot.action.trading.BotTradeManager
import api.bot.action.trading.BotTradePrice
import api.bot.action.trading.BotTradeIntentions
import api.bot.action.trading.BotMarketCooldown
import api.bot.action.trading.MerchantStoreBargains
import api.bot.script.BotScript.ActionSafety
import api.predef.*
import api.bot.action.trading.BotMarketplace
import api.predef.ext.*
import game.bot.scripts.trading.BuyFromStoreBotScript
import game.bot.scripts.trading.BuyStoreBargainBotScript
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
        val purpose = sellingPurpose(bot) ?: return null
        val market = BotMarketplace.select(bot)
        return TradeBotScript(bot, purpose, market)
    }

    /**
     * Resolves a useful selling purpose from actual priced, transferable surplus. Empty and negligible
     * offers skip the visit; a follow-up never accidentally chooses a purchase through the general coordinator.
     */
    internal fun sellingPurpose(bot: Bot): BotTradePurpose? {
        if (!BotMarketCooldown.ready(bot, BotTradePurpose.SELL)) return null
        val strategy = BotTradePrice.computePriceStrategy(bot)
        val purposes = if (bot.preferences.isMerchant) listOf(BotTradePurpose.MERCHANT_SELL)
            else listOf(BotTradePurpose.SELL)
        return purposes.firstOrNull { purpose ->
            BotTradeManager.computeOfferItems(bot, strategy, purpose).sumOf { it.totalPrice().toLong() } >=
                BotTradeManager.MINIMUM_OFFER_VALUE
        }
    }

    override fun accept(bot: Bot) {
        val canMarketSell = BotMarketCooldown.ready(bot, BotTradePurpose.SELL)
        val canMarketBuyNow = BotMarketCooldown.ready(bot, BotTradePurpose.BUY)
        if (bot.preferences.isMerchant) {
            if (!canMarketSell && !canMarketBuyNow) return
            (if (canMarketSell) MerchantStoreBargains.poll(bot) else null)?.let { deal ->
                bot.scriptStack.push(BuyStoreBargainBotScript(bot, deal))
                bot.scriptStack.pushTail(TradeBotScript(bot, BotTradePurpose.MERCHANT_SELL))
                return
            }
            val selling = sellingPurpose(bot)
            val canBuy = canMarketBuyNow && bot.totalItemAmount(995).toDouble() *
                BotTradeIntentions.budgetFraction(bot, BotTradePurpose.MERCHANT_BUY) >= BotTradeManager.MINIMUM_OFFER_VALUE
            val purpose = when {
                chooseSelling(selling != null, canBuy) -> selling
                canBuy -> BotTradePurpose.MERCHANT_BUY
                else -> null
            }
            if (purpose != null) bot.scriptStack.push(TradeBotScript(bot, purpose, BotMarketplace.select(bot)))
            return
        }
        val selling = sellingPurpose(bot)
        val needs = BotTradeIntentions.purchases(bot)
        val strategy = BotTradePrice.computePriceStrategy(bot)
        val payment = if (!canMarketBuyNow || needs.isEmpty()) emptyList() else
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
                val market = BotMarketplace.select(bot)
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
                val market = BotMarketplace.select(bot)
                bot.scriptStack.push(TradeBotScript(bot, purpose, market))
            }
        }
    }

    /**
     * Gives buying and selling equal chances when both are available.
     */
    internal fun chooseSelling(hasSurplus: Boolean, needsItems: Boolean,
                               sellCoinFlip: () -> Boolean = { randBoolean() }): Boolean =
        hasSurplus && (!needsItems || sellCoinFlip())
}
