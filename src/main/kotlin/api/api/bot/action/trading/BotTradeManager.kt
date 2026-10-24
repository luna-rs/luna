package api.bot.action.trading

import api.attr.Attr
import api.bot.action.trading.BotTradeManager.SURPLUS_SELL_RATIO
import api.bot.action.trading.BotTradeSpeechPool.BotTradeSpeech
import api.predef.*
import api.predef.ext.*
import com.google.common.math.IntMath
import engine.bot.gear.BotItemTracker.Companion.itemTracker
import io.luna.game.model.def.ItemDefinition
import io.luna.game.model.def.WantedItemDefinition
import io.luna.game.model.def.WantedItemDefinition.WantedItemTag
import io.luna.game.model.item.Item
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.bot.brain.BotPersonalityManager.PersonalityTemplateType.PKER
import io.luna.game.model.mob.bot.brain.BotPersonalityManager.PersonalityTemplateType.SKILLER
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

    /** The configured minimum trade-offer value, in coins. */
    internal const val MINIMUM_OFFER_VALUE = 100L

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

    /** The owned-to-target ratio required before normal selling considers an item eligible. */
    private const val SURPLUS_SELL_RATIO = 2.50

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
                // Buy headers + merchant buy suffix.
                val header = BotTradeSpeechPool.take(bot, BotTradeSpeech.BUYING)
                val suffix = BotTradeSpeechPool.take(bot, BotTradeSpeech.MERCHANT_BUYING)
                "$header $suffix"
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
                goldItem + computeSellItems(bot, priceStrategy, BotTradePurpose.SELL)
            }

            // Sellers offer whatever they're selling.
            BotTradePurpose.SELL, BotTradePurpose.MERCHANT_SELL -> computeSellItems(bot, priceStrategy, purpose)
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
     * Normal selling collects every priced, unrestricted surplus item when its unnoted stock
     * reaches [SURPLUS_SELL_RATIO] times the wanted target. The proposed amount comes from the
     * wanted-item surplus calculation, which can include ownership beyond the unnoted stock.
     *
     * Merchant selling selects eligible wanted items whose economy prices have increased, capped to transferable
     * ownership above the retained target and minimum. Actual tradeable amounts are not reserved here.
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
        } else if (purpose == BotTradePurpose.MERCHANT_SELL) {
            // Merchant sellers sell as much as possible of wanted items that have recently increased in value.
            val candidates = ArrayList<BotTradeItem>()
            for (item in bot.preferences.wantedItems) {
                if (item.id == 995) {
                    continue
                }
                val price = world.economy.prices[item.id]
                if (price != null) {
                    val retained = maxOf(0, item.target, item.min)
                    val count = minOf(transferableAmount(bot, item.id),
                        (bot.totalItemAmount(item.id).toLong() - retained).coerceIn(0, Int.MAX_VALUE.toLong()).toInt())
                    if (price.price > price.lastPrice && !isSellRestricted(bot, item) && count > 0) {
                        candidates += BotTradeItem(Item(item.id, count),
                                                   priceStrategy.applySell(price.price.toLong()).coerceAtLeast(1),
                                                   purpose,
                                                   bot)
                    }
                }
            }
            return candidates
        }

        // Otherwise, sell any surplus of wanted items we can.
        val candidates = ArrayList<BotTradeItem>()
        for ((item, surplus) in bot.preferences.wantedItemsToTrim()) {
            if (!isSellRestricted(bot, item)) {
                val amount = bot.itemTracker.count(item.id)
                if (amount >= item.target * SURPLUS_SELL_RATIO) {
                    val price = world.economy.prices[item.id]
                    if (price != null) {
                        val available = minOf(surplus, transferableAmount(bot, item.id))
                        if (available < 1) continue
                        candidates += BotTradeItem(Item(item.id, available),
                                                   priceStrategy.applySell(price.price.toLong()).coerceAtLeast(1),
                                                   purpose,
                                                   bot)
                    }
                }
            }
        }
        return candidates
    }


    /**
     * Builds candidate wanted items for normal or merchant buying.
     *
     * Normal buying rebalances wanted items and collects every priced item, using the remaining amount
     * needed to reach its target. Merchant buying accepts priced, tradeable items regardless of price trends,
     * paying normal buying quotes for wanted items and half guide price for other stock (at least one coin).
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
            // Quote base items once; the trading script normalizes bank notes to these same quotes.
            val candidates = ArrayList<BotTradeItem>()
            for (definition in ItemDefinition.ALL.filterNotNull()) {
                val id = definition.id()
                if (id == 995 || id == 0 || definition.isNoted || !definition.isTradeable) continue
                val guide = world.economy.getPrice(id)
                if (guide <= 0) continue
                val quote = if (bot.preferences.hasWantedItem(id)) priceStrategy.applyBuy(guide)
                else guide / 2
                candidates += BotTradeItem(Item(id, Int.MAX_VALUE), quote.coerceAtLeast(1), purpose, bot)
            }
            return candidates.sortedWith(compareByDescending<BotTradeItem> { bot.canUseWantedItem(it.item.id) }
                .thenByDescending { bot.preferences.getWantedItem(it.item.id)?.priority?.level ?: 0 })
        }

        // Otherwise, buy any wanted items we need to acquire.
        bot.preferences.rebalanceWantedItems()
        val wantedItems = bot.preferences.wantedItemsToAcquire()
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
                val price = world.economy.prices[item.id]
                if (price != null) {
                    val deficit = (item.target.toLong() - bot.totalItemAmount(item.id))
                        .coerceIn(0, Int.MAX_VALUE.toLong()).toInt()
                    if (deficit < 1 || !itemDef(item.id).isTradeable) continue
                    candidates += BotTradeItem(Item(item.id, deficit),
                                               priceStrategy.applyBuy(price.price.toLong()).coerceAtLeast(1),
                                               purpose,
                                               bot)
                }
            }
            return candidates
        }
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

    /**
     * Checks whether a wanted item is excluded from selling for the specified bot.
     *
     * Coins and untradeable items are always restricted. Additional checks protect low-stock items,
     * training consumables for skiller-template bots, and combat consumables for PKer-template bots.
     * When no skill is associated with the item, it is also restricted while the bot's combat level
     * is below the specified maximum level.
     *
     * @param bot The bot whose stock and personality determine the restriction.
     * @param item The wanted-item definition to check.
     * @return `true` if the item must not be included in a sale candidate.
     */
    private fun isSellRestricted(bot: Bot, item: WantedItemDefinition): Boolean {
        return item.id == 995 || !itemDef(item.id).isTradeable ||
                (item.target == 1 && bot.itemTracker.count(item.id) < 3) ||
                (item.target > 1 && bot.itemTracker.count(item.id) < item.target * SURPLUS_SELL_RATIO) ||
                (bot.preferences.isSkiller && item.tags.contains(WantedItemTag.SKILLING_CONSUMABLE)) ||
                (item.skill == -1 && bot.combatLevel < item.maxLevel) ||
                (bot.preferences.isPker && item.tags.contains(WantedItemTag.COMBAT_CONSUMABLE))
    }
}
