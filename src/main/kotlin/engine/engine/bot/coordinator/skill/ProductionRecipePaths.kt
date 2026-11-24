package engine.bot.coordinator.skill

import api.bot.script.ProductionChainEconomics
import api.bot.script.ProductionEconomics
import api.bot.script.ProductionRecipe
import api.bot.script.ProductionStage

/**
 * Enumerates short native preparation paths through the existing recipe indexes.
 * Stage counts express whole native operations for one final operation. Intermediate production must
 * reliably provide the exact requirement, without leaving stock that the remaining stages cannot use.
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
                  allowOtherSkills: Boolean = false,
                  trainingPrerequisites: Boolean = false,
                  preparation: (ProductionRecipe) -> Boolean = { true }): List<List<ProductionStage>> =
        expand(listOf(recipe), { id ->
            nativeRecipes(id, recipe.skill.takeUnless { allowOtherSkills || trainingPrerequisites }, describe)
        }, allowOtherSkills, preparation, trainingPrerequisites)

    /**
     * Shares one traversal allowance across alternative native recipes for the exact requested output.
     */
    fun forOutput(output: Int, skill: Int,
                  describe: (ProductionCatalogue.Entry) -> ProductionRecipe?,
                  allowOtherSkills: Boolean = false,
                  preparation: (ProductionRecipe) -> Boolean = { true }): List<List<ProductionStage>> =
        expand(nativeRecipes(output, skill, describe).filter { it.products[output] == 1 },
            { id -> nativeRecipes(id, skill.takeUnless { allowOtherSkills }, describe) }, allowOtherSkills, preparation)

    private fun nativeRecipes(id: Int, skill: Int?,
                              describe: (ProductionCatalogue.Entry) -> ProductionRecipe?): List<ProductionRecipe> =
        ProductionCatalogue.byOutput[id].orEmpty().take(16).mapNotNull { entry ->
            if (skill == null || entry.skill == skill) describe(entry) else null
        }

    /**
     * Builds bounded dependency paths using only indexed, executable native recipes for each input.
     */
    internal fun expand(recipes: List<ProductionRecipe>, nativeRecipes: (Int) -> List<ProductionRecipe>,
                        allowOtherSkills: Boolean = false,
                        preparation: (ProductionRecipe) -> Boolean = { true },
                        trainingPrerequisites: Boolean = false): List<List<ProductionStage>> {
        val result = ArrayList<List<ProductionStage>>()
        val queue = ArrayDeque<List<ProductionStage>>()
        val seen = HashSet<List<Pair<String, Int>>>()
        val producers = HashMap<Int, List<ProductionRecipe>>()
        fun enqueue(path: List<ProductionStage>) {
            if (seen.size < MAX_VISITS && seen.add(path.map { it.recipe.key to it.operations })) queue.add(path)
        }
        recipes.forEach { enqueue(listOf(ProductionStage(it, 1))) }
        while (queue.isNotEmpty() && result.size < MAX_PATHS) {
            val path = queue.removeFirst()
            val aggregate = ProductionChainEconomics.aggregate(path) ?: continue
            result += path
            if (path.size >= ProductionChainEconomics.MAX_STAGES || seen.size >= MAX_VISITS) continue
            for ((input, amount) in aggregate.materials) {
                for (prefix in producers.getOrPut(input) { nativeRecipes(input).take(16) }) {
                    val consumers = path.filter { input in it.recipe.materials }
                    val explicit = trainingPrerequisites && consumers.all {
                        ProductionTrainingPrerequisites.permits(it.recipe, input, prefix)
                    }
                    val sameSkill = if (trainingPrerequisites) consumers.all { it.recipe.skill == prefix.skill }
                        else prefix.skill == path.last().recipe.skill
                    if ((!allowOtherSkills && !sameSkill && !explicit) || !preparation(prefix) ||
                        prefix.productProbability != 1.0 || input in prefix.materials ||
                        path.any { it.recipe.key == prefix.key } ||
                        prefix.materials.keys.any { id -> path.any { id in it.recipe.products } }) continue
                    val yield = prefix.products.entries.singleOrNull()?.takeIf { it.key == input }?.value ?: continue
                    val scale = when {
                        amount % yield == 0 -> 1
                        explicit -> yield / gcd(amount, yield)
                        else -> continue
                    }
                    val nativeOperations = amount.toLong() * scale / yield
                    val maximum = ProductionEconomics.MAX_OPERATIONS.toLong()
                    if (nativeOperations > maximum || path.any { it.operations.toLong() * scale > maximum }) continue
                    enqueue(listOf(ProductionStage(prefix, nativeOperations.toInt())) +
                        path.map { it.copy(operations = it.operations * scale) })
                }
            }
        }
        return result
    }

    private tailrec fun gcd(a: Int, b: Int): Int = if (b == 0) a else gcd(b, a % b)

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
            val output = recipe.products.entries.singleOrNull() ?: return prices
            if (recipe.productProbability != 1.0) return prices
            var value = recipe.coinCost
            for ((id, amount) in recipe.materials) {
                val unit = prices[id] ?: ceilings[id] ?: return prices
                if (unit < 1L) return prices
                value = ProductionEconomics.saturatingAdd(value,
                    ProductionEconomics.saturatingMultiply(unit, amount.toLong()))
            }
            if (value !in 1 until Long.MAX_VALUE) return prices
            prices[output.key] = 1 + (value - 1) / output.value
        }
        return prices
    }

    /**
     * Limits positive-XP preparation to awards reachable before the training goal ends.
     * Every prefix operation and all but the last final award must precede that boundary.
     * The existing quote may then impose a smaller next-level, inventory or purchasing limit.
     */
    fun trainingOperationLimit(path: List<ProductionStage>, requested: Int,
                               remainingExperience: Double): Int {
        if (path.isEmpty() || requested < 1 || !remainingExperience.isFinite() || remainingExperience <= 0.0) return 0
        val maximum = requested.coerceAtMost(ProductionEconomics.operationLimit(path.last().recipe.skill, training = true))
        if (path.size == 1 && path.single().operations == 1) return maximum
        val final = path.last().recipe
        val perBatch = trainingExperience(path)
        return (1..maximum).lastOrNull { perBatch * it - final.experience < remainingExperience } ?: 0
    }

    /**
     * Repeats reachability for saved stages, whose current stage may be only partly finished.
     */
    fun trainingReachable(stages: List<ProductionStage>, remainingExperience: Double): Boolean {
        if (stages.isEmpty() || !remainingExperience.isFinite() || remainingExperience <= 0.0) return false
        if (stages.size == 1) return true
        return trainingExperience(stages) - stages.last().recipe.experience < remainingExperience
    }

    /**
     * Only experience in the final goal's skill contributes to training value and its experience boundary.
     */
    fun trainingExperience(stages: List<ProductionStage>): Double {
        val skill = stages.last().recipe.skill
        return stages.filter { it.recipe.skill == skill }.sumOf { it.recipe.experience * it.operations }
    }

    /**
     * A supplied suffix of the same native lineage takes precedence over repeating its raw preparation.
     * Unrelated recipes with the same output are not treated as interchangeable work.
     */
    fun hasSuppliedSuffix(path: List<ProductionStage>, supplied: Collection<List<ProductionStage>>): Boolean =
        supplied.any { suffix ->
            suffix.isNotEmpty() && suffix.size < path.size &&
                path.takeLast(suffix.size) == suffix
        }
}
