package api.bot.action.trading

import api.attr.Attr
import api.bot.action.trading.BotTradeSpeechPool.BotTradeSpeech
import api.predef.*
import api.predef.ext.*
import com.google.common.math.IntMath
import engine.bot.gear.BotEquipmentPreferences
import engine.bot.gear.BotItemTracker.Companion.itemTracker
import io.luna.game.model.def.ItemDefinition
import io.luna.game.model.item.Item
import io.luna.game.model.item.economy.WorldEconomy
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.bot.io.BotOutputMessageHandler.ChatColor
import io.luna.game.model.mob.bot.io.BotOutputMessageHandler.ChatEffect
import io.luna.util.NumberUtils
import io.luna.util.StringUtils

/**
 * Prepares trade advertisements, candidate offers, and expected trade returns for bots.
 *
 * Builds trade data for buying, selling, and merchanting without performing the exchange.
 * Callers must validate available items, quantities, and payment when executing the trade.
 *
 * Merchant buying uses [Int.MAX_VALUE] as an unrestricted demand marker. Sale offers contain only surplus stock
 * available in the bank or inventory; equipped and dynamic items are excluded from transferable quantities.
 *
 * @author lare96
 */
object BotTradeManager {

    private var catalogueEconomy: WorldEconomy? = null
    private var catalogueUpdatedAt = 0L
    private var popularCatalogue = emptyList<Int>()

    /** The configured minimum trade-offer value, in coins. */
    internal const val MINIMUM_OFFER_VALUE = 10L

    /** The configured wait duration for trade requests, in seconds. */
    internal const val REQUEST_WAIT_SECONDS = 30

    /** The configured wait duration for the trade-offer stage, in seconds. */
    internal const val OFFER_WAIT_SECONDS = 60

    /** The configured wait duration for trade confirmation, in seconds. */
    internal const val CONFIRM_WAIT_SECONDS = 60

    /** The expected-value multiplier used by normal scrutiny when validating sale returns. */
    internal const val NORMAL_SELLING_ATTENTION = 0.75

    /** The expected-value multiplier used by trustworthy scrutiny when validating sale returns. */
    internal const val TRUSTWORTHY_SELLING_ATTENTION = 0.55

    /** The chat colors available for generated trade advertisements. */
    private val VALID_CHAT_COLORS = ChatColor.entries.toList()

    /** The chat effects available for generated trade advertisements. */
    private val VALID_CHAT_EFFECTS =
        listOf(ChatEffect.WAVE_2, ChatEffect.SHAKE, ChatEffect.SLIDE, ChatEffect.WAVE, ChatEffect.NONE)

    /** The configured advertisement cooldown range for bots that dislike merchanting. */
    internal val HATE_COOLDOWN = 6..8

    /** The configured advertisement cooldown range for ordinary bots. */
    internal val REGULAR_COOLDOWN = 4..6

    /** The configured advertisement cooldown range for bots that like merchanting. */
    internal val MERCHANT_COOLDOWN = 2..4

    /** The configured advertisement cooldown range for bots that love merchanting. */
    internal val STRONG_MERCHANT_COOLDOWN = 1..2

    /** The trade-request handler associated with each bot. */
    val Bot.tradeRequests by Attr.obj { BotTradeRequestHandler() }

    /**
     * Creates a chat advertisement for the specified trade purpose.
     *
     * Merchant advertisements use generic buying or selling messages. Ordinary advertisements
     * include the item name, quantity, and unit price, followed by a randomized chat color and effect.
     * This method only constructs the message; it does not send it or enforce a cooldown.
     *
     * @param bot The bot whose advertisement is being generated.
     * @param tradeItem The advertised item for ordinary buying or selling, or `null` for merchant messages.
     * @param purpose The buying or selling purpose of the advertisement.
     * @return The generated trade message and its chat formatting.
     * @throws IllegalStateException If an ordinary trade purpose is provided without an item.
     */
    fun advertiseTrade(bot: Bot, tradeItem: BotTradeItem?, purpose: BotTradePurpose): BotTradeMessage {
        val message =
            if (purpose == BotTradePurpose.MERCHANT_BUY) {
                "Buying anything"
            } else if (purpose == BotTradePurpose.MERCHANT_SELL) {
                // Sell headers + merchant sell suffix.
                val header = BotTradeSpeechPool.take(bot, BotTradeSpeech.SELLING)
                val suffix = BotTradeSpeechPool.take(bot, BotTradeSpeech.MERCHANT_SELLING)
                "$header $suffix"
            } else if (tradeItem != null) {
                // Buying/selling a single item.
                val speechContext = when (purpose) {
                    BotTradePurpose.BUY -> BotTradeSpeech.BUYING
                    BotTradePurpose.SELL -> BotTradeSpeech.SELLING
                    else -> throw IllegalStateException(
                        "If <saleItem> is not <null> then <purpose> must be <BUY> or <SELL>."
                    )
                }
                val price = NumberUtils.formatPrice(tradeItem.price)
                val header = BotTradeSpeechPool.take(bot, speechContext)
                val suffix = BotTradeSpeechPool.take(bot, BotTradeSpeech.PER_ITEM)
                val name =  StringUtils.capitalize(itemName(tradeItem.item.id))
                "$header ${if(tradeItem.item.amount > 1) StringUtils.addPlural(name) else name} $price $suffix"
            } else {
                throw IllegalStateException(
                    "If <saleItem> is <null> then <purpose> must be <MERCHANT_BUY> or <MERCHANT_SELL>."
                )
            }
        val color = VALID_CHAT_COLORS.random()
        val effect = VALID_CHAT_EFFECTS.random()
        return BotTradeMessage(StringUtils.capitalize(message), color, effect)
    }

    /**
     * Finds candidate items that the bot may contribute to a trade.
     *
     * For buying purposes, the result contains transferable coins and eligible surplus items used as payment.
     * For selling purposes, candidates are selected using the normal or merchant selling rules.
     * The returned items are not reserved, and their actual transferable amounts must be checked later.
     *
     * @param bot The bot preparing the offer.
     * @param priceStrategy The pricing strategy used for non-coin items.
     * @param purpose The buying or selling purpose of the trade.
     * @return Candidate items for the bot's side of the trade.
     */
    fun computeOfferItems(bot: Bot, priceStrategy: BotTradePrice, purpose: BotTradePurpose): List<BotTradeItem> {
        return when (purpose) {
            BotTradePurpose.BUY, BotTradePurpose.MERCHANT_BUY -> {
                // Buyers may pay with coins and eligible surplus stock, never retained equipment or supplies.
                val totalGold = transferableAmount(bot, 995)
                val goldItem = if (totalGold == 0) {
                    emptyList()
                } else {
                    listOf(BotTradeItem(Item(995, totalGold), 1, purpose, bot))
                }
                val stockPurpose = if (purpose == BotTradePurpose.MERCHANT_BUY) BotTradePurpose.MERCHANT_SELL
                    else BotTradePurpose.SELL
                goldItem + computeSellItems(bot, priceStrategy, stockPurpose)
            }

            // Sellers offer whatever they're selling.
            BotTradePurpose.SELL, BotTradePurpose.MERCHANT_SELL -> computeSellItems(bot, priceStrategy, purpose)
        }
    }

    /**
     * Applies the bot's spending reserve to transferable payment stock when preparing an actual buying session.
     * Accounting callers can still inspect all available stock through [computeOfferItems]. Sale quantities and
     * already sampled unit prices are unchanged; barter payment retains whole-item budget limits.
     */
    fun computeBudgetedOfferItems(bot: Bot, priceStrategy: BotTradePrice,
                                  purpose: BotTradePurpose): List<BotTradeItem> {
        val available = computeOfferItems(bot, priceStrategy, purpose)
        if (purpose != BotTradePurpose.BUY && purpose != BotTradePurpose.MERCHANT_BUY) return available
        val fraction = BotTradeIntentions.budgetFraction(bot, purpose).coerceIn(0.0, 1.0)
        val coins = available.filter { it.item.id == 995 }.mapNotNull { quote ->
            val amount = (quote.item.amount.toDouble() * fraction).toInt()
            if (amount > 0) BotTradeItem(Item(995, amount), quote.price, quote.purpose, quote.bot) else null
        }
        return coins + spendingStock(available.filter { it.item.id != 995 }, fraction)
    }

    /**
     * Limits barter payment by value while retaining whole items and the already sampled unit quotes.
     */
    internal fun spendingStock(stock: List<BotTradeItem>, fraction: Double): List<BotTradeItem> {
        val quantities = stock.associate { it.item.id to it.item.amount }
        val prices = stock.associate { it.item.id to it.price }
        val budget = (TradeBasket.value(quantities, prices).toDouble() * fraction.coerceIn(0.0, 1.0)).toLong()
        val selected = TradeBasket.select(quantities, prices, budget)
        return stock.mapNotNull { quote ->
            selected[quote.item.id]?.let { BotTradeItem(Item(quote.item.id, it), quote.price, quote.purpose, quote.bot) }
        }
    }

    /**
     * Determines what the bot expects to receive in exchange for its offer.
     *
     * Buying purposes select candidate wanted items. Selling purposes convert the offered item values
     * into a synthetic coin entry representing the expected total trade value, rather than a demand for
     * coins specifically. Per-item values and the running total saturate at the 32-bit integer limits.
     *
     * @param bot The bot calculating its expected return.
     * @param priceStrategy The strategy used to price wanted items when buying.
     * @param purpose The buying or selling purpose of the trade.
     * @param offering The proposed items used to calculate the expected value when selling.
     * @return Candidate wanted items for buying, or a synthetic coin-value entry for selling.
     */
    fun computeExpected(bot: Bot,
                        priceStrategy: BotTradePrice,
                        purpose: BotTradePurpose,
                        offering: List<BotTradeItem>): List<BotTradeItem> {
        return when (purpose) {
            // Buyers expect whatever they're buying.
            BotTradePurpose.BUY, BotTradePurpose.MERCHANT_BUY -> affordablePurchases(
                computeBuyItems(bot, priceStrategy, purpose), offering)
            BotTradePurpose.SELL, BotTradePurpose.MERCHANT_SELL -> {
                // Sellers only expect gold value equivalent to what they're offering.
                // This will later be interpreted as "general value" when trading.
                var totalValue = 0
                for (item in offering) {
                    totalValue = IntMath.saturatedAdd(totalValue, item.totalPrice())
                }
                if (totalValue > 0) {
                    listOf(BotTradeItem(Item(995, totalValue), 1, purpose, bot))
                } else {
                    listOf()
                }
            }
        }
    }

    /**
     * Reserves available payment across wanted items in priority order, using the same whole-item payment
     * rules as negotiation. Unaffordable items are skipped so cheaper needs can still be selected.
     * Merchant quotes are alternatives: each may use the full budget, with the actual offer capped during negotiation.
     * Call again with actual carried stock after banking, since withdrawals can be limited by capacity.
     */
    internal fun affordablePurchases(candidates: List<BotTradeItem>, payment: List<BotTradeItem>): List<BotTradeItem> {
        val remaining = payment.associate { it.item.id to it.item.amount }.toMutableMap()
        val paymentPrices = payment.associate { it.item.id to it.price }
        val selected = ArrayList<BotTradeItem>()
        for (candidate in candidates) {
            val id = candidate.item.id
            val demand = mapOf(id to candidate.item.amount)
            val prices = mapOf(id to candidate.price)
            val exchange = TradeBasket.exchange(demand, demand, prices, prices, remaining, paymentPrices) ?: continue
            selected += BotTradeItem(Item(id, exchange.first.getValue(id)), candidate.price,
                candidate.purpose, candidate.bot)
            if (candidate.purpose != BotTradePurpose.MERCHANT_BUY) {
                for ((paymentId, amount) in exchange.second) {
                    remaining[paymentId] = remaining.getValue(paymentId) - amount
                }
            }
        }
        return selected
    }

    /**
     * Builds candidate sale items for normal or merchant selling.
     *
     * Sale candidates include acquired tradeable stock even without a wanted definition or recent price rise,
     * retaining concrete personal supplies and preferred equipment. Merchants require a resale margin while
     * ordinary sellers use their normal price strategy. Actual amounts are not reserved here.
     *
     * @param bot The bot selecting items to sell.
     * @param priceStrategy The strategy used to calculate sale prices.
     * @param purpose The selling purpose, either [BotTradePurpose.SELL] or [BotTradePurpose.MERCHANT_SELL].
     * @return All eligible items to sell; the trading script selects the session stock.
     * @throws IllegalStateException If a buying purpose is supplied.
     */
    private fun computeSellItems(bot: Bot, priceStrategy: BotTradePrice, purpose: BotTradePurpose): List<BotTradeItem> {
        if (purpose == BotTradePurpose.BUY || purpose == BotTradePurpose.MERCHANT_BUY) {
            throw IllegalStateException("Must be a valid SELL based purpose.")
        }
        val candidates = ArrayList<BotTradeItem>()
        val merchantPrices = if (purpose == BotTradePurpose.MERCHANT_SELL) BotMerchantValuation.snapshot(bot) else null
        val retainedStock = BotTradeIntentions.retainedStock(bot)
        val ids = (bot.inventory.filterNotNull() + bot.bank.filterNotNull()).asSequence()
            .filter { !it.isDynamic && it.itemDef.isTradeable }
            .map { it.itemDef.unnotedId.orElse(it.id) }.filter { it != 995 && it != 0 }.distinct()
        for (id in ids) {
            val retained = (retainedStock[id] ?: 0).coerceAtLeast(0)
            val note = itemDef(id).notedId.orElse(-1)
            val owned = bot.itemTracker.count(id).toLong() + if (note == -1) 0L else bot.itemTracker.count(note).toLong()
            val count = minOf(transferableAmount(bot, id),
                (owned - retained).coerceIn(0, Int.MAX_VALUE.toLong()).toInt())
            if (count < 1) continue
            val guide = world.economy.getPrice(id)
            if (purpose == BotTradePurpose.SELL && guide <= 0) continue
            val quote = if (purpose == BotTradePurpose.MERCHANT_SELL)
                merchantPrices!!.sell(id, guide, priceStrategy)
            else priceStrategy.applySell(guide).coerceAtLeast(1)
            candidates += BotTradeItem(Item(id, count), quote, purpose, bot)
        }
        return BotTradeSelection.order(candidates)
    }


    /**
     * Protects concrete urgent supplies before fashion purchases, then uses personal taste and market history.
     * Merchant opportunities favour cosmetics independently of historical wanted-item ownership targets.
     */
    internal fun prioritizePurchases(bot: Bot, candidates: List<BotTradeItem>): List<BotTradeItem> {
        val intentions = BotTradeIntentions.purchases(bot).associateBy { it.id }
        return BotTradeSelection.order(candidates)
            .sortedWith(compareByDescending<BotTradeItem> {
                if (it.purpose == BotTradePurpose.BUY) intentions[it.item.id]?.priority?.level ?: 0 else 0
            }.thenByDescending { BotEquipmentPreferences.prefers(bot, it.item.id) }
                .thenByDescending { BotEquipmentPreferences.isCosmetic(it.item.id) }
                .thenByDescending { bot.canUseWantedItem(it.item.id) })
    }

    /**
     * Builds candidate wanted items for normal or merchant buying.
     *
     * Normal buying uses deliberate activity, progression, and cosmetic intentions. Merchant buying quotes
     * a bounded catalogue of common and preferred stock against guide and net alchemy values. Actual offered
     * items receive a complete resale quote lazily, with strong discounts for unwanted stock and personal
     * temptation for cosmetics, even when absent from this catalogue.
     * Merchant demand uses [Int.MAX_VALUE] to mark unrestricted quantities.
     *
     * [computeExpected] caps these candidates to available payment. Partner stock is checked during negotiation.
     *
     * @param bot The bot selecting items to buy.
     * @param priceStrategy The strategy used to calculate purchase prices.
     * @param purpose The buying purpose, either [BotTradePurpose.BUY] or [BotTradePurpose.MERCHANT_BUY].
     * @return All eligible items to buy; the trading script selects the session demand.
     * @throws IllegalStateException If a selling purpose is supplied.
     */
    private fun computeBuyItems(bot: Bot, priceStrategy: BotTradePrice, purpose: BotTradePurpose): List<BotTradeItem> {
        if (purpose == BotTradePurpose.SELL || purpose == BotTradePurpose.MERCHANT_SELL) {
            throw IllegalStateException("Must be a valid BUY based purpose.")
        } else if (purpose == BotTradePurpose.MERCHANT_BUY) {
            val candidates = ArrayList<BotTradeItem>()
            val useful = BotTradeIntentions.purchases(bot).mapTo(HashSet()) { it.id }
            val cosmetics = BotEquipmentPreferences.cosmeticIds().asSequence()
                .filter { ItemDefinition.ALL.get(it).map { definition -> definition.isTradeable && !definition.isNoted }.orElse(false) }
                .sortedWith(compareByDescending<Int> { BotEquipmentPreferences.prefers(bot, it) }
                    .thenByDescending { BotTradeSelection.popularity(it) }).take(32).toList()
            val catalogue = (marketCatalogue() + useful.take(64) + cosmetics + listOf(314, 1511, 436, 526)).distinct()
            val pricing = BotMerchantValuation.snapshot(bot, includeShops = false)
            for (id in catalogue) {
                val definition = ItemDefinition.ALL.get(id).orElse(null) ?: continue
                if (id == 995 || id == 0 || definition.isNoted || !definition.isTradeable) continue
                val quote = pricing.buy(id, BotMerchantValuation.peekGuide(id), id in useful)
                candidates += BotTradeItem(Item(id, Int.MAX_VALUE), quote.coerceAtLeast(1), purpose, bot)
            }
            return prioritizePurchases(bot, candidates)
        }

        // Otherwise, buy any wanted items we need to acquire.
        val wantedItems = BotTradeIntentions.purchases(bot)
        if (wantedItems.isEmpty()) {
            // Bot has nothing they want to buy.
            return listOf()
        } else {
            // Ownership targets describe total holdings, so buy only the current deficit.
            val candidates = ArrayList<BotTradeItem>()
            for (item in wantedItems) {
                if (item.id == 995) {
                    continue
                }
                val price = world.economy.getPrice(item.id)
                if (price > 0) {
                    val deficit = (item.target.toLong() - bot.totalItemAmount(item.id))
                        .coerceIn(0, Int.MAX_VALUE.toLong()).toInt()
                    if (deficit < 1 || !itemDef(item.id).isTradeable) continue
                    candidates += BotTradeItem(Item(item.id, deficit),
                                               if (BotEquipmentPreferences.isCosmetic(item.id))
                                                   BotMerchantValuation.cosmeticBuy(bot, item.id, price)
                                               else priceStrategy.applyBuy(price).coerceAtLeast(1),
                                               purpose,
                                               bot)
                }
            }
            return prioritizePurchases(bot, candidates)
        }
    }

    /**
     * Shares a bounded popular shortlist across bots without creating price records for the item catalogue.
     * Arbitrary offered items receive lazy quotes in the trading script, independently of this shortlist.
     */
    private fun marketCatalogue(): List<Int> {
        val economy = world.economy
        val now = System.nanoTime()
        if (catalogueEconomy !== economy || now - catalogueUpdatedAt >= 30_000_000_000L) {
            popularCatalogue = economy.prices.values.asSequence().filter { it.price > 0 }
                .sortedByDescending { it.samples }.take(64).map { it.id }.toList()
            catalogueEconomy = economy
            catalogueUpdatedAt = now
        }
        return popularCatalogue
    }

    /**
     * Counts transferable bank and inventory stock, combining noted and unnoted ids and excluding dynamic items.
     *
     * @param bot The bot whose available stock is counted.
     * @param id The normalized item id to count.
     * @return The available quantity, capped to the maximum item amount.
     */
    private fun transferableAmount(bot: Bot, id: Int): Int {
        return (bot.inventory.filterNotNull() + bot.bank.filterNotNull()).asSequence()
            .filter { !it.isDynamic && itemDef(it.id).unnotedId.orElse(it.id) == id }
            .sumOf { it.amount.toLong() }.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

}
