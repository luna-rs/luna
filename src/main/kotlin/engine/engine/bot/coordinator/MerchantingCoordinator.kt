package engine.bot.coordinator

import api.bot.action.trading.BotTradePurpose
import api.bot.action.trading.BotTradeManager
import api.bot.action.trading.BotTradePrice
import api.bot.action.trading.BotTradeIntentions
import api.bot.action.trading.BotMarketCooldown
import api.bot.action.trading.MerchantStoreBargains
import api.bot.action.trading.BotMerchantCapital
import api.attr.Attr
import io.luna.game.model.mob.bot.brain.BotActivity
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
import io.luna.game.model.mob.bot.brain.BotBrain.BotCoordinator
import io.luna.game.model.item.shop.Currency

/**
 * Coordinates merchanting activities for bots.
 *
 * Selects economy-related scripts based on the bot's wanted-item stock, merchanting preferences,
 * personality, and finances. Merchants and bots who enjoy merchanting can take funded production,
 * gathering or shop work. Trading also serves personal supplies, upgrades and surplus.
 *
 * Trading and shop interactions are performed by the scripts pushed onto the bot's script stack.
 * This coordinator only determines which activity the bot should attempt.
 *
 * @author lare96
 */
object MerchantingCoordinator : BotCoordinator {

    private val nextFundingCheck = Attr.long()

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
            BotTradeManager.computeOfferItems(bot, strategy, purpose, nativeTrade = true)
                .sumOf { it.totalPrice().toLong() } >=
                BotTradeManager.MINIMUM_OFFER_VALUE
        }
    }

    override fun accept(bot: Bot) {
        val canMarketSell = BotMarketCooldown.ready(bot, BotTradePurpose.SELL)
        val canMarketBuyNow = BotMarketCooldown.ready(bot, BotTradePurpose.BUY)
        if (bot.preferences.isMerchant) {
            val capital = BotMerchantCapital.snapshot(bot)
            val selling = sellingPurpose(bot)
            if ((selling == null || !capital.cashRecovery && !capital.stockHeavy) &&
                !capital.busy) {
                MerchantWork.script(bot, capital)?.let {
                    bot.scriptStack.push(it)
                    return
                }
            }
            if (!canMarketSell && !canMarketBuyNow) {
                if (capital.cashRecovery) fundMerchant(bot)
                return
            }
            val canBuy = canMarketBuyNow && !capital.busy &&
                (capital.electiveCoins >= BotTradeManager.MINIMUM_OFFER_VALUE || capital.hasPersonalDemand() &&
                    capital.coins.toDouble() * BotTradeIntentions.budgetFraction(bot, BotTradePurpose.MERCHANT_BUY) >=
                        BotTradeManager.MINIMUM_OFFER_VALUE)
            val purpose = when {
                selling != null && (capital.cashRecovery || capital.stockHeavy) -> selling
                chooseSelling(selling != null, canBuy) -> selling
                canBuy -> BotTradePurpose.MERCHANT_BUY
                else -> null
            }
            if (purpose != null) {
                bot.scriptStack.push(TradeBotScript(bot, purpose, BotMarketplace.select(bot)))
                if (!capital.cashRecovery && !capital.stockHeavy)
                    MerchantStoreBargains.poll(bot)?.let { bot.scriptStack.pushTail(BuyStoreBargainBotScript(bot, it)) }
            } else if (capital.cashRecovery) fundMerchant(bot)
            return
        }
        MerchantWork.script(bot)?.let {
            bot.scriptStack.push(it)
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
            .filter { it.currency == Currency.COINS && it.npcSellers.isNotEmpty() }
        val canShopBuy = needs.any { wanted ->
            shops.any { shop ->
                val price = shop.computeBuyValue(wanted.id, 1)
                price.isPresent && price.asInt in 1..shopBudget
            }
        }
        val needsItems = canMarketBuy || canShopBuy
        val store = if (canMarketSell && bot.personality.isDumb) SellToStoreBotScript(bot) else null
        val storeOnly = store?.takeIf { selling == null && it.saleCandidates().isNotEmpty() }
        if (chooseSelling(selling != null || storeOnly != null, needsItems)) {
            val desperate = bot.totalItemAmount(995) < 1_000
            val shopSale = storeOnly ?: store?.takeIf {
                (desperate || randBoolean()) && it.saleCandidates().isNotEmpty()
            }
            if (shopSale != null) {
                bot.scriptStack.push(shopSale)
            } else if (selling != null) {
                val market = BotMarketplace.select(bot)
                bot.scriptStack.push(TradeBotScript(bot, BotTradePurpose.SELL, market))
            }
        } else if (needsItems) {
            // Visit the market before using a shop for wanted items.
            val purpose = BotTradePurpose.BUY
            if (canMarketBuy) {
                val market = BotMarketplace.select(bot)
                bot.scriptStack.push(TradeBotScript(bot, purpose, market))
            } else if (canShopBuy) {
                bot.scriptStack.push(BuyFromStoreBotScript(bot, shopBudget))
            }
        }
    }

    /**
     * Uses existing permitted profit activities after useful liquidation is unavailable, with a bounded retry.
     */
    private fun fundMerchant(bot: Bot) {
        val interest = bot.preferences.activities[BotActivity.GENERAL_PROFIT] ?: return
        if (!interest.isFinite() || interest <= 0.0 ||
            !bot.preferences.canChooseActivity(BotActivity.GENERAL_PROFIT)) return
        val now = System.currentTimeMillis()
        val attributes = bot.attributes()
        if (attributes.has(nextFundingCheck.attr) && now < attributes[nextFundingCheck.attr]) return
        attributes[nextFundingCheck.attr] = now + 300_000L
        GeneralProfitCoordinator.getScript(bot)?.let { bot.scriptStack.push(it) }
    }

    /**
     * Gives buying and selling equal chances when both are available.
     */
    internal fun chooseSelling(hasSurplus: Boolean, needsItems: Boolean,
                               sellCoinFlip: () -> Boolean = { randBoolean() }): Boolean =
        hasSurplus && (!needsItems || sellCoinFlip())
}
