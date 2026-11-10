package api.bot.action.trading

import api.bot.isRealBot
import api.bot.action.trading.PlayerTradeAdvertisementPayment.Plan
import api.bot.action.trading.PlayerTradeAdvertisementPolicy.Profile
import api.bot.action.trading.PlayerTradeAdvertisementPolicy.Quote
import api.bot.script.BotScript
import api.predef.rand
import api.predef.world
import engine.bot.gear.BotItemTracker.Companion.itemOwnershipRevision
import engine.bot.speech.BotChatHandler
import engine.bot.speech.BotChatMessage
import engine.bot.speech.BotItemNames
import game.bot.scripts.trading.TradeBotScript
import io.luna.game.model.EntityState
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.bot.Bot
import io.luna.game.task.Task
import io.luna.game.task.TaskState
import java.time.Instant
import java.util.Collections
import java.util.IdentityHashMap

/**
 * Remembers heard player sales and selects one prepared marketplace trader without changing trade screens.
 *
 * Parsing happens once per real-player message. Quotes and tie samples belong to the advertisement, so ordinary
 * polling and repeated advertisements cannot reroll a bargain. The trade script owns approach, payment and cleanup.
 *
 * @author lare96
 */
internal class PlayerTradeAdvertisementMarket(
    private val clock: () -> Instant = { Instant.now() },
    internal val registry: PlayerTradeAdvertisementRegistry = PlayerTradeAdvertisementRegistry(clock),
    private val parser: PlayerTradeAdvertisementParser = PlayerTradeAdvertisementParser(BotItemNames::resolve),
    private val sample: () -> Double = { rand().nextDouble() },
    private val reactiveBuyer: (Bot, Player) -> TradeBotScript? = { bot, seller ->
        TradeBotScript.playerSaleBuyer(bot, seller)
    },
    private val reactiveSeller: (Bot, Player, List<PlayerTradeAdvertisement>) -> TradeBotScript? = { bot, buyer, offers ->
        TradeBotScript.playerPurchaseSeller(bot, buyer, offers)
    },
    private val schedule: (Task) -> Unit = { world.schedule(it) }
) : BotChatHandler {

    /**
     * The game-thread marketplace shared by public chat and active trading scripts.
     *
     * @author lare96
     */
    companion object {
        private const val SELLER_PROBES_PER_TICK = 8
        private const val SELLER_RETRY_TICKS = 10L
        val live = PlayerTradeAdvertisementMarket()
    }

    /**
     * One commodity's captured valuation and negotiated price, independent of later inventory changes.
     *
     * @author lare96
     */
    data class Pricing(val itemId: Int, val maximumUnitPrice: Long, val quote: Quote, val profile: Profile)

    /**
     * A current plan and its original advertisement. Inspection resolves one commodity before any payment is made.
     *
     * @author lare96
     */
    class Opportunity(val market: PlayerTradeAdvertisementMarket,
                      val script: TradeBotScript,
                      val entry: PlayerTradeAdvertisementRegistry.Entry,
                      val offer: PlayerTradeAdvertisement,
                      var pricing: Pricing?,
                      var plan: Plan?,
                      val allowance: Map<Int, Int>,
                      val paymentPrices: Map<Int, Long>,
                      val inspection: Boolean,
                      var unexpected: Boolean,
                      var counterSent: Boolean = false,
                      val continuation: Boolean = false) {
        var response: PlayerTradeAdvertisementRegistry.Response? = null
        var quantityNotified = false
        var lastNotifiedQuantity: Int? = null
        var quantityShrinkNotified = false
        var communicated = false
        var completed = false
        val receivedPrices = HashMap<Int, Long>()
    }

    /**
     * A previously communicated deal that can be resumed by a player's own native trade request.
     *
     * @author lare96
     */
    data class Remembered(val entry: PlayerTradeAdvertisementRegistry.Entry,
                          val offer: PlayerTradeAdvertisement,
                          val pricing: Pricing?,
                          val counterSent: Boolean)

    /**
     * Retains selection identity and immutable terms without retaining a mutable funding or payment plan.
     *
     * @author lare96
     */
    private data class Selected(val script: TradeBotScript, val offer: PlayerTradeAdvertisement)

    /**
     * Avoids rebuilding an unsuccessful seller preview until its ownership or activity changes, or a short retry.
     *
     * @author lare96
     */
    private data class SellerProbe(val revision: Long, val activity: BotScript?, val tick: Long)

    /**
     * Audience and quote memory for one live advertisement identity.
     *
     * @author lare96
     */
    private class Heard(val tieSample: Double) {
        val audience = ArrayList<Bot>()
        val membership: MutableSet<Bot> = Collections.newSetFromMap(IdentityHashMap())
        val quotes = IdentityHashMap<Bot, MutableMap<Pair<PlayerTradeAdvertisement, Int>, Pricing?>>()
        val sent = IdentityHashMap<Bot, LinkedHashMap<PlayerTradeAdvertisement, Opportunity>>()
        var selectionTick = Long.MIN_VALUE
        var selected: Selected? = null
        var reactiveDispatchTick = Long.MIN_VALUE
        var sellerCursor = 0
        var sellerQueued = false
        var sellerNotBeforeTick = Long.MIN_VALUE
        val sellerProbes = IdentityHashMap<Bot, SellerProbe>()

        /**
         * Structural advertisement changes begin a fresh round without changing stable quotes or tie samples.
         */
        fun invalidateSelection() {
            selectionTick = Long.MIN_VALUE
            selected = null
        }
    }

    private val heard = LinkedHashMap<PlayerTradeAdvertisementRegistry.Entry, Heard>()
    private val sellerSearches = ArrayDeque<PlayerTradeAdvertisementRegistry.Entry>()
    private var sellerTask: Task? = null
    private var sellerBudgetTick = Long.MIN_VALUE
    private var sellerBudget = 0

    override fun handle(message: BotChatMessage): Boolean {
        val player = message.speaker
        if (player.isRealBot() || player.state != EntityState.ACTIVE ||
            player.world.getPlayer(player.usernameHash).orElse(null) !== player) return false
        val offers = parser.parseAdvertisement(message.text) ?: return false
        prune()
        val entry = registry.publish(player, offers)
        heard.entries.removeIf { it.key.player == player && it.key !== entry }
        if (entry != null) {
            val memory = heard.getOrPut(entry) { Heard(sample()) }
            pruneBuyers(memory)
            for (bot in message.hearers) {
                if (isCurrentBuyer(bot) && memory.membership.add(bot)) {
                    memory.audience += bot
                    if (memory.selected?.offer?.direction != PlayerTradeAdvertisement.Direction.BUY) {
                        memory.invalidateSelection()
                    }
                }
            }
            dispatchReactive(entry, memory)
        }
        return true
    }

    /**
     * Lets an otherwise occupied nearby bot take one bounded, carried-cash buying visit. Only human messages
     * create previews; ordinary trader polls never create scripts or inspect workers throughout the world.
     * One seller's existing response blocks further dispatch, including repeated advertisements in later ticks.
     */
    private fun dispatchReactive(entry: PlayerTradeAdvertisementRegistry.Entry, memory: Heard) {
        if (entry.offers.any { it.direction == PlayerTradeAdvertisement.Direction.BUY }) {
            queueSellerSearch(entry, memory, restart = true)
            processSellerSearches()
            scheduleSellerSearches()
            return
        }
        if (entry.offers.none { it.direction == PlayerTradeAdvertisement.Direction.SELL } ||
            !registry.isAvailable(entry)) return
        val tick = world.currentTick
        if (memory.reactiveDispatchTick == tick) return
        memory.reactiveDispatchTick = tick
        val previews = IdentityHashMap<Bot, TradeBotScript>()
        for (buyer in memory.audience) {
            if (buyer.scriptStack.current() !is TradeBotScript && isCurrentBuyer(buyer) &&
                entry.hasUntriedOffer(buyer)) {
                reactiveBuyer(buyer, entry.player)?.let { previews[buyer] = it }
            }
        }
        if (previews.isEmpty()) return
        val opportunity = winner(entry, memory, previews) ?: return
        memory.selectionTick = tick
        memory.selected = Selected(opportunity.script, opportunity.offer)
        if (previews[opportunity.script.bot] !== opportunity.script) return
        opportunity.response = registry.claim(entry, opportunity.script.bot, opportunity.offer) ?: return
        var started = false
        try {
            started = opportunity.script.beginPlayerSale(opportunity)
        } finally {
            if (!started) release(opportunity)
        }
    }

    /**
     * Searches only the audience of a real player's buying message. Each sweep is finite, and unchanged misses
     * are remembered across repetitions. A shared tick budget covers all players rather than multiplying work
     * by the number of simultaneous advertisements. Claiming one response stops its search while banking.
     */
    private fun queueSellerSearch(entry: PlayerTradeAdvertisementRegistry.Entry, memory: Heard, restart: Boolean) {
        if (memory.selected?.offer?.direction == PlayerTradeAdvertisement.Direction.BUY) return
        if (!registry.isAvailable(entry) || entry.availableOffers.none {
                it.direction == PlayerTradeAdvertisement.Direction.BUY && it.itemIds.size == 1 && !it.requiresInspection
            }) return
        if (restart && memory.sellerCursor >= memory.audience.size) memory.sellerCursor = 0
        if (memory.sellerQueued || memory.sellerCursor >= memory.audience.size) return
        memory.sellerQueued = true
        sellerSearches += entry
    }

    /**
     * Continues an advertisement's remaining hearers without requiring the player to repeat their request.
     * This task disappears after the queued sweeps finish; ordinary trader polling never scans worker banks.
     */
    private fun scheduleSellerSearches() {
        if (sellerSearches.isEmpty() || sellerTask?.state?.let { it != TaskState.CANCELLED } == true) return
        val task = object : Task(1) {
            override fun execute() {
                processSellerSearches()
                if (sellerSearches.isEmpty()) {
                    sellerTask = null
                    cancel()
                }
            }
        }
        sellerTask = task
        schedule(task)
    }

    /**
     * Visits at most eight hearers per game tick across all pending messages, rotating between their audiences.
     * Cheap session and revision checks precede the seller factory and all retained-stock or valuation work.
     */
    private fun processSellerSearches() {
        val tick = world.currentTick
        if (sellerBudgetTick != tick) {
            sellerBudgetTick = tick
            sellerBudget = SELLER_PROBES_PER_TICK
        }
        var entriesVisited = 0
        while (sellerBudget > 0 && sellerSearches.isNotEmpty() && entriesVisited < SELLER_PROBES_PER_TICK) {
            entriesVisited++
            val entry = sellerSearches.removeFirst()
            val memory = heard[entry] ?: continue
            memory.sellerQueued = false
            if (!registry.isAvailable(entry) || entry.player.state != EntityState.ACTIVE ||
                entry.player.world.getPlayer(entry.player.usernameHash).orElse(null) !== entry.player ||
                memory.sellerCursor >= memory.audience.size) continue
            if (tick < memory.sellerNotBeforeTick) {
                queueSellerSearch(entry, memory, restart = false)
                continue
            }
            val bot = memory.audience[memory.sellerCursor++]
            sellerBudget--
            val selected = if (isCurrentBuyer(bot) && entry.hasUntriedOffer(bot)) {
                selectSeller(entry, memory, bot, tick)
            } else false
            if (!selected) queueSellerSearch(entry, memory, restart = false)
        }
    }

    /**
     * Lets a ready trader keep its current trip; otherwise previews one surplus visit before activating it.
     * Preview stock is revalidated by the selected script after any bank trip and before a native trade opens.
     */
    private fun selectSeller(entry: PlayerTradeAdvertisementRegistry.Entry, memory: Heard, bot: Bot, tick: Long): Boolean {
        val offers = entry.availableOffers.filter {
            it.direction == PlayerTradeAdvertisement.Direction.BUY && it.itemIds.size == 1 &&
                    !it.requiresInspection && !entry.wasAttempted(bot, it)
        }
        if (offers.isEmpty()) return false
        val current = bot.scriptStack.current()
        if (current is TradeBotScript) {
            val ready = offers.firstNotNullOfOrNull {
                current.advertisementCandidate(this, entry, it, allowPendingBotApproach = true)
            }
            if (ready != null) {
                memory.selectionTick = tick
                memory.selected = Selected(ready.script, ready.offer)
                return true
            }
        }
        val revision = bot.itemOwnershipRevision
        val previous = memory.sellerProbes[bot]
        if (previous != null && previous.revision == revision && previous.activity === current &&
            tick - previous.tick < SELLER_RETRY_TICKS) return false
        memory.sellerProbes[bot] = SellerProbe(revision, current, tick)
        val script = reactiveSeller(bot, entry.player, offers) ?: return false
        val opportunity = winner(entry, memory, mapOf(bot to script), listOf(bot)) ?: return false
        if (opportunity.script !== script) return false
        memory.selectionTick = tick
        memory.selected = Selected(script, opportunity.offer)
        opportunity.response = registry.claim(entry, bot, opportunity.offer) ?: run {
            memory.invalidateSelection()
            return false
        }
        var started = false
        try {
            started = script.beginPlayerPurchase(opportunity)
            return started
        } finally {
            if (!started) release(opportunity)
        }
    }

    /**
     * Captures one decision per exact commodity and buyer, including unsuccessful valuations.
     */
    fun pricing(entry: PlayerTradeAdvertisementRegistry.Entry, buyer: Bot, offer: PlayerTradeAdvertisement, id: Int,
                create: () -> Pricing?): Pricing? {
        if (!isCurrentBuyer(buyer)) return null
        val memory = heard.getOrPut(entry) { Heard(sample()) }
        val quotes = memory.quotes.getOrPut(buyer) { HashMap() }
        val key = offer to id
        if (!quotes.containsKey(key)) quotes[key] = create()
        return quotes[key]
    }

    /**
     * Tests priority without claiming an advertisement, sending messages or consuming pending requests.
     */
    fun hasPriority(script: TradeBotScript): Boolean = chooseFor(script) != null

    /**
     * Claims only the globally selected buyer's advertisement immediately before its trade attempt.
     */
    fun claim(script: TradeBotScript): Opportunity? {
        val opportunity = chooseFor(script) ?: return null
        opportunity.response = registry.claim(opportunity.entry, script.bot, opportunity.offer) ?: return null
        heard[opportunity.entry]?.invalidateSelection()
        return opportunity
    }

    /**
     * Retains only communicated requests or counteroffers for later player-initiated acceptance.
     */
    fun remember(opportunity: Opportunity) {
        if (!isCurrentBuyer(opportunity.script.bot)) return
        if (registry.find(opportunity.entry.player) === opportunity.entry) {
            opportunity.communicated = true
            if (opportunity.pricing != null) {
                val quotes = heard[opportunity.entry]?.sent?.getOrPut(opportunity.script.bot) { LinkedHashMap() }
                quotes?.remove(opportunity.offer)
                quotes?.set(opportunity.offer, opportunity)
            }
        }
    }

    /**
     * Finds a still-live communicated quote, even if another response currently owns the unsolicited-response slot.
     */
    fun remembered(script: TradeBotScript, player: Player): Remembered? = rememberedOffers(script, player).lastOrNull()

    /**
     * Captures all communicated concrete deals in communication order for an incoming native trade. The caller
     * binds only after the player's actual commodity is known, keeping unrelated human trades on their normal path.
     */
    fun rememberedOffers(script: TradeBotScript, player: Player): List<Remembered> {
        prune()
        val entry = registry.find(player) ?: return emptyList()
        return heard[entry]?.sent?.get(script.bot)?.values?.map {
            Remembered(entry, it.offer, it.pricing, it.counterSent)
        }.orEmpty()
    }

    /**
     * Matches a player's own request to their current explicit buying message. Cashless bots may respond only
     * with that exact commodity; unrelated carried stock and unknown item groups never create an exception.
     */
    fun directPurchase(script: TradeBotScript, player: Player): Opportunity? {
        prune()
        val entry = registry.find(player) ?: return null
        if (heard[entry]?.membership?.contains(script.bot) != true) return null
        val remembered = rememberedOffers(script, player)
        for (offer in entry.availableOffers) {
            if (offer.direction != PlayerTradeAdvertisement.Direction.BUY) continue
            val previous = remembered.lastOrNull { it.offer == offer }
            if (entry.wasAttempted(script.bot, offer) && previous == null) continue
            val context = script.advertisementCandidate(this, entry, offer,
                allowPendingBotApproach = true,
                remembered = previous ?: Remembered(entry, offer, null, false)) ?: continue
            return context
        }
        return null
    }

    /**
     * Reserves a direct buying-message response only after the matching native request has been chosen.
     */
    fun claimDirectPurchase(opportunity: Opportunity): Boolean {
        opportunity.response = if (opportunity.entry.wasAttempted(opportunity.script.bot, opportunity.offer))
            registry.resume(opportunity.entry, opportunity.script.bot)
        else registry.claim(opportunity.entry, opportunity.script.bot, opportunity.offer)
        if (opportunity.response != null) heard[opportunity.entry]?.invalidateSelection()
        return opportunity.response != null
    }

    /**
     * Accounts the exact completed delivery without extending the advertisement or releasing its active response.
     */
    fun completed(opportunity: Opportunity, quantity: Int) {
        opportunity.response?.let {
            if (registry.recordCompleted(it, mapOf(opportunity.offer to quantity))) {
                heard[opportunity.entry]?.invalidateSelection()
            }
        }
    }

    /**
     * Releases only this attempt's token; its communicated price remains available until the advertisement expires.
     */
    fun release(opportunity: Opportunity) {
        opportunity.response?.let {
            if (registry.release(it)) {
                heard[opportunity.entry]?.invalidateSelection()
                val current = registry.find(opportunity.entry.player)
                val memory = current?.let(heard::get)
                if (current != null && memory != null &&
                    current.offers.any { offer -> offer.direction == PlayerTradeAdvertisement.Direction.BUY }) {
                    memory.invalidateSelection()
                    if (opportunity.script.bot.scriptStack.current() === opportunity.script) {
                        // Tasks run before bot stacks; let the finishing child leave the stack before its next probe.
                        memory.sellerNotBeforeTick = maxOf(memory.sellerNotBeforeTick, world.currentTick + 2)
                    }
                    queueSellerSearch(current, memory, restart = true)
                    scheduleSellerSearches()
                }
            }
        }
    }

    /**
     * Revalidates only the selected seller for player buying requests. The bounded discovery task owns their
     * audience search, including expanded equipment clauses; ordinary polls never restart that work. Player sales
     * retain their shared per-tick buyer comparison. Selected participants always rebuild their live deal.
     */
    private fun chooseFor(script: TradeBotScript): Opportunity? {
        if (heard.isEmpty() || !script.bot.isRealBot()) return null
        prune()
        if (heard.isEmpty()) return null
        val currentTick = world.currentTick
        for (entry in registry.availableFor(script.bot)) {
            val memory = heard[entry] ?: continue
            if (script.bot !in memory.membership) continue
            if (entry.offers.any { it.direction == PlayerTradeAdvertisement.Direction.BUY }) {
                val selected = memory.selected ?: continue
                val seller = selected.script
                if (!isCurrentBuyer(seller.bot) || seller.bot.scriptStack.current() !== seller) {
                    resumeSellerSearch(entry, memory)
                    continue
                }
                if (seller !== script) continue
                val current = if (!entry.wasAttempted(script.bot, selected.offer)) {
                    script.advertisementCandidate(this, entry, selected.offer, allowPendingBotApproach = true)
                } else null
                if (current != null) return current
                resumeSellerSearch(entry, memory)
                continue
            }
            var fresh: Opportunity? = null
            if (memory.selectionTick != currentTick) {
                fresh = winner(entry, memory)
                memory.selected = fresh?.let { Selected(it.script, it.offer) }
                memory.selectionTick = currentTick
            }
            val selected = memory.selected ?: continue
            if (selected.script !== script) continue
            // Rebuild physical funding, demand and readiness on every winning poll; no saved plan is reused.
            val current = fresh ?: if (script.bot.scriptStack.current() === script &&
                !entry.wasAttempted(script.bot, selected.offer)) {
                script.advertisementCandidate(this, entry, selected.offer, allowPendingBotApproach = true)
            } else null
            if (current != null) return current
            // Do not repeat an audience-wide scan for a stale winner in the same unchanged tick.
            memory.selected = null
        }
        return null
    }

    /**
     * Defers a stale seller's remaining audience to the shared discovery budget rather than scanning in a poll.
     */
    private fun resumeSellerSearch(entry: PlayerTradeAdvertisementRegistry.Entry, memory: Heard) {
        memory.invalidateSelection()
        queueSellerSearch(entry, memory, restart = false)
        scheduleSellerSearches()
    }

    /**
     * Never compares quantities or prices across unrelated commodities. Unknown terms are an inspection fallback.
     */
    private fun winner(entry: PlayerTradeAdvertisementRegistry.Entry, memory: Heard,
                       previews: Map<Bot, TradeBotScript> = emptyMap(),
                       audience: List<Bot> = memory.audience): Opportunity? {
        val groups = entry.availableOffers.map { offer ->
            audience.mapNotNull { buyer ->
                if (entry.wasAttempted(buyer, offer)) null else
                    (previews[buyer] ?: (buyer.scriptStack.current() as? TradeBotScript))
                        ?.advertisementCandidate(this, entry, offer, allowPendingBotApproach = true)
            }
        }
        for (counteroffer in listOf(false, true)) {
            for (group in groups) {
                val eligible = group.filter { !it.inspection && it.pricing?.quote?.counteroffer == counteroffer }
                val candidates = eligible.map { opportunity ->
                    PlayerTradeAdvertisementSelection.Candidate(opportunity.script.bot, opportunity.offer,
                        opportunity.pricing!!.quote, opportunity.plan!!)
                }
                val chosen = PlayerTradeAdvertisementSelection.choose(candidates, memory.tieSample) ?: continue
                return eligible.first { it.script.bot === chosen.buyer }
            }
        }
        for (group in groups) {
            val inspectors = group.filter { it.inspection }
            if (inspectors.isNotEmpty()) {
                return inspectors[(memory.tieSample * inspectors.size).toInt().coerceAtMost(inspectors.lastIndex)]
            }
        }
        return null
    }

    /**
     * Releases departed buyer sessions when an advertisement is heard again, without adding audience scans to polls.
     * Unrefreshed advertisements retain their existing expiry cleanup.
     */
    private fun pruneBuyers(memory: Heard) {
        val removed = memory.audience.removeIf {
            if (isCurrentBuyer(it)) false else {
                memory.membership.remove(it)
                true
            }
        }
        memory.quotes.keys.removeIf { !isCurrentBuyer(it) }
        memory.sent.keys.removeIf { !isCurrentBuyer(it) }
        memory.sellerProbes.keys.removeIf { !isCurrentBuyer(it) }
        if (removed) memory.sellerCursor = 0
        if (memory.selected?.let {
                !isCurrentBuyer(it.script.bot) || it.script.bot.scriptStack.current() !== it.script
            } == true ||
            removed && memory.selected?.offer?.direction != PlayerTradeAdvertisement.Direction.BUY) {
            memory.invalidateSelection()
        }
    }

    /**
     * Checks the current session by reference so an active replacement does not keep the former buyer alive.
     */
    private fun isCurrentBuyer(buyer: Bot): Boolean = buyer.state == EntityState.ACTIVE &&
        buyer.world.getPlayer(buyer.username).orElse(null) === buyer

    /**
     * Drops expired or disconnected sellers without scanning advertisement audiences on every candidate poll.
     */
    private fun prune() {
        val now = clock()
        heard.entries.removeIf { (entry, _) ->
            val player = entry.player
            val invalid = !entry.expiresAt.isAfter(now) || player.state != EntityState.ACTIVE ||
                    player.world.getPlayer(player.usernameHash).orElse(null) !== player
            if (invalid) registry.remove(player)
            invalid
        }
    }
}
