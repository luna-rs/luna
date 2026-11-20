package api.bot.script

import com.google.gson.JsonObject
import java.util.UUID

/**
 * The original economic terms of a merchant's production batch.
 *
 * @author lare96
 */
data class ProductionSaleGuard(val itemId: Int, val minimumPrice: Long, val batchCost: Long, val startedAt: Long,
                               val identity: String = UUID.randomUUID().toString()) {
    init {
        require(itemId >= 0 && minimumPrice in 1..Int.MAX_VALUE.toLong() && batchCost >= 0 && startedAt > 0)
        require(UUID.fromString(identity).toString() == identity)
    }

    /**
     * Preserves the spending decision across pauses and account saves.
     */
    fun save(): JsonObject = JsonObject().also {
        it.addProperty("id", itemId)
        it.addProperty("minimum", minimumPrice)
        it.addProperty("cost", batchCost)
        it.addProperty("started", startedAt)
        it.addProperty("identity", identity)
    }

    /**
     * Allocates cost to a partial sale without multiplying the full basis by its quantity.
     */
    fun costFor(units: Int, totalUnits: Int): Long {
        require(totalUnits > 0 && units in 0..totalUnits)
        return batchCost / totalUnits * units + batchCost % totalUnits * units / totalUnits
    }

    companion object {
        /**
         * Restores the exact floor and cost of the admitted batch.
         */
        fun load(data: JsonObject): ProductionSaleGuard = ProductionSaleGuard(data.get("id").asInt,
            data.get("minimum").asLong, data.get("cost").asLong, data.get("started").asLong,
            data.get("identity").asString)

        /**
         * Computes a whole-GP break-even floor with a positive profit and conservative seller tax.
         */
        fun minimumGross(cost: Long, quantity: Long, taxed: Boolean): Long {
            if (cost !in 0 until Long.MAX_VALUE || quantity < 1) return Long.MAX_VALUE
            val net = cost / quantity + 1
            if (!taxed) return net
            val extra = (net - 1) / 19 + 1
            return ProductionEconomics.saturatingAdd(net, extra)
        }
    }
}
