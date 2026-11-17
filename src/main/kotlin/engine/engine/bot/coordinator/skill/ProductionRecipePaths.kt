package engine.bot.coordinator.skill

import api.bot.script.ProductionChainEconomics
import api.bot.script.ProductionEconomics
import api.bot.script.ProductionRecipe
import api.bot.script.ProductionStage

/**
 * Enumerates short native preparation paths through the existing recipe indexes.
 * Each stage performs the same number of operations: only deterministic single-item prefixes
 * can replace a one-unit input. Other ratios, uncertain intermediate yields and other skills stay separate.
 *
 * @author lare96
 */
internal object ProductionRecipePaths {
    private const val MAX_PATHS = 24
    private const val MAX_VISITS = 64

    /**
     * Returns the direct recipe first, followed by bounded native prefixes in dependency order.
     * Native eligibility belongs to the caller's description; this walk never acquires or creates supplies.
     */
    fun forRecipe(recipe: ProductionRecipe,
                  describe: (ProductionCatalogue.Entry) -> ProductionRecipe?,
                  preparation: (ProductionRecipe) -> Boolean = { true }): List<List<ProductionRecipe>> =
        expand(listOf(recipe), describe, preparation)

    /**
     * Shares one traversal allowance across alternative native recipes for the exact requested output.
     */
    fun forOutput(output: Int, skill: Int,
                  describe: (ProductionCatalogue.Entry) -> ProductionRecipe?,
                  preparation: (ProductionRecipe) -> Boolean = { true }): List<List<ProductionRecipe>> {
        val recipes = ProductionCatalogue.byOutput[output].orEmpty().take(16).mapNotNull { entry ->
            if (entry.skill != skill) null else describe(entry)?.takeIf { it.products[output] == 1 }
        }
        return expand(recipes, describe, preparation)
    }

    private fun expand(recipes: List<ProductionRecipe>,
                       describe: (ProductionCatalogue.Entry) -> ProductionRecipe?,
                       preparation: (ProductionRecipe) -> Boolean): List<List<ProductionRecipe>> {
        val result = ArrayList<List<ProductionRecipe>>()
        val queue = ArrayDeque<List<ProductionRecipe>>()
        val seen = HashSet<List<String>>()
        recipes.forEach { queue.add(listOf(it)) }
        var visits = 0
        while (queue.isNotEmpty() && result.size < MAX_PATHS && visits++ < MAX_VISITS) {
            val path = queue.removeFirst()
            if (!seen.add(path.map { it.key })) continue
            val aggregate = ProductionChainEconomics.aggregate(path.map { ProductionStage(it, 1) }) ?: continue
            result += path
            if (path.size >= ProductionChainEconomics.MAX_STAGES) continue
            for ((input, amount) in aggregate.materials) {
                if (amount != 1) continue
                for (entry in ProductionCatalogue.byOutput[input].orEmpty().take(16)) {
                    if (entry.skill != path.last().skill) continue
                    val prefix = describe(entry) ?: continue
                    if (prefix.skill != path.last().skill || !preparation(prefix) ||
                        prefix.productProbability != 1.0 || prefix.products != mapOf(input to 1) ||
                        path.any { it.key == prefix.key } ||
                        prefix.materials.keys.any { id -> path.any { id in it.products } }) continue
                    queue.add(listOf(prefix) + path)
                }
            }
        }
        return result
    }

    /**
     * Captures preparation costs in internal ceilings before the complete external basket is quoted.
     * Reusable tools remain in the complete basket's separate one-time charge; containers receive no credit.
     */
    fun pricePreparations(path: List<ProductionRecipe>, prices: MutableMap<Int, Long>) {
        for ((id, value) in carriedInputPrices(path, path.lastIndex, prices))
            if (id in prices) prices[id] = minOf(prices.getValue(id), value)
    }

    /**
     * Carries original material ceilings and native fees into deterministic single-item outputs.
     * Including the current stage accounts for partially completed work. Missing or overflowing costs
     * stop propagation; callers require actual ownership and compare executable disposal alternatives.
     */
    fun carriedInputPrices(path: List<ProductionRecipe>, currentStage: Int,
                           ceilings: Map<Int, Long>): Map<Int, Long> {
        if (path.isEmpty() || path.size > ProductionChainEconomics.MAX_STAGES ||
            currentStage !in path.indices || path.map { it.key }.distinct().size != path.size) return emptyMap()
        val prices = LinkedHashMap<Int, Long>()
        for (recipe in path.take(currentStage + 1)) {
            val output = recipe.products.entries.singleOrNull()?.takeIf { it.value == 1 } ?: return prices
            if (recipe.productProbability != 1.0) return prices
            var value = recipe.coinCost
            for ((id, amount) in recipe.materials) {
                val unit = prices[id] ?: ceilings[id] ?: return prices
                if (unit < 1L) return prices
                value = ProductionEconomics.saturatingAdd(value,
                    ProductionEconomics.saturatingMultiply(unit, amount.toLong()))
            }
            if (value !in 1 until Long.MAX_VALUE) return prices
            prices[output.key] = value
        }
        return prices
    }

    /**
     * Limits positive-XP preparation to awards reachable before the training goal ends.
     * Every prefix operation and all but the last final award must precede that boundary.
     * The existing quote may then impose a smaller next-level, inventory or purchasing limit.
     */
    fun trainingOperationLimit(path: List<ProductionRecipe>, requested: Int,
                               remainingExperience: Double): Int {
        if (path.isEmpty() || requested < 1 || !remainingExperience.isFinite() || remainingExperience <= 0.0) return 0
        val prefix = path.dropLast(1).sumOf { it.experience }
        var operations = requested.coerceAtMost(ProductionEconomics.MAX_OPERATIONS)
        if (prefix == 0.0) return operations
        val final = path.last().experience
        while (operations > 0 && prefix * operations + final * (operations - 1) >= remainingExperience) operations--
        return operations
    }

    /**
     * Repeats reachability for saved stages, whose current stage may be only partly finished.
     */
    fun trainingReachable(stages: List<ProductionStage>, remainingExperience: Double): Boolean {
        if (stages.isEmpty() || !remainingExperience.isFinite() || remainingExperience <= 0.0) return false
        val prefix = stages.dropLast(1).sumOf { it.recipe.experience * it.operations }
        if (prefix == 0.0) return true
        val final = stages.last()
        return prefix + final.recipe.experience * (final.operations - 1) < remainingExperience
    }

    /**
     * A supplied suffix of the same native lineage takes precedence over repeating its raw preparation.
     * Unrelated recipes with the same output are not treated as interchangeable work.
     */
    fun hasSuppliedSuffix(path: List<ProductionRecipe>, supplied: Collection<List<ProductionRecipe>>): Boolean =
        supplied.any { suffix ->
            suffix.isNotEmpty() && suffix.size < path.size &&
                path.takeLast(suffix.size).map { it.key } == suffix.map { it.key }
        }
}
