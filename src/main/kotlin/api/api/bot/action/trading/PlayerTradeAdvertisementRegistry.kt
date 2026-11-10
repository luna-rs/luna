package api.bot.action.trading

import io.luna.game.model.mob.Player
import io.luna.game.model.mob.bot.Bot
import java.time.Instant
import java.util.Collections
import java.util.Locale

/**
 * Keeps each player's latest marketplace advertisement and one active response per player on the game thread.
 *
 * Identical terms refresh the same entry for 60 seconds, preserving buyer decisions and response history. A different
 * advertisement replaces those terms, but does not release an outstanding response: its trade may already be open.
 * Completed deliveries reduce separate stock counters while preserving the original total-price ratios.
 * The response owner must release its token in its attempt's cleanup, including cancellation and timeout paths.
 *
 * @author lare96
 */
class PlayerTradeAdvertisementRegistry(private val clock: () -> Instant = { Instant.now() }) {

    /**
     * Stable advertisement identity, with original pricing terms, remaining stock and a refreshable deadline.
     *
     * @author lare96
     */
    class Entry internal constructor(val player: Player,
                                     val offers: List<PlayerTradeAdvertisement>,
                                     expiresAt: Instant) {

        var expiresAt: Instant = expiresAt
            internal set

        /**
         * Account identities preserve attempts across relogins without retaining former buyer sessions.
         */
        internal val attemptedBuyers = HashSet<Long>()
        private val attemptedOffers = HashMap<Long, MutableSet<PlayerTradeAdvertisement>>()
        private val remainingQuantities = offers.associateWith { it.quantity }.toMutableMap()

        /**
         * Offers with stock still available. Original quantities remain unchanged for total-price calculations.
         */
        val availableOffers: List<PlayerTradeAdvertisement>
            get() = offers.filter { remainingQuantities.getValue(it) != 0 }

        /**
         * Reads remaining advertised stock, or `null` when the player did not specify a quantity.
         * The offer must belong to this entry so an unrelated offer cannot appear to have unspecified stock.
         */
        fun remainingQuantity(offer: PlayerTradeAdvertisement): Int? {
            require(offer in remainingQuantities)
            return remainingQuantities.getValue(offer)
        }

        /**
         * Tests whether this buyer has already approached for one of the original advertised offers.
         */
        fun wasAttempted(buyer: Bot, offer: PlayerTradeAdvertisement): Boolean {
            require(offer in remainingQuantities)
            return attemptedOffers[buyer.usernameHash]?.contains(offer) == true
        }

        internal val hasRemainingOffers: Boolean
            get() = remainingQuantities.values.any { it == null || it > 0 }

        /**
         * Finds remaining offers this buyer has not approached for, without consuming response ownership.
         */
        internal fun hasUntriedOffer(buyer: Bot): Boolean {
            val attempted = attemptedOffers[buyer.usernameHash].orEmpty()
            return remainingQuantities.any { (offer, quantity) -> quantity != 0 && offer !in attempted }
        }

        /**
         * Records either one selected offer or the original whole-advertisement attempt.
         */
        internal fun attempt(buyer: Bot, offer: PlayerTradeAdvertisement?): Boolean {
            val buyerId = buyer.usernameHash
            if (offer == null) {
                if (buyerId in attemptedBuyers) return false
            } else if (offer !in remainingQuantities || remainingQuantities[offer] == 0 || wasAttempted(buyer, offer)) {
                return false
            }
            attemptedBuyers += buyerId
            val attempted = attemptedOffers.getOrPut(buyerId) { HashSet() }
            if (offer == null) attempted.addAll(offers) else attempted.add(offer)
            return true
        }

        /**
         * Validates every delivery before changing any remaining quantity.
         */
        internal fun consume(deliveries: Map<PlayerTradeAdvertisement, Int>): Boolean {
            if (deliveries.isEmpty() || deliveries.any { (offer, quantity) ->
                quantity <= 0 || offer !in remainingQuantities ||
                        remainingQuantities[offer]?.let { quantity > it } == true
            }) return false
            for ((offer, quantity) in deliveries) {
                remainingQuantities[offer]?.let { remainingQuantities[offer] = it - quantity }
            }
            return true
        }
    }

    /**
     * Exclusive ownership of one response attempt. Token identity prevents old cleanup from releasing a new attempt.
     *
     * @author lare96
     */
    class Response internal constructor(val advertisement: Entry, val buyer: Bot) {
        internal var completed = false
    }

    /**
     * Semantic identity independent of known-item aliases and clause ordering. Unknown names retain their wording.
     *
     * @author lare96
     */
    private data class Terms(val itemIds: Set<Int>, val unknownName: String?, val quantity: Int?,
                             val price: PlayerTradeAdvertisement.Price?,
                             val direction: PlayerTradeAdvertisement.Direction)

    private val entries = LinkedHashMap<Player, Entry>()
    private val responses = HashMap<Player, Response>()
    private val spaces = Regex("""\s+""")

    /**
     * Publishes the player's latest sales, or withdraws the advertisement when no valid offers remain.
     * Duplicate clauses are collapsed. Refreshing equivalent terms preserves the entry and its attempted buyers.
     */
    fun publish(player: Player, offers: List<PlayerTradeAdvertisement>): Entry? {
        val now = clock()
        discardExpired(now)
        if (offers.isEmpty()) {
            remove(player)
            return null
        }
        val unique = LinkedHashMap<Terms, PlayerTradeAdvertisement>()
        for (offer in offers) {
            val snapshot = offer.copy(itemIds = Collections.unmodifiableSet(LinkedHashSet(offer.itemIds)))
            unique.putIfAbsent(terms(snapshot), snapshot)
        }
        val previous = entries[player]
        if (previous != null && previous.player === player && previous.offers.map(::terms).toSet() == unique.keys) {
            previous.expiresAt = now.plusSeconds(60)
            return previous
        }
        val entry = Entry(player, Collections.unmodifiableList(ArrayList(unique.values)), now.plusSeconds(60))
        entries.remove(player)
        entries[player] = entry
        return entry
    }

    /**
     * Finds current terms even while a response is active, without claiming or consuming the advertisement.
     */
    fun find(player: Player): Entry? {
        discardExpired(clock())
        return entries[player]?.takeIf { it.player === player }
    }

    /**
     * Lists unclaimed advertisements with a remaining offer this buyer has not tried. The marketplace checks
     * eligibility and valuation before claiming; reading never reserves a player or changes another buyer's priority.
     */
    fun availableFor(buyer: Bot): List<Entry> {
        discardExpired(clock())
        return entries.values.filter {
            it.player !in responses && it.hasUntriedOffer(buyer)
        }
    }

    /**
     * Claims a first whole-advertisement attempt before sending a PM or trade request, marking every original offer
     * attempted. Repeating the advertisement cannot restart this buyer's unsolicited whole-advertisement attempt.
     */
    fun claim(entry: Entry, buyer: Bot): Response? = beginResponse(entry, buyer, false)

    /**
     * Claims one original available offer without preventing this buyer from approaching for a different offer later.
     * Repeated attempts for this same offer remain blocked, and every offer shares the player's response slot.
     */
    fun claim(entry: Entry, buyer: Bot, offer: PlayerTradeAdvertisement): Response? =
        beginResponse(entry, buyer, false, offer)

    /**
     * Resumes a previous buyer's attempt when the player directly requests the bot's previously sent quote.
     * The caller verifies that quote was sent and remains applicable; this method preserves attempt history and
     * requires an available response slot, current terms and remaining stock without extending the advertisement.
     */
    fun resume(entry: Entry, buyer: Bot): Response? = beginResponse(entry, buyer, true)

    /**
     * Creates an exclusive response for either a first unsolicited attempt or a returning direct request.
     */
    private fun beginResponse(entry: Entry, buyer: Bot, resuming: Boolean,
                              offer: PlayerTradeAdvertisement? = null): Response? {
        discardExpired(clock())
        if (entries[entry.player] !== entry || !entry.hasRemainingOffers || entry.player in responses) return null
        if (resuming) {
            if (buyer.usernameHash !in entry.attemptedBuyers) return null
        } else if (!entry.attempt(buyer, offer)) return null
        return Response(entry, buyer).also { responses[entry.player] = it }
    }

    /**
     * Checks whether an unanswered approach still owns a live advertisement. An already-open trade must continue
     * through its normal lifecycle even if this becomes false after expiry, withdrawal or replacement.
     */
    fun isCurrent(response: Response): Boolean {
        discardExpired(clock())
        val entry = response.advertisement
        return responses[entry.player] === response && entries[entry.player] === entry
    }

    /**
     * Records a completed trade once, keeping any unsold quantities advertised until their existing expiry.
     * Deliveries identify the original offers so total-price ratios remain intact. Old or expired responses never
     * subtract from a replacement advertisement. Accounting does not release ownership of the response slot.
     */
    fun recordCompleted(response: Response, deliveries: Map<PlayerTradeAdvertisement, Int>): Boolean {
        if (response.completed || !isCurrent(response)) return false
        val entry = response.advertisement
        if (!entry.consume(deliveries)) return false
        response.completed = true
        if (!entry.hasRemainingOffers) remove(entry.player)
        return true
    }

    /**
     * Releases only this exact attempt's response slot. Repeated or stale cleanup cannot affect a newer response.
     */
    fun release(response: Response): Boolean {
        val player = response.advertisement.player
        if (responses[player] !== response) return false
        responses.remove(player)
        return true
    }

    /**
     * Withdraws pending terms without interrupting an active response or any trade it has already opened.
     */
    fun remove(player: Player) {
        if (entries[player]?.player === player) entries.remove(player)
    }

    /**
     * Normalizes identity while retaining unknown names so unrelated vague offers cannot refresh each other.
     */
    private fun terms(offer: PlayerTradeAdvertisement): Terms = Terms(offer.itemIds,
        if (offer.itemIds.isEmpty()) offer.itemName.lowercase(Locale.ROOT).trim().replace(spaces, " ") else null,
        offer.quantity, offer.price, offer.direction)

    /**
     * Expires advertisements only; response ownership ends in the responding script's cleanup.
     */
    private fun discardExpired(now: Instant) {
        entries.entries.removeIf { !it.value.expiresAt.isAfter(now) }
    }
}
