package game.bot.scripts.trading

import api.bot.isRealBot

import api.bot.Suspendable.waitFor
import api.bot.action.trading.*
import api.bot.action.trading.BotTradeManager.tradeRequests
import api.bot.action.trading.PlayerTradeAdvertisementMarket.Opportunity
import api.bot.action.trading.PlayerTradeAdvertisementMarket.Pricing
import api.bot.action.trading.PlayerTradeAdvertisementPolicy.Profile
import api.bot.script.BotScriptData
import api.bot.script.ZonedBotScript
import api.bot.social.BotConversations
import api.bot.social.BotSocialInteractions
import api.bot.zone.SubZone
import api.bot.webwalk.plan.BankAccess
import api.predef.*
import api.predef.ext.*
import com.google.common.base.Stopwatch
import engine.bot.gear.BotItemTracker.Companion.itemTracker
import engine.bot.gear.BotGearLocator
import engine.bot.gear.BotEquipmentPreferences
import engine.trade.ConfirmTradeInterface
import engine.trade.OfferTradeInterface
import engine.trade.tradingWith
import game.bot.scripts.IdleBotScript
import io.luna.Luna
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.item.Item
import io.luna.game.model.item.ItemContainer
import io.luna.game.model.item.ItemContainer.StackPolicy
import io.luna.game.model.def.WantedItemDefinition.WantedItemPriority
import io.luna.game.model.def.WantedItemDefinition
import io.luna.game.model.def.ItemDefinition
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.PlayerContextMenuOption.TRADE
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.bot.brain.BotActivity
import io.luna.game.model.mob.bot.brain.BotEmotion.EmotionType
import io.luna.game.model.mob.varp.PersistentVarp
import java.time.Instant
import java.math.BigDecimal
import java.math.RoundingMode
import io.luna.util.NumberUtils
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration
import kotlinx.coroutines.withTimeoutOrNull

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
 * @property purchaseDeadline Optional absolute deadline for a targeted supply visit; zero keeps ordinary waits.
 * @author lare96
 */
class TradeBotScript(bot: Bot,
                     private val purpose: BotTradePurpose,
                     private val marketplace: BotMarketplace = BotMarketplace.VARROCK,
                     duration: Duration = getPreferenceBasedDuration(bot),
                     zone: MutableList<SubZone> = mutableListOf(marketplace.activityZone),
                     private val purchaseTarget: Item? = null,
                     private val purchasePrice: Long = 0,
                     private val purchaseDeadline: Long = 0L) :
    ZonedBotScript(bot, duration, zone) {

    companion object {

        /**
         * Creates a single-item buying session only when a prepared seller has an affordable match.
         * The target is total owned stock, not an additional quantity. Normal negotiation revalidates
         * stock and payment after travel; no items or money are reserved by this scan.
         * An optional absolute deadline also bounds suspending negotiations for a temporary supply visit.
         */
        fun buySpecific(bot: Bot, id: Int, target: Int, deadlineMillis: Long = 0L): TradeBotScript? {
            require(target > 0 && id != COINS)
            if (deadlineMillis > 0L && System.currentTimeMillis() >= deadlineMillis) return null
            val guide = world.economy.getPrice(id)
            if (guide <= 0) return null
            val price = BotTradePrice.computePriceStrategy(bot).applyBuy(guide).coerceAtLeast(1)
            for (market in BotMarketplace.entries) {
                val script = TradeBotScript(bot, BotTradePurpose.BUY, market, 10.minutes,
                    purchaseTarget = Item(id, target), purchasePrice = price, purchaseDeadline = deadlineMillis)
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
                        script.quantities(payment.map { it.item }, true), script.prices(payment), seller.salePriority()) != null
                }) return script
            }
            return null
        }

        /** The currency used for marketplace payments. */
        private const val COINS = 995

        /** The maximum number of offer changes permitted in one negotiation. */
        private const val MAX_NEGOTIATION_ROUNDS = 12
        private const val MAX_CUSTOMER_APPROACHES = 4
        private const val ADVERTISEMENT_NOTICE_COLOR = "@blu@"

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

    /**
     * Retains only candidate order across bounded search passes, independently of incoming requests.
     */
    private var partnerSearch: BotTradePartnerSearch<Player>? = null

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
     * Identifies an unanswered bot approach that may yield to a matching human advertisement.
     */
    private var pendingPartner: Player? = null
    private var advertisementTrade: Opportunity? = null

    /**
     * Personality and browsing mood stay fixed for this marketplace visit, including banking and resume.
     */
    private val advertisementProfile by lazy(LazyThreadSafetyMode.NONE) {
        Profile(bot.personality.intelligence, bot.personality.kindness, bot.personality.confidence,
            bot.personality.social, bot.preferences.activities[BotActivity.MERCHANTING] ?: 0.5,
            bot.preferences.isMerchant || purpose == BotTradePurpose.MERCHANT_BUY ||
                    purpose == BotTradePurpose.MERCHANT_SELL, bot.emotions.isFeeling(EmotionType.GREEDY))
    }
    private val curiousVisit by lazy(LazyThreadSafetyMode.NONE) {
        PlayerTradeAdvertisementPolicy.curious(advertisementProfile, rand().nextDouble())
    }

    /**
     * Prepared physical payment limits are captured once per bank trip. Sellers reserve buying stock once;
     * ordinary buying stock has already passed its purchasing reserve.
     */
    private var advertisementFunding: Map<Int, Int>? = null
    private val advertisementGuides = HashMap<Int, Long>()

    /**
     * Unexpected ordinary purchases share ten percent of the first prepared market budget for the whole visit.
     */
    private var curiosityRemaining: Long? = null

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
     * Personal purchases retain the trip's normal buying strategy independently of resale bids.
     */
    private var tradePrice = BotTradePrice.EXACT
    private val wantedBarterPrices = HashMap<Int, Long>()

    /**
     * Lazily captures accessible resale channels once for incoming stock, outside the bounded initial catalogue.
     */
    private var merchantValuation: BotMerchantValuation.Quotes? = null

    /**
     * A full market retries elsewhere instead of sending an otherwise prepared trader to a shop.
     */
    private var capacityBlocked = false

    /**
     * Customers of existing advertisers need no trading lease and never advertise their own stock.
     */
    private var browsing = false
    private var browsingMarket: BotMarketplace? = null
    private var browseAttempts = 0

    /**
     * Preserves the unspent session budget when a full market makes this visit yield to other work.
     */
    private var retryDuration: Duration? = null

    /** The advertisement interval selected for the current intentions, in seconds. */
    private var messageCooldown = 0

    /** The bank withdrawal mode to restore when the session finishes or pauses. */
    private var previousBankMode: Int? = null

    /** Whether this purpose acquires wanted items in exchange for coins or surplus payment stock. */
    private val buying: Boolean
        get() = purpose == BotTradePurpose.BUY || purpose == BotTradePurpose.MERCHANT_BUY

    /**
     * Merchant accounts also use merchant batches when selling ordinary surplus.
     */
    private val merchantSelling: Boolean
        get() = purpose == BotTradePurpose.MERCHANT_SELL || purpose == BotTradePurpose.SELL && bot.preferences.isMerchant

    override suspend fun onInit(resumed: Boolean): Boolean {
        if (purchaseExpired()) {
            endSession("Specific purchase deadline expired.")
            return false
        }
        partnerSearch = null
        BotSocialInteractions.cancel(bot)
        BotConversations.cancel(bot)
        prepared = false
        browsing = false
        browsingMarket = null
        browseAttempts = 0
        retryDuration = null
        if (reserveTradingSpot() == null) {
            if (BotMarketplace.entries.any { it.hasAvailableTradingSpot(bot) }) {
                bot.log("Marketplace is full; another market has trading space.")
                return false
            }
            browsing = true
        }
        for ((name, feeling) in bot.preferences.playerFeelings) {
            if (feeling < 0.25) addBlacklisted(name, 365.days)
        }
        if (!computeOfferAndExpected()) return false
        if (browsing && findBrowsePartner(prospectiveStock()) == null) {
            deferTrading("All marketplaces are full and no existing offer matches.")
            return false
        }
        previousBankMode = bot.varpManager.getValue(PersistentVarp.WITHDRAW_AS_NOTE)
        bot.speechStack.suppressGeneral(this)
        // Zoned scripts skip initial banking on resume; explicitly prepare again in that case.
        forceBanking = resumed
        return true
    }

    override suspend fun equipment(): BotGearLocator? = null

    override suspend fun onBankOpen(initial: Boolean) {
        withinPurchaseDeadline { prepareTradeInventory() }
    }

    private suspend fun prepareTradeInventory() {
        partnerSearch = null
        prepared = false
        if (!computeOfferAndExpected()) return
        handler.banking.clickBankingMode(true)
        val stackLimit = if (merchantSelling) Luna.settings().bots().merchantSaleStackLimit() else Int.MAX_VALUE
        for (entry in offering) {
            if (bot.inventory.computeRemainingSize() <= 1 || bot.inventory.size() >= stackLimit) break
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
        advertisementFunding = null
        advertisementGuides.clear()
        if (bot.isRealBot()) preparedAdvertisementFunding()
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
        if (browsing) {
            prepared = true
            forceBanking = false
            return
        }
        val tile = reserveTradingSpot()
        if (tile == null) {
            deferTrading("The trading position became unavailable during banking.")
            return
        }
        prepared = handler.webWalkTo(marketplace.parent) &&
                handler.webWalkTo(tile) && bot.position == tile
        if (!prepared) endSession("Could not return to an available marketplace tile.")
    }

    override suspend fun finish() {
        partnerSearch = null
        BotSocialInteractions.cancel(bot)
        BotConversations.cancel(bot)
        prepared = false
        marketplace.releaseTradingSpot(this)
        browsingMarket = null
        activeTrade?.decline()
        activeTrade = null
        cancelAdvertisementTrade()
        bot.tradingWith = -1
        previousBankMode?.let { bot.varpManager.setValue(PersistentVarp.WITHDRAW_AS_NOTE, it) }
        previousBankMode = null
        bot.speechStack.releaseGeneral(this)
    }

    override suspend fun completed() {
        if (capacityBlocked) {
            if (purchaseDeadline > 0L) return
            val next = BotMarketplace.select(bot)
            val remaining = retryDuration ?: remainingDuration()
            if (remaining > Duration.ZERO) {
                val otherWork = bot.scriptStack.scripts.any {
                    it !== this && it !is TradeBotScript && !it.isFinished() && !it.isTerminated()
                }
                if (!otherWork && BotMarketplace.entries.none { it.hasAvailableTradingSpot(bot) }) {
                    bot.scriptStack.softPushHead(IdleBotScript(bot, SubZone.HOME,
                        System.currentTimeMillis() + rand(2, 5) * 60_000L))
                }
                bot.scriptStack.pushTail(TradeBotScript(bot, purpose, next, remaining,
                    purchaseTarget = purchaseTarget, purchasePrice = purchasePrice,
                    purchaseDeadline = purchaseDeadline))
            }
            return
        }
        if (purchaseTarget == null && !traded && !bot.preferences.isMerchant) {
            if (buying) bot.scriptStack.push(BuyFromStoreBotScript(bot))
            else bot.scriptStack.push(SellToStoreBotScript(bot))
        }
    }

    override suspend fun executeInZone(): Boolean = withinPurchaseDeadline { executeTradeInZone() } ?: false

    override suspend fun beforeZoneWork(): Boolean {
        if (!browsing) return false
        withinPurchaseDeadline {
            if (!prepared) {
                deferTrading("No stock remains prepared for this customer visit.")
                return@withinPurchaseDeadline
            }
            if (tryPlayerTrade()) {
                if (!prepared || forceBanking) deferTrading("Customer exchange completed.")
                return@withinPurchaseDeadline
            }
            val stock = carriedOffer.mapValues { (id, amount) -> minOf(amount, bot.inventory.computeAmountForId(id)) }
                .filterValues { it > 0 }
            val partner = findBrowsePartner(stock)
            if (partner == null) {
                deferTrading("No matching advertised offers remain at any marketplace.")
                return@withinPurchaseDeadline
            }
            if (browseAttempts++ >= MAX_CUSTOMER_APPROACHES) {
                deferTrading("The available advertisers could not complete a customer trade.")
                return@withinPurchaseDeadline
            }
            browsingMarket = partner.marketplace
            val tile = partner.marketplace.availableWorkPositions(bot)
                .filter { it.isWithinDistance(partner.bot.position, Position.VIEWING_DISTANCE) }
                .minByOrNull { it.computeLongestDistance(bot.position) }
            if (tile == null || !handler.webWalkTo(partner.marketplace.parent) || !handler.webWalkTo(tile)) {
                addBlacklisted(partner.bot.username, 5.minutes)
                return@withinPurchaseDeadline
            }
            carriedOffer = carriedOffer.mapValues { (id, amount) -> minOf(amount, bot.inventory.computeAmountForId(id)) }
                .filterValues { it > 0 }
            val available = partner.carriedOffer.mapValues { (id, amount) ->
                minOf(amount, partner.bot.inventory.computeAmountForId(id))
            }.filterValues { it > 0 }
            if (availableAtMarket(partner) && planExchange(partner, carriedOffer, available) != null &&
                canTradeWith(partner.bot) && bot.isViewableFrom(partner.bot)) {
                attemptTrade(partner.bot)
                if (!prepared || forceBanking) deferTrading("Customer exchange completed.")
            } else addBlacklisted(partner.bot.username, 5.seconds)
        }
        return true
    }

    private suspend fun executeTradeInZone(): Boolean {
        if (!prepared) {
            forceBanking = true
            return true
        }
        if (!tryPlayerTrade()) {
            val incoming = bot.tradeRequests.pollBotRequests { canAcceptRequest(it) }
            val search = partnerSearch ?: BotTradePartnerSearch<Player>(world.currentTick).also { partnerSearch = it }
            val other = incoming ?: search.find(world.currentTick, candidates = {
                blacklist.entries.removeIf { !it.value.isAfter(Instant.now()) }
                world.locator.findPlayers(bot, Position.VIEWING_DISTANCE * 2) {
                    it.isRealBot() && bot.isViewableFrom(it)
                }.toList()
            }, eligible = {
                it.isRealBot() && bot.isViewableFrom(it) && eligibleTradePartner(it)
            }, matches = {
                // Eligibility and planning run together on the game thread without suspending.
                botExchange((it as Bot).scriptStack.current() as TradeBotScript) != null
            })
            if (other != null) {
                attemptTrade(other)
                if (prepared && !forceBanking) tryPlayerTrade()
            }
        }
        if (prepared && !forceBanking) {
            if (tryStoreBargain()) return true
            sendMessage()
        }
        return true
    }

    /**
     * Lets an idle merchant take one bounded store opportunity without abandoning the current market session.
     * Player requests and negotiations are serviced first; the shared planner throttles unsuccessful checks.
     */
    private fun tryStoreBargain(): Boolean {
        if (!bot.preferences.isMerchant || purchaseTarget != null || negotiating || pendingPartner != null ||
            activeTrade != null || advertisementTrade != null || bot.tradingWith != -1 || bot.overlays.hasWindow() ||
            !bot.walking.isEmpty || bot.navigator.isActive) return false
        val deal = MerchantStoreBargains.poll(bot) ?: return false
        if (bot.tradeRequests.hasRequest { canAcceptRequest(it) } ||
            bot.scriptStack.scripts.any { it is BuyStoreBargainBotScript && !it.isFinished() && !it.isTerminated() }) {
            return false
        }
        bot.scriptStack.pushHead(BuyStoreBargainBotScript(bot, deal))
        return true
    }

    override suspend fun travelToWorkZone(zone: SubZone, bank: BankAccess): Boolean =
        withinPurchaseDeadline { super.travelToWorkZone(zone, bank) } ?: false

    /**
     * A specific supply purchase shares its caller's absolute deadline. Cancellation unwinds native negotiation
     * cleanup, so an unanswered request or an offer wait cannot consume the following gathering stage's budget.
     * Ordinary marketplace sessions retain their existing duration and wait policy.
     */
    private suspend fun <T> withinPurchaseDeadline(action: suspend () -> T): T? {
        if (purchaseDeadline <= 0L) return action()
        val remaining = purchaseDeadline - System.currentTimeMillis()
        if (remaining <= 0L) {
            endSession("Specific purchase deadline expired.")
            return null
        }
        return withTimeoutOrNull(remaining) { action() }.also {
            if (it == null) endSession("Specific purchase deadline expired.")
        }
    }

    private fun purchaseExpired(): Boolean = purchaseDeadline > 0L &&
        System.currentTimeMillis() >= purchaseDeadline

    /**
     * Gives direct human requests first choice, followed by the selected heard player advertisement.
     */
    private suspend fun tryPlayerTrade(): Boolean {
        val incoming = bot.tradeRequests.pollHumanRequests { canAcceptRequest(it) }
        if (incoming != null) {
            attemptTrade(incoming)
            return true
        }
        val advertised = PlayerTradeAdvertisementMarket.live.claim(this) ?: return false
        attemptTrade(advertised.entry.player, advertised)
        return true
    }

    override suspend fun prepareMarketplace(marketplace: BotMarketplace?): Boolean {
        if (browsing) return true
        if (marketplace == null) return true
        val tile = reserveTradingSpot()
        if (tile == null) {
            bot.log("Marketplace trading spots are full.")
            return false
        }
        return bot.position == tile || handler.webWalkTo(tile) && bot.position == tile
    }

    override fun snapshot(): BotScriptData? = null

    /**
     * Keeps every advertising session within its marketplace's exclusive trading slots.
     */
    private fun reserveTradingSpot(): Position? {
        val tile = marketplace.reserveTradingSpot(this)
        capacityBlocked = tile == null
        return tile
    }

    /**
     * Remembers the time left before completing normally, so a retry runs behind existing work.
     */
    private fun deferTrading(reason: String) {
        if (retryDuration == null) retryDuration = remainingDuration()
        capacityBlocked = true
        endSession(reason)
    }

    /**
     * Inspects transferable ownership before banking, without reserving or moving any items.
     */
    private fun prospectiveStock(): Map<Int, Int> = offering.mapNotNull { quote ->
        val amount = minOf(quote.item.amount, BotTradeManager.transferableAmount(bot, quote.item.id))
        if (amount > 0) quote.item.id to amount else null
    }.toMap()

    /**
     * Matches a customer's stock against live, leased advertisers throughout all marketplaces.
     */
    private fun findBrowsePartner(stock: Map<Int, Int>): TradeBotScript? =
        world.players.filterIsInstance<Bot>().mapNotNull { it.scriptStack.current() as? TradeBotScript }
            .filter { availableAtMarket(it) && it.marketplace.tradingSpot(it) != null }
            .sortedBy { it.bot.position.computeLongestDistance(bot.position) }
            .firstOrNull { other ->
                val actual = other.carriedOffer.mapValues { (id, amount) ->
                    minOf(amount, other.bot.inventory.computeAmountForId(id))
                }.filterValues { it > 0 }
                planExchange(other, stock, actual) != null
            }

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
        tradePrice = strategy
        val offers = BotTradeManager.computeBudgetedOfferItems(bot, strategy, purpose)
            .filter { purchaseTarget == null || it.item.id == COINS }
        val wants = if (purchaseTarget == null) BotTradeManager.computeExpected(bot, strategy, purpose, offers)
        else BotTradeManager.affordablePurchases(specificDemand(), offers)
        val traders = world.players.filterIsInstance<Bot>().mapNotNull {
            it.scriptStack.current() as? TradeBotScript
        }.filter { availableAtMarket(it) }
        val candidates = if (buying) wants else offers
        // Preserve the buyer's budget allocation order; sellers favor stock with a current market match.
        val selected = if (purpose == BotTradePurpose.SELL && !merchantSelling || browsing && purpose == BotTradePurpose.BUY) {
            prioritizeMarketItems(candidates.sortedByDescending(::stackValue), offers, traders).take(1)
        } else candidates
        offering = if (buying) offers else selected
        expected = if (purpose == BotTradePurpose.BUY) selected.take(1) else if (buying) selected
        else BotTradeManager.computeExpected(bot, strategy, purpose, offering)
        cosmeticQuotes.clear()
        merchantQuotes.clear()
        barterPrices.clear()
        wantedBarterPrices.clear()
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
            !other.browsing && !other.forceBanking && !other.negotiating &&
            other.bot.scriptStack.current() === other && buying != other.buying &&
            (if (browsing) other.marketplace else marketplace).contains(other.bot.position) &&
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
        if (browsing) return
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
     * Advertises the highest-priority carried sale stack instead of an unrelated generic category.
     */
    private fun saleAdvertisement(): BotTradeItem? {
        val carried = quantities(carriedOffer.toItems(), true)
        val stock = offering.mapNotNull { quote ->
            val amount = if (carriedOffer.isEmpty()) quote.item.amount else carried[quote.item.id] ?: 0
            if (amount > 0) BotTradeItem(Item(quote.item.id, amount), quote.price, quote.purpose, bot) else null
        }
        return stock.firstOrNull()
    }

    /**
     * Compares entire stack values without clipping them to the coin item's integer quantity limit.
     */
    private fun stackValue(quote: BotTradeItem): Long =
        TradeBasket.value(mapOf(quote.item.id to quote.item.amount), mapOf(quote.item.id to quote.price))

    /**
     * Prepares a current advertised purchase without claiming the player or sending a message.
     * Pending bot approaches may inspect their own higher-priority opportunity before yielding.
     */
    internal fun advertisementCandidate(market: PlayerTradeAdvertisementMarket,
                                        entry: PlayerTradeAdvertisementRegistry.Entry,
                                        advertised: PlayerTradeAdvertisement,
                                        allowPendingBotApproach: Boolean = false,
                                        remembered: PlayerTradeAdvertisementMarket.Remembered? = null,
                                        offered: Collection<Item> = emptyList(),
                                        receiving: Map<Int, Int> = emptyMap()): Opportunity? {
        if (!advertisementReady(entry.player, allowPendingBotApproach, continuing = remembered != null)) return null
        val remaining = entry.remainingQuantity(advertised)
        if (remaining == 0) return null
        val allowance = preparedAdvertisementFunding()
        val physical = currentAdvertisementFunding(allowance, offered)
        val funding = quantities(physical.toItems(), true)
        val guides = funding.keys.associateWith { guideForAdvertisement(it) }
        if (TradeBasket.value(funding, guides) <= 0) return null
        val inspection = advertised.requiresInspection
        val id = remembered?.pricing?.itemId ?: advertised.itemIds.singleOrNull()
        if (inspection && !advertisementProfile.merchant && !curiousVisit) return null
        if (id == null) {
            if (!advertisementProfile.merchant && (curiosityRemaining ?: 0) <= 0) return null
            return Opportunity(market, this, entry, advertised, null, null, allowance, guides, true, false,
                remembered?.counterSent == true, remembered != null)
        }
        val definition = ItemDefinition.ALL.get(id).orElse(null) ?: return null
        if (id == COINS || !definition.isTradeable) return null
        val demand = advertisementDemand(id)
        if (demand.first <= 0) return null
        val pricing = remembered?.pricing ?: advertisementPricing(market, entry, advertised, id, demand.second)
            ?: return null
        val context = Opportunity(market, this, entry, advertised, pricing, null, allowance, guides, inspection,
            demand.second, remembered?.counterSent == true, remembered != null)
        context.communicated = remembered != null
        context.plan = advertisementPaymentPlan(context, minOf(demand.first, remaining ?: Int.MAX_VALUE),
            funding, receiving, offered) ?: return null
        return context
    }

    /**
     * Checks live permissions without excluding the particular unanswered bot approach being interrupted.
     */
    private fun advertisementReady(other: Player, allowPendingBotApproach: Boolean = false,
                                   continuing: Boolean = false, inAttempt: Boolean = false): Boolean {
        if (!bot.isRealBot() || other.isRealBot() || bot.state != EntityState.ACTIVE ||
            other.state != EntityState.ACTIVE || !prepared || forceBanking ||
            bot.scriptStack.current() !== this || !bot.isAlive || bot.isLocked ||
            !isActionSafe() ||
            bot.combat.target != null || bot.reflex.activeReflex != null ||
            world.getPlayer(bot.usernameHash).orElse(null) !== bot ||
            world.getPlayer(other.usernameHash).orElse(null) !== other ||
            checkBlacklisted(other.username) || TRADE !in bot.contextMenu ||
            other.usernameHash in bot.ignores || bot.usernameHash in other.ignores ||
            !other.isViewableFrom(bot) || !bot.isViewableFrom(other)) return false
        val mine = bot.overlays[OfferTradeInterface::class]
        val theirs = other.overlays[OfferTradeInterface::class]
        val matching = mine?.other === other && theirs?.other === bot
        if (!matching && !PlayerTradeAdvertisementMessages.allowsTrade(bot, other)) return false
        if (bot.overlays.hasWindow() && !((continuing || inAttempt) && matching)) return false
        if (other.overlays.hasWindow() && !matching) return false
        if (other.tradingWith != -1 && other.tradingWith != bot.index && !matching) return false
        if (inAttempt) return pendingPartner === other
        if (!(browsingMarket ?: marketplace).contains(bot.position)) return false
        return !negotiating || continuing && pendingPartner === other ||
                allowPendingBotApproach && pendingPartner.isRealBot() && !bot.overlays.hasWindow()
    }

    /**
     * Captures only eligible carried stock; banked funds never create an advertisement promise.
     */
    private fun preparedAdvertisementFunding(): Map<Int, Int> {
        advertisementFunding?.let { return it }
        val selected = if (buying) carriedOffer else {
            val normalized = quantities(carriedOffer.toItems(), true)
            val profile = advertisementProfile
            val fraction = BotTradeIntentions.spendingFraction(profile.intelligence, profile.greedy, profile.merchant)
            val stock = normalized.filterKeys { it != COINS }.map { (id, amount) ->
                BotTradeItem(Item(id, amount), guideForAdvertisement(id), BotTradePurpose.BUY, bot)
            }
            val budgeted = BotTradeManager.spendingStock(stock, fraction).associate { it.item.id to it.item.amount }
                .toMutableMap()
            val coins = BigDecimal.valueOf((normalized[COINS] ?: 0).toLong()).multiply(BigDecimal.valueOf(fraction))
                .setScale(0, RoundingMode.FLOOR).toInt()
            if (coins > 0) budgeted[COINS] = coins
            carriedQuantities(budgeted)
        }
        advertisementFunding = selected.toMap()
        if (curiosityRemaining == null) {
            val normalized = quantities(selected.toItems(), true)
            val values = normalized.keys.associateWith { guideForAdvertisement(it) }
            curiosityRemaining = TradeBasket.value(normalized, values) / 10
        }
        return advertisementFunding!!
    }

    /**
     * Includes payment already on this bot's offer screen so replanning cannot repeatedly withdraw and re-add it.
     */
    private fun currentAdvertisementFunding(allowance: Map<Int, Int>, offered: Collection<Item> = emptyList()): Map<Int, Int> {
        val held = bot.inventory.filterNotNull() + offered
        val dynamic = held.filter { it.isDynamic }.map { it.id }.toSet()
        val amounts = quantities(held.filter { !it.isDynamic && it.itemDef.isTradeable })
        return allowance.mapNotNull { (id, limit) ->
            val amount = minOf(limit, amounts[id] ?: 0)
            if (amount > 0 && id !in dynamic) id to amount else null
        }.toMap()
    }

    /**
     * Resolves genuine current demand independently of the single item advertised by ordinary buying scripts.
     * The second result identifies a purchase using the visit's discretionary curiosity allowance.
     */
    private fun advertisementDemand(id: Int): Pair<Int, Boolean> {
        val target = purchaseTarget
        if (target?.id == id) return (specificDemand().firstOrNull()?.item?.amount ?: 0) to false
        val wanted = BotTradeIntentions.purchases(bot).firstOrNull { it.id == id }
        if (wanted != null) {
            val missing = (wanted.target.toLong() - bot.totalItemAmount(id)).coerceIn(0, Int.MAX_VALUE.toLong()).toInt()
            if (missing > 0) return missing to false
        }
        if (advertisementProfile.merchant) return Int.MAX_VALUE to false
        return if (curiousVisit && (curiosityRemaining ?: 0) > 0) Int.MAX_VALUE to true else 0 to false
    }

    /**
     * Captures one maximum valuation and one haggle decision per advertisement and exact commodity.
     */
    private fun advertisementPricing(market: PlayerTradeAdvertisementMarket,
                                     entry: PlayerTradeAdvertisementRegistry.Entry,
                                     advertised: PlayerTradeAdvertisement, id: Int, unexpected: Boolean): Pricing? =
        market.pricing(entry, bot, advertised, id) {
            val guide = BotMerchantValuation.peekGuide(id)
            val profile = advertisementProfile
            val normal = expected.firstOrNull { it.item.id == id && buying }?.price
            val maximum = when {
                purchaseTarget?.id == id -> purchasePrice
                profile.merchant -> merchantPriceSnapshot().buy(id, guide, !unexpected &&
                    BotTradeIntentions.purchases(bot).any { it.id == id }, tradePrice)
                normal != null -> normal
                else -> resolveTradePrice().applyBuy(guide)
            }
            PlayerTradeAdvertisementPolicy.quote(concreteAdvertisement(advertised, id), maximum, profile,
                rand().nextDouble(), rand().nextDouble())?.let { Pricing(id, maximum, it, profile) }
        }

    /**
     * Unclear wording is inspected as an unpriced offer instead of becoming a guessed asking price.
     */
    private fun concreteAdvertisement(advertised: PlayerTradeAdvertisement, id: Int): PlayerTradeAdvertisement =
        advertised.copy(itemIds = setOf(id), price = advertised.price?.takeUnless {
            it.basis == PlayerTradeAdvertisement.PriceBasis.UNCLEAR ||
                    it.basis == PlayerTradeAdvertisement.PriceBasis.TOTAL && advertised.quantity == null
        })

    /**
     * Payment-item guide values remain fixed for the prepared basket and are copied into each trade attempt.
     */
    private fun guideForAdvertisement(id: Int): Long = advertisementGuides.getOrPut(id) {
        if (id == COINS) 1 else BotMerchantValuation.peekGuide(id).coerceAtLeast(1)
    }

    /** Owns request, negotiation, and cleanup for one participant, including every unsuccessful return path. */
    private suspend fun attemptTrade(other: Player) = attemptTrade(other, null)

    /**
     * Runs an advertised or ordinary trade through the same request, offer and confirmation lifecycle.
     */
    private suspend fun attemptTrade(other: Player, advertised: Opportunity?) {
        negotiating = true
        pendingPartner = other
        var success = false
        var yieldedToPlayer = false
        var advertisement = advertised
        try {
            advertisementTrade = advertisement
            val existing = bot.overlays[OfferTradeInterface::class]
            val offer = if (existing?.other === other) {
                BotTradeOfferStage(bot, other, BotTradeRequestStage(bot, other))
            } else {
                val request = BotTradeRequestStage(bot, other)
                val context = advertisement
                if (context != null) {
                    request.sendAndAwait(beforeRequest = {
                        if (!advertisementApproachAlive(context)) false else {
                            context.plan = advertisementPlan(context, emptyMap(), emptyList())
                            (context.pricing == null || context.plan != null) && announceAdvertisement(context)
                        }
                    }, canContinue = { advertisementApproachAlive(context) })
                } else request.sendAndAwait {
                    val proceed = !other.isRealBot() ||
                            !hasHumanRequest() && !PlayerTradeAdvertisementMarket.live.hasPriority(this)
                    if (!proceed) yieldedToPlayer = true
                    proceed
                }
            } ?: return
            activeTrade = offer
            val context = advertisement
            if (context != null) {
                if (context.response == null && context.market.registry.find(other) === context.entry) {
                    waitFor(2.seconds) {
                        context.response = context.market.registry.resume(context.entry, bot)
                        context.response != null || context.market.registry.find(other) !== context.entry
                    }
                    if (context.response == null && context.market.registry.find(other) === context.entry) return
                }
                // An incoming reciprocal request may open the screen before the submission prerequisite runs.
                if (!announceAdvertisement(context)) return
                success = negotiateAdvertisement(offer, context)
            } else {
                // The quote must still be live when the native screen opens; this snapshot then stays fixed.
                val remembered = if (other.isRealBot()) emptyList() else
                    PlayerTradeAdvertisementMarket.live.rememberedOffers(this, other)
                success = negotiate(offer, other, remembered) {
                    advertisement = it
                    advertisementTrade = it
                }
            }
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
                if (!yieldedToPlayer && advertisement?.communicated != true) {
                    addBlacklisted(other.username, if (other.isRealBot()) 5.minutes else 5.seconds)
                }
            }
            activeTrade = null
            if (bot.tradingWith == other.index) bot.tradingWith = -1
            advertisement?.let { it.market.release(it) }
            advertisementTrade = null
            pendingPartner = null
            negotiating = false
        }
    }

    /**
     * Keeps an unanswered advertised approach live without claiming another offer or rerolling its quote.
     */
    private fun advertisementApproachAlive(context: Opportunity): Boolean {
        val registry = context.market.registry
        val current = context.response?.let(registry::isCurrent)
            ?: (context.continuation && registry.find(context.entry.player) === context.entry)
        if (!current || !advertisementReady(context.entry.player, continuing = true, inAttempt = true)) return false
        val held = quantities(currentAdvertisementFunding(context.allowance).toItems(), true)
        val value = TradeBasket.value(held, context.paymentPrices)
        val pricing = context.pricing ?: return value > 0
        val minimum = PlayerTradeAdvertisementPayment.cost(concreteAdvertisement(context.offer, pricing.itemId),
            pricing.quote, 1) ?: return false
        return value >= minimum && (!context.unexpected || (curiosityRemaining ?: 0) >= minimum)
    }

    /**
     * Sends a price counteroffer at most once and records the communicated deal for player-initiated acceptance.
     */
    private fun announceAdvertisement(context: Opportunity): Boolean {
        val pricing = context.pricing
        if (pricing != null && pricing.quote.counteroffer && !context.counterSent) {
            val plan = context.plan ?: return false
            val units = if (pricing.quote.price.basis == PlayerTradeAdvertisement.PriceBasis.EACH) "ea"
                else context.offer.quantity?.let { "for ${numF(it)}" } ?: return false
            val prefix = "${numF(pricing.quote.price.coins)}gp $units "
            val suffix = "; up to ${numF(plan.quantity)}. Trade me."
            val room = 80 - prefix.length - suffix.length
            if (room <= 0) return false
            val name = itemDef(pricing.itemId).name.take(room)
            if (!PlayerTradeAdvertisementMessages.counteroffer(bot, context.entry.player, prefix + name + suffix)) return false
            context.counterSent = true
        }
        context.market.remember(context)
        return true
    }

    /**
     * Explains a partial quantity only to the participant, respecting their current Trade/compete setting.
     */
    private fun notifyAdvertisementQuantity(context: Opportunity, plan: PlayerTradeAdvertisementPayment.Plan) {
        val previous = context.lastNotifiedQuantity
        if (previous == null && context.offer.quantity?.let { plan.quantity >= it } == true) return
        if (previous != null && (plan.quantity >= previous || context.quantityShrinkNotified)) return
        val id = context.pricing?.itemId ?: return
        if (!PlayerTradeAdvertisementMessages.notice(bot, context.entry.player,
                "$ADVERTISEMENT_NOTICE_COLOR${bot.username} wants up to ${numF(plan.quantity)} " +
                        "${itemDef(id).name}.@bla@")) return
        context.quantityNotified = true
        context.lastNotifiedQuantity = plan.quantity
        if (previous != null) context.quantityShrinkNotified = true
    }

    /**
     * Replans from original payment limits, current demand and the original lot ratio after each offer change.
     */
    private fun advertisementPlan(context: Opportunity, receiving: Map<Int, Int>,
                                  offered: Collection<Item>): PlayerTradeAdvertisementPayment.Plan? =
        advertisementPlan(context, receiving, offered, false)

    /**
     * Calculates the useful maximum for the observed physical form, independent of how many units the player has
     * added so far. Quantity notices therefore remain truthful while the player builds a stack one item at a time.
     */
    private fun advertisementMaximumPlan(context: Opportunity, receiving: Map<Int, Int>,
                                         offered: Collection<Item>): PlayerTradeAdvertisementPayment.Plan? =
        advertisementPlan(context, receiving, offered, true)

    private fun advertisementPlan(context: Opportunity, receiving: Map<Int, Int>, offered: Collection<Item>,
                                  maximum: Boolean): PlayerTradeAdvertisementPayment.Plan? {
        val received = quantities(receiving.toItems(), true)
        val id = if (received.isEmpty()) context.pricing?.itemId else received.keys.singleOrNull()
        if (id == null || id == COINS || context.pricing?.let { it.itemId != id } == true ||
            context.offer.itemIds.isNotEmpty() && id !in context.offer.itemIds) return null
        val definition = ItemDefinition.ALL.get(id).orElse(null) ?: return null
        if (!definition.isTradeable) return null
        val demand = advertisementDemand(id)
        if (demand.first <= 0) return null
        val pricing = context.pricing ?: advertisementPricing(context.market, context.entry, context.offer, id, demand.second)
            ?: return null
        context.pricing = pricing
        context.unexpected = demand.second
        val stockLimit = context.entry.remainingQuantity(context.offer) ?: Int.MAX_VALUE
        val wanted = minOf(demand.first, stockLimit, if (maximum) Int.MAX_VALUE else received[id] ?: Int.MAX_VALUE)
        val funding = quantities(currentAdvertisementFunding(context.allowance, offered).toItems(), true)
        val physical = if (maximum) receiving.mapValues { Int.MAX_VALUE } else receiving
        return advertisementPaymentPlan(context, wanted, funding, physical, offered)
    }

    /**
     * Bounds a payment by the physical goods that fit after that payment leaves the inventory. Before inspection,
     * noteable goods may arrive as notes; once goods are offered their actual physical ids determine capacity.
     * Only the at-most-inventory-sized non-stackable tail needs a descending search.
     */
    private fun advertisementPaymentPlan(context: Opportunity, wanted: Int, funding: Map<Int, Int>,
                                         receiving: Map<Int, Int>, offered: Collection<Item>): PlayerTradeAdvertisementPayment.Plan? {
        val pricing = context.pricing ?: return null
        val held = quantities((bot.inventory.filterNotNull() + offered).filter { !it.isDynamic })
        val incoming = if (receiving.isEmpty()) {
            val definition = itemDef(pricing.itemId)
            mapOf(definition.notedId.orElse(pricing.itemId) to wanted)
        } else receiving
        val available = incoming.toSortedMap().mapNotNull { (id, amount) ->
            val limit = if (itemDef(id).isStackable) Int.MAX_VALUE - (held[id] ?: 0) else bot.inventory.capacity()
            minOf(amount, limit).takeIf { it > 0 }?.let { id to it }
        }.sortedByDescending { itemDef(it.first).isStackable }
        val stackable = available.filter { itemDef(it.first).isStackable }.sumOf { it.second.toLong() }
        var limit = minOf(wanted.toLong(), available.sumOf { it.second.toLong() }).toInt()
        repeat(bot.inventory.capacity() + 1) {
            val plan = PlayerTradeAdvertisementPayment.plan(concreteAdvertisement(context.offer, pricing.itemId),
                pricing.quote, pricing.maximumUnitPrice, pricing.profile, limit, funding, context.paymentPrices,
                if (context.unexpected) curiosityRemaining ?: 0 else Long.MAX_VALUE) ?: return null
            val projected = projectedAdvertisementInventory(context, plan.payment, offered) ?: return null
            var remaining = plan.quantity
            val delivery = available.mapNotNull { (id, amount) ->
                val take = minOf(amount, remaining)
                remaining -= take
                if (take > 0) Item(id, take) else null
            }
            if (remaining == 0 && projected.hasSpaceForAll(delivery)) return plan
            if (plan.quantity.toLong() <= stackable) return null
            limit = plan.quantity - 1
        }
        return null
    }

    /**
     * Projects the native standard inventory after the chosen physical payment without changing real containers.
     * Already-offered payment is included, so spending the rest of a stack correctly frees its occupied slot.
     */
    private fun projectedAdvertisementInventory(context: Opportunity, payment: Map<Int, Int>,
                                                offered: Collection<Item>): ItemContainer? {
        val selected = advertisementPhysicalPayment(context, payment, offered)
        if (selected.isEmpty()) return null
        val remaining = selected.toMutableMap()
        val projected = ItemContainer(bot.inventory.capacity(), StackPolicy.STANDARD, -1)
        for (item in bot.inventory.filterNotNull() + offered) {
            val consumed = if (item.isDynamic) 0 else minOf(item.amount, remaining[item.id] ?: 0)
            if (consumed > 0) remaining[item.id] = remaining.getValue(item.id) - consumed
            val amount = item.amount - consumed
            if (amount > 0) {
                val kept = if (amount == item.amount) item else item.withAmount(amount)
                if (!projected.hasSpaceFor(kept)) return null
                projected.add(kept)
            }
        }
        return projected.takeIf { remaining.values.none { it > 0 } }
    }

    /**
     * Maps a normalized advertised payment to currently held physical ids, including already-offered notes.
     */
    private fun advertisementPhysicalPayment(context: Opportunity, selected: Map<Int, Int>,
                                             offered: Collection<Item>): Map<Int, Int> {
        val remaining = selected.toMutableMap()
        val physical = currentAdvertisementFunding(context.allowance, offered)
        val result = linkedMapOf<Int, Int>()
        for ((id, available) in physical.toSortedMap()) {
            val base = unnotedId(id)
            val amount = minOf(available, remaining[base] ?: 0)
            if (amount > 0) {
                result[id] = amount
                remaining[base] = remaining.getValue(base) - amount
            }
        }
        return if (remaining.values.any { it > 0 }) emptyMap() else result
    }

    /**
     * Cancels only the script-owned advertised attempt when its marketplace visit is stopped or paused.
     */
    private fun cancelAdvertisementTrade() {
        advertisementTrade?.let {
            BotTradeStage(bot, it.entry.player).decline()
            it.market.release(it)
        }
        advertisementTrade = null
    }

    /**
     * Charges a completed delivery once, including a trade whose original advertisement expired while open.
     */
    private fun completeAdvertisement(context: Opportunity, plan: PlayerTradeAdvertisementPayment.Plan) {
        if (context.completed) return
        context.completed = true
        context.market.completed(context, plan.quantity)
        if (context.unexpected) curiosityRemaining = ((curiosityRemaining ?: 0) - plan.paidValue).coerceAtLeast(0)
    }

    /**
     * Negotiates one advertised commodity at its captured terms, requiring the entire received quantity to be paid.
     * The normal stage finisher still validates both participants and baskets on both acceptance screens.
     */
    private suspend fun negotiateAdvertisement(offer: BotTradeOfferStage, context: Opportunity): Boolean {
        val timer = Stopwatch.createStarted()
        val timing = BotTradeOfferTiming()
        val seen = HashMap<Pair<Map<Int, Int>, Map<Int, Int>>, Int>()
        var previous: Pair<Map<Int, Int>, Map<Int, Int>>? = null
        var rounds = 0
        while (timer.elapsed().seconds < BotTradeManager.OFFER_WAIT_SECONDS && rounds < MAX_NEGOTIATION_ROUNDS) {
            val screen = bot.overlays[OfferTradeInterface::class] ?: return false
            if (screen.other !== context.entry.player) return false
            if (!awaitPlayerOffer(screen, timer, timing)) continue
            val revision = screen.otherOffer.revision
            val mine = offer.getTradeItems()
            val theirs = offer.getOtherTradeItems()
            if (theirs.any { it.isDynamic || !it.itemDef.isTradeable }) return false
            val own = quantities(mine)
            val receiving = quantities(theirs)
            val state = own to receiving
            if (state != previous) {
                previous = state
                rounds++
                val visits = (seen[state] ?: 0) + 1
                seen[state] = visits
                if (visits > 2) return false
            }
            val plan = advertisementPlan(context, receiving, mine)
            if (plan == null && (context.pricing != null || receiving.isNotEmpty())) return false
            context.plan = plan
            if (!announceAdvertisement(context)) return false
            if (plan != null) {
                val maximum = if (receiving.isEmpty()) plan else advertisementMaximumPlan(context, receiving, mine)
                if (maximum != null) notifyAdvertisementQuantity(context, maximum)
            }
            val target = if (plan == null) emptyMap() else advertisementPhysicalPayment(context, plan.payment, mine)
            if (plan != null && target.isEmpty()) return false
            if (own != target) {
                if (!applyTarget(offer, target) { screen.otherOffer.revision == revision }) {
                    if (bot.overlays[OfferTradeInterface::class] === screen &&
                        screen.otherOffer.revision != revision) continue
                    return false
                }
                continue
            }
            val normalized = quantities(theirs, true)
            val id = context.pricing?.itemId
            val valid = plan != null && id != null && own.isNotEmpty() && normalized.size == 1 &&
                    normalized[id] == plan.quantity
            if (valid && finishTradeWith(offer, own, receiving) {
                    bot.inventory.hasSpaceForAll(theirs) && context.entry.player.inventory.hasSpaceForAll(mine)
                }) {
                val completed = plan ?: return false
                completeAdvertisement(context, completed)
                return true
            }
            if (bot.overlays[OfferTradeInterface::class] !== screen) return false
            waitFor(2.seconds) {
                bot.overlays[OfferTradeInterface::class] !== screen || quantities(offer.getTradeItems()) != own ||
                        quantities(offer.getOtherTradeItems()) != receiving
            }
        }
        bot.log("Advertised purchase expired or stopped making progress.")
        return false
    }

    /**
     * Applies complete target quantities and accepts only a stable, non-empty exchange.
     *
     * Bot partners share one deterministic plan. Human partners may change quantities during negotiation, so their
     * next target is always recomputed from this trip's original stock. Repeated states and the overall deadline
     * prevent cyclic or inactive trades from occupying the script indefinitely.
     */
    private suspend fun negotiate(offer: BotTradeOfferStage, other: Player,
                                  remembered: List<PlayerTradeAdvertisementMarket.Remembered> = emptyList(),
                                  onAdvertisement: (Opportunity) -> Unit = {}): Boolean {
        val partner = (other as? Bot)?.scriptStack?.current() as? TradeBotScript
        val exchange = if (other.isRealBot()) partner?.let { botExchange(it) } ?: return false else null
        val guidePrices = HashMap<Int, Long>()
        val timer = Stopwatch.createStarted()
        val timing = if (other.isRealBot()) null else BotTradeOfferTiming()
        val seen = HashMap<Pair<Map<Int, Int>, Map<Int, Int>>, Int>()
        var lastState: Pair<Map<Int, Int>, Map<Int, Int>>? = null
        var rounds = 0
        while (timer.elapsed().seconds < BotTradeManager.OFFER_WAIT_SECONDS && rounds < MAX_NEGOTIATION_ROUNDS) {
            val screen = bot.overlays[OfferTradeInterface::class] ?: return false
            if (screen.other !== other) return false
            if (timing != null && !awaitPlayerOffer(screen, timer, timing)) continue
            val revision = screen.otherOffer.revision
            val offered = offer.getTradeItems()
            val own = quantities(offered)
            val receiving = quantities(offer.getOtherTradeItems())
            if (remembered.isNotEmpty()) {
                val commodity = quantities(receiving.toItems(), true).keys.singleOrNull()
                val previousQuote = remembered.lastOrNull { it.pricing?.itemId == commodity }
                if (previousQuote != null) {
                    val context = advertisementCandidate(PlayerTradeAdvertisementMarket.live,
                        previousQuote.entry, previousQuote.offer, remembered = previousQuote, offered = offered,
                        receiving = receiving)
                        ?: return false
                    onAdvertisement(context)
                    context.response = context.market.registry.resume(context.entry, bot)
                    if (context.response == null && context.market.registry.find(other) === context.entry) {
                        waitFor(2.seconds) {
                            context.response = context.market.registry.resume(context.entry, bot)
                            context.response != null || context.market.registry.find(other) !== context.entry
                        }
                        if (context.response == null && context.market.registry.find(other) === context.entry) return false
                    }
                    return negotiateAdvertisement(offer, context)
                }
            }
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
                if (!applyTarget(offer, target) { timing == null || screen.otherOffer.revision == revision }) {
                    if (bot.overlays[OfferTradeInterface::class] === screen && timing != null &&
                        screen.otherOffer.revision != revision) continue
                    return false
                }
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
     * Waits for the player's initial opportunity or a quiet period after their latest native offer edit.
     * The enclosing negotiation retains its overall deadline while edits restart only this shorter wait.
     */
    private suspend fun awaitPlayerOffer(screen: OfferTradeInterface, timer: Stopwatch,
                                         timing: BotTradeOfferTiming): Boolean {
        if (timing.ready(screen.otherOffer.revision, timer.elapsed().toMillis())) return true
        waitFor(2.seconds) {
            bot.overlays[OfferTradeInterface::class] !== screen ||
                    timing.ready(screen.otherOffer.revision, timer.elapsed().toMillis())
        }
        return bot.overlays[OfferTradeInterface::class] === screen &&
                timing.ready(screen.otherOffer.revision, timer.elapsed().toMillis())
    }

    /**
     * Builds both sides of a bot exchange, bounded by stock, demand, and the buyer's payment basket.
     *
     * Sale goods use the seller's asking prices and barter payments use the buyer's surplus-sale quotes.
     * Normalized ids are used for matching; both resulting baskets use their actual carried note ids.
     */
    private fun botExchange(other: TradeBotScript): Pair<Map<Int, Int>, Map<Int, Int>>? {
        return planExchange(other, carriedOffer, other.carriedOffer)
    }

    /**
     * Uses the same unit quotes for projected ownership and actual prepared customer stock.
     */
    private fun planExchange(other: TradeBotScript, ownStock: Map<Int, Int>, otherStock: Map<Int, Int>):
            Pair<Map<Int, Int>, Map<Int, Int>>? {
        if (buying == other.buying) return null
        val buyer = if (buying) this else other
        val seller = if (buying) other else this
        val asks = seller.prices(seller.offering)
        val saleStock = if (seller === this) ownStock else otherStock
        val payingStock = if (buyer === this) ownStock else otherStock
        val stock = seller.quantities(saleStock.toItems(), true)
        val payment = buyer.quantities(payingStock.toItems(), true).toMutableMap()
        val paymentPrices = buyer.prices(buyer.offering).toMutableMap()
        if (seller.merchantSelling) {
            for (id in payment.keys.filter { it != COINS }) {
                val wanted = seller.personalDemand(id)
                paymentPrices[id] = if (wanted > 0) {
                    // One exchange uses a single unit price; surplus copies can be resold on a later visit.
                    payment[id] = minOf(payment.getValue(id), wanted)
                    seller.wantedBarterPrice(id)
                } else seller.resalePrice(id)
            }
        }
        fun select(demand: List<BotTradeItem>): Pair<Map<Int, Int>, Map<Int, Int>>? {
            val quantities = buyer.quantities(demand.map { it.item }, true).toMutableMap()
            val bids = buyer.prices(demand).toMutableMap()
            if (buyer.purpose == BotTradePurpose.MERCHANT_BUY) {
                for (id in quantities.keys.filter { it in buyer.actionableWants }) {
                    val wanted = buyer.personalDemand(id)
                    if (wanted > 0) quantities[id] = minOf(quantities.getValue(id), wanted)
                    else bids[id] = buyer.resalePrice(id)
                }
            }
            return TradeBasket.exchange(stock, quantities, asks, bids, payment, paymentPrices, seller.salePriority())
        }
        var selected = select(buyer.expected)
        if (selected == null && buyer.purpose == BotTradePurpose.MERCHANT_BUY) {
            selected = select(buyer.effectiveExpected(stock, payingStock))
        } else if (selected == null && buyer.purpose == BotTradePurpose.BUY && buyer.purchaseTarget == null) {
            val temptations = seller.offering.filter { it.item.id in stock && BotEquipmentPreferences.isCosmetic(it.item.id) }
                .sortedWith(compareByDescending<BotTradeItem> { BotEquipmentPreferences.prefers(buyer.bot, it.item.id) }
                    .thenByDescending { it.totalPrice() })
            for (temptation in temptations) {
                val quote = buyer.cosmeticQuote(temptation.item.id, payingStock) ?: continue
                selected = select(listOf(quote))
                if (selected != null) break
            }
        }
        val exchange = selected ?: return null
        val goods = seller.carriedQuantities(exchange.first, saleStock)
        val paid = buyer.carriedQuantities(exchange.second, payingStock)
        return if (buying) paid to goods else goods to paid
    }

    /** Computes the outgoing target for a human offer using stable per-attempt guide prices for barter. */
    private fun humanTarget(receiving: Map<Int, Int>, guidePrices: MutableMap<Int, Long>): Map<Int, Int> {
        if (buying) {
            val demand = effectiveExpected(receiving)
            val wanted = quantities(demand.map { it.item }, true)
            val received = quantities(receiving.toItems(), true)
            val useful = received.mapValues { (id, amount) -> minOf(amount, wanted[id] ?: 0) }
            val selected = if (receiving.isEmpty()) wanted else useful
            val unitPrices = prices(demand)
            val budget = if (purpose == BotTradePurpose.MERCHANT_BUY && receiving.isNotEmpty()) {
                val personal = selected.mapValues { (id, amount) -> minOf(amount, personalDemand(id)) }
                val resale = selected.mapValues { (id, amount) -> amount - (personal[id] ?: 0) }
                val normalValue = TradeBasket.value(personal, unitPrices)
                val resaleValue = TradeBasket.value(resale, resale.filterValues { it > 0 }.keys.associateWith {
                    if (it in actionableWants) resalePrice(it) else unitPrices[it] ?: 0L
                })
                if (normalValue > Long.MAX_VALUE - resaleValue) Long.MAX_VALUE else normalValue + resaleValue
            } else TradeBasket.value(selected, unitPrices)
            val payment = quantities(carriedOffer.toItems(), true)
            val paymentPrices = prices(offering)
            val coins = minOf(budget, (payment[COINS] ?: 0).toLong()).toInt()
            val offered = TradeBasket.select(payment.filterKeys { it != COINS }, paymentPrices, budget - coins)
                .toMutableMap()
            if (coins > 0) offered[COINS] = coins
            return carriedQuantities(offered)
        }
        val stock = humanSaleStock()
        if (receiving.isEmpty()) return stock
        val budget = receivedValue(receiving, guidePrices)
        return carriedQuantities(TradeBasket.select(quantities(stock.toItems(), true), prices(offering), budget,
            salePriority()))
    }

    /**
     * Keeps merchant sale priority consistent for both participants and for partial human purchases.
     */
    private fun salePriority(): List<Int> =
        if (merchantSelling) offering.map { it.item.id } else emptyList()

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
    private fun effectiveExpected(receiving: Map<Int, Int>, paymentStock: Map<Int, Int> = carriedOffer): List<BotTradeItem> {
        if (purchaseTarget != null || !buying) return expected
        val primary = expected.map { it.item.id }.toSet()
        val offered = quantities(receiving.toItems(), true).keys
        val alternatives = if (purpose == BotTradePurpose.MERCHANT_BUY) {
            offered.filter { it !in primary }.mapNotNull { merchantQuote(it, paymentStock) }
        } else {
            // Ordinary shoppers buy one commodity; extra goods cannot silently turn the trip into bulk buying.
            offered.singleOrNull()?.takeIf { it !in primary }?.let { cosmeticQuote(it, paymentStock) }?.let(::listOf).orEmpty()
        }
        return expected + alternatives
    }

    /**
     * Captures one affordable quote for a cosmetic the bot does not already own.
     */
    private fun cosmeticQuote(id: Int, paymentStock: Map<Int, Int> = carriedOffer): BotTradeItem? {
        if (purchaseTarget != null || !BotEquipmentPreferences.isCosmetic(id) ||
            !itemDef(id).isTradeable || bot.totalItemAmount(id) > 0) return null
        val guide = world.economy.getPrice(id)
        if (guide <= 0) return null
        val quote = cosmeticQuotes.getOrPut(id) {
            BotTradeItem(Item(id, 1), BotMerchantValuation.cosmeticBuy(bot, id, guide), BotTradePurpose.BUY, bot)
        }
        val wallet = TradeBasket.value(quantities(paymentStock.toItems(), true), prices(offering))
        return quote.takeIf { it.price <= wallet }
    }

    /**
     * A merchant's broad buying advertisement also covers tradeable stock without existing market history.
     */
    private fun merchantQuote(id: Int, paymentStock: Map<Int, Int> = carriedOffer): BotTradeItem? {
        if (id == COINS || !itemDef(id).isTradeable) return null
        val quote = merchantQuotes.getOrPut(id) {
            val price = merchantPriceSnapshot().buy(id, BotMerchantValuation.peekGuide(id),
                id in actionableWants, tradePrice)
            BotTradeItem(Item(id, Int.MAX_VALUE), price, BotTradePurpose.MERCHANT_BUY, bot)
        }
        val carried = quantities(paymentStock.toItems(), true)
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
     * Human offers can buy any carried sale stock, using the same priority as other exchanges.
     */
    private fun humanSaleStock(): Map<Int, Int> = carriedOffer

    /**
     * Values personal needs at normal buying quotes and surplus payment at resale quotes, with coins worth one coin.
     */
    private fun receivedValue(receiving: Map<Int, Int>, guidePrices: MutableMap<Int, Long>): Long {
        val normalized = quantities(receiving.toItems(), true)
        val useful = normalized.mapValues { (id, amount) -> minOf(amount, personalDemand(id)) }.filterValues { it > 0 }
        useful.keys.forEach(::wantedBarterPrice)
        for (id in normalized.keys) {
            guidePrices.getOrPut(id) {
                if (id == COINS) 1L
                else if (merchantSelling) resalePrice(id)
                else world.economy.getPrice(id).coerceAtLeast(0)
            }
        }
        val remaining = normalized.mapValues { (id, amount) -> amount - (useful[id] ?: 0) }
        val personal = TradeBasket.value(useful, wantedBarterPrices)
        val resale = TradeBasket.value(remaining, guidePrices)
        return if (personal > Long.MAX_VALUE - resale) Long.MAX_VALUE else personal + resale
    }

    /**
     * Caps personal-use pricing at the outstanding ownership target, including owned notes.
     */
    private fun personalDemand(id: Int): Int {
        if (id == COINS) return 0
        return actionableWants[id]?.let {
            (it.target.toLong() - bot.totalItemAmount(id)).coerceIn(0, Int.MAX_VALUE.toLong()).toInt()
        } ?: 0
    }

    /**
     * Keeps normal wanted-item quotes stable while the player revises quantities.
     */
    private fun wantedBarterPrice(id: Int): Long = wantedBarterPrices.getOrPut(id) {
        val guide = BotMerchantValuation.peekGuide(id)
        if (BotEquipmentPreferences.isCosmetic(id)) BotMerchantValuation.cosmeticBuy(bot, id, guide)
        else tradePrice.applyBuy(guide).coerceAtLeast(1)
    }

    /**
     * Applies the merchant's stable resale bid only to quantities without a personal use.
     */
    private fun resalePrice(id: Int): Long = barterPrices.getOrPut(id) {
        merchantPriceSnapshot().buy(id, BotMerchantValuation.peekGuide(id))
    }

    /** Returns selected normalized quantities using the exact physical ids carried for the current trip. */
    private fun carriedQuantities(selected: Map<Int, Int>, stock: Map<Int, Int> = carriedOffer): Map<Int, Int> {
        val remaining = selected.toMutableMap()
        val result = linkedMapOf<Int, Int>()
        val carried = if (merchantSelling) stock else stock.toSortedMap()
        for ((id, amount) in carried) {
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
    private suspend fun applyTarget(offer: BotTradeOfferStage, target: Map<Int, Int>,
                                    validate: () -> Boolean = { true }): Boolean {
        for ((id, amount) in quantities(offer.getTradeItems())) {
            if (!validate()) return false
            val remove = amount - (target[id] ?: 0)
            if (remove > 0 && !offer.remove(Item(id, remove))) return false
        }
        for ((id, amount) in target) {
            if (!validate()) return false
            val add = amount - (quantities(offer.getTradeItems())[id] ?: 0)
            if (add > 0 && !offer.offer(Item(id, add))) return false
        }
        return validate() && quantities(offer.getTradeItems()) == target
    }

    /** Validates the original participants and both accepted baskets before each acceptance click. */
    private suspend fun finishTradeWith(offer: BotTradeOfferStage, own: Map<Int, Int>, receiving: Map<Int, Int>,
                                        validate: () -> Boolean = { true }): Boolean {
        val original = bot.overlays[OfferTradeInterface::class] ?: return false
        val ownRevision = original.revision
        val otherRevision = original.otherOffer.revision
        fun unchanged() = original.revision == ownRevision && original.otherOffer.revision == otherRevision && validate()
        val confirm = offer.offerAndAwait {
            quantities(offer.getTradeItems()) == own && quantities(offer.getOtherTradeItems()) == receiving && unchanged()
        } ?: return false
        val screen = bot.overlays[ConfirmTradeInterface::class] ?: return false
        val otherScreen = offer.other.overlays[ConfirmTradeInterface::class] ?: return false
        if (screen.offer.other !== offer.other || otherScreen.offer.other !== bot ||
            quantities(screen.offer.items.filterNotNull()) != own ||
            quantities(otherScreen.offer.items.filterNotNull()) != receiving || !unchanged()) return false
        return confirm.confirmAndAwait()
    }

    /**
     * Rejects unavailable and same-purpose partners before calculating any trade quantities or prices.
     */
    private fun eligibleTradePartner(other: Player): Boolean {
        if (other === bot || other.state != EntityState.ACTIVE || checkBlacklisted(other.username)) return false
        val existing = other.overlays[OfferTradeInterface::class]
        if (other.overlays.hasWindow() && existing?.other !== bot) return false
        if (!other.isRealBot()) return true
        val script = other.scriptStack.current() as? TradeBotScript ?: return false
        if (script.negotiating && other.tradingWith != bot.index && existing?.other !== bot) return false
        if (other.tradingWith != -1 && other.tradingWith != bot.index && existing?.other !== bot) return false
        return prepared && script.prepared && !forceBanking && !script.forceBanking && buying != script.buying
    }

    /**
     * Matches complementary purposes using fresh quantities and the existing exchange calculation.
     */
    private fun canTradeWith(other: Player): Boolean {
        if (!eligibleTradePartner(other)) return false
        if (!other.isRealBot()) return true
        return botExchange(other.scriptStack.current() as TradeBotScript) != null
    }

    /**
     * Accepts only requests still addressed to this bot, including a reciprocal request that just opened a trade.
     */
    private fun canAcceptRequest(other: Player): Boolean =
        (other.tradingWith == bot.index || other.overlays[OfferTradeInterface::class]?.other === bot) &&
            canTradeWith(other)

    /**
     * Lets a waiting bot request yield to a live player request without consuming that player's place in the queue.
     */
    private fun hasHumanRequest(): Boolean = bot.tradeRequests.hasRequest { !it.isRealBot() && canAcceptRequest(it) }

    /** Exposes the selected scrutiny policy for future personality-specific negotiation behavior. */
    fun resolveScrutiny(other: Player): BotTradeScrutiny = BotTradeScrutiny.resolveScrutinyFor(bot, other)

    override fun onPaused() {
        partnerSearch = null
        cancelAdvertisementTrade()
        prepared = false
        marketplace.releaseTradingSpot(this)
        browsing = false
        browsingMarket = null
        BotSocialInteractions.cancel(bot)
        BotConversations.cancel(bot)
    }

    override suspend fun onBankRequested(initial: Boolean): Boolean {
        if (purchaseExpired()) {
            endSession("Specific purchase deadline expired.")
            return false
        }
        partnerSearch = null
        if (!initial) {
            marketplace.releaseTradingSpot(this)
            browsingMarket = null
        }
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
        partnerSearch = null
        prepared = false
        marketplace.releaseTradingSpot(this)
        browsingMarket = null
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
