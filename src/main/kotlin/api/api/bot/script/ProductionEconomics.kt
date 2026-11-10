package api.bot.script

import kotlin.math.ceil

/**
 * Native quantities and experience for one production operation, independent of owned stock.
 * Tools are retained rather than counted as consumed materials. Catalogues derive these values from
 * player recipes and retain their own spellbook, route, quest and secondary-skill eligibility checks.
 *
 * @author lare96
 */
data class ProductionRecipe(
    val key: String,
    val skill: Int,
    val level: Int,
    val materials: Map<Int, Int>,
    val tools: Set<Int>,
    val products: Map<Int, Int>,
    val experience: Double,
    val productProbability: Double = 1.0,
    val coinCost: Long = 0L
) {
    init {
        require(key.isNotBlank() && key.length <= 128)
        require(skill >= 0 && level in 1..99)
        require(materials.isNotEmpty() && materials.size <= 8 && products.size <= 8 && tools.size <= 8)
        require((materials.entries + products.entries).all { (id, amount) -> id >= 0 && amount > 0 })
        require(tools.all { it >= 0 && it !in materials })
        require(experience.isFinite() && experience >= 0.0)
        require(productProbability.isFinite() && productProbability in 0.0..1.0)
        require(coinCost >= 0L)
    }
}

/**
 * Prices finite production goals using complete input opportunity costs and recoverable output values.
 * Output values are supplied by the caller: an advertised or seeded guide alone is not sale evidence.
 * No inventory, shop, player or world iteration takes place in this policy.
 *
 * @author lare96
 */
object ProductionEconomics {
    /**
     * A single plan cannot purchase an indefinite training or resale stockpile.
     */
    const val MAX_OPERATIONS = 100

    /**
     * The affordable complete recipe basket and its immutable per-unit purchase ceilings.
     * Output value is the greater of a real exit and a validated personal use, rather than two cash revenues.
     * Missing tools and native processing fees are fully charged instead of assumed to be free.
     *
     * @author lare96
     */
    data class Quote(
        val operations: Int,
        val maximumCoins: Long,
        val maximumUnitPrices: Map<Int, Long>,
        val inputValue: Long,
        val outputValue: Long,
        val maximumLossPerOperation: Long
    )

    /**
     * Selects the largest useful goal that fits real coins and conservative recipe economics.
     * Input ceilings include the caller's complete opportunity-cost estimate even for already owned inputs.
     * Training can pay for native XP within the existing alchemy allowance; profit requires a positive margin.
     * Zero-XP preparation therefore needs a profitable exit or a separately validated downstream recipe.
     */
    fun quote(
        recipe: ProductionRecipe,
        training: Boolean,
        requestedOperations: Int,
        remainingExperience: Double,
        owned: Map<Int, Long>,
        maximumCoins: Long,
        inputUnitPrices: Map<Int, Long>,
        recoverableOutputPrices: Map<Int, Long>,
        personalUsePrices: Map<Int, Long> = emptyMap()
    ): Quote? {
        if (requestedOperations < 1 || maximumCoins < 0L || !remainingExperience.isFinite()) return null
        if (training && (recipe.experience <= 0.0 || remainingExperience <= 0.0)) return null
        val ids = recipe.materials.keys + recipe.tools
        if (ids.any { inputUnitPrices[it] == null || inputUnitPrices.getValue(it) < 1L }) return null
        val materialValue = value(recipe.materials, inputUnitPrices) ?: return null
        val input = saturatingAdd(materialValue, recipe.coinCost)
        if (input == Long.MAX_VALUE) return null
        val usefulOutput = recipe.products.keys.associateWith { id ->
            maxOf(recoverableOutputPrices[id] ?: 0L, personalUsePrices[id] ?: 0L)
        }
        val output = ((value(recipe.products, usefulOutput, allowMissing = true)
            ?: return null) * recipe.productProbability).toLong().coerceAtLeast(0L)
        val permittedLoss = if (training)
            (recipe.experience * AlchemyPolicy.MAX_TRAINING_GP_PER_XP).toLong().coerceAtLeast(0L) else 0L
        if (training) {
            if (input > saturatingAdd(output, permittedLoss)) return null
        } else if (output <= input) return null

        var limit = requestedOperations.coerceAtMost(MAX_OPERATIONS)
        if (training) limit = minOf(limit,
            ceil(remainingExperience / recipe.experience).coerceIn(1.0, MAX_OPERATIONS.toDouble()).toInt())
        val toolCost = recipe.tools.fold(0L) { total, id ->
            saturatingAdd(total, if ((owned[id] ?: 0L) > 0L) 0L else inputUnitPrices.getValue(id))
        }
        fun purchaseCost(operations: Int): Long {
            var cost = saturatingAdd(toolCost, saturatingMultiply(recipe.coinCost, operations.toLong()))
            for ((id, amount) in recipe.materials) {
                val missing = (amount.toLong() * operations - (owned[id] ?: 0L).coerceAtLeast(0L)).coerceAtLeast(0L)
                cost = saturatingAdd(cost, saturatingMultiply(missing, inputUnitPrices.getValue(id)))
            }
            return cost
        }
        fun economicallyUseful(operations: Int): Boolean {
            val allowance = saturatingMultiply(saturatingAdd(output, permittedLoss), operations.toLong())
            val cost = saturatingAdd(saturatingMultiply(input, operations.toLong()), toolCost)
            return if (training) cost <= allowance else cost < allowance
        }
        // Affordability is monotone; at most seven checks cover the complete one-hundred-operation limit.
        var operations = 0
        var upper = limit
        while (operations < upper) {
            val candidate = operations + (upper - operations + 1) / 2
            val cost = purchaseCost(candidate)
            if (cost < Long.MAX_VALUE && cost <= maximumCoins) operations = candidate else upper = candidate - 1
        }
        if (operations < 1 || !economicallyUseful(operations)) return null
        return Quote(operations, purchaseCost(operations), ids.associateWith { inputUnitPrices.getValue(it) },
            input, output, permittedLoss)
    }

    private fun value(amounts: Map<Int, Int>, prices: Map<Int, Long>, allowMissing: Boolean = false): Long? {
        var total = 0L
        for ((id, amount) in amounts) {
            val price = prices[id] ?: if (allowMissing) 0L else return null
            if (price < 0L) return null
            val cost = saturatingMultiply(price, amount.toLong())
            if (cost == Long.MAX_VALUE || total > Long.MAX_VALUE - cost) return null
            total += cost
        }
        return total
    }

    internal fun saturatingAdd(first: Long, second: Long): Long =
        if (first > Long.MAX_VALUE - second) Long.MAX_VALUE else first + second

    internal fun saturatingMultiply(first: Long, second: Long): Long =
        if (first != 0L && second > Long.MAX_VALUE / first) Long.MAX_VALUE else first * second
}
