package game.bot.scripts.trading

import api.bot.Suspendable.waitFor
import api.bot.action.trading.*
import api.bot.action.trading.BotTradeManager.tradeRequests
import api.bot.script.BotScriptData
import api.bot.script.ZonedBotScript
import api.bot.social.BotConversations
import api.bot.social.BotTradeScams
import api.bot.social.BotSocialInteractions
import api.bot.social.SocialBotScript
import api.bot.zone.SubZone
import api.predef.*
import api.predef.ext.*
import com.google.common.base.Stopwatch
import engine.bot.gear.BotItemTracker.Companion.itemTracker
import engine.bot.gear.BotGearLocator
import engine.bot.gear.BotEquipmentPreferences
import engine.trade.ConfirmTradeInterface
import engine.trade.OfferTradeInterface
import engine.trade.tradingWith
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.item.Item
import io.luna.game.model.def.WantedItemDefinition.WantedItemPriority
import io.luna.game.model.def.WantedItemDefinition
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.bot.brain.BotActivity
import io.luna.game.model.mob.varp.PersistentVarp
import java.time.Instant
import io.luna.util.NumberUtils
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

/**
 * Buys wanted items or sells surplus stock at a configured marketplace.
 *
 * Each banking trip resolves fresh intentions and stable unit prices, then withdraws only transferable stock.
 * Buyers carry coins and eligible surplus payment stock; sellers carry noted sale stock when possible.
 * Bot-to-bot exchanges use the seller's asking
 * prices when they fit the buyer's limits, so both participants independently calculate the same exchange.
 * Human sellers are paid for wanted quantities at the bot's advertised buying prices. Human buyers may pay in
 * coins, priced tradeable items, or both, valued at guide prices for this trade attempt.
 *
 * Negotiations apply quantity differences against explicit target baskets. Empty exchanges, unsuccessful item
 * movements, repeated offer cycles, and expired waits decline the trade. Both screens are checked against the
 * accepted basket before confirmation. This temporary script is not persisted across logout or server restart.
 *
 * @param bot The bot participating in marketplace trading.
 * @property purpose Whether the bot is buying or selling ordinary or merchant stock.
 * @property marketplace The marketplace to return to after banking.
 * @param duration The maximum remaining duration of the marketplace session.
 * @param zone The candidate zones containing the marketplace.
 * @author lare96
 */
class TradeBotScript(bot: Bot,
                     private val purpose: BotTradePurpose,
                     private val marketplace: BotMarketplace = BotMarketplace.VARROCK,
                     duration: Duration = getPreferenceBasedDuration(bot),
                     zone: MutableList<SubZone> = mutableListOf(marketplace.activityZone),
                     private val purchaseTarget: Item? = null,
                     private val purchasePrice: Long = 0) :
    ZonedBotScript(bot, duration, zone), SocialBotScript {

    companion object {

        /**
         * Creates a single-item buying session only when a prepared seller has an affordable match.
         * The target is total owned stock, not an additional quantity. Normal negotiation revalidates
         * stock and payment after travel; no items or money are reserved by this scan.
         */
        fun buySpecific(bot: Bot, id: Int, target: Int): TradeBotScript? {
            require(target > 0 && id != COINS)
            val guide = world.economy.getPrice(id)
            if (guide <= 0) return null
            val price = BotTradePrice.computePriceStrategy(bot).applyBuy(guide).coerceAtLeast(1)
            for (market in BotMarketplace.entries) {
                val script = TradeBotScript(bot, BotTradePurpose.BUY, market, 10.minutes,
                    purchaseTarget = Item(id, target), purchasePrice = price)
                if (script.specificDemand().isEmpty()) return null
                val payment = BotTradeManager.computeBudgetedOfferItems(bot, script.resolveTradePrice(), BotTradePurpose.BUY)
                    .filter { it.item.id == COINS }
                if (TradeBasket.value(script.quantities(payment.map { it.item }, true), script.prices(payment)) <
                    BotTradeManager.MINIMUM_OFFER_VALUE) return null
                val sellers = world.players.filterIsInstance<Bot>().mapNotNull {
                    it.scriptStack.current() as? TradeBotScript
                }.filter { script.availableAtMarket(it) }
                if (sellers.any { seller ->
                    TradeBasket.exchange(seller.quantities(seller.carriedOffer.mapNotNull { (id, amount) ->
                        minOf(amount, seller.bot.inventory.computeAmountForId(id)).takeIf { it > 0 }?.let { Item(id, it) }
                    }, true),
                        script.quantities(script.specificDemand().map { it.item }, true),
                        seller.prices(seller.offering), script.prices(script.specificDemand()),
                        script.quantities(payment.map { it.item }, true), script.prices(payment)) != null
                }) return script
            }
            return null
        }

        /** The currency used for marketplace payments. */
        private const val COINS = 995

        /** The maximum number of offer changes permitted in one negotiation. */
        private const val MAX_NEGOTIATION_ROUNDS = 12

        /** Selects a session duration, checking loved merchanting before the broader liked preference. */
        private fun getPreferenceBasedDuration(bot: Bot): Duration {
            return when {
                bot.preferences.lovesActivity(BotActivity.MERCHANTING) -> rand(2, 4).hours
                bot.preferences.likesActivity(BotActivity.MERCHANTING) -> rand(1, 3).hours
                bot.preferences.hatesActivity(BotActivity.MERCHANTING) -> rand(30, 60).minutes
                else -> rand(45, 120).minutes
            }
        }
    }

    /** Players temporarily excluded after failed attempts, or for persistent negative feelings. */
    private val blacklist = HashMap<String, Instant>()

    /** Throttles outgoing requests independently of incoming request processing. */
    private val searchTimer = Stopwatch.createUnstarted()

    /** Throttles advertisements for the current intentions. */
    private val messageTimer = Stopwatch.createUnstarted()

    /** Candidate sale stock or coin payment, using unnoted ids before withdrawal. */
    private var offering = emptyList<BotTradeItem>()

    /** Wanted quantities or expected sale value resolved for the current banking trip. */
    private var expected = emptyList<BotTradeItem>()

    /** Actual carried ids and quantities available for this trip, including bank notes. */
    private var carriedOffer = emptyMap<Int, Int>()

    /** Whether banking and marketplace travel have finished and requests may be processed. */
    private var prepared = false

    /** Whether this session has completed at least one exchange. */
    private var traded = false

    /** The offer stage owned by this script, retained for cancellation cleanup. */
    private var activeTrade: BotTradeOfferStage? = null

    /**
     * Includes the outstanding request and approach before an offer screen exists.
     */
    private var negotiating = false

    /**
     * A completed trust trade invalidates carried quantities once its optional aftermath dialogue has finished.
     */
    private var socialStockChanged = false

    /**
     * Advertisements keep their wording and formatting until the advertised item or its price changes.
     */
    private val autotyper = BotTradeAutotyper()

    /**
     * Concrete needs captured with this trip's quotes, reused for truthful speech and merchant barter bids.
     */
    private var actionableWants = emptyMap<Int, WantedItemDefinition>()

    /**
     * Cosmetic temptations are quoted lazily, once per banking trip, without becoming unsolicited shopping ads.
     */
    private val cosmeticQuotes = HashMap<Int, BotTradeItem>()

    /**
     * Unlisted merchant purchases receive the same stable, discounted treatment as prepared buying quotes.
     */
    private val merchantQuotes = HashMap<Int, BotTradeItem>()

    /**
     * Merchant barter bids are stable for the same prepared stock, including independent bot-side planning.
     */
    private val barterPrices = HashMap<Int, Long>()

    /**
     * Lazily captures accessible resale channels once for incoming stock, outside the bounded initial catalogue.
     */
    private var merchantValuation: BotMerchantValuation.Quotes? = null

    /**
     * A full market ends this visit without sending an otherwise prepared trader to a shop.
     */
    private var capacityBlocked = false

    /** The advertisement interval selected for the current intentions, in seconds. */
    private var messageCooldown = 0

    /** The bank withdrawal mode to restore when the session finishes or pauses. */
    private var previousBankMode: Int? = null

    /** Whether this purpose acquires wanted items in exchange for coins or surplus payment stock. */
    private val buying: Boolean
        get() = purpose == BotTradePurpose.BUY || purpose == BotTradePurpose.MERCHANT_BUY

    override suspend fun onInit(resumed: Boolean): Boolean {
        prepared = false
        if (marketplace.reserveTradingSpot(this) == null) {
            capacityBlocked = true
            bot.log("Marketplace has no free trading spots.")
            return false
        }
        capacityBlocked = false
        for ((name, feeling) in bot.preferences.playerFeelings) {
            if (feeling < 0.25) addBlacklisted(name, 365.days)
        }
        if (!computeOfferAndExpected()) return false
        previousBankMode = bot.varpManager.getValue(PersistentVarp.WITHDRAW_AS_NOTE)
        bot.speechStack.suppressGeneral(this)
        // Zoned scripts skip initial banking on resume; explicitly prepare again in that case.
        forceBanking = resumed
        return true
    }

    override suspend fun equipment(): BotGearLocator? = null

    override suspend fun onBankOpen(initial: Boolean) {
        prepared = false
        if (!computeOfferAndExpected()) return
        handler.banking.clickBankingMode(true)
        for (entry in offering) {
            if (bot.inventory.computeRemainingSize() <= 1) break
            val id = entry.item.id
            // Bank.withdraw selects by id. Skip mixed stacks as well to avoid withdrawing dynamic instances.
            if (bot.bank.any { it != null && it.id == id && it.isDynamic }) continue
            val definition = itemDef(id)
            val capacity = if (definition.isStackable || definition.notedId.isPresent) Int.MAX_VALUE
            else (bot.inventory.computeRemainingSize() - 1).coerceAtLeast(0)
            val transferable = minOf(entry.item.amount, bot.bank.computeAmountForId(id), capacity)
            if (transferable > 0) handler.banking.withdraw(Item(id, transferable))
        }
        val limits = quantities(offering.map { it.item }, true).toMutableMap()
        val stock = linkedMapOf<Int, Int>()
        for (item in bot.inventory.filterNotNull()) {
            if (item.isDynamic || !item.itemDef.isTradeable) continue
            val id = unnotedId(item.id)
            val amount = minOf(item.amount, limits[id] ?: 0)
            if (amount > 0) {
                stock[item.id] = (stock[item.id] ?: 0) + amount
                limits[id] = limits.getValue(id) - amount
            }
        }
        carriedOffer = stock
        if (buying) {
            val carried = quantities(stock.toItems(), true)
            expected = BotTradeManager.affordablePurchases(expected, offering.mapNotNull { quote ->
                val amount = carried[quote.item.id] ?: return@mapNotNull null
                BotTradeItem(Item(quote.item.id, amount), quote.price, quote.purpose, bot)
            })
            if (expected.isEmpty()) {
                endSession("No wanted items are affordable with the withdrawn payment.")
                return
            }
        }
        if (stock.isEmpty()) {
            endSession("No transferable trade stock could be withdrawn.")
            return
        }
        bot.overlays.closeWindows()
        val tile = marketplace.reserveTradingSpot(this)
        if (tile == null) capacityBlocked = true
        prepared = tile != null && handler.webWalkTo(marketplace.parent) &&
                handler.webWalkTo(tile) && bot.position == tile
        if (!prepared) endSession("Could not return to an available marketplace tile.")
    }

    override suspend fun finish() {
        BotSocialInteractions.cancel(bot)
        BotConversations.cancel(bot)
        prepared = false
        marketplace.releaseTradingSpot(this)
        activeTrade?.decline()
        activeTrade = null
        bot.tradingWith = -1
        previousBankMode?.let { bot.varpManager.setValue(PersistentVarp.WITHDRAW_AS_NOTE, it) }
        previousBankMode = null
        bot.speechStack.releaseGeneral(this)
    }

    override suspend fun completed() {
        if (purchaseTarget == null && !traded && !capacityBlocked && !bot.preferences.isMerchant) {
            if (buying) bot.scriptStack.push(BuyFromStoreBotScript(bot))
            else bot.scriptStack.push(SellToStoreBotScript(bot))
        }
    }

    override suspend fun executeInZone(): Boolean {
        BotConversations.tick(bot)
        if (BotConversations.engaged(bot)) return true
        if (socialStockChanged) {
            socialStockChanged = false
            prepared = false
            forceBanking = true
            return true
        }
        if (!prepared) {
            forceBanking = true
            return true
        }
        val incoming = bot.tradeRequests.pollRequests { canTradeWith(it) }
        if (incoming != null) {
            attemptTrade(incoming)
        } else if (!searchTimer.isRunning || searchTimer.elapsed().seconds >= 8) {
            searchTimer.reset().start()
            blacklist.entries.removeIf { !it.value.isAfter(Instant.now()) }
            val other = world.locator.findPlayers(bot, Position.VIEWING_DISTANCE * 2) {
                it is Bot && bot.isViewableFrom(it) && canTradeWith(it)
            }.shuffled().firstOrNull()
            if (other != null) attemptTrade(other)
        }
        if (prepared && !forceBanking) sendMessage()
        return true
    }

    override suspend fun prepareMarketplace(marketplace: BotMarketplace?): Boolean {
        if (marketplace == null) return true
        val tile = marketplace.reserveTradingSpot(this)
        if (tile == null) {
            capacityBlocked = true
            endSession("Marketplace trading spots are full.")
            return false
        }
        return bot.position == tile || handler.webWalkTo(tile) && bot.position == tile
    }

    override suspend fun beforeZoneWork(): Boolean = purchaseTarget == null && BotSocialInteractions.participate(bot)

    override fun snapshot(): BotScriptData? = null

    /**
     * Counts both noted and unnoted purchases so a banked or carried delivery cannot be bought twice.
     */
    private fun specificDemand(): List<BotTradeItem> {
        val target = purchaseTarget ?: return emptyList()
        val note = itemDef(target.id).notedId.orElse(-1)
        val owned = bot.itemTracker.count(target.id).toLong() +
            if (note >= 0) bot.itemTracker.count(note).toLong() else 0L
        val missing = (target.amount.toLong() - owned).coerceAtLeast(0).toInt()
        return if (missing == 0) emptyList() else
            listOf(BotTradeItem(Item(target.id, missing), purchasePrice, BotTradePurpose.BUY, bot))
    }

    /** Refreshes intentions from current ownership and ends the session normally when nothing useful remains. */
    private fun computeOfferAndExpected(): Boolean {
        bot.preferences.rebalanceWantedItems()
        actionableWants = BotTradeIntentions.purchases(bot).associateBy { it.id }
        val strategy = resolveTradePrice()
        val offers = BotTradeManager.computeBudgetedOfferItems(bot, strategy, purpose)
            .filter { purchaseTarget == null || it.item.id == COINS }
        val wants = if (purchaseTarget == null) BotTradeManager.computeExpected(bot, strategy, purpose, offers)
        else BotTradeManager.affordablePurchases(specificDemand(), offers)
        val traders = world.players.filterIsInstance<Bot>().mapNotNull {
            it.scriptStack.current() as? TradeBotScript
        }.filter { availableAtMarket(it) }
        val candidates = if (buying) wants else offers
        // Preserve the buyer's budget allocation order; sellers favor stock with a current market match.
        val selected = if (purpose == BotTradePurpose.SELL) {
            prioritizeMarketItems(candidates.sortedByDescending(::stackValue), offers, traders)
        } else if (purpose == BotTradePurpose.MERCHANT_SELL) {
            prioritizeMarketItems(candidates.sortedByDescending(::stackValue), offers, traders)
        } else candidates
        offering = if (buying) offers else if (purpose == BotTradePurpose.SELL) selected.take(1) else selected
        expected = if (purpose == BotTradePurpose.BUY) selected.take(1) else if (buying) selected
        else BotTradeManager.computeExpected(bot, strategy, purpose, offering)
        cosmeticQuotes.clear()
        merchantQuotes.clear()
        barterPrices.clear()
        merchantValuation = null
        messageTimer.reset()
        if (offering.isEmpty() || expected.isEmpty() ||
            TradeBasket.value(quantities(offering.map { it.item }, true), prices(offering)) <
            BotTradeManager.MINIMUM_OFFER_VALUE) {
            endSession("No useful trade intentions remain.")
            return false
        }
        return true
    }

    /**
     * Inspects prepared traders throughout this market, independently of the arriving bot's location.
     */
    private fun availableAtMarket(other: TradeBotScript): Boolean =
        other.bot !== bot && other.bot.state == EntityState.ACTIVE && other.prepared &&
            !BotSocialInteractions.engaged(other.bot) && !BotConversations.engaged(other.bot) &&
            buying != other.buying && marketplace.contains(other.bot.position) &&
            !checkBlacklisted(other.bot.username) && !other.checkBlacklisted(bot.username) &&
            other.bot.tradingWith == -1 && !other.bot.overlays.hasWindow()

    /**
     * Keeps every eligible intention, placing currently matchable stock first for capacity-limited withdrawal.
     * Later partners can match any retained item without waiting for a new banking trip.
     */
    private fun prioritizeMarketItems(candidates: List<BotTradeItem>, payment: List<BotTradeItem>,
                                      traders: List<TradeBotScript>): List<BotTradeItem> {
        val eligible = candidates.filter {
            buying || TradeBasket.value(quantities(listOf(it.item), true), prices(listOf(it))) >=
                BotTradeManager.MINIMUM_OFFER_VALUE
        }
        val matching = eligible.filter { candidate ->
            traders.any { other ->
                if (buying) {
                    TradeBasket.exchange(quantities(other.carriedOffer.toItems(), true),
                        quantities(listOf(candidate.item), true), prices(other.offering), prices(listOf(candidate)),
                        quantities(payment.map { it.item }, true), prices(payment)) != null
                } else {
                    TradeBasket.exchange(quantities(listOf(candidate.item), true),
                        quantities(other.expected.map { it.item }, true), prices(listOf(candidate)), prices(other.expected),
                        quantities(other.carriedOffer.toItems(), true), prices(other.offering)) != null
                }
            }
        }
        return matching + eligible.filterNot { it in matching }
    }

    /**
     * Repeats one configured ordinary advertisement, or a merchant's truthful, preconfigured rotation.
     */
    private fun sendMessage() {
        if (messageTimer.isRunning && messageTimer.elapsed().seconds < messageCooldown) return
        val advertised = if (buying) expected.take(1) else saleAdvertisement()?.let(::listOf).orEmpty()
        if (advertised.isEmpty()) return
        val urgent = if (purpose == BotTradePurpose.MERCHANT_BUY) {
            val needed = actionableWants.values.filter { it.priority == WantedItemPriority.URGENT }
                .map { it.id }.toSet()
            expected.filter { it.item.id in needed }.take(2)
        } else emptyList()
        val key = purpose to (advertised + urgent).map { it.item.id to it.price }
        autotyper.configure(key) {
            messageCooldown = when {
                bot.preferences.lovesActivity(BotActivity.MERCHANTING) -> BotTradeManager.STRONG_MERCHANT_COOLDOWN
                bot.preferences.likesActivity(BotActivity.MERCHANTING) -> BotTradeManager.MERCHANT_COOLDOWN
                bot.preferences.hatesActivity(BotActivity.MERCHANTING) -> BotTradeManager.HATE_COOLDOWN
                else -> BotTradeManager.REGULAR_COOLDOWN
            }.random()
            if (purpose == BotTradePurpose.MERCHANT_BUY) {
                val format = BotTradeManager.advertiseTrade(bot, null, purpose)
                val messages = mutableListOf(BotTradeMessage("Buying anything cheap! Cosmetics welcome!",
                    format.color, format.effect))
                if (urgent.isNotEmpty()) {
                    val entries = urgent.map { "${itemName(it.item.id)} ${NumberUtils.formatPrice(it.price)} ea" }
                    val both = "Buying: ${entries.joinToString(" / ")}"
                    val text = if (both.length <= 80) both else "Buying: ${entries.first()}"
                    messages += BotTradeMessage(text, format.color, format.effect)
                }
                messages
            } else {
                val advertisedPurpose = if (buying) BotTradePurpose.BUY else BotTradePurpose.SELL
                val item = advertised.first()
                val generated = BotTradeManager.advertiseTrade(bot, item, advertisedPurpose)
                val priority = actionableWants[item.item.id]?.priority
                if (buying && priority == WantedItemPriority.LOW && BotEquipmentPreferences.isCosmetic(item.item.id)) {
                    listOf(BotTradeMessage("Collecting: ${itemName(item.item.id)} ${NumberUtils.formatPrice(item.price)} ea",
                        generated.color, generated.effect))
                } else if (buying && priority != WantedItemPriority.URGENT && purchaseTarget == null) {
                    val header = listOf("Buying", "WTB:", "B:").random()
                    listOf(BotTradeMessage("$header ${itemName(item.item.id)} ${NumberUtils.formatPrice(item.price)} ea",
                        generated.color, generated.effect))
                } else listOf(generated)
            }
        }
        if (!messageTimer.isRunning || messageTimer.elapsed().seconds >= messageCooldown) {
            autotyper.next()?.let { bot.output.chat(it.text, it.color, it.effect) }
            messageTimer.reset().start()
        }
    }

    /**
     * Advertises the most valuable carried merchant stack instead of an unrelated generic category.
     */
    private fun saleAdvertisement(): BotTradeItem? {
        val carried = quantities(carriedOffer.toItems(), true)
        val stock = offering.mapNotNull { quote ->
            val amount = if (carriedOffer.isEmpty()) quote.item.amount else carried[quote.item.id] ?: 0
            if (amount > 0) BotTradeItem(Item(quote.item.id, amount), quote.price, quote.purpose, bot) else null
        }
        return if (purpose == BotTradePurpose.MERCHANT_SELL) stock.maxByOrNull(::stackValue)
        else stock.firstOrNull()
    }

    /**
     * Compares entire stack values without clipping them to the coin item's integer quantity limit.
     */
    private fun stackValue(quote: BotTradeItem): Long =
        TradeBasket.value(mapOf(quote.item.id to quote.item.amount), mapOf(quote.item.id to quote.price))

    /** Owns request, negotiation, and cleanup for one participant, including every unsuccessful return path. */
    private suspend fun attemptTrade(other: Player) {
        negotiating = true
        var success = false
        try {
            val existing = bot.overlays[OfferTradeInterface::class]
            val offer = if (existing?.other === other) {
                BotTradeOfferStage(bot, other, BotTradeRequestStage(bot, other))
            } else BotTradeRequestStage(bot, other).sendAndAwait() ?: return
            activeTrade = offer
            success = negotiate(offer, other)
            if (success) {
                traded = true
                prepared = false
                forceBanking = true
                bot.preferences.rebalanceWantedItems()
            }
        } finally {
            if (!success) {
                // Requests can open a screen just before cancellation resumes the coroutine.
                BotTradeStage(bot, other).decline()
                addBlacklisted(other.username, 5.minutes)
            }
            activeTrade = null
            if (bot.tradingWith == other.index) bot.tradingWith = -1
            negotiating = false
        }
    }

    /**
     * Applies complete target quantities and accepts only a stable, non-empty exchange.
     *
     * Bot partners share one deterministic plan. Human partners may change quantities during negotiation, so their
     * next target is always recomputed from this trip's original stock. Repeated states and the overall deadline
     * prevent cyclic or inactive trades from occupying the script indefinitely.
     */
    private suspend fun negotiate(offer: BotTradeOfferStage, other: Player): Boolean {
        val partner = (other as? Bot)?.scriptStack?.current() as? TradeBotScript
        val exchange = if (other is Bot) partner?.let { botExchange(it) } ?: return false else null
        val guidePrices = HashMap<Int, Long>()
        val timer = Stopwatch.createStarted()
        val seen = HashMap<Pair<Map<Int, Int>, Map<Int, Int>>, Int>()
        var lastState: Pair<Map<Int, Int>, Map<Int, Int>>? = null
        var rounds = 0
        while (timer.elapsed().seconds < BotTradeManager.OFFER_WAIT_SECONDS && rounds < MAX_NEGOTIATION_ROUNDS) {
            val screen = bot.overlays[OfferTradeInterface::class] ?: return false
            if (screen.other !== other) return false
            val own = quantities(offer.getTradeItems())
            val receiving = quantities(offer.getOtherTradeItems())
            val state = own to receiving
            if (state != lastState) {
                lastState = state
                rounds++
                val visits = (seen[state] ?: 0) + 1
                seen[state] = visits
                if (visits > 2) return false
            }
            val target = exchange?.first ?: humanTarget(receiving, guidePrices)
            if (own != target) {
                if (!applyTarget(offer, target)) return false
                continue
            }
            val valid = own.isNotEmpty() && receiving.isNotEmpty() &&
                    if (exchange != null) receiving == exchange.second
                    else humanTarget(receiving, guidePrices) == own && hasUsefulReturn(receiving, guidePrices)
            if (valid && finishTradeWith(offer, own, receiving)) return true
            if (bot.overlays[OfferTradeInterface::class] !== screen) return false
            waitFor(2.seconds) {
                bot.overlays[OfferTradeInterface::class] !== screen ||
                        quantities(offer.getTradeItems()) != own || quantities(offer.getOtherTradeItems()) != receiving
            }
        }
        bot.log("Trade negotiation expired or stopped making progress.")
        return false
    }

    /**
     * Builds both sides of a bot exchange, bounded by stock, demand, and the buyer's payment basket.
     *
     * Sale goods use the seller's asking prices and barter payments use the buyer's surplus-sale quotes.
     * Normalized ids are used for matching; both resulting baskets use their actual carried note ids.
     */
    private fun botExchange(other: TradeBotScript): Pair<Map<Int, Int>, Map<Int, Int>>? {
        if (buying == other.buying) return null
        val buyer = if (buying) this else other
        val seller = if (buying) other else this
        val asks = seller.prices(seller.offering)
        val stock = seller.quantities(seller.carriedOffer.toItems(), true)
        val payment = buyer.quantities(buyer.carriedOffer.toItems(), true)
        val paymentPrices = buyer.prices(buyer.offering).toMutableMap()
        if (seller.bot.preferences.isMerchant) {
            for (id in payment.keys.filter { it != COINS }) {
                paymentPrices[id] = seller.barterPrices.getOrPut(id) {
                    seller.merchantPriceSnapshot().buy(id, BotMerchantValuation.peekGuide(id),
                        id in seller.actionableWants)
                }
            }
        }
        fun select(demand: List<BotTradeItem>) = TradeBasket.exchange(stock,
            buyer.quantities(demand.map { it.item }, true), asks, buyer.prices(demand), payment, paymentPrices)
        var selected = select(buyer.expected)
        if (selected == null && buyer.purpose == BotTradePurpose.MERCHANT_BUY) {
            selected = select(buyer.effectiveExpected(stock))
        } else if (selected == null && buyer.purpose == BotTradePurpose.BUY && buyer.purchaseTarget == null) {
            val temptations = seller.offering.filter { it.item.id in stock && BotEquipmentPreferences.isCosmetic(it.item.id) }
                .sortedWith(compareByDescending<BotTradeItem> { BotEquipmentPreferences.prefers(buyer.bot, it.item.id) }
                    .thenByDescending { it.totalPrice() })
            for (temptation in temptations) {
                val quote = buyer.cosmeticQuote(temptation.item.id) ?: continue
                selected = select(listOf(quote))
                if (selected != null) break
            }
        }
        val exchange = selected ?: return null
        val goods = seller.carriedQuantities(exchange.first)
        val paid = buyer.carriedQuantities(exchange.second)
        return if (buying) paid to goods else goods to paid
    }

    /** Computes the outgoing target for a human offer using stable per-attempt guide prices for barter. */
    private fun humanTarget(receiving: Map<Int, Int>, guidePrices: MutableMap<Int, Long>): Map<Int, Int> {
        if (buying) {
            val demand = effectiveExpected(receiving)
            val wanted = quantities(demand.map { it.item }, true)
            val received = quantities(receiving.toItems(), true)
            val useful = received.mapValues { (id, amount) -> minOf(amount, wanted[id] ?: 0) }
            val budget = TradeBasket.value(if (receiving.isEmpty()) wanted else useful, prices(demand))
            val payment = quantities(carriedOffer.toItems(), true)
            val unitPrices = prices(offering)
            val coins = minOf(budget, (payment[COINS] ?: 0).toLong()).toInt()
            val selected = TradeBasket.select(payment.filterKeys { it != COINS }, unitPrices, budget - coins)
                .toMutableMap()
            if (coins > 0) selected[COINS] = coins
            return carriedQuantities(selected)
        }
        val stock = humanSaleStock()
        if (receiving.isEmpty()) return stock
        val budget = receivedValue(receiving, guidePrices)
        return carriedQuantities(TradeBasket.select(quantities(stock.toItems(), true), prices(offering), budget))
    }

    /** Ensures ordinary exchanges receive useful stock or payment, even when scrutiny is permissive. */
    private fun hasUsefulReturn(receiving: Map<Int, Int>, guidePrices: MutableMap<Int, Long>): Boolean {
        if (!buying) return receivedValue(receiving, guidePrices) > 0
        val received = quantities(receiving.toItems(), true)
        if (purpose == BotTradePurpose.BUY && received.size != 1) return false
        val wanted = quantities(effectiveExpected(receiving).map { it.item }, true)
        // Do not pay for extras beyond wanted stock or accept unrelated items in place of the advertised purchase.
        return received.isNotEmpty() && received.all { (id, amount) -> amount <= (wanted[id] ?: 0) }
    }

    /**
     * An offered cosmetic can tempt an ordinary shopper without becoming a new unsolicited purchase advertisement.
     */
    private fun effectiveExpected(receiving: Map<Int, Int>): List<BotTradeItem> {
        if (purchaseTarget != null || !buying) return expected
        val primary = expected.map { it.item.id }.toSet()
        val offered = quantities(receiving.toItems(), true).keys
        val alternatives = if (purpose == BotTradePurpose.MERCHANT_BUY) {
            offered.filter { it !in primary }.mapNotNull(::merchantQuote)
        } else {
            // Ordinary shoppers buy one commodity; extra goods cannot silently turn the trip into bulk buying.
            offered.singleOrNull()?.takeIf { it !in primary }?.let(::cosmeticQuote)?.let(::listOf).orEmpty()
        }
        return expected + alternatives
    }

    /**
     * Captures one affordable quote for a cosmetic the bot does not already own.
     */
    private fun cosmeticQuote(id: Int): BotTradeItem? {
        if (purchaseTarget != null || !BotEquipmentPreferences.isCosmetic(id) ||
            !itemDef(id).isTradeable || bot.totalItemAmount(id) > 0) return null
        val guide = world.economy.getPrice(id)
        if (guide <= 0) return null
        val quote = cosmeticQuotes.getOrPut(id) {
            BotTradeItem(Item(id, 1), BotMerchantValuation.cosmeticBuy(bot, id, guide), BotTradePurpose.BUY, bot)
        }
        val wallet = TradeBasket.value(quantities(carriedOffer.toItems(), true), prices(offering))
        return quote.takeIf { it.price <= wallet }
    }

    /**
     * A merchant's broad buying advertisement also covers tradeable stock without existing market history.
     */
    private fun merchantQuote(id: Int): BotTradeItem? {
        if (id == COINS || !itemDef(id).isTradeable) return null
        val quote = merchantQuotes.getOrPut(id) {
            val price = merchantPriceSnapshot().buy(id, BotMerchantValuation.peekGuide(id),
                id in actionableWants)
            BotTradeItem(Item(id, Int.MAX_VALUE), price, BotTradePurpose.MERCHANT_BUY, bot)
        }
        val carried = quantities(carriedOffer.toItems(), true)
        val payment = offering.mapNotNull {
            val amount = carried[it.item.id] ?: return@mapNotNull null
            BotTradeItem(Item(it.item.id, amount), it.price, it.purpose, bot)
        }
        return BotTradeManager.affordablePurchases(listOf(quote), payment).firstOrNull()
    }

    /**
     * Reuses a single resale valuation snapshot for every incoming item during this banking trip.
     */
    private fun merchantPriceSnapshot(): BotMerchantValuation.Quotes =
        merchantValuation ?: BotMerchantValuation.snapshot(bot).also { merchantValuation = it }

    /**
     * Human buyers first see the single advertised merchant stack; bot buyers can match a useful collection.
     */
    private fun humanSaleStock(): Map<Int, Int> {
        if (purpose != BotTradePurpose.MERCHANT_SELL) return carriedOffer
        val item = saleAdvertisement()?.item ?: return emptyMap()
        return carriedOffer.filterKeys { unnotedId(it) == item.id }
    }

    /** Values barter once per id for this attempt, with coins always worth one coin. */
    private fun receivedValue(receiving: Map<Int, Int>, guidePrices: MutableMap<Int, Long>): Long {
        val normalized = quantities(receiving.toItems(), true)
        for (id in normalized.keys) {
            guidePrices.getOrPut(id) {
                if (id == COINS) 1L
                else if (bot.preferences.isMerchant) barterPrices.getOrPut(id) {
                    merchantPriceSnapshot().buy(id, BotMerchantValuation.peekGuide(id),
                        id in actionableWants)
                } else world.economy.getPrice(id).coerceAtLeast(0)
            }
        }
        return TradeBasket.value(normalized, guidePrices)
    }

    /** Returns selected normalized quantities using the exact physical ids carried for the current trip. */
    private fun carriedQuantities(selected: Map<Int, Int>): Map<Int, Int> {
        val remaining = selected.toMutableMap()
        val result = linkedMapOf<Int, Int>()
        for ((id, amount) in carriedOffer.toSortedMap()) {
            val base = unnotedId(id)
            val take = minOf(amount, remaining[base] ?: 0)
            if (take > 0) {
                result[id] = take
                remaining[base] = remaining.getValue(base) - take
            }
        }
        return result
    }

    /** Removes excess quantities before adding only the positive difference to the target. */
    private suspend fun applyTarget(offer: BotTradeOfferStage, target: Map<Int, Int>): Boolean {
        for ((id, amount) in quantities(offer.getTradeItems())) {
            val remove = amount - (target[id] ?: 0)
            if (remove > 0 && !offer.remove(Item(id, remove))) return false
        }
        for ((id, amount) in target) {
            val add = amount - (quantities(offer.getTradeItems())[id] ?: 0)
            if (add > 0 && !offer.offer(Item(id, add))) return false
        }
        return quantities(offer.getTradeItems()) == target
    }

    /** Validates the original participants and both accepted baskets before each acceptance click. */
    private suspend fun finishTradeWith(offer: BotTradeOfferStage, own: Map<Int, Int>, receiving: Map<Int, Int>): Boolean {
        val confirm = offer.offerAndAwait {
            quantities(offer.getTradeItems()) == own && quantities(offer.getOtherTradeItems()) == receiving
        } ?: return false
        val screen = bot.overlays[ConfirmTradeInterface::class] ?: return false
        val otherScreen = offer.other.overlays[ConfirmTradeInterface::class] ?: return false
        if (screen.offer.other !== offer.other || otherScreen.offer.other !== bot ||
            quantities(screen.offer.items.filterNotNull()) != own ||
            quantities(otherScreen.offer.items.filterNotNull()) != receiving) return false
        return confirm.confirmAndAwait()
    }

    /** Matches complementary purposes by normalized item ids, ignoring price-wrapper object identity. */
    private fun canTradeWith(other: Player): Boolean {
        if (BotSocialInteractions.engaged(bot) || other is Bot && BotSocialInteractions.engaged(other)) return false
        if (BotConversations.engaged(bot) || other is Bot && BotConversations.engaged(other)) return false
        if (other === bot || other.state != EntityState.ACTIVE || checkBlacklisted(other.username)) return false
        val existing = other.overlays[OfferTradeInterface::class]
        if (other.overlays.hasWindow() && existing?.other !== bot) return false
        if (other !is Bot) return true
        val script = other.scriptStack.current() as? TradeBotScript ?: return false
        return prepared && script.prepared && botExchange(script) != null
    }

    /** Exposes the selected scrutiny policy for future personality-specific negotiation behavior. */
    fun resolveScrutiny(other: Player): BotTradeScrutiny = BotTradeScrutiny.resolveScrutinyFor(bot, other)

    override fun canSocialize(): Boolean = purchaseTarget == null && prepared && !forceBanking && !negotiating && activeTrade == null &&
        duration > Duration.ZERO && marketplace.contains(bot.position)

    override fun socialTradeCompleted() {
        socialStockChanged = true
    }

    override fun onPaused() {
        prepared = false
        marketplace.releaseTradingSpot(this)
        BotSocialInteractions.cancel(bot)
        BotConversations.cancel(bot)
    }

    override suspend fun onBankRequested(initial: Boolean): Boolean {
        if (!initial) marketplace.releaseTradingSpot(this)
        BotSocialInteractions.cancel(bot)
        prepared = false
        BotConversations.cancel(bot)
        return true
    }

    /** Selects one pricing strategy when intentions are refreshed, rather than repricing during negotiation. */
    fun resolveTradePrice(): BotTradePrice = BotTradePrice.computePriceStrategy(bot)

    /** Records the expiry of a participant's temporary exclusion. */
    private fun addBlacklisted(name: String, duration: Duration) {
        blacklist[name] = Instant.now().plus(duration.toJavaDuration())
    }

    /** Tests exclusions without allowing expired entries to block a participant. */
    private fun checkBlacklisted(name: String): Boolean = blacklist[name]?.isAfter(Instant.now()) == true

    /** Finishes through the normal zoned-script lifecycle so shop fallback remains available. */
    private fun endSession(reason: String) {
        prepared = false
        marketplace.releaseTradingSpot(this)
        duration = Duration.ZERO
        bot.log(reason)
    }

    /** Returns the base id used to match bank notes and their unnoted counterparts. */
    private fun unnotedId(id: Int): Int = itemDef(id).unnotedId.orElse(id)

    /** Combines duplicate quantities, optionally normalizing notes, without overflowing item amounts. */
    private fun quantities(items: Collection<Item>, normalize: Boolean = false): Map<Int, Int> {
        val result = linkedMapOf<Int, Int>()
        for (item in items) {
            if (item.amount < 1) continue
            val id = if (normalize) unnotedId(item.id) else item.id
            result[id] = minOf(Int.MAX_VALUE.toLong(), (result[id] ?: 0).toLong() + item.amount).toInt()
        }
        return result
    }

    /** Converts prepared quotes into positive unit prices keyed by base item id. */
    private fun prices(items: List<BotTradeItem>): Map<Int, Long> =
        items.associate { unnotedId(it.item.id) to it.price.coerceAtLeast(1) }

    /** Converts a quantity basket into interface item requests. */
    private fun Map<Int, Int>.toItems(): List<Item> = map { (id, amount) -> Item(id, amount) }
}
