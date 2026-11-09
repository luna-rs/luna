package api.bot.action.trading

import api.bot.isRealBot
import api.bot.action.trading.PlayerTradeAdvertisementPayment.Plan
import api.bot.action.trading.PlayerTradeAdvertisementPolicy.Profile
import api.bot.action.trading.PlayerTradeAdvertisementPolicy.Quote
import api.predef.rand
import api.predef.world
import engine.bot.speech.BotChatHandler
import engine.bot.speech.BotChatMessage
import engine.bot.speech.BotItemNames
import game.bot.scripts.trading.TradeBotScript
import io.luna.game.model.EntityState
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.bot.Bot
import java.time.Instant
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
    private val sample: () -> Double = { rand().nextDouble() }
) : BotChatHandler {

    /**
     * The game-thread marketplace shared by public chat and active trading scripts.
     *
     * @author lare96
     */
    companion object {
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
     * Bounded audience and quote memory for one live advertisement identity.
     *
     * @author lare96
     */
    private class Heard(val tieSample: Double) {
        val audience = ArrayList<Bot>()
        val quotes = IdentityHashMap<Bot, MutableMap<Pair<PlayerTradeAdvertisement, Int>, Pricing?>>()
        val sent = IdentityHashMap<Bot, LinkedHashMap<PlayerTradeAdvertisement, Opportunity>>()
        var selectionTick = Long.MIN_VALUE
        var selected: Selected? = null

        /**
         * Structural advertisement changes begin a fresh round without changing stable quotes or tie samples.
         */
        fun invalidateSelection() {
            selectionTick = Long.MIN_VALUE
            selected = null
        }
    }

    private val heard = LinkedHashMap<PlayerTradeAdvertisementRegistry.Entry, Heard>()

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
            for (bot in message.hearers) {
                if (memory.audience.none { it === bot }) {
                    memory.audience += bot
                    memory.invalidateSelection()
                }
            }
        }
        return true
    }

    /**
     * Captures one decision per exact commodity and buyer, including unsuccessful valuations.
     */
    fun pricing(entry: PlayerTradeAdvertisementRegistry.Entry, buyer: Bot, offer: PlayerTradeAdvertisement, id: Int,
                create: () -> Pricing?): Pricing? {
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
            if (registry.release(it)) heard[opportunity.entry]?.invalidateSelection()
        }
    }

    /**
     * Uses publication order between players and item clauses, with accepting buyers ahead of all counteroffers.
     * Unchanged audiences share one complete selection pass per game tick, including a cached no-match result.
     * Readiness changes can wait at most until the next tick (normally 600 ms); new listeners and response/stock
     * changes invalidate immediately. A selected participant always rebuilds its live deal before a claim.
     */
    private fun chooseFor(script: TradeBotScript): Opportunity? {
        if (!script.bot.isRealBot()) return null
        prune()
        val currentTick = world.currentTick
        for (entry in registry.availableFor(script.bot)) {
            val memory = heard[entry] ?: continue
            if (memory.audience.none { it === script.bot }) continue
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
     * Never compares quantities or prices across unrelated commodities. Unknown terms are an inspection fallback.
     */
    private fun winner(entry: PlayerTradeAdvertisementRegistry.Entry, memory: Heard): Opportunity? {
        val groups = entry.availableOffers.map { offer ->
            memory.audience.mapNotNull { buyer ->
                if (entry.wasAttempted(buyer, offer)) null else
                    (buyer.scriptStack.current() as? TradeBotScript)
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
     * Drops expired and disconnected audiences without repeated full registry scans during candidate selection.
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
