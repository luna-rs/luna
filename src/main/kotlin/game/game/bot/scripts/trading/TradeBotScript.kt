package game.bot.scripts.trading

import api.bot.isRealBot
import engine.controllers.Controllers.inWilderness

import api.bot.Suspendable.delay
import api.bot.Suspendable.waitFor
import api.bot.action.trading.*
import api.bot.action.trading.BotTradeManager.tradeRequests
import api.bot.action.trading.PlayerTradeAdvertisementMarket.Opportunity
import api.bot.action.trading.PlayerTradeAdvertisementMarket.Pricing
import api.bot.action.trading.PlayerTradeAdvertisementPolicy.Profile
import api.bot.script.BotScriptData
import api.bot.script.BotScript
import api.bot.script.ZonedBotScript
import api.bot.social.BotConversations
import api.bot.social.BotSocialInteractions
import api.bot.social.PlayerTradeScams
import api.bot.social.SocialBotScript
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
import kotlin.time.Duration.Companion.milliseconds
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
 * Human sellers receive only carried coins for affordable wanted quantities. Bots offer items to a human only
 * for the exact commodity in that player's buying advertisement; those buyers may pay with coins or items.
 * Scammer accounts may occasionally pause idle trading for a separately guarded misleading sale to a human.
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
 * @property retryAt Earliest retry after deferring a visit, independent of hub capacity or intervening work.
 * @author lare96
 */
class TradeBotScript(bot: Bot,
                     private val purpose: BotTradePurpose,
                     private val marketplace: BotMarketplace = BotMarketplace.VARROCK,
                     duration: Duration = getPreferenceBasedDuration(bot),
                     zone: MutableList<SubZone> = mutableListOf(marketplace.activityZone),
                     private val purchaseTarget: Item? = null,
                     private val purchasePrice: Long = 0,
                     private val purchaseDeadline: Long = 0L,
                     private val retryAt: Long = 0L,
                     private val reactiveSeller: Player? = null,
                     private val reactiveBuyer: Player? = null,
                     private val reactivePrevious: BotScript? = null) :
    ZonedBotScript(bot, duration, zone), SocialBotScript {

    companion object {

        /**
         * Previews one nearby player-sale visit using only coins already carried. The marketplace activates only
         * its selected buyer, leaving other listeners' activities and bank balances untouched.
         */
        internal fun playerSaleBuyer(bot: Bot, seller: Player): TradeBotScript? {
            val previous = bot.scriptStack.current()
            if (previous is TradeBotScript || !reactiveReady(bot, seller)) return null
            val script = TradeBotScript(bot,
                if (bot.preferences.isMerchant) BotTradePurpose.MERCHANT_BUY else BotTradePurpose.BUY,
                duration = 2.minutes, purchaseDeadline = System.currentTimeMillis() + 120_000L,
                reactiveSeller = seller, reactivePrevious = previous)
            if (!script.prepareReactiveFunds()) return null
            return script
        }

        /**
         * Previews only requested surplus from a listener's inventory and bank. Selection and response ownership
         * precede all movement; ordinary trading trips may pause to supply stock outside their prepared basket.
         */
        internal fun playerPurchaseSeller(bot: Bot, buyer: Player,
                                          offers: List<PlayerTradeAdvertisement>): TradeBotScript? {
            val owned = offers.filter { offer ->
                if (offer.direction != PlayerTradeAdvertisement.Direction.BUY || offer.requiresInspection) false else {
                    val id = offer.itemIds.singleOrNull()
                    val definition = id?.let { ItemDefinition.ALL.get(it).orElse(null) }
                    val note = definition?.notedId?.orElse(-1) ?: -1
                    id != null && id > 0 && id != COINS && definition?.isTradeable == true &&
                            (bot.itemTracker.count(id) > 0 || note >= 0 && bot.itemTracker.count(note) > 0)
                }
            }
            if (owned.isEmpty()) return null
            val previous = bot.scriptStack.current()
            if (!reactiveReady(bot, buyer) || previous is TradeBotScript &&
                (previous.reactivePlayer != null || previous.negotiating || !previous.prepared)) return null
            if (!PlayerTradeAdvertisementMessages.allowsTrade(bot, buyer) ||
                buyer.usernameHash in bot.ignores || bot.usernameHash in buyer.ignores) return null
            val requested = owned.filter { PlayerTradeSaleStock.available(bot, it.itemIds.single()) > 0 }
            if (requested.isEmpty()) return null
            val script = TradeBotScript(bot, BotTradePurpose.SELL,
                duration = 2.minutes, purchaseDeadline = System.currentTimeMillis() + 120_000L,
                reactiveBuyer = buyer, reactivePrevious = previous)
            script.tradePrice = script.resolveTradePrice()
            return script.takeIf { it.prepareReactiveSaleStock(requested) }
        }

        /**
         * Rejects busy listeners before resolving ownership goals, item valuations or trade-screen plans.
         */
        private fun reactiveReady(bot: Bot, seller: Player): Boolean =
            bot.isRealBot() && !seller.isRealBot() && bot.state == EntityState.ACTIVE &&
                seller.state == EntityState.ACTIVE && ActionSafety.NON_COMBAT.allows(bot) &&
                bot.combat.target == null && bot.reflex.activeReflex == null && !bot.inWilderness() &&
                bot.tradingWith == -1 && !bot.overlays.hasWindow() && !seller.overlays.hasWindow() &&
                !BotSocialInteractions.engaged(bot) && !BotConversations.engaged(bot) &&
                bot.walking.isEmpty && !bot.navigator.isActive &&
                bot.position.isWithinDistance(seller.position, Position.VIEWING_DISTANCE) &&
                bot.isViewableFrom(seller) && seller.isViewableFrom(bot)

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
            val price = BotMarketExperience.buy(bot, id,
                BotTradePrice.computePriceStrategy(bot).applyBuy(guide),
                BotTradeIntentions.productionLimit(bot, id)?.maximumUnitPrice ?: Long.MAX_VALUE)
            for (market in BotMarketplace.entries) {
                val script = TradeBotScript(bot, BotTradePurpose.BUY, market, 10.minutes,
                    purchaseTarget = Item(id, target), purchasePrice = price, purchaseDeadline = deadlineMillis)
                if (script.specificDemand().isEmpty()) return null
                val payment = BotTradeManager.computeBudgetedOfferItems(bot, script.resolveTradePrice(), BotTradePurpose.BUY)
                    .filter { it.item.id == COINS }
                if (TradeBasket.value(script.quantities(payment.map { it.item }, true), script.prices(payment)) <
                    BotTradeManager.MINIMUM_OFFER_VALUE) return null
                val sellers = market.tradingSessions().filterIsInstance<TradeBotScript>()
                    .filter { script.availableAtMarket(it) }
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

    private val marketExperience = BotMarketExperience.Visit()
    private var completedExposure: BotMarketExperience.Failure? = null
    private var nextExperienceSample = 0L
    private val productionQuotedIds = hashSetOf<Int>()
    private val productionQuoteExpiries = hashMapOf<Int, Long>()
    private val productionQuoteOwners = hashMapOf<Int, Any>()

    /** The offer stage owned by this script, retained for cancellation cleanup. */
    private var activeTrade: BotTradeOfferStage? = null

    /**
     * Includes the outstanding request and approach before an offer screen exists.
     */
    private var negotiating = false
    private var nextScamConsideration = System.currentTimeMillis() + (30_000L..90_000L).random()
    private var consideringPlayerScam = false
    private var playerScamActive = false

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
     * The reserved coin purse prepared separately from sale stock, already reduced by the spending reserve.
     */
    private var sellerCashBudget: Int? = null

    /**
     * A claimed one-shot player sale, held while the interrupted activity completes its cancellation cleanup.
     */
    private var reactiveOpportunity: Opportunity? = null

    /**
     * Bank stock is prospective until the selected seller has physically fetched it and returned.
     */
    private var reactiveSalePreparing = reactiveBuyer != null
    private val reactiveCarriedReserves = HashMap<Int, Map<Int, Int>>()
    private val reactivePurchaseOrigin = reactiveBuyer?.position
    private val reactivePlayer: Player?
        get() = reactiveSeller ?: reactiveBuyer

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
     * Stable unit prices for opportunistic wanted goods, with quantities rechecked against live ownership.
     */
    private val ordinaryQuotes = HashMap<Int, Long>()

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
        val customer = reactivePlayer
        if (customer != null) {
            if (reactivePrevious?.isPassive() == false &&
                !waitFor(2.seconds) { reactivePrevious.isPassive() }) return false
            val opportunity = reactiveOpportunity ?: return false
            if (!reactiveReady(bot, customer) || purchaseExpired() || reactiveBuyer != null &&
                opportunity.response?.let(opportunity.market.registry::isCurrent) != true) return false
            bot.actions.interruptWeak()
            BotSocialInteractions.cancel(bot)
            BotConversations.cancel(bot)
            bot.speechStack.suppressGeneral(this)
            if (reactiveBuyer != null) {
                PlayerTradeSaleStock.refresh(bot)
                return prepareReactiveSaleStock(listOf(opportunity.offer))
            }
            return prepareReactiveFunds()
        }
        val cooldown = retryAt - System.currentTimeMillis()
        if (cooldown > 0L) delay(cooldown.milliseconds)
        if (!awaitActionSafety()) return false
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

    override suspend fun awaitActionSafety(safety: ActionSafety): Boolean =
        if (reactivePlayer == null) super.awaitActionSafety(safety)
        else withinPurchaseDeadline { super.awaitActionSafety(safety) } ?: false

    /**
     * Binds an already selected response before pausing the previous activity. A rejected preview sends no request
     * and changes no script state. The game thread owns selection, claim and activation without suspension.
     */
    internal fun beginPlayerSale(opportunity: Opportunity): Boolean {
        val seller = reactiveSeller ?: return false
        if (opportunity.script !== this || opportunity.entry.player !== seller || opportunity.response == null ||
            reactiveOpportunity != null || bot.scriptStack.current() !== reactivePrevious ||
            !reactiveReady(bot, seller)) return false
        val current = advertisementCandidate(opportunity.market, opportunity.entry, opportunity.offer) ?: return false
        opportunity.plan = current.plan
        reactiveOpportunity = opportunity
        bot.scriptStack.pushHead(this)
        return bot.scriptStack.current() === this
    }

    /**
     * Claims one requested commodity before interrupting an activity or travelling to retrieve its surplus.
     */
    internal fun beginPlayerPurchase(opportunity: Opportunity): Boolean {
        val buyer = reactiveBuyer ?: return false
        if (opportunity.script !== this || opportunity.entry.player !== buyer || opportunity.response == null ||
            opportunity.offer.direction != PlayerTradeAdvertisement.Direction.BUY || reactiveOpportunity != null ||
            bot.scriptStack.current() !== reactivePrevious || !reactiveReady(bot, buyer) ||
            !opportunity.market.registry.isCurrent(opportunity.response!!)) return false
        PlayerTradeSaleStock.refresh(bot)
        if (!prepareReactiveSaleStock(listOf(opportunity.offer))) return false
        val current = advertisementCandidate(opportunity.market, opportunity.entry, opportunity.offer) ?: return false
        opportunity.plan = current.plan
        reactiveOpportunity = opportunity
        bot.scriptStack.pushHead(this)
        return bot.scriptStack.current() === this
    }

    /**
     * Resolves a bounded requested basket without applying ordinary merchant batch or popularity filters.
     * Prospective bank quantities can participate in selection but never enter a native offer screen.
     */
    private fun prepareReactiveSaleStock(offers: List<PlayerTradeAdvertisement>): Boolean {
        val quotes = ArrayList<BotTradeItem>()
        val stock = linkedMapOf<Int, Int>()
        for (offer in offers.distinctBy { it.itemIds }) {
            val id = offer.itemIds.singleOrNull() ?: continue
            val amount = minOf(PlayerTradeSaleStock.available(bot, id), offer.quantity ?: Int.MAX_VALUE)
            if (amount <= 0) continue
            var reserve = PlayerTradeSaleStock.carriedReserve(bot, id)
            reactiveCarriedReserves[id] = PlayerTradeSaleStock.physical(bot, id).mapNotNull { (physicalId, held) ->
                val count = minOf(reserve, held)
                reserve -= count
                if (count > 0) physicalId to count else null
            }.toMap()
            if (reactiveSaleStock(id).isEmpty() && bot.inventory.computeRemainingSize() <= 1) continue
            val price = offering.firstOrNull { it.item.id == id }?.price ?: run {
                val guide = BotMerchantValuation.peekGuide(id)
                if (merchantSelling) merchantPriceSnapshot().sell(id, guide, tradePrice)
                else BotMarketExperience.sell(bot, id, tradePrice.applySell(guide))
            }
            if (price <= 0) continue
            quotes += BotTradeItem(Item(id, amount), price, purpose, bot)
            var remaining = amount
            for ((physicalId, held) in reactiveSaleStock(id, includeBank = true)) {
                val count = minOf(remaining, held)
                if (count > 0) stock[physicalId] = count
                remaining -= count
            }
        }
        offering = quotes
        carriedOffer = stock
        expected = emptyList()
        prepared = stock.isNotEmpty()
        forceBanking = false
        return prepared
    }

    /**
     * Keeps the activity's original carried reserve in place even when an equivalent copy is banked.
     * Newly fetched surplus does not replace a tool or supply stack needed when the parent resumes.
     */
    private fun reactiveSaleStock(id: Int, includeBank: Boolean = false): Map<Int, Int> =
        PlayerTradeSaleStock.physical(bot, id, includeBank).mapNotNull { (physicalId, held) ->
            val amount = (held - (reactiveCarriedReserves[id]?.get(physicalId) ?: 0)).coerceAtLeast(0)
            if (amount > 0) physicalId to amount else null
        }.toMap()

    /**
     * Fetches only missing goods, preserving the paused activity's tools and inventory. A returned physical
     * opportunity replaces the preview while retaining its claim and stable price. Every suspended step rechecks
     * the buyer's session, original neighbourhood, advertisement and the seller's current surplus.
     */
    private suspend fun preparePlayerPurchaseStock(context: Opportunity): Opportunity? {
        val buyer = reactiveBuyer ?: return null
        val id = context.offer.itemIds.singleOrNull() ?: return null
        fun current(): Boolean = playerPurchaseCurrent(context)
        if (!current()) return null
        PlayerTradeSaleStock.refresh(bot)
        val desired = minOf(PlayerTradeSaleStock.available(bot, id), context.plan?.quantity ?: 0,
            context.entry.remainingQuantity(context.offer) ?: Int.MAX_VALUE)
        if (desired <= 0) return null
        var carried = reactiveSaleStock(id).values.sumOf { it.toLong() }
        if (carried < desired) {
            if (bot.inventory.computeRemainingSize() <= 1) return null
            val name = itemName(id)
            PlayerTradeAdvertisementMessages.counteroffer(bot, buyer, "I'll fetch $name from my bank.")
            previousBankMode = bot.varpManager.getValue(PersistentVarp.WITHDRAW_AS_NOTE)
            if (!handler.banking.travelToBankOpen() || !current()) return null
            handler.banking.clickBankingMode(true)
            PlayerTradeSaleStock.refresh(bot)
            val physicalIds = listOf(id, itemDef(id).notedId.orElse(-1)).filter { it >= 0 }
            for (physicalId in physicalIds) {
                if (!current()) return null
                if (bot.bank.any { it != null && it.id == physicalId && it.isDynamic }) continue
                val safe = minOf(desired, PlayerTradeSaleStock.available(bot, id))
                carried = reactiveSaleStock(id).values.sumOf { it.toLong() }
                val missing = (safe.toLong() - carried).coerceAtLeast(0).toInt()
                if (missing <= 0) break
                val definition = itemDef(physicalId)
                val capacity = if (definition.isStackable || definition.notedId.isPresent)
                    if (bot.inventory.computeRemainingSize() > 1 ||
                        bot.inventory.computeAmountForId(definition.notedId.orElse(physicalId)) > 0) Int.MAX_VALUE else 0
                else (bot.inventory.computeRemainingSize() - 1).coerceAtLeast(0)
                val amount = minOf(missing, bot.bank.computeAmountForId(physicalId), capacity)
                if (amount > 0 && !handler.banking.withdraw(Item(physicalId, amount))) return null
                if (!current()) return null
            }
            bot.overlays.closeWindows()
            if (!current() || !handler.webWalkTo(buyer.position, radius = 1) || !current()) return null
        }
        PlayerTradeSaleStock.refresh(bot)
        val amount = minOf(desired, PlayerTradeSaleStock.available(bot, id))
        if (amount <= 0) return null
        val quote = context.pricing ?: return null
        offering = listOf(BotTradeItem(Item(id, amount), quote.maximumUnitPrice, purpose, bot))
        var remaining = amount
        carriedOffer = reactiveSaleStock(id).mapNotNull { (physicalId, held) ->
            val count = minOf(remaining, held)
            remaining -= count
            if (count > 0) physicalId to count else null
        }.toMap()
        reactiveSalePreparing = false
        prepared = carriedOffer.isNotEmpty()
        val physical = advertisementCandidate(context.market, context.entry, context.offer,
            remembered = PlayerTradeAdvertisementMarket.Remembered(context.entry, context.offer, quote,
                context.counterSent)) ?: return null
        physical.response = context.response
        physical.communicated = context.communicated
        reactiveOpportunity = physical
        return physical
    }

    private fun playerPurchaseCurrent(context: Opportunity): Boolean {
        val buyer = reactiveBuyer ?: return false
        val origin = reactivePurchaseOrigin ?: return false
        return !purchaseExpired() && context.response?.let(context.market.registry::isCurrent) == true &&
                bot.scriptStack.current() === this && bot.state == EntityState.ACTIVE && bot.isAlive &&
                !bot.isLocked && bot.combat.target == null && bot.reflex.activeReflex == null &&
                !bot.inWilderness() && buyer.state == EntityState.ACTIVE && buyer.isAlive &&
                world.getPlayer(buyer.usernameHash).orElse(null) === buyer && !buyer.inWilderness() &&
                buyer.position.isWithinDistance(origin, Position.VIEWING_DISTANCE) &&
                !buyer.overlays.hasWindow() && (buyer.tradingWith == -1 || buyer.tradingWith == bot.index) &&
                buyer.usernameHash !in bot.ignores && bot.usernameHash !in buyer.ignores &&
                PlayerTradeAdvertisementMessages.allowsTrade(bot, buyer)
    }

    /**
     * Captures the carried-cash reserve without touching the bank or advertising the listener's current activity.
     */
    private fun prepareReactiveFunds(): Boolean {
        val held = bot.inventory.filterNotNull().asSequence().filter { it.id == COINS && !it.isDynamic }
            .sumOf { it.amount.toLong() }.coerceAtMost(Int.MAX_VALUE.toLong())
        val profile = advertisementProfile
        val fraction = BotTradeIntentions.spendingFraction(profile.intelligence, profile.greedy, profile.merchant)
        val budget = BigDecimal.valueOf(held).multiply(BigDecimal.valueOf(fraction))
            .setScale(0, RoundingMode.FLOOR).toInt()
        if (budget <= 0) return false
        carriedOffer = mapOf(COINS to budget)
        offering = listOf(BotTradeItem(Item(COINS, budget), 1L, purpose, bot))
        expected = emptyList()
        advertisementFunding = carriedOffer
        curiosityRemaining = budget.toLong() / 10
        tradePrice = resolveTradePrice()
        prepared = true
        forceBanking = false
        return true
    }

    override suspend fun onBankOpen(initial: Boolean) {
        withinPurchaseDeadline { prepareTradeInventory() }
    }

    private suspend fun prepareTradeInventory() {
        partnerSearch = null
        prepared = false
        if (!computeOfferAndExpected()) return
        handler.banking.clickBankingMode(true)
        val stackLimit = if (merchantSelling) Luna.settings().bots().merchantSaleStackLimit() else Int.MAX_VALUE
        val reservedSlots = if (buying) 1 else 2
        for (entry in offering) {
            if (bot.inventory.computeRemainingSize() <= reservedSlots || bot.inventory.size() >= stackLimit) break
            val id = entry.item.id
            // Bank.withdraw selects by id. Skip mixed stacks as well to avoid withdrawing dynamic instances.
            if (bot.bank.any { it != null && it.id == id && it.isDynamic }) continue
            val definition = itemDef(id)
            val capacity = if (definition.isStackable || definition.notedId.isPresent) Int.MAX_VALUE
            else (bot.inventory.computeRemainingSize() - reservedSlots).coerceAtLeast(0)
            val transferable = minOf(entry.item.amount, bot.bank.computeAmountForId(id), capacity)
            if (transferable > 0) handler.banking.withdraw(Item(id, transferable))
        }
        prepareSellerCash()
        if (!refreshCarriedStock()) {
            endSession("No useful trade stock could be prepared from the bank.")
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

    /**
     * Carries a limited buying purse on a seller's normal bank trip, independently of the advertised sale stock.
     * Hearing a player's sale never initiates banking, and failed withdrawals cannot qualify as carried payment.
     */
    private suspend fun prepareSellerCash() {
        sellerCashBudget = null
        if (buying) return
        sellerCashBudget = 0
        if (bot.bank.any { it != null && it.id == COINS && it.isDynamic }) return
        val profile = advertisementProfile
        val fraction = BotTradeIntentions.spendingFraction(profile.intelligence, profile.greedy, profile.merchant)
        val available = BotTradeManager.transferableAmount(bot, COINS)
        val budget = BigDecimal.valueOf(available.toLong()).multiply(BigDecimal.valueOf(fraction))
            .setScale(0, RoundingMode.FLOOR).toInt()
        sellerCashBudget = budget
        val held = bot.inventory.filterNotNull().filter { it.id == COINS && !it.isDynamic }
            .sumOf { it.amount.toLong() }.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        val missing = (budget - held).coerceAtLeast(0)
        if (missing > 0 && bot.inventory.computeRemainingSize() > 1) {
            handler.banking.withdraw(Item(COINS, missing))
        }
    }

    /**
     * Refreshes tradeable carried stock and affordable wants without requiring another bank visit.
     */
    private fun refreshCarriedStock(): Boolean {
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
                return false
            }
        }
        if (stock.isEmpty()) {
            return false
        }
        return true
    }

    override suspend fun finish() {
        val quote = marketExperienceQuote()
        completedExposure = if (quote != null && marketExperienceReady() && marketExperienceStillUseful(quote))
            marketExperience.unsuccessful(quote.item.id, buying, quote.price) else null
        partnerSearch = null
        BotSocialInteractions.cancel(bot)
        BotConversations.cancel(bot)
        prepared = false
        marketplace.releaseTradingSpot(this)
        browsingMarket = null
        activeTrade?.decline()
        activeTrade = null
        cancelAdvertisementTrade()
        reactiveOpportunity?.let { it.market.release(it) }
        reactiveOpportunity = null
        bot.tradingWith = -1
        previousBankMode?.let { bot.varpManager.setValue(PersistentVarp.WITHDRAW_AS_NOTE, it) }
        if (reactiveBuyer != null && previousBankMode != null) bot.overlays.closeWindows()
        previousBankMode = null
        bot.speechStack.releaseGeneral(this)
    }

    override suspend fun completed() {
        if (reactivePlayer != null) return
        completedExposure?.let { BotMarketExperience.unsuccessful(bot, it) }
        completedExposure = null
        if (capacityBlocked) {
            if (purchaseDeadline > 0L) return
            val next = BotMarketplace.select(bot)
            val remaining = retryDuration ?: remainingDuration()
            if (remaining > Duration.ZERO) {
                bot.scriptStack.pushTail(TradeBotScript(bot, purpose, next, remaining,
                    purchaseTarget = purchaseTarget, purchasePrice = purchasePrice,
                    purchaseDeadline = purchaseDeadline,
                    retryAt = System.currentTimeMillis() + rand(2, 5) * 60_000L))
            }
            return
        }
        if (purchaseTarget == null && !traded && !bot.preferences.isMerchant) {
            if (buying) bot.scriptStack.push(BuyFromStoreBotScript(bot))
            else bot.scriptStack.push(SellToStoreBotScript(bot))
        }
    }

    override suspend fun executeInZone(): Boolean = withinPurchaseDeadline { executeTradeInZone() } ?: false

    override fun canSocialize(): Boolean = consideringPlayerScam && canAttemptPlayerScam()

    /**
     * Tests idle scammer availability; social recruitment is exposed only during its own player-scam consideration.
     */
    internal fun canAttemptPlayerScam(): Boolean = bot.preferences.isScammer && reactivePlayer == null &&
        purchaseTarget == null && !browsing && prepared && !forceBanking && !negotiating && pendingPartner == null &&
        activeTrade == null && advertisementTrade == null && !reactiveSalePreparing &&
        marketplace.tradingSpot(this) == bot.position && bot.tradingWith == -1 && !bot.overlays.hasWindow() &&
        bot.walking.isEmpty && !bot.navigator.isActive && !bot.tradeRequests.hasRequest { true }

    override suspend fun beforeZoneWork(): Boolean {
        if (BotSocialInteractions.participate(bot)) {
            if (!BotSocialInteractions.engaged(bot)) refreshSocialStock()
            return true
        }
        if (playerScamActive) refreshSocialStock()
        if (BotConversations.engaged(bot)) {
            BotConversations.tick(bot)
            return true
        }
        if (reactiveBuyer != null) {
            try {
                val opportunity = reactiveOpportunity
                if (opportunity != null) withinPurchaseDeadline {
                    val physical = preparePlayerPurchaseStock(opportunity)
                    if (physical != null) attemptTrade(reactiveBuyer, physical)
                }
            } finally {
                endSession("Nearby player-purchase visit completed.")
            }
            return true
        }
        if (reactiveSeller != null) {
            try {
                val opportunity = reactiveOpportunity
                if (opportunity != null && opportunity.response?.let(opportunity.market.registry::isCurrent) == true &&
                    advertisementReady(reactiveSeller)) {
                    withinPurchaseDeadline { attemptTrade(reactiveSeller, opportunity) }
                }
            } finally {
                endSession("Nearby player-sale visit completed.")
            }
            return true
        }
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
        sampleMarketExperience()
        if (!prepared) {
            forceBanking = true
            return true
        }
        if (considerPlayerScam()) return true
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
     * Gives idle scammer merchants one rare opportunity per consideration interval, using carried stock only.
     */
    private fun considerPlayerScam(): Boolean {
        val now = System.currentTimeMillis()
        if (now < nextScamConsideration) return false
        nextScamConsideration = now + (30_000L..90_000L).random()
        if (!canAttemptPlayerScam() || PlayerTradeAdvertisementMarket.live.hasPriority(this)) return false
        val player = world.locator.findViewablePlayers(bot) {
            !it.isRealBot() && it.isWithinDistance(bot, 8)
        }.toList().randomOrNull() ?: return false
        consideringPlayerScam = true
        val started = try {
            PlayerTradeScams.approach(bot, player)
        } finally {
            consideringPlayerScam = false
        }
        if (!started) return false
        playerScamActive = true
        partnerSearch = null
        marketExperience.pause()
        return true
    }

    /**
     * Rebuilds ordinary trading quantities after a social exchange or a cancelled player-scam attempt.
     */
    private fun refreshSocialStock() {
        if (!playerScamActive) return
        playerScamActive = false
        partnerSearch = null
        marketExperience.clear()
        prepared = false
        prepared = reactivePlayer == null && computeOfferAndExpected() && refreshCarriedStock()
        forceBanking = !prepared || bot.inventory.computeRemainingSize() <= 1
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
        BotMarketplace.entries.flatMap { it.tradingSessions() }.filterIsInstance<TradeBotScript>()
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
            productionPurchaseQuote(BotTradeItem(Item(target.id, missing), purchasePrice, BotTradePurpose.BUY, bot))
                ?.let(::listOf).orEmpty()
    }

    /** Refreshes intentions from current ownership and ends the session normally when nothing useful remains. */
    private fun computeOfferAndExpected(): Boolean {
        marketExperience.clear()
        val specificOwner = purchaseTarget?.id?.let { productionQuoteOwners[it] }
        val specificExpiry = purchaseTarget?.id?.let { productionQuoteExpiries[it] }
        productionQuotedIds.clear()
        productionQuoteExpiries.clear()
        productionQuoteOwners.clear()
        if (purchaseTarget != null && specificOwner != null && specificExpiry != null) {
            productionQuotedIds += purchaseTarget.id
            productionQuoteOwners[purchaseTarget.id] = specificOwner
            productionQuoteExpiries[purchaseTarget.id] = specificExpiry
        }
        bot.preferences.rebalanceWantedItems()
        actionableWants = BotTradeIntentions.purchases(bot).associateBy { it.id }
        val strategy = resolveTradePrice()
        tradePrice = strategy
        val offers = BotTradeManager.computeBudgetedOfferItems(bot, strategy, purpose)
            .filter { purchaseTarget == null || it.item.id == COINS }
        val wants = if (purchaseTarget == null) BotTradeManager.computeExpected(bot, strategy, purpose, offers)
        else BotTradeManager.affordablePurchases(specificDemand(), offers)
        val traders = (if (browsing) BotMarketplace.entries else listOf(marketplace))
            .flatMap { it.tradingSessions() }.filterIsInstance<TradeBotScript>().filter { availableAtMarket(it) }
        val candidates = if (buying) wants else offers
        // Preserve the buyer's budget allocation order; sellers favor stock with a current market match.
        val selected = if (purpose == BotTradePurpose.SELL && !merchantSelling || browsing && purpose == BotTradePurpose.BUY) {
            prioritizeMarketItems(candidates, offers, traders).take(1)
        } else candidates
        offering = if (buying) offers else selected
        expected = if (purpose == BotTradePurpose.BUY) selected.take(1) else if (buying) selected
        else BotTradeManager.computeExpected(bot, strategy, purpose, offering)
        if (buying) expected.forEach { quote ->
            BotTradeIntentions.productionLimit(bot, quote.item.id)?.let {
                productionQuotedIds += quote.item.id
                productionQuoteExpiries[quote.item.id] = it.expires
                productionQuoteOwners[quote.item.id] = it.identity
            }
        }
        cosmeticQuotes.clear()
        ordinaryQuotes.clear()
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
            !BotSocialInteractions.engaged(other.bot) && !BotConversations.engaged(other.bot) &&
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
        if (browsing || reactivePlayer != null) return
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
                val messages = mutableListOf(BotTradeMessage(BotMerchantSpecialty.forBot(bot).advertisement,
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
                val category = if (buying) BotTradeBuyingPolicy.buyingAdvertisement(bot, item.item.id) else null
                if (category != null) {
                    listOf(BotTradeMessage(category, generated.color, generated.effect))
                } else if (buying && priority == WantedItemPriority.LOW && BotEquipmentPreferences.isCosmetic(item.item.id)) {
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
            autotyper.next()?.let {
                val sent = bot.output.chat(it.text, it.color, it.effect)
                if (sent && purpose != BotTradePurpose.MERCHANT_BUY && marketExperienceReady()) {
                    val item = advertised.first()
                    marketExperience.advertise(item.item.id, buying, item.price, System.currentTimeMillis())
                }
            }
            messageTimer.reset().start()
        }
    }

    /**
     * Samples one advertised basket at most once per second without searching players or item definitions.
     */
    private fun sampleMarketExperience() {
        val now = System.currentTimeMillis()
        if (now < nextExperienceSample) return
        nextExperienceSample = now + 1_000L
        if (marketExperienceReady()) marketExperience.sample(now) else marketExperience.pause()
    }

    /**
     * Only an idle, prepared advertiser at its leased tile can accumulate unsuccessful market exposure.
     */
    private fun marketExperienceReady(): Boolean =
        reactivePlayer == null && purpose != BotTradePurpose.MERCHANT_BUY && !browsing && prepared &&
            !forceBanking && !negotiating && marketplace.tradingSpot(this) == bot.position &&
            bot.scriptStack.current() === this && ActionSafety.NON_COMBAT.allows(bot) &&
            !bot.overlays.hasWindow() && bot.walking.isEmpty && !bot.navigator.isActive

    /**
     * Uses the first actually carried sale stack or the ordinary buyer's one advertised commodity.
     */
    private fun marketExperienceQuote(): BotTradeItem? =
        if (buying) expected.firstOrNull() else offering.firstOrNull { quote ->
            carriedOffer.any { (id, amount) -> amount > 0 && unnotedId(id) == quote.item.id &&
                bot.inventory.computeAmountForId(id) > 0 }
        }

    /**
     * An unaffordable or already fulfilled purchase is not evidence that its bid was too low.
     */
    private fun marketExperienceStillUseful(quote: BotTradeItem): Boolean {
        if (!buying) return true
        val target = purchaseTarget?.takeIf { it.id == quote.item.id }?.amount
            ?: actionableWants[quote.item.id]?.target ?: return false
        if (bot.totalItemAmount(quote.item.id) >= target) return false
        val payment = offering.mapNotNull {
            val count = minOf(it.item.amount, bot.inventory.computeAmountForId(it.item.id))
            if (count > 0) BotTradeItem(Item(it.item.id, count), it.price, it.purpose, bot) else null
        }
        val affordable = BotTradeManager.affordablePurchases(listOf(quote), payment).isNotEmpty()
        val definition = itemDef(quote.item.id)
        val physical = definition.notedId.orElse(quote.item.id)
        return affordable && bot.inventory.hasSpaceFor(Item(physical))
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
        if (advertised.direction == PlayerTradeAdvertisement.Direction.BUY) {
            return advertisedSaleCandidate(market, entry, advertised, remembered, offered, receiving)
        }
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
        val pricing = remembered?.pricing ?: advertisementPricing(market, entry, advertised, id)
            ?: return null
        val context = Opportunity(market, this, entry, advertised, pricing, null, allowance, guides, inspection,
            demand.second, remembered?.counterSent == true, remembered != null)
        context.communicated = remembered != null
        context.plan = advertisementPaymentPlan(context, minOf(demand.first, remaining ?: Int.MAX_VALUE),
            funding, receiving, offered) ?: return null
        return context
    }

    /**
     * Offers only the exact requested commodity from transferable surplus. A selected nearby seller may preview
     * banked goods; its one-shot visit replaces that preview with physically carried stock before requesting trade.
     */
    private fun advertisedSaleCandidate(market: PlayerTradeAdvertisementMarket,
                                        entry: PlayerTradeAdvertisementRegistry.Entry,
                                        advertised: PlayerTradeAdvertisement,
                                        remembered: PlayerTradeAdvertisementMarket.Remembered?,
                                        offered: Collection<Item>, receiving: Map<Int, Int>): Opportunity? {
        val id = advertised.itemIds.singleOrNull() ?: return null
        if (id == COINS || !itemDef(id).isTradeable) return null
        val allowance = carriedOffer.filterKeys { unnotedId(it) == id }
        if (!(reactiveBuyer != null && reactiveSalePreparing) &&
            currentAdvertisementFunding(allowance, offered).isEmpty()) return null
        val ask = offering.firstOrNull { unnotedId(it.item.id) == id }?.price ?: return null
        val pricing = remembered?.pricing ?: market.pricing(entry, bot, advertised, id) {
            PlayerTradeAdvertisementPolicy.saleQuote(advertised, ask)?.let {
                Pricing(id, ask, it, advertisementProfile)
            }
        } ?: return null
        val context = Opportunity(market, this, entry, advertised, pricing, null, allowance,
            mapOf(id to ask), false, false, remembered?.counterSent == true, remembered != null)
        context.communicated = remembered?.pricing != null
        context.plan = advertisedSalePlan(context, receiving, offered) ?: return null
        return context
    }

    /**
     * Bounds an explicit sale by remaining requested quantity, current carried goods, and the human's payment
     * value. Before payment appears the bot displays its available quantity at the captured asking terms.
     */
    private fun advertisedSalePlan(context: Opportunity, receiving: Map<Int, Int>,
                                   offered: Collection<Item>): PlayerTradeAdvertisementPayment.Plan? {
        val pricing = context.pricing ?: return null
        val physical = if (reactiveBuyer != null && reactiveSalePreparing) context.allowance
            else currentAdvertisementFunding(context.allowance, offered)
        val stock = quantities(physical.toItems(), true)
        val available = minOf(stock[pricing.itemId] ?: 0,
            context.entry.remainingQuantity(context.offer) ?: Int.MAX_VALUE,
            if (reactiveBuyer != null) PlayerTradeSaleStock.available(bot, pricing.itemId, offered) else Int.MAX_VALUE)
        if (available <= 0) return null
        val budget = if (receiving.isEmpty()) Long.MAX_VALUE else receivedValue(receiving, context.receivedPrices)
        val quantity = PlayerTradeAdvertisementPayment.affordableQuantity(context.offer, pricing.quote, available, budget)
        if (quantity <= 0) return null
        val cost = PlayerTradeAdvertisementPayment.cost(context.offer, pricing.quote, quantity) ?: return null
        val payment = carriedQuantities(mapOf(pricing.itemId to quantity), physical)
        if (payment.isEmpty()) return null
        return PlayerTradeAdvertisementPayment.Plan(quantity, payment, cost, cost)
    }

    /**
     * Checks live permissions without excluding the particular unanswered bot approach being interrupted.
     */
    private fun advertisementReady(other: Player, allowPendingBotApproach: Boolean = false,
                                   continuing: Boolean = false, inAttempt: Boolean = false): Boolean {
        val detachedReactive = reactivePlayer === other && reactiveOpportunity == null && isIdle() &&
                bot.scriptStack.current() === reactivePrevious && reactiveReady(bot, other)
        if (!bot.isRealBot() || other.isRealBot() || bot.state != EntityState.ACTIVE ||
            other.state != EntityState.ACTIVE || !prepared || forceBanking ||
            BotSocialInteractions.engaged(bot) || BotConversations.engaged(bot) ||
            bot.scriptStack.current() !== this && !detachedReactive || !bot.isAlive || bot.isLocked ||
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
        if (reactivePlayer != null) {
            if (reactivePlayer !== other || !bot.position.isWithinDistance(other.position, Position.VIEWING_DISTANCE)) return false
        } else if (!(browsingMarket ?: marketplace).contains(bot.position)) return false
        return !negotiating || continuing && pendingPartner === other ||
                allowPendingBotApproach && pendingPartner.isRealBot() && !bot.overlays.hasWindow()
    }

    /**
     * Captures only eligible carried stock; banked funds never create an advertisement promise.
     */
    private fun preparedAdvertisementFunding(): Map<Int, Int> {
        advertisementFunding?.let { return it }
        val selected = if (buying) carriedOffer.filterKeys { it == COINS } else {
            val profile = advertisementProfile
            val fraction = BotTradeIntentions.spendingFraction(profile.intelligence, profile.greedy, profile.merchant)
            val held = bot.inventory.filterNotNull().asSequence().filter { it.id == COINS && !it.isDynamic }
                .sumOf { it.amount.toLong() }.coerceAtMost(Int.MAX_VALUE.toLong())
            val coins = sellerCashBudget?.let { minOf(held, it.toLong()).toInt() }
                ?: BigDecimal.valueOf(held).multiply(BigDecimal.valueOf(fraction))
                    .setScale(0, RoundingMode.FLOOR).toInt()
            if (coins > 0) mapOf(COINS to coins) else emptyMap()
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
        val production = BotTradeIntentions.productionLimit(bot, id)
        if (production != null) {
            if (productionQuoteOwners[id]?.let { it !== production.identity } == true) return 0 to false
            val missing = (production.target.toLong() - bot.totalItemAmount(id)).coerceIn(0L, Int.MAX_VALUE.toLong())
            return (if (production.maximumCoins > 0L && production.maximumUnitPrice > 0L) missing.toInt() else 0) to false
        }
        if (id in productionQuotedIds) return 0 to false
        val target = purchaseTarget
        if (target?.id == id) return (specificDemand().firstOrNull()?.item?.amount ?: 0) to false
        if (advertisementProfile.merchant && BotMerchantSpecialty.accepts(bot, id)) return Int.MAX_VALUE to false
        if (advertisementProfile.merchant) return merchantPersonalDemand(id) to false
        val wanted = BotTradeBuyingPolicy.demand(bot, id)
        if (wanted != null) {
            val missing = (wanted.target.toLong() - bot.totalItemAmount(id)).coerceIn(0, Int.MAX_VALUE.toLong()).toInt()
            if (missing > 0) return missing to false
        }
        return if (curiousVisit && (curiosityRemaining ?: 0) > 0) Int.MAX_VALUE to true else 0 to false
    }

    /**
     * Captures one maximum valuation and one haggle decision per advertisement and exact commodity.
     */
    private fun advertisementPricing(market: PlayerTradeAdvertisementMarket,
                                     entry: PlayerTradeAdvertisementRegistry.Entry,
                                     advertised: PlayerTradeAdvertisement, id: Int): Pricing? =
        market.pricing(entry, bot, advertised, id) {
            val guide = BotMerchantValuation.peekGuide(id)
            val profile = advertisementProfile
            val normal = expected.firstOrNull { it.item.id == id && buying }?.price
            val normalMaximum = when {
                purchaseTarget?.id == id -> purchasePrice
                profile.merchant && BotMerchantSpecialty.accepts(bot, id) ->
                    merchantPriceSnapshot().buy(id, guide, false, tradePrice)
                normal != null -> normal
                else -> BotMarketExperience.buy(bot, id, tradePrice.applyBuy(guide))
            }
            val production = BotTradeIntentions.productionLimit(bot, id)
            if (production != null) {
                productionQuotedIds += id
                productionQuoteExpiries[id] = production.expires
                productionQuoteOwners[id] = production.identity
            }
            val maximum = minOf(normalMaximum, production?.maximumUnitPrice ?: Long.MAX_VALUE)
            PlayerTradeAdvertisementPolicy.quote(concreteAdvertisement(advertised, id), maximum, profile,
                rand().nextDouble(), rand().nextDouble())
                ?.let { Pricing(id, maximum, it, profile) }
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
        val direct = if (advertised == null && !other.isRealBot())
            PlayerTradeAdvertisementMarket.live.directPurchase(this, other) else null
        if (direct != null && !direct.market.claimDirectPurchase(direct)) return
        if (!other.isRealBot() && advertised == null && direct == null && (!buying || humanPaymentStock().isEmpty())) return
        negotiating = true
        marketExperience.pause()
        pendingPartner = other
        var success = false
        var yieldedToPlayer = false
        var advertisement = advertised ?: direct
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
                    val proceed = if (!other.isRealBot()) buying && humanPaymentStock().isNotEmpty()
                        else !hasHumanRequest() && !PlayerTradeAdvertisementMarket.live.hasPriority(this)
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
                partnerSearch = null
                if (reactivePlayer == null && computeOfferAndExpected()) {
                    // Keep the market position while carried supplies can fund another exchange.
                    prepared = refreshCarriedStock()
                    forceBanking = !prepared || bot.inventory.computeRemainingSize() <= 1
                }
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
            // A recruited seller owns its response until finish has restored the paused activity's state.
            if (reactiveBuyer == null) advertisement?.let { it.market.release(it) }
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
        if (context.offer.direction == PlayerTradeAdvertisement.Direction.BUY) {
            return advertisedSalePlan(context, emptyMap(), emptyList()) != null
        }
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
        if (pricing != null && !context.counterSent && (pricing.quote.counteroffer ||
            context.offer.direction == PlayerTradeAdvertisement.Direction.BUY && !context.communicated)) {
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
        if (context.offer.direction == PlayerTradeAdvertisement.Direction.BUY) {
            return advertisedSalePlan(context, if (maximum) emptyMap() else receiving, offered)
        }
        val received = quantities(receiving.toItems(), true)
        val id = if (received.isEmpty()) context.pricing?.itemId else received.keys.singleOrNull()
        if (id == null || id == COINS || context.pricing?.let { it.itemId != id } == true ||
            context.offer.itemIds.isNotEmpty() && id !in context.offer.itemIds) return null
        val definition = ItemDefinition.ALL.get(id).orElse(null) ?: return null
        if (!definition.isTradeable) return null
        val demand = advertisementDemand(id)
        if (demand.first <= 0) return null
        val pricing = context.pricing ?: advertisementPricing(context.market, context.entry, context.offer, id)
            ?: return null
        context.pricing = pricing
        context.unexpected = demand.second
        val stockLimit = context.entry.remainingQuantity(context.offer) ?: Int.MAX_VALUE
        val wanted = minOf(demand.first, stockLimit, if (maximum) Int.MAX_VALUE else received[id] ?: Int.MAX_VALUE)
        val heldFunding = quantities(currentAdvertisementFunding(context.allowance, offered).toItems(), true)
        val production = BotTradeIntentions.productionLimit(bot, pricing.itemId)
        val funding = if (production == null) heldFunding else mapOf(COINS to
            minOf((heldFunding[COINS] ?: 0).toLong(), production.maximumCoins).toInt())
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
            val plan = PlayerTradeAdvertisementPayment.cashPlan(concreteAdvertisement(context.offer, pricing.itemId),
                pricing.quote, pricing.maximumUnitPrice, limit, funding[COINS] ?: 0,
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
        if (context.offer.direction == PlayerTradeAdvertisement.Direction.BUY) {
            return negotiateAdvertisedSale(offer, context)
        }
        val timer = Stopwatch.createStarted()
        val timing = BotTradeOfferTiming()
        val feedback = BotTradeFeedback(bot, context.entry.player)
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
            if (plan == null && (context.pricing != null || receiving.isNotEmpty())) {
                val id = context.pricing?.itemId
                val received = quantities(theirs, true)
                val message = if (id != null && received.isNotEmpty() && (received.size != 1 || id !in received))
                    buyingCommodityAdjustment(id, received)
                else "I can't buy that offer with my available money or space. Try fewer items or bank notes."
                feedback.send(message, revision)
                waitFor(2.seconds) {
                    bot.overlays[OfferTradeInterface::class] !== screen || screen.otherOffer.revision != revision
                }
                continue
            }
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
            val adjustment = when {
                plan != null && id != null && (normalized[id] ?: 0) > plan.quantity ->
                    "I can buy ${numF(plan.quantity)} ${tradeItemName(id)}. Remove " +
                            "${numF(normalized.getValue(id) - plan.quantity)} from your offer."
                receiving.isEmpty() && plan != null && id != null ->
                    "I'm buying up to ${numF(plan.quantity)} ${tradeItemName(id)}. Please add your offer."
                receiving.isEmpty() -> "Please add the item you're selling so I can make an offer."
                else -> tradeSpaceAdjustment(context.entry.player, own, receiving)
            }
            if (adjustment != null) feedback.send(adjustment, revision)
            if (valid && adjustment == null) feedback.send("My offer is ready. Please review it and accept.", revision)
            if (valid && adjustment == null) {
                val completed = plan ?: return false
                if (finishTradeWith(offer, own, receiving,
                    onCompleted = { completeAdvertisement(context, completed) })) return true
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
     * Sells one explicitly requested commodity, accepting coins or valued tradeable items. Target changes and
     * both acceptance screens use the captured price and current physical goods, including already offered notes.
     */
    private suspend fun negotiateAdvertisedSale(offer: BotTradeOfferStage, context: Opportunity): Boolean {
        val timer = Stopwatch.createStarted()
        val timing = BotTradeOfferTiming()
        val feedback = BotTradeFeedback(bot, context.entry.player)
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
            val plan = advertisedSalePlan(context, receiving, mine)
            if (plan == null) {
                feedback.send("Your payment doesn't cover one item. Please offer more coins or useful items.", revision)
                waitFor(2.seconds) {
                    bot.overlays[OfferTradeInterface::class] !== screen || screen.otherOffer.revision != revision
                }
                continue
            }
            context.plan = plan
            if (!announceAdvertisement(context)) return false
            val target = plan.payment
            if (own != target) {
                if (!applyTarget(offer, target) { screen.otherOffer.revision == revision }) {
                    if (bot.overlays[OfferTradeInterface::class] === screen &&
                        screen.otherOffer.revision != revision) continue
                    return false
                }
                continue
            }
            val adjustment = if (receiving.isEmpty()) "Please offer coins or items for the stock you asked to buy."
                else tradeSpaceAdjustment(context.entry.player, own, receiving)
            if (adjustment != null) feedback.send(adjustment, revision)
            else {
                feedback.send("My offer is ready. Please review it and accept.", revision)
                if (finishTradeWith(offer, own, receiving,
                    onCompleted = { completeAdvertisement(context, plan) }) {
                    advertisedSalePlan(context, receiving, mine)?.payment == own
                }) {
                    return true
                }
            }
            if (bot.overlays[OfferTradeInterface::class] !== screen) return false
            waitFor(2.seconds) {
                bot.overlays[OfferTradeInterface::class] !== screen || quantities(offer.getTradeItems()) != own ||
                    quantities(offer.getOtherTradeItems()) != receiving
            }
        }
        bot.log("Advertised sale expired or stopped making progress.")
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
        val feedback = BotTradeFeedback(bot, other)
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
            val adjustment = if (exchange == null) humanTradeAdjustment(other, own, receiving, guidePrices) else null
            if (adjustment != null) feedback.send(adjustment, revision)
            if (valid && adjustment == null) {
                feedback.send("My offer is ready. Please review it and accept.", revision)
                if (finishTradeWith(offer, own, receiving)) return true
                if (bot.overlays[OfferTradeInterface::class] === screen && !screen.accepted &&
                    screen.otherOffer.revision == revision) {
                    feedback.send("I couldn't accept that offer. Please adjust the quantity or reopen the trade.", revision)
                }
            }
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
     * Explains the next player action using this attempt's actual desired quantities and stable quotes.
     */
    private fun humanTradeAdjustment(other: Player, own: Map<Int, Int>, receiving: Map<Int, Int>,
                                      guidePrices: MutableMap<Int, Long>): String? {
        if (receiving.isEmpty()) {
            return if (buying) {
                val wanted = expected.firstOrNull() ?: return "Please offer an item I can buy."
                "I'm buying up to ${numF(wanted.item.amount)} ${tradeItemName(wanted.item.id)}. Please add your offer."
            } else "I'm selling my displayed stock. Please offer coins or useful items."
        }
        if (buying) {
            val received = quantities(receiving.toItems(), true)
            val demand = humanPurchaseDemand(receiving)
            val wanted = quantities(demand.map { it.item }, true)
            if (purpose == BotTradePurpose.BUY && received.size != 1) {
                val id = demand.firstOrNull { it.item.id in received }?.item?.id ?: expected.firstOrNull()?.item?.id
                return if (id == null) "Please offer one type of item at a time."
                else buyingCommodityAdjustment(id, received)
            }
            val unwanted = received.keys.firstOrNull { (wanted[it] ?: 0) <= 0 }
            if (unwanted != null) {
                val id = expected.firstOrNull()?.item?.id
                return if (id == null) "I can't buy ${tradeItemName(unwanted)} in this trade. Please remove it."
                else "I can't buy ${tradeItemName(unwanted)} in this trade. Please offer ${tradeItemName(id)} instead."
            }
            val excessive = received.entries.firstOrNull { it.value > (wanted[it.key] ?: 0) }
            if (excessive != null) {
                val amount = wanted.getValue(excessive.key)
                return "I can buy up to ${numF(amount)} ${tradeItemName(excessive.key)}. " +
                        "Remove ${numF(excessive.value - amount)} from your offer."
            }
            if (own.isEmpty()) return "I can't pay for that quantity with my carried stock. Please try a different quantity."
        } else if (own.isEmpty()) {
            val minimum = offering.filter { carriedOffer.keys.any { id -> unnotedId(id) == it.item.id } }
                .minByOrNull { it.price }
            return if (receivedValue(receiving, guidePrices) <= 0 || minimum == null)
                "I can't use that payment. Please offer coins or items I need."
            else "I need at least ${numF(minimum.price)} gp in value for one ${tradeItemName(minimum.item.id)}. " +
                    "Please increase your offer."
        }
        return tradeSpaceAdjustment(other, own, receiving)
    }

    /**
     * Acknowledges matching stock before asking the player to remove unrelated commodities.
     * Quantities include both bank notes and ordinary items under the same base id.
     */
    private fun buyingCommodityAdjustment(id: Int, received: Map<Int, Int>): String {
        val amount = received[id] ?: 0
        val name = tradeItemName(id)
        val notes = itemDef(id).notedId.isPresent
        return if (amount > 0)
            "I count ${numF(amount)} $name${if (notes) ", including notes" else ""}. Remove the other item types."
        else "Please offer $name${if (notes) " or its bank notes" else ""}, and remove the other item types."
    }

    /**
     * Both native acceptance checks must have space after offered stock has left the inventories.
     */
    private fun tradeSpaceAdjustment(other: Player, own: Map<Int, Int>, receiving: Map<Int, Int>): String? = when {
        !other.inventory.hasSpaceForAll(own.toItems()) ->
            "You don't have room for my offer. Reduce your offer for a smaller trade, or free some inventory space."
        !bot.inventory.hasSpaceForAll(receiving.toItems()) ->
            "I don't have room for those items. Please offer bank notes or fewer items."
        else -> null
    }

    /**
     * Keeps complete quantity instructions short enough for the client's directed trade-notice packet.
     */
    private fun tradeItemName(id: Int): String = itemDef(unnotedId(id)).name.take(40)

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
            val current = demand.filter { it.item.id in stock }.mapNotNull(buyer::productionPurchaseQuote)
            val hasProduction = current.any { BotTradeIntentions.productionLimit(buyer.bot, it.item.id) != null }
            val funding = if (hasProduction) payment.filterKeys { it == COINS } else payment
            val availablePayment = buyer.offering.mapNotNull { quote ->
                val count = funding[quote.item.id] ?: return@mapNotNull null
                BotTradeItem(Item(quote.item.id, count), quote.price, quote.purpose, buyer.bot)
            }
            val affordable = if (hasProduction) BotTradeManager.affordablePurchases(current, availablePayment) else current
            val quantities = buyer.quantities(affordable.map { it.item }, true).toMutableMap()
            val bids = buyer.prices(affordable).toMutableMap()
            if (buyer.purpose == BotTradePurpose.MERCHANT_BUY) {
                for (id in quantities.keys.filter { it in buyer.actionableWants }) {
                    val wanted = buyer.personalDemand(id)
                    if (wanted > 0) quantities[id] = minOf(quantities.getValue(id), wanted)
                    else bids[id] = buyer.resalePrice(id)
                }
            }
            return TradeBasket.exchange(stock, quantities, asks, bids, funding, paymentPrices, seller.salePriority())
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

    /**
     * Computes an affordable coin-only payment for a human seller.
     */
    private fun humanTarget(receiving: Map<Int, Int>, guidePrices: MutableMap<Int, Long>): Map<Int, Int> {
        if (buying) {
            val demand = humanPurchaseDemand(receiving)
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
            val payment = quantities(humanPaymentStock().toItems(), true)
            val coins = minOf(budget, (payment[COINS] ?: 0).toLong()).toInt()
            return if (coins > 0) mapOf(COINS to coins) else emptyMap()
        }
        return emptyMap()
    }

    /**
     * Keeps merchant sale priority consistent for both participants and for partial human purchases.
     */
    private fun salePriority(): List<Int> =
        if (merchantSelling) offering.map { it.item.id } else emptyList()

    /** Ensures ordinary exchanges receive useful stock or payment, even when scrutiny is permissive. */
    private fun hasUsefulReturn(receiving: Map<Int, Int>, guidePrices: MutableMap<Int, Long>): Boolean {
        if (!buying) return false
        val received = quantities(receiving.toItems(), true)
        val wanted = quantities(humanPurchaseDemand(receiving).map { it.item }, true)
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
        if (purpose == BotTradePurpose.BUY && offered.isNotEmpty()) {
            val groups = HashSet<Int>()
            return offered.mapNotNull { id ->
                val quote = ordinaryQuote(id) ?: cosmeticQuote(id, paymentStock) ?: return@mapNotNull null
                val group = BotTradeBuyingPolicy.groupKey(bot, id)
                if (group != null && !groups.add(group)) null else quote
            }
        }
        val alternatives = if (purpose == BotTradePurpose.MERCHANT_BUY) {
            offered.filter { it !in primary }.mapNotNull { merchantQuote(it, paymentStock) }
        } else {
            // Ordinary shoppers buy one commodity; extra goods cannot silently turn the trip into bulk buying.
            offered.singleOrNull()?.takeIf { it !in primary }?.let {
                ordinaryQuote(it) ?: cosmeticQuote(it, paymentStock)
            }?.let(::listOf).orEmpty()
        }
        return expected + alternatives
    }

    /**
     * Accepts a wanted commodity independently of the visit's one advertised item, without reserving its buying
     * ceiling from later sales. Equivalent equipment shares the live ownership goal supplied by the policy.
     */
    private fun ordinaryQuote(id: Int): BotTradeItem? {
        val wanted = BotTradeBuyingPolicy.demand(bot, id) ?: return null
        val missing = (wanted.target.toLong() - bot.totalItemAmount(id)).coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
        if (missing <= 0) return null
        val price = ordinaryQuotes.getOrPut(id) {
            val guide = world.economy.getPrice(id)
            if (guide <= 0) 0L else BotMarketExperience.buy(bot, id, tradePrice.applyBuy(guide))
        }
        return if (price <= 0) null else
            productionPurchaseQuote(BotTradeItem(Item(id, missing), price, BotTradePurpose.BUY, bot))
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
            BotTradeItem(Item(id, 1), BotMarketExperience.buy(bot, id,
                BotMerchantValuation.cosmeticBuy(bot, id, guide)), BotTradePurpose.BUY, bot)
        }
        val wallet = TradeBasket.value(quantities(paymentStock.toItems(), true), prices(offering))
        return quote.takeIf { it.price <= wallet }
    }

    /**
     * A merchant's broad buying advertisement also covers tradeable stock without existing market history.
     */
    private fun merchantQuote(id: Int, paymentStock: Map<Int, Int> = carriedOffer): BotTradeItem? {
        if (id == COINS || !itemDef(id).isTradeable) return null
        val resale = BotMerchantSpecialty.accepts(bot, id)
        val demand = if (resale) Int.MAX_VALUE else merchantPersonalDemand(id)
        if (demand <= 0) return null
        val quote = merchantQuotes.getOrPut(id) {
            val price = merchantPriceSnapshot().buy(id, BotMerchantValuation.peekGuide(id),
                !resale, tradePrice)
            BotTradeItem(Item(id, demand), price, BotTradePurpose.MERCHANT_BUY, bot)
        }
        val carried = quantities(paymentStock.toItems(), true)
        val payment = offering.mapNotNull {
            val amount = carried[it.item.id] ?: return@mapNotNull null
            BotTradeItem(Item(it.item.id, amount), it.price, it.purpose, bot)
        }
        val current = productionPurchaseQuote(BotTradeItem(Item(id, demand), quote.price, quote.purpose, bot))
            ?: return null
        return BotTradeManager.affordablePurchases(listOf(current), payment).firstOrNull()
    }

    /**
     * Specialists may still acquire their actual activity inputs and prepared personal upgrades, independently of
     * their resale category. Broad default buying ceilings do not turn every specialty into a general merchant.
     */
    private fun merchantPersonalDemand(id: Int): Int {
        val wanted = BotTradeIntentions.supplyDemand(bot, id) ?: actionableWants[id] ?: return 0
        if (!bot.canUseWantedItem(id)) return 0
        return (wanted.target.toLong() - bot.totalItemAmount(id)).coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
    }

    /**
     * Reuses a single resale valuation snapshot for every incoming item during this banking trip.
     */
    private fun merchantPriceSnapshot(): BotMerchantValuation.Quotes =
        merchantValuation ?: BotMerchantValuation.snapshot(bot).also { merchantValuation = it }

    /**
     * Captures a finite goal's identity when quoting, so an expired or replaced goal cannot reuse that quote.
     */
    private fun productionPurchaseQuote(quote: BotTradeItem): BotTradeItem? {
        val limit = BotTradeIntentions.productionLimit(bot, quote.item.id)
        if (limit == null) return quote.takeUnless { quote.item.id in productionQuotedIds }
        if (productionQuoteExpiries[quote.item.id]?.let { it != limit.expires } == true) return null
        if (productionQuoteOwners[quote.item.id]?.let { it !== limit.identity } == true) return null
        productionQuotedIds += quote.item.id
        productionQuoteExpiries[quote.item.id] = limit.expires
        productionQuoteOwners[quote.item.id] = limit.identity
        return BotTradeManager.capProductionPurchase(quote)
    }

    /**
     * Caps direct player purchases to the original coin allowance and gold still held or already offered.
     */
    private fun humanPaymentStock(): Map<Int, Int> = currentAdvertisementFunding(
        carriedOffer.filterKeys { it == COINS },
        bot.overlays[OfferTradeInterface::class]?.items?.filterNotNull().orEmpty())

    /**
     * Human sellers never inherit the larger budget supplied by bot-to-bot barter stock.
     */
    private fun humanPurchaseDemand(receiving: Map<Int, Int>): List<BotTradeItem> {
        val coins = humanPaymentStock()[COINS] ?: return emptyList()
        val payment = listOf(BotTradeItem(Item(COINS, coins), 1, purpose, bot))
        val requested = if (purpose == BotTradePurpose.MERCHANT_BUY && receiving.isNotEmpty()) {
            quantities(receiving.toItems(), true).keys.mapNotNull { merchantQuote(it, mapOf(COINS to coins)) }
        } else effectiveExpected(receiving, mapOf(COINS to coins))
        return BotTradeManager.affordablePurchases(requested, payment)
    }

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
        else BotMarketExperience.buy(bot, id, tradePrice.applyBuy(guide))
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
                                        onCompleted: () -> Unit = {},
                                        validate: () -> Boolean = { true }): Boolean {
        val original = bot.overlays[OfferTradeInterface::class] ?: return false
        val ownRevision = original.revision
        val otherRevision = original.otherOffer.revision
        val mine = own.toItems()
        val theirs = receiving.toItems()
        fun unchanged() = original.revision == ownRevision && original.otherOffer.revision == otherRevision &&
                bot.inventory.hasSpaceForAll(theirs) && offer.other.inventory.hasSpaceForAll(mine) &&
                productionTradeAllowed(own, receiving) && validate()
        original.guardNativeTrade(validate = {
            bot.scriptStack.current() === this && !purchaseExpired() && unchanged()
        }, settle = {
            settleNativeTrade(own, receiving)
            onCompleted()
        })
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
     * Debits and learns from the real native transfer before window cleanup can cancel a waiting coroutine.
     */
    private fun settleNativeTrade(own: Map<Int, Int>, receiving: Map<Int, Int>) {
        traded = true
        val planned = quantities(receiving.toItems(), true).keys.firstOrNull { it in productionQuotedIds }
        if (planned != null) BotTradeIntentions.recordProductionPurchase(bot, planned, (own[COINS] ?: 0).toLong(),
            productionQuoteOwners[planned])
        quantities(own.toItems(), true).keys.filter { it != COINS }.forEach {
            BotMarketExperience.completed(bot, it, buying = false)
        }
        quantities(receiving.toItems(), true).keys.filter { it != COINS }.forEach {
            BotMarketExperience.completed(bot, it, buying = true)
        }
        marketExperience.clear()
    }

    /**
     * Rechecks a finite goal's live target, maximum unit value and shared cash allowance on both trade screens.
     */
    private fun productionTradeAllowed(own: Map<Int, Int>, receiving: Map<Int, Int>): Boolean {
        val normalized = quantities(receiving.toItems(), true)
        val limits = normalized.keys.mapNotNull { id ->
            BotTradeIntentions.productionLimit(bot, id)?.let { id to it }
        }.toMap()
        val ids = normalized.keys.filter { it in productionQuotedIds || it in limits }
        if (ids.isEmpty()) return true
        if (own.any { it.key != COINS && it.value > 0 }) return false
        val coins = (own[COINS] ?: 0).toLong()
        var maximum = 0L
        var allowance = Long.MAX_VALUE
        for (id in ids) {
            val limit = limits[id] ?: return false
            if (productionQuoteExpiries[id]?.let { it != limit.expires } == true) return false
            if (productionQuoteOwners[id]?.let { it !== limit.identity } == true) return false
            productionQuotedIds += id
            productionQuoteExpiries[id] = limit.expires
            productionQuoteOwners[id] = limit.identity
            val amount = normalized.getValue(id)
            if (amount.toLong() + bot.totalItemAmount(id) > limit.target || limit.maximumUnitPrice < 1L) return false
            val value = if (limit.maximumUnitPrice > Long.MAX_VALUE / amount) Long.MAX_VALUE
                else limit.maximumUnitPrice * amount
            maximum = if (value > Long.MAX_VALUE - maximum) Long.MAX_VALUE else maximum + value
            allowance = minOf(allowance, limit.maximumCoins)
        }
        return coins <= maximum && coins <= allowance
    }

    /**
     * Rejects unavailable and same-purpose partners before calculating any trade quantities or prices.
     */
    private fun eligibleTradePartner(other: Player): Boolean {
        if (other === bot || other.state != EntityState.ACTIVE || checkBlacklisted(other.username) ||
            BotSocialInteractions.engaged(bot) || BotConversations.engaged(bot) ||
            other.isRealBot() && (BotSocialInteractions.engaged(other) || BotConversations.engaged(other))) return false
        val existing = other.overlays[OfferTradeInterface::class]
        if (other.overlays.hasWindow() && existing?.other !== bot) return false
        if (!other.isRealBot()) return buying && humanPaymentStock().isNotEmpty() ||
                PlayerTradeAdvertisementMarket.live.directPurchase(this, other) != null
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
        marketExperience.clear()
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
        marketExperience.clear()
        if (reactivePlayer != null) return false
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
        marketExperience.clear()
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
