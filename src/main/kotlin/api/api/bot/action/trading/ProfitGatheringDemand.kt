package api.bot.action.trading

import api.bot.script.ProfitGatheringGoal
import io.luna.game.model.World
import io.luna.game.model.mob.bot.Bot
import game.bot.scripts.trading.TradeBotScript
import java.util.TreeSet
import io.luna.util.RandomUtils
import kotlin.math.ln1p
import kotlin.math.pow

/**
 * Prices existing native gathering against bounded funded consumer quotes and actual competing stock.
 * These are conservative activity-boundary hints; native transactions still validate their own funding.
 *
 * @author lare96
 */
internal object ProfitGatheringDemand {
    /**
     * One output's remaining demand after owned, listed and prospective supply.
     *
     * @author lare96
     */
    data class Quote(val itemId: Int, val quantity: Int, val minimumUnitPrice: Long, val score: Double)

    /**
     * A finite output promise, released by its script or its original deadline.
     *
     * @author lare96
     */
    private data class Lease(val bot: Bot, val goal: ProfitGatheringGoal, val quantity: Int, val held: Long)

    private val leases = HashMap<String, Lease>()
    private val pressure = HashMap<Pair<World, Int>, Long>()
    private val expiry = TreeSet<Lease>(compareBy<Lease> { it.goal.deadline }.thenBy { it.goal.identity })

    /**
     * Keeps personality variation among economically useful alternatives rather than level ranking.
     */
    fun <T> select(bot: Bot, options: Map<T, Quote>): T? = RandomUtils.weightedRoll(options.mapValues {
        ln1p(it.value.score).pow(0.5 + bot.personality.intelligence.coerceIn(0.0, 1.0) +
            bot.personality.dexterity.coerceIn(0.0, 1.0) * 0.25)
    })

    internal fun capacity(demand: Long, supply: Long, owned: Long, promised: Long): Int =
        (demand.coerceAtLeast(0L) - supply.coerceAtLeast(0L) - owned.coerceAtLeast(0L) -
            promised.coerceAtLeast(0L)).coerceIn(0L, 56L).toInt()

    fun inputPrice(bot: Bot, id: Int): Double = bot.world.economy.getPrice(id).toDouble().coerceAtLeast(0.0)

    /**
     * Reads at most thirty-two leased advertisers and sixteen private quotes per side, with no player scan.
     */
    fun quote(bot: Bot, id: Int, ticks: Double = 1.0, unitCost: Double = 0.0,
              minimumUnitPrice: Long = 1L, excluding: String? = null): Quote? {
        if (!ticks.isFinite() || ticks <= 0.0 || !unitCost.isFinite() || unitCost < 0.0) return null
        prune(System.currentTimeMillis())
        val buyers = HashMap<String, Pair<Long, Int>>()
        val suppliers = ArrayList<Pair<Long, Int>>()
        val traders = BotMarketplace.entries.asSequence().flatMap { it.tradingSessions().asSequence() }
            .filterIsInstance<TradeBotScript>().take(32)
        for (trader in traders) {
            if (trader.bot === bot) continue
            trader.profitGatheringPurchase(id)?.let { bid ->
                buyers[trader.bot.username.lowercase()] = bid.price to bid.item.amount
            }
            trader.profitGatheringSupply(id)?.let { suppliers += it.price to it.item.amount }
        }

        val eligible = buyers.values.filter { it.first >= minimumUnitPrice && it.second > 0 }
        if (eligible.isEmpty()) return null
        val price = eligible.minOf { it.first }
        val demand = eligible.sumOf { it.second.toLong() }
        val supply = suppliers.filter { it.first <= price }.sumOf { it.second.toLong() }
        val advertised = suppliers.sumOf { it.second.toLong() }
        val key = bot.world to id
        val ownLease = excluding?.let(leases::get)?.takeIf { it.bot.world === bot.world && it.goal.itemId == id }
        val promised = ((pressure[key] ?: 0L) - (ownLease?.let { it.quantity + it.held } ?: 0L)).coerceAtLeast(0L)
        val quantity = capacity(demand, supply, owned(bot, id), promised)
        if (price * quantity < BotTradeManager.MINIMUM_OFFER_VALUE) return null
        val proceeds = price * 1.0 - unitCost
        if (quantity < 1 || !proceeds.isFinite() || proceeds <= 0.0) return null
        return Quote(id, quantity, minimumUnitPrice.coerceAtLeast((unitCost / 1.0).toLong() + 1L),
            proceeds / ticks * quantity.toDouble() / (quantity + advertised + owned(bot, id) + promised).coerceAtLeast(1L))
    }

    /**
     * Includes native notes and any private order escrow or pending delivery in the owner's existing supply.
     */
    fun owned(bot: Bot, id: Int): Long {
        val physical = ProfitGatheringGoal.physical(bot, id)
        return physical
    }

    private fun unlisted(bot: Bot, id: Int): Long = ProfitGatheringGoal.physical(bot, id)

    fun reserve(bot: Bot, goal: ProfitGatheringGoal, quantity: Int): Boolean {
        release(goal.identity)
        if (quantity < 1 || leases.size >= 16_384 || goal.deadline <= System.currentTimeMillis()) return false
        val held = unlisted(bot, goal.itemId)
        val lease = Lease(bot, goal, quantity, held)
        leases[goal.identity] = lease
        expiry.add(lease)
        val key = bot.world to goal.itemId
        pressure[key] = (pressure[key] ?: 0L) + quantity + held
        return true
    }

    fun release(identity: String) {
        val previous = leases.remove(identity) ?: return
        expiry.remove(previous)
        val key = previous.bot.world to previous.goal.itemId
        val remaining = (pressure[key] ?: 0L) - previous.quantity - previous.held
        if (remaining > 0L) pressure[key] = remaining else pressure.remove(key)
    }

    private fun prune(now: Long) {
        repeat(32) {
            val lease = expiry.firstOrNull() ?: return
            if (lease.goal.deadline > now) return
            release(lease.goal.identity)
        }
    }
}
