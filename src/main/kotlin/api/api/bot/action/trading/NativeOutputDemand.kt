package api.bot.action.trading

import game.bot.scripts.trading.TradeBotScript
import io.luna.game.model.World
import io.luna.game.model.mob.bot.Bot
import java.util.TreeSet
import java.util.WeakHashMap

/**
 * Counts bounded native buying visits and shares unlisted commitments across profitable activities.
 * Prepared traders still revalidate the actual payment and stock through the existing trade screens.
 *
 * @author lare96
 */
internal object NativeOutputDemand {
    private const val MAX_TRADERS = 32
    private const val MAX_LEASES = 16_384
    private const val MAX_OUTPUTS = 64
    private const val EXPIRATIONS_PER_CHECK = 32

    /**
     * The cumulative native demand available at or above this unit price.
     *
     * @author lare96
     */
    data class Depth(val quantity: Int, val unitPrice: Long)

    /**
     * One finite account's unlisted output, handed between the work and sale scripts.
     *
     * @author lare96
     */
    private data class Lease(val world: World, val owner: String, val identity: String,
                             val quantities: Map<Int, Long>, val deadline: Long)

    /**
     * One tick's indicative price index, derived from the prepared trading visits.
     *
     * @author lare96
     */
    private data class CandidateSnapshot(val tick: Long, val prices: Map<Int, Long>)

    private val leases = HashMap<String, Lease>()
    private val pressure = HashMap<World, MutableMap<Int, Long>>()
    private val deadlines = TreeSet<Lease>(compareBy<Lease> { it.deadline }.thenBy { it.identity })
    private val candidates = WeakHashMap<World, CandidateSnapshot>()

    fun traders(bot: Bot): Sequence<TradeBotScript> = traders(bot.world, bot)

    private fun traders(world: World, excluding: Bot? = null): Sequence<TradeBotScript> = BotMarketplace.entries.asSequence()
        .flatMap { it.tradingSessions().asSequence() }.filterIsInstance<TradeBotScript>()
        .filter { it.bot !== excluding && it.bot.world === world }.take(MAX_TRADERS)

    fun candidateIds(world: World): List<Int> = candidatePrices(world).keys.toList()

    fun peakPrice(world: World, id: Int): Long = candidatePrices(world)[id] ?: 0L

    private fun candidatePrices(world: World): Map<Int, Long> {
        val tick = world.currentTick
        val snapshot = candidates[world]
        if (snapshot?.tick == tick) return snapshot.prices
        val prices = LinkedHashMap<Int, Long>()
        for (trader in traders(world)) {
            val bid = trader.nativeProfitBid() ?: continue
            val id = bid.item.id
            prices[id] = maxOf(prices[id] ?: 0L, bid.price)
        }
        candidates[world] = CandidateSnapshot(tick, prices)
        return prices
    }

    fun depth(bot: Bot, id: Int, now: Long = System.currentTimeMillis(), excluding: String? = null): List<Depth> {
        val buyers = HashMap<String, BotTradeItem>()
        val suppliers = ArrayList<BotTradeItem>()
        for (trader in traders(bot)) {
            trader.profitGatheringPurchase(id)?.let { buyers[trader.bot.username] = it }
            trader.profitGatheringSupply(id)?.let(suppliers::add)
        }
        val promised = promised(bot.world, id, now, excluding)
        var units = 0L
        return buyers.values.groupBy { it.price }.toSortedMap(reverseOrder()).mapNotNull { (price, quotes) ->
            units += quotes.sumOf { it.item.amount.toLong() }
            val advertised = suppliers.filter { it.price <= price }.sumOf { it.item.amount.toLong() }
            val available = (units - advertised - promised).coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
            if (available > 0) Depth(available, price) else null
        }
    }

    /**
     * Starts or enlarges a commitment only when all output has current native buyers.
     * A failed increase preserves the earlier commitment.
     */
    fun reserve(bot: Bot, identity: String, quantities: Map<Int, Long>, deadline: Long,
                minimumPrices: Map<Int, Long> = emptyMap()): Boolean {
        val now = System.currentTimeMillis()
        if (!valid(bot, identity, quantities, deadline, now) || quantities.isEmpty()) return false
        if (quantities.any { (id, quantity) ->
                depth(bot, id, now, identity).none {
                    it.quantity >= quantity && it.unitPrice >= (minimumPrices[id] ?: 1L) &&
                        it.unitPrice >= (BotTradeManager.MINIMUM_OFFER_VALUE + quantity - 1L) / quantity
                }
            }) return false
        replace(bot, identity, quantities, deadline)
        return true
    }

    /**
     * Tracks already committed work or owned cargo through a later sale. An empty basket releases the lease.
     */
    fun track(bot: Bot, identity: String, quantities: Map<Int, Long>, deadline: Long): Boolean {
        if (quantities.isEmpty()) {
            val previous = leases[identity] ?: return true
            if (previous.world !== bot.world || previous.owner != bot.username) return false
            release(identity)
            return true
        }
        if (!valid(bot, identity, quantities, deadline, System.currentTimeMillis())) return false
        replace(bot, identity, quantities, deadline)
        return true
    }

    fun promised(world: World, id: Int, now: Long, excluding: String? = null): Long {
        expire(now)
        val own = excluding?.let(leases::get)?.takeIf { it.world === world && it.deadline > now }
        return ((pressure[world]?.get(id) ?: 0L) - (own?.quantities?.get(id) ?: 0L)).coerceAtLeast(0L)
    }

    fun release(identity: String) {
        val previous = leases.remove(identity) ?: return
        deadlines.remove(previous)
        adjust(previous, -1)
    }

    fun expire(now: Long = System.currentTimeMillis()) {
        repeat(EXPIRATIONS_PER_CHECK) {
            val earliest = deadlines.firstOrNull() ?: return
            if (earliest.deadline > now) return
            release(earliest.identity)
        }
    }

    private fun valid(bot: Bot, identity: String, quantities: Map<Int, Long>, deadline: Long, now: Long): Boolean {
        expire(now)
        val previous = leases[identity]
        val capacity = if (previous == null) leases.size < MAX_LEASES else
            previous.world === bot.world && previous.owner == bot.username
        return capacity && identity.isNotBlank() && deadline > now && quantities.size <= MAX_OUTPUTS &&
            quantities.all { (id, quantity) -> id >= 0 && quantity in 1..Int.MAX_VALUE.toLong() }
    }

    private fun replace(bot: Bot, identity: String, quantities: Map<Int, Long>, deadline: Long) {
        release(identity)
        val lease = Lease(bot.world, bot.username, identity, quantities.toMap(), deadline)
        leases[identity] = lease
        deadlines.add(lease)
        adjust(lease, 1)
    }

    private fun adjust(lease: Lease, direction: Int) {
        val byItem = pressure.getOrPut(lease.world) { HashMap() }
        for ((id, quantity) in lease.quantities) {
            val amount = (byItem[id] ?: 0L) + direction * quantity
            if (amount > 0) byItem[id] = amount else byItem.remove(id)
        }
        if (byItem.isEmpty()) pressure.remove(lease.world)
    }
}
