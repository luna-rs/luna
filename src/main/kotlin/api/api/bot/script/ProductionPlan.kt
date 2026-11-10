package api.bot.script

import com.google.gson.JsonArray
import com.google.gson.JsonObject

/**
 * A finite production goal whose purchase budget survives banking, child trades and saved sessions.
 * All mutations belong to the game thread. Native actions still own material consumption and XP.
 *
 * @author lare96
 */
class ProductionPlan(
    recipe: ProductionRecipe,
    val training: Boolean,
    val operations: Int,
    val maximumCoins: Long,
    maximumUnitPrices: Map<Int, Long>,
    val deadline: Long,
    completedOperations: Int = 0,
    spentCoins: Long = 0L,
    attemptedMarketItems: Set<Int> = emptySet(),
    continuationRecipes: List<ProductionRecipe> = emptyList(),
    currentStageIndex: Int = 0,
    personalUsePrices: Map<Int, Long> = emptyMap()
) {
    val recipes = (listOf(recipe) + continuationRecipes).map { native -> native.copy(
        materials = native.materials.toMap(), tools = native.tools.toSet(), products = native.products.toMap()) }
    val maximumUnitPrices = maximumUnitPrices.toMap()
    val personalUsePrices = personalUsePrices.toMap()
    var currentStageIndex = currentStageIndex
        private set
    val recipe: ProductionRecipe get() = recipes[currentStageIndex]
    var completedOperations = completedOperations
        private set
    var spentCoins = spentCoins
        private set
    val attemptedMarketItems = attemptedMarketItems.toMutableSet()
    var supplyDeadline = 0L
    var shopAttempted = false
    private var targetCache: Map<Int, Int>? = null

    val remainingOperations: Int get() = (operations - completedOperations).coerceAtLeast(0)
    val remainingCoins: Long get() = (maximumCoins - spentCoins).coerceAtLeast(0L)

    init {
        require(operations in 1..ProductionEconomics.MAX_OPERATIONS)
        require(completedOperations in 0..operations && maximumCoins >= 0L && spentCoins >= 0L)
        require(deadline > 0L && maximumUnitPrices.size <= 64)
        require(maximumUnitPrices.all { (id, price) -> id >= 0 && price > 0L })
        require(recipes.size <= ProductionChainEconomics.MAX_STAGES && currentStageIndex in recipes.indices)
        require(recipes.all { native -> (native.materials.keys + native.tools).all { it in maximumUnitPrices } })
        require(attemptedMarketItems.size <= 64 && attemptedMarketItems.all { it in maximumUnitPrices })
        require(ProductionChainEconomics.aggregate(recipes.map { ProductionStage(it, operations) }) != null)
        require(personalUsePrices.size <= 64 && personalUsePrices.all { (id, price) -> id >= 0 && price > 0L })
    }

    /**
     * Returns ownership targets for only this remaining goal, without turning consumed stock into replacement demand.
     */
    fun targets(): Map<Int, Int> = targetCache ?: economicRecipe()?.let { economic ->
        economic.materials + economic.tools.associateWith { 1 }
    }.orEmpty().also { targetCache = it }

    /**
     * A constant-size lookup used by sale reserves and every native trade-stage quantity check.
     */
    fun target(id: Int): Int = targets()[id] ?: 0

    /**
     * Counts only remaining native work, including supplied future stages, when refreshing economic policy.
     */
    fun economicRecipe(): ProductionRecipe? {
        val stages = recipes.drop(currentStageIndex).mapIndexedNotNull { index, native ->
            val amount = if (index == 0) remainingOperations else operations
            if (amount > 0) ProductionStage(native, amount) else null
        }
        return ProductionChainEconomics.aggregate(stages)
    }

    fun hasNextStage(): Boolean = currentStageIndex < recipes.lastIndex

    /**
     * Advances only after complete verified input consumption; spending and absolute deadlines never reset.
     */
    fun advanceStage(): Boolean {
        if (remainingOperations > 0 || !hasNextStage()) return false
        currentStageIndex++
        completedOperations = 0
        targetCache = null
        shopAttempted = false
        return true
    }

    /**
     * Debits a verified coin payment once; a changing market cannot replenish the session allowance.
     */
    fun recordSpending(coins: Long) {
        require(coins >= 0L)
        spentCoins = ProductionEconomics.saturatingAdd(spentCoins, coins)
    }

    /**
     * Counts verified complete native operations, bounded by the original goal.
     */
    fun recordProduction(amount: Int) {
        require(amount >= 0)
        completedOperations = minOf(operations.toLong(), completedOperations.toLong() + amount).toInt()
        targetCache = null
    }

    /**
     * Serializes the complete guard, avoiding an unbudgeted native session after a server restart.
     */
    fun save(): JsonObject = JsonObject().also { data ->
        saveRecipe(recipes.first(), data)
        data.add("continuations", JsonArray().also { array -> recipes.drop(1).forEach { native ->
            array.add(JsonObject().also { saveRecipe(native, it) })
        } })
        data.addProperty("stage", currentStageIndex)
        data.addProperty("training", training)
        data.addProperty("operations", operations)
        data.addProperty("completed", completedOperations)
        data.addProperty("maximum_coins", maximumCoins)
        data.addProperty("spent_coins", spentCoins)
        data.addProperty("deadline", deadline)
        data.addProperty("supply_deadline", supplyDeadline)
        data.addProperty("shop_attempted", shopAttempted)
        data.add("unit_prices", JsonObject().also { prices ->
            maximumUnitPrices.forEach { (id, price) -> prices.addProperty(id.toString(), price) }
        })
        data.add("personal_use_prices", JsonObject().also { prices ->
            personalUsePrices.forEach { (id, price) -> prices.addProperty(id.toString(), price) }
        })
        data.add("attempted_market", JsonArray().also { array -> attemptedMarketItems.forEach { array.add(it) } })
    }

    companion object {
        /**
         * Rejects malformed guards instead of silently resuming their recipes with unlimited acquisition.
         */
        fun load(data: JsonObject): ProductionPlan {
            val prices = data.getAsJsonObject("unit_prices")
            val attempted = data.getAsJsonArray("attempted_market")
            val continuations = data.getAsJsonArray("continuations") ?: JsonArray()
            val personal = data.getAsJsonObject("personal_use_prices") ?: JsonObject()
            require(prices.size() <= 64 && attempted.size() <= 64 && continuations.size() < ProductionChainEconomics.MAX_STAGES)
            require(personal.size() <= 64)
            val recipe = loadRecipe(data)
            return ProductionPlan(recipe, data.get("training").asBoolean, data.get("operations").asInt,
                data.get("maximum_coins").asLong,
                prices.entrySet().associate { it.key.toInt() to it.value.asLong }, data.get("deadline").asLong,
                data.get("completed").asInt, data.get("spent_coins").asLong,
                attempted.map { it.asInt }.toSet(), continuations.map { loadRecipe(it.asJsonObject) },
                data.get("stage")?.asInt ?: 0, personal.entrySet().associate { it.key.toInt() to it.value.asLong }).also {
                it.supplyDeadline = data.get("supply_deadline")?.asLong ?: 0L
                it.shopAttempted = data.get("shop_attempted")?.asBoolean ?: false
                require(it.supplyDeadline >= 0L)
            }
        }

        private fun quantities(amounts: Map<Int, Int>): JsonObject = JsonObject().also { data ->
            amounts.forEach { (id, amount) -> data.addProperty(id.toString(), amount) }
        }

        private fun saveRecipe(recipe: ProductionRecipe, data: JsonObject) {
            data.addProperty("key", recipe.key)
            data.addProperty("skill", recipe.skill)
            data.addProperty("level", recipe.level)
            data.addProperty("experience", recipe.experience)
            data.addProperty("product_probability", recipe.productProbability)
            data.addProperty("coin_cost", recipe.coinCost)
            data.add("materials", quantities(recipe.materials))
            data.add("products", quantities(recipe.products))
            data.add("tools", JsonArray().also { array -> recipe.tools.forEach { array.add(it) } })
        }

        private fun loadRecipe(data: JsonObject): ProductionRecipe {
            fun amounts(name: String): Map<Int, Int> {
                val source = data.getAsJsonObject(name)
                require(source.size() <= 8)
                return source.entrySet().associate { it.key.toInt() to it.value.asInt }
            }
            val tools = data.getAsJsonArray("tools")
            require(tools.size() <= 8)
            return ProductionRecipe(data.get("key").asString, data.get("skill").asInt,
                data.get("level").asInt, amounts("materials"), tools.map { it.asInt }.toSet(),
                amounts("products"), data.get("experience").asDouble,
                data.get("product_probability")?.asDouble ?: 1.0, data.get("coin_cost")?.asLong ?: 0L)
        }
    }
}
