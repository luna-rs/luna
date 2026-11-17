package api.bot.script

import com.google.gson.JsonObject
import java.util.UUID

/**
 * One finite native equipment outing whose time, engagements and owned-supply exposure survive saved sessions.
 * A failed or empty-handed outing retains real cargo and never creates an item, payment, assignment or experience.
 *
 * @author lare96
 */
class EquipmentDropPlan(val sourceKey: String, val itemId: Int, val personal: Boolean, val startingStock: Int,
                        val deadline: Long, val maximumCoins: Long, val supplies: Map<Int, Int>,
                        val unitPrices: Map<Int, Long>, val startingCoins: Long,
                        val identity: String = UUID.randomUUID().toString(), var engagements: Int = 0,
                        var kills: Int = 0, var spentCoins: Long = 0L,
                        consumed: Map<Int, Int> = emptyMap(), remaining: Map<Int, Int> = supplies,
                        lastOwned: Map<Int, Int> = supplies, private var lastCoins: Long = startingCoins,
                        val startedAt: Long = deadline - MAX_MILLIS, val minimumBid: Int = 0) {
    val consumed = consumed.toMutableMap()
    private val remaining = remaining.toMutableMap()
    private val lastOwned = lastOwned.toMutableMap()
    var ended = false
        private set
    val stockTarget: Int get() = startingStock + 1
    val exposure: Long get() = spentCoins + consumed.entries.sumOf { (id, amount) ->
        amount.toLong() * (unitPrices[id] ?: 0L)
    }

    init {
        require(sourceKey.length in 1..160 && itemId in 0..0xffff && startingStock in 0 until Int.MAX_VALUE)
        require(startedAt >= 0L && deadline > startedAt && deadline - startedAt <= MAX_MILLIS)
        require(maximumCoins in 0..Int.MAX_VALUE.toLong() && startingCoins in 0..Int.MAX_VALUE.toLong())
        require(supplies.isNotEmpty() && supplies.size <= 32 && supplies.all { (id, amount) -> id in 0..0xffff && amount in 1..100 })
        require(unitPrices.keys == supplies.keys && unitPrices.values.all { it in 0..Int.MAX_VALUE.toLong() })
        require(engagements in 0..MAX_KILLS && kills in 0..engagements && spentCoins in 0..Int.MAX_VALUE.toLong())
        require(this.remaining.keys == supplies.keys && this.remaining.all { (id, amount) -> amount in 0..supplies.getValue(id) })
        require(this.consumed.all { (id, amount) -> id in supplies && amount in 0..supplies.getValue(id) })
        require(supplies.all { (id, amount) -> this.remaining.getValue(id) + (this.consumed[id] ?: 0) == amount })
        require(this.lastOwned.keys == supplies.keys && this.lastOwned.values.all { it >= 0 })
        require(lastCoins in 0..Int.MAX_VALUE.toLong() && minimumBid >= 0 && (personal || minimumBid > 0))
        require(UUID.fromString(identity).toString() == identity)
    }

    /**
     * Charges decreases in physical owned stock once; banking cannot reset the allowance or credit later side loot.
     */
    fun observe(stock: Map<Int, Int>, coins: Long) {
        for ((id, before) in lastOwned.toMap()) {
            val now = stock[id]?.coerceAtLeast(0) ?: 0
            val decrease = (before - now).coerceAtLeast(0)
            if (decrease > 0) consumed[id] = ((consumed[id] ?: 0) + decrease).coerceAtMost(supplies.getValue(id))
            remaining[id] = supplies.getValue(id) - (consumed[id] ?: 0)
            lastOwned[id] = now
        }
        spentCoins = (spentCoins + (lastCoins - coins).coerceAtLeast(0L)).coerceAtMost(Int.MAX_VALUE.toLong())
        lastCoins = coins.coerceIn(0L, Int.MAX_VALUE.toLong())
    }

    fun mayContinue(now: Long): Boolean = !ended && now < deadline && kills < MAX_KILLS &&
        exposure <= maximumCoins && consumed.none { (id, amount) -> amount >= supplies.getValue(id) }

    fun engage(): Boolean {
        if (engagements >= MAX_KILLS || ended) return false
        engagements++
        return true
    }

    fun creditedKill() { if (!ended && kills < engagements) kills++ }
    fun end() { ended = true }
    fun remainingSupply(id: Int): Int = remaining[id] ?: 0

    fun save(): JsonObject = JsonObject().also { data ->
        data.addProperty("source", sourceKey); data.addProperty("item", itemId)
        data.addProperty("personal", personal); data.addProperty("starting_stock", startingStock)
        data.addProperty("deadline", deadline); data.addProperty("maximum_coins", maximumCoins)
        data.addProperty("starting_coins", startingCoins); data.addProperty("identity", identity)
        data.addProperty("engagements", engagements); data.addProperty("kills", kills)
        data.addProperty("spent_coins", spentCoins); data.addProperty("ended", ended)
        data.addProperty("started_at", startedAt); data.addProperty("minimum_bid", minimumBid)
        data.addProperty("last_coins", lastCoins)
        fun counts(values: Map<Int, out Number>) = JsonObject().also { obj ->
            values.forEach { (id, amount) -> obj.addProperty(id.toString(), amount) }
        }
        data.add("supplies", counts(supplies)); data.add("unit_prices", counts(unitPrices))
        data.add("consumed", counts(consumed)); data.add("remaining", counts(remaining))
        data.add("last_owned", counts(lastOwned))
    }

    companion object {
        const val MAX_KILLS = 40
        const val MAX_MILLIS = 10 * 60_000L

        fun load(data: JsonObject): EquipmentDropPlan {
            fun longs(name: String): Map<Int, Long> = data.getAsJsonObject(name).also { require(it.size() <= 32) }
                .entrySet().associate { it.key.toInt() to it.value.asLong }
            fun ints(name: String) = longs(name).mapValues { (_, amount) ->
                require(amount in 0..Int.MAX_VALUE.toLong()); amount.toInt()
            }
            return EquipmentDropPlan(data["source"].asString, data["item"].asInt, data["personal"].asBoolean,
                data["starting_stock"].asInt, data["deadline"].asLong, data["maximum_coins"].asLong,
                ints("supplies"), longs("unit_prices"), data["starting_coins"].asLong,
                data["identity"].asString, data["engagements"].asInt, data["kills"].asInt,
                data["spent_coins"].asLong, ints("consumed"), ints("remaining"), ints("last_owned"),
                data["last_coins"].asLong, data["started_at"].asLong, data["minimum_bid"].asInt).also {
                if (data["ended"]?.asBoolean == true) it.end()
            }
        }
    }
}
