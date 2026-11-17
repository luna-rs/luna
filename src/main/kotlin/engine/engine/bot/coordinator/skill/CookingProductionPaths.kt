package engine.bot.coordinator.skill

import api.bot.script.ProductionChainEconomics
import api.bot.script.ProductionRecipe
import api.bot.script.ProductionEconomics
import api.bot.script.ProductionStage
import api.predef.SKILL_COOKING
import game.player.item.consume.food.Food

/**
 * Finds short native Cooking paths for an actual edible output without requiring intermediate listings.
 * Only deterministic, zero-experience, one-item preparation can precede the final native recipe.
 * Other skills, gathering, uncertain intermediate cooking and changed production ratios remain excluded.
 *
 * @author lare96
 */
internal object CookingProductionPaths {
    private const val MAX_PATHS = 24
    private const val MAX_VISITS = 64

    /**
     * Whether the native consumable registry recognizes this exact finished or partly eaten food id.
     */
    fun isFood(id: Int): Boolean = id in Food.ID_TO_FOOD

    /**
     * Keeps deterministic prepared inputs within their original complete basket's captured value.
     * Returned containers receive no credit, and no stage can increase a purchase ceiling or the job's purse.
     */
    fun pricePreparations(path: List<ProductionRecipe>, prices: MutableMap<Int, Long>) {
        for (recipe in path.dropLast(1)) {
            if (recipe.productProbability != 1.0 || recipe.experience != 0.0) continue
            val output = recipe.products.entries.singleOrNull()?.takeIf { it.value == 1 }?.key ?: continue
            var value = recipe.coinCost
            for ((id, amount) in recipe.materials) value = ProductionEconomics.saturatingAdd(value,
                ProductionEconomics.saturatingMultiply(prices[id] ?: Long.MAX_VALUE, amount.toLong()))
            for (tool in recipe.tools) value = ProductionEconomics.saturatingAdd(value, prices[tool] ?: Long.MAX_VALUE)
            if (value in 1 until Long.MAX_VALUE && output in prices) prices[output] = minOf(prices.getValue(output), value)
        }
    }

    /**
     * Returns the directly usable recipe first, followed by bounded preparation prefixes.
     * Every returned path retains its native identities and the existing six-stage economic limit.
     */
    fun forOutput(output: Int,
                  describe: (ProductionCatalogue.Entry) -> ProductionRecipe?): List<List<ProductionRecipe>> {
        if (!isFood(output)) return emptyList()
        val result = ArrayList<List<ProductionRecipe>>()
        val queue = ArrayDeque<List<ProductionRecipe>>()
        val seen = HashSet<List<String>>()
        for (entry in ProductionCatalogue.byOutput[output].orEmpty().take(16)) {
            if (entry.skill != SKILL_COOKING) continue
            val recipe = describe(entry) ?: continue
            if (recipe.products[output] != 1) continue
            queue.add(listOf(recipe))
        }
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
                    if (entry.skill != SKILL_COOKING) continue
                    val recipe = describe(entry) ?: continue
                    if (recipe.experience != 0.0 || recipe.productProbability != 1.0 ||
                        recipe.products != mapOf(input to 1) || path.any { it.key == recipe.key } ||
                        recipe.materials.keys.any { id -> path.any { id in it.products } }) continue
                    queue.add(listOf(recipe) + path)
                }
            }
        }
        return result
    }
}
