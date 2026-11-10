package api.bot.script

/**
 * One native stage and its finite number of operations in an explicitly validated production chain.
 *
 * @author lare96
 */
data class ProductionStage(val recipe: ProductionRecipe, val operations: Int) {
    init {
        require(operations in 1..ProductionEconomics.MAX_OPERATIONS)
    }
}

/**
 * Costs a short native production chain without fabricating experience for its preparation stages.
 * Intermediate products pay for later inputs once; only final leftovers remain recoverable output.
 * All stages are supplied explicitly from native recipes, without recursive world or inventory searches.
 *
 * @author lare96
 */
object ProductionChainEconomics {
    const val MAX_STAGES = 6

    /**
     * Returns the complete external basket, reusable tools, final output and native XP for this finite chain.
     * Uncertain intermediate yields are rejected because they cannot guarantee the promised downstream work.
     */
    fun aggregate(stages: List<ProductionStage>): ProductionRecipe? {
        if (stages.isEmpty() || stages.size > MAX_STAGES ||
            stages.map { it.recipe.key }.distinct().size != stages.size) return null
        if (stages.dropLast(1).any { it.recipe.productProbability != 1.0 }) return null
        val external = LinkedHashMap<Int, Long>()
        val products = LinkedHashMap<Int, Long>()
        val tools = LinkedHashSet<Int>()
        var experience = 0.0
        var coinCost = 0L
        for ((recipe, operations) in stages) {
            tools += recipe.tools
            for ((id, amount) in recipe.materials) {
                val required = amount.toLong() * operations
                val available = products[id] ?: 0L
                val used = minOf(required, available)
                if (used > 0L) {
                    if (used == available) products.remove(id) else products[id] = available - used
                }
                val missing = required - used
                if (missing > 0L) external[id] = (external[id] ?: 0L) + missing
            }
            recipe.products.forEach { (id, amount) ->
                products[id] = (products[id] ?: 0L) + amount.toLong() * operations
            }
            experience += recipe.experience * operations
            coinCost = ProductionEconomics.saturatingAdd(coinCost,
                ProductionEconomics.saturatingMultiply(recipe.coinCost, operations.toLong()))
        }
        if (external.isEmpty() || external.size > 8 || products.size > 8 || tools.size > 8 ||
            external.any { (_, amount) -> amount > Int.MAX_VALUE } ||
            products.any { (_, amount) -> amount > Int.MAX_VALUE } || tools.any { it in external } ||
            !experience.isFinite() || coinCost == Long.MAX_VALUE) return null
        val first = stages.first().recipe
        return ProductionRecipe("chain:${first.key}:${stages.last().recipe.key}".take(128), first.skill, first.level,
            external.mapValues { it.value.toInt() }, tools, products.mapValues { it.value.toInt() }, experience,
            stages.last().recipe.productProbability, coinCost)
    }
}
