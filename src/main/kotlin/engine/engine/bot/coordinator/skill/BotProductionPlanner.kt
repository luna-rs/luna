package engine.bot.coordinator.skill

import api.attr.Attr
import api.bot.action.BotCombatActionHandler
import api.bot.action.trading.BotTradeIntentions
import api.bot.action.trading.BotTradePurpose
import api.bot.script.*
import api.predef.*
import engine.bot.economy.BotProductionUses
import engine.bot.gear.BotItemTracker.Companion.itemOwnershipRevision
import engine.bot.gear.BotItemTracker.Companion.itemTracker
import io.luna.game.model.def.ItemDefinition
import io.luna.game.model.mob.SkillSet
import io.luna.game.model.mob.bot.Bot
import java.util.IdentityHashMap
import kotlin.math.ceil
import kotlin.time.Duration

/**
 * Selects finite production goals before testing whether their materials are already owned.
 * Decisions run at activity boundaries using one skill's indexed recipes, a bounded useful-goal graph,
 * and this account's indexed stock. Failed unchanged choices have a retry cooldown.
 *
 * @author lare96
 */
object BotProductionPlanner {
    /**
     * A failed unchanged decision is not reconsidered on every coordinator tick.
     *
     * @author lare96
     */
    private data class Retry(val revision: Long, val level: Int, val after: Long)

    /**
     * Concrete missing stock and the most that producing it can save against an ordinary purchase.
     * These values express personal utility; they are never reported as sale proceeds.
     *
     * @author lare96
     */
    private data class UseGoals(val operations: Map<String, Int>, val prices: Map<Int, Long>,
                                val quantities: Map<Int, Int>)

    /**
     * One bounded dependency node, with the remaining purchase allowance allocated to this input.
     *
     * @author lare96
     */
    private data class UseNode(val id: Int, val amount: Int, val depth: Int, val unitPrice: Long)

    /**
     * A failed basket's missing per-operation inputs, including future preparation stages.
     *
     * @author lare96
     */
    private data class Unavailable(val after: Long, val requirements: Map<Int, Int>)

    private val retries = Attr.map<String, Retry>()
    private val unavailableRecipes = Attr.map<String, Unavailable>()

    /**
     * Defers an exhausted acquisition so another recipe can use available materials at the next activity boundary.
     * New owned supplies immediately make the recipe eligible again; the account-local map stays bounded.
     */
    fun unavailable(bot: Bot, plan: ProductionPlan) {
        val failed = bot.attributes()[unavailableRecipes.attr]
        val now = System.currentTimeMillis()
        failed.entries.removeIf { it.value.after <= now }
        failed.remove(plan.recipe.key)
        val blockers = plan.targets().filter { (id, amount) -> owned(bot, id) < amount }.mapValues { (_, amount) ->
            ((amount.toLong() + plan.operations - 1) / plan.operations).coerceAtLeast(1L).toInt()
        }
        if (blockers.isEmpty()) return
        failed[plan.recipe.key] = Unavailable(now + 15 * 60_000L, blockers)
        if (failed.size > 32) failed.remove(failed.minBy { it.value.after }.key)
    }

    /**
     * Tests a static skill index without allocating candidate scripts.
     */
    fun supports(skill: Int): Boolean = skill in ProductionCatalogue.bySkill

    /**
     * Chooses a bounded, affordable native batch or a fully funded short native production chain.
     * Zero-XP preparation may use a later same-skill recipe's real XP only when the complete chain's
     * external materials and tools fit its shared budget. It never awards that future experience itself.
     */
    fun select(bot: Bot, skill: Int, level: Int, training: Boolean, duration: Duration,
               keyPrefix: String? = null): InventoryBotScript? {
        val now = System.currentTimeMillis()
        val key = "$skill:${keyPrefix.orEmpty()}:$training"
        val history = bot.attributes()[retries.attr]
        val retry = history[key]
        if (retry != null && retry.revision == bot.itemOwnershipRevision && retry.level == level && retry.after > now)
            return null
        if (duration <= Duration.ZERO || !BotCombatActionHandler.mayGainExperience(bot, skill) ||
            training && !BotCombatActionHandler.wantsToTrain(bot, skill)) return null
        history[key] = Retry(bot.itemOwnershipRevision, level, now + 30_000L)
        if (history.size > 24) history.remove(history.minBy { it.value.after }.key)

        val ownership = HashMap<Int, Long>()
        fun count(id: Int): Long = ownership.getOrPut(id) { owned(bot, id) }
        val described = IdentityHashMap<ProductionCatalogue.Entry, ProductionRecipe?>()
        fun describe(entry: ProductionCatalogue.Entry): ProductionRecipe? {
            if (described.containsKey(entry)) return described[entry]
            val native = entry.describe(bot)
            val recipe = native?.takeIf { bot.skill(it.skill).staticLevel >= it.level &&
                BotCombatActionHandler.mayGainExperience(bot, it.skill) }
            described[entry] = recipe
            return recipe
        }
        val failedRecipes = bot.attributes()[unavailableRecipes.attr]
        val candidates = ProductionCatalogue.bySkill[skill].orEmpty().mapNotNull { entry ->
            val native = describe(entry) ?: return@mapNotNull null
            val failure = failedRecipes[native.key]
            val deferred = failure != null && failure.after > now
            val ready = failure?.requirements?.all { (id, amount) -> count(id) >= amount } ?: true
            if (native.level > level || keyPrefix != null && !native.key.startsWith(keyPrefix)) null
            else if (deferred && !ready) null
            else entry to native
        }
        if (candidates.isEmpty()) return null
        val goals = usefulGoals(bot, ::count, ::describe)

        /**
         * A native execution path and the complete economic basket that justifies starting it.
         *
         * @author lare96
         */
        data class Pipeline(val entry: ProductionCatalogue.Entry, val stages: List<ProductionRecipe>,
                            val economy: ProductionRecipe)
        val pipelines = buildList {
            for ((entry, recipe) in candidates) {
                add(Pipeline(entry, listOf(recipe), recipe))
                if (recipe.experience == 0.0) for (chain in downstream(recipe, ::describe)) {
                    val aggregate = ProductionChainEconomics.aggregate(chain.map { ProductionStage(it, 1) }) ?: continue
                    add(Pipeline(entry, chain, aggregate))
                }
            }
        }
        val products = pipelines.flatMap { it.economy.products.entries }.groupBy { it.key }
            .mapValues { (_, entries) -> entries.maxOf { it.value } }
        val recoverable = HashMap<Int, Long>()
        products.entries.chunked(BotProductionUses.MAX_PRODUCT_TYPES).forEach { chunk ->
            recoverable.putAll(BotProductionUses.recoverableValues(bot, chunk.associate { it.key to it.value }))
        }
        val prices = HashMap<Int, Long>()
        fun ceiling(id: Int): Long = prices.getOrPut(id) {
            val guide = bot.world.economy.getPrice(id).coerceAtLeast(1L)
            if (guide > Long.MAX_VALUE / 2) Long.MAX_VALUE else guide * 2
        }
        val coins = count(995)
        val budget = (coins * BotTradeIntentions.budgetFraction(bot, BotTradePurpose.BUY)).toLong()
        val current = bot.skill(skill)
        val remainingXp = if (!training) Double.MAX_VALUE else if (current.staticLevel < 99)
            (SkillSet.experienceForLevel(current.staticLevel + 1) - current.experience).coerceAtLeast(1.0)
        else (SkillSet.MAXIMUM_EXPERIENCE - current.experience).coerceAtLeast(0.0)

        /**
         * One feasible pipeline; all quotes share the account's actual purse and the native XP allowance.
         *
         * @author lare96
         */
        data class Candidate(val path: Pipeline, val quote: ProductionEconomics.Quote,
                             val caps: Map<Int, Long>, val usePrices: Map<Int, Long>,
                             val useful: Boolean, val stocked: Boolean, val score: Double)
        val scored = pipelines.mapNotNull { path ->
            val native = path.stages.first()
            val recipe = path.economy
            val stockLimit = goals.operations[native.key]
            if (!training && stockLimit == null && recipe.products.any { (id, amount) ->
                    count(id) >= amount.toLong() * 56 }) return@mapNotNull null
            val ids = path.stages.flatMapTo(hashSetOf()) { it.materials.keys + it.tools }
            val inputPrices = ids.associateWith(::ceiling)
            val loose = recipe.materials.filterKeys { !itemDef(it).isStackable }.values.sumOf { it.toLong() }
            val stackSlots = recipe.materials.keys.count { itemDef(it).isStackable }
            val capacity = if (loose == 0L) ProductionEconomics.MAX_OPERATIONS else
                ((bot.inventory.capacity() - recipe.tools.size - stackSlots - 1).coerceAtLeast(0) / loose).toInt()
            val usePrices = recipe.products.filter { (id, amount) ->
                (goals.prices[id] ?: 0L) > 0 && (goals.quantities[id] ?: 0) >= amount
            }.keys.associateWith { goals.prices.getValue(it) }
            val useLimit = usePrices.keys.minOfOrNull { id ->
                goals.quantities.getValue(id) / recipe.products.getValue(id)
            } ?: ProductionEconomics.MAX_OPERATIONS
            val requested = minOf(capacity, stockLimit ?: ProductionEconomics.MAX_OPERATIONS, useLimit)
            val stock = ids.associateWith(::count)
            val quote = ProductionEconomics.quote(recipe, training, requested, remainingXp,
                stock, budget, inputPrices, recoverable, usePrices) ?: return@mapNotNull null
            val ticks = path.stages.sumOf { processingTicks(it) }
            val netCost = (quote.inputValue - quote.outputValue).coerceAtLeast(0L).toDouble()
            val score = if (training) recipe.experience / ticks /
                (1.0 + netCost / recipe.experience.coerceAtLeast(1.0))
            else (quote.outputValue - quote.inputValue).toDouble() / ticks
            val stocked = recipe.materials.all { (id, amount) -> (stock[id] ?: 0L) >= amount } &&
                recipe.tools.all { (stock[it] ?: 0L) > 0L }
            Candidate(path, quote, inputPrices, usePrices, stockLimit != null, stocked, score)
        }.sortedWith(compareByDescending<Candidate> { it.useful }.thenByDescending { it.stocked }.thenByDescending { it.score }
            .thenByDescending { it.path.stages.first().level })
        val chosen = when {
            scored.isEmpty() -> return null
            bot.personality.isIntelligent || bot.personality.isDextrous -> scored.first()
            bot.personality.isDumb && randBoolean() -> scored.last()
            else -> scored.take(8).random()
        }
        val deadline = now + minOf(duration.inWholeMilliseconds, 10 * 60_000L)
        val plan = ProductionPlan(chosen.path.stages.first(), training, chosen.quote.operations,
            chosen.quote.maximumCoins, chosen.caps, deadline,
            continuationRecipes = chosen.path.stages.drop(1), personalUsePrices = chosen.usePrices)
        return chosen.path.entry.create(bot, duration).planProduction(plan)
    }

    /**
     * Resolves one saved next-stage identity through its native output index, retaining the same shared purse.
     */
    fun continuePlan(bot: Bot, plan: ProductionPlan, duration: Duration): InventoryBotScript? {
        if (!remainsUseful(bot, plan)) return null
        val recipe = plan.recipe
        val entry = nativeEntry(bot, recipe) ?: return null
        return entry.create(bot, duration).planProduction(plan)
    }

    /**
     * Revalidates the complete remaining chain after suspension. Original ceilings and the shared lifetime
     * coin allowance never increase, and already spent ingredients are not credited as future XP twice.
     */
    fun remainsUseful(bot: Bot, plan: ProductionPlan): Boolean {
        val native = plan.recipe
        if (plan.deadline <= System.currentTimeMillis() || plan.remainingOperations < 1 ||
            bot.skill(native.skill).level < native.level ||
            !BotCombatActionHandler.mayGainExperience(bot, native.skill) ||
            plan.training && !BotCombatActionHandler.wantsToTrain(bot, native.skill)) return false
        for (remaining in plan.recipes.drop(plan.currentStageIndex)) {
            if (bot.skill(remaining.skill).level < remaining.level ||
                !BotCombatActionHandler.mayGainExperience(bot, remaining.skill) ||
                plan.training && remaining.experience > 0.0 &&
                    !BotCombatActionHandler.wantsToTrain(bot, remaining.skill) ||
                nativeEntry(bot, remaining) == null) return false
        }
        val recipe = plan.economicRecipe() ?: return false
        val inputPrices = plan.maximumUnitPrices.mapValues { (id, ceiling) ->
            maxOf(ceiling, bot.world.economy.getPrice(id).coerceAtLeast(1L))
        }
        val goals = if (plan.personalUsePrices.isEmpty()) null else usefulGoals(bot) { entry ->
            entry.describe(bot)?.takeIf { bot.skill(it.skill).staticLevel >= it.level &&
                BotCombatActionHandler.mayGainExperience(bot, it.skill) }
        }
        val usePrices = plan.personalUsePrices.mapValues { (id, captured) ->
            if ((goals?.quantities?.get(id) ?: 0) < (recipe.products[id] ?: 0)) 0L
            else minOf(captured, goals?.prices?.get(id) ?: 0L)
        }
        return ProductionEconomics.quote(recipe, plan.training, 1, Double.MAX_VALUE,
            inputPrices.keys.associateWith { owned(bot, it) }, minOf(plan.remainingCoins, owned(bot, 995)),
            inputPrices, BotProductionUses.recoverableValues(bot, recipe.products, operations = 1), usePrices) != null
    }

    /**
     * Resolves at most one output's native candidates, rejecting stale gameplay inputs and optimistic old yields.
     */
    private fun nativeEntry(bot: Bot, recipe: ProductionRecipe): ProductionCatalogue.Entry? {
        val candidates = recipe.products.keys.firstOrNull()?.let { ProductionCatalogue.byOutput[it] }
            ?: ProductionCatalogue.bySkill[recipe.skill].orEmpty()
        return candidates.firstOrNull { candidate ->
            candidate.skill == recipe.skill && candidate.describe(bot)?.let {
                it.key == recipe.key && it.level == recipe.level && it.materials == recipe.materials &&
                    it.tools == recipe.tools && it.products == recipe.products &&
                    it.experience >= recipe.experience && it.productProbability >= recipe.productProbability &&
                    it.coinCost == recipe.coinCost
            } == true
        }
    }

    private fun owned(bot: Bot, id: Int): Long {
        val noted = ItemDefinition.ALL.get(id).map { it.notedId.orElse(id) }.orElse(id)
        return bot.itemTracker.count(id).toLong() + if (noted == id) 0L else bot.itemTracker.count(noted).toLong()
    }

    /**
     * Traverses at most sixty-four useful item nodes in total, with three upstream links and sixteen recipes
     * per node. Empty activity demand performs no recipe graph walk or unrelated skill descriptions.
     */
    private fun usefulGoals(bot: Bot, count: (Int) -> Long = { owned(bot, it) },
                            describe: (ProductionCatalogue.Entry) -> ProductionRecipe?): UseGoals {
        val result = HashMap<String, Int>()
        val prices = HashMap<Int, Long>()
        val quantities = HashMap<Int, Int>()
        val bids = HashMap<Int, Long>()
        fun bid(id: Int): Long = bids.getOrPut(id) { bot.world.economy.getPrice(id).coerceAtLeast(1L) }
        var remainingNodes = 64
        for (id in BotTradeIntentions.supplyDemandIds(bot)) {
            if (remainingNodes == 0) break
            val demand = BotTradeIntentions.supplyDemand(bot, id) ?: continue
            val pendingWine = if (id == 1993) count(1995) else 0L
            val missing = (demand.target().toLong() - count(id) - pendingWine).coerceIn(0, 100).toInt()
            if (missing == 0) continue
            val queue = ArrayDeque<UseNode>()
            val visited = HashSet<Int>()
            queue.add(UseNode(if (id == 1993) 1995 else id, missing, 0,
                minOf(bid(id), BotTradeIntentions.productionLimit(bot, id)?.maximumUnitPrice ?: Long.MAX_VALUE)))
            while (queue.isNotEmpty() && remainingNodes > 0) {
                val (item, amount, depth, usePrice) = queue.removeFirst()
                if (!visited.add(item)) continue
                remainingNodes--
                // Keep value and quantity from the same real use; independent maxima would invent a larger budget.
                val previousPrice = prices[item] ?: 0L
                if (usePrice > previousPrice || usePrice == previousPrice && amount > (quantities[item] ?: 0)) {
                    prices[item] = usePrice
                    quantities[item] = amount
                }
                for (entry in ProductionCatalogue.byOutput[item].orEmpty().take(16)) {
                    val recipe = describe(entry) ?: continue
                    val operations = ceil(amount.toDouble() / recipe.products.getValue(item)).toInt().coerceIn(1, 100)
                    result[recipe.key] = maxOf(result[recipe.key] ?: 0, operations)
                    var missingToolCost = 0L
                    for (tool in recipe.tools) if (count(tool) == 0L) {
                        val cost = bid(tool).coerceAtMost(Long.MAX_VALUE / 2) * 2
                        missingToolCost = if (missingToolCost > Long.MAX_VALUE - cost) Long.MAX_VALUE
                        else missingToolCost + cost
                    }
                    val toolsPerOperation = missingToolCost / operations +
                        if (missingToolCost % operations == 0L) 0L else 1L
                    if (depth < 3) recipe.materials.forEach { (input, quantity) ->
                        val needed = (quantity.toLong() * operations - count(input)).coerceIn(0, 100).toInt()
                        var otherCost = if (recipe.coinCost > Long.MAX_VALUE - toolsPerOperation) Long.MAX_VALUE
                        else recipe.coinCost + toolsPerOperation
                        for ((other, required) in recipe.materials) if (other != input) {
                            val cost = bid(other).coerceAtMost(Long.MAX_VALUE / required) * required
                            otherCost = if (otherCost > Long.MAX_VALUE - cost) Long.MAX_VALUE else otherCost + cost
                        }
                        val outputValue = usePrice.coerceAtMost(Long.MAX_VALUE / recipe.products.getValue(item)) *
                            recipe.products.getValue(item)
                        val allowance = if (outputValue <= otherCost) 0L else (outputValue - otherCost) / quantity
                        if (needed > 0) queue.add(UseNode(input, needed, depth + 1, minOf(bid(input), allowance)))
                    }
                }
            }
        }
        return UseGoals(result, prices, quantities)
    }

    /**
     * Preparation chains support exact one-to-one native links. Uncertain yields, cycles and unknown ratios
     * stay outside speculative chaining; their ordinary single-stage recipes remain available.
     */
    private fun downstream(first: ProductionRecipe,
                           describe: (ProductionCatalogue.Entry) -> ProductionRecipe?): List<List<ProductionRecipe>> {
        val result = ArrayList<List<ProductionRecipe>>()
        val queue = ArrayDeque<List<ProductionRecipe>>()
        queue.add(listOf(first))
        var visited = 0
        while (queue.isNotEmpty() && result.size < 8 && visited++ < 32) {
            val path = queue.removeFirst()
            val last = path.last()
            if (path.size >= ProductionChainEconomics.MAX_STAGES || last.productProbability != 1.0) continue
            val output = last.products.entries.singleOrNull()?.takeIf { it.value == 1 }?.key ?: continue
            for (entry in ProductionCatalogue.byInput[output].orEmpty().take(16)) {
                if (entry.skill != first.skill) continue
                val next = describe(entry) ?: continue
                if (next.key in path.map { it.key } || next.materials[output] != 1) continue
                val extended = path + next
                if (next.experience > 0.0) result += extended else queue.add(extended)
                if (result.size >= 8) break
            }
        }
        return result
    }

    /**
     * Native processing cadence estimates compare useful XP or net proceeds per processing tick.
     * They omit travel and human-like interaction delays; unknown methods receive no speed advantage.
     */
    internal fun processingTicks(recipe: ProductionRecipe): Double = when (recipe.key.substringBefore(':')) {
        "gem", "jewellery", "molten-glass", "smelt" -> 3.0
        "hide", "cook" -> 4.0
        "smith" -> 5.0
        "staff", "string-jewellery", "glass", "textile", "string-bow", "unfinished", "potion", "grind" -> 2.0
        "pottery" -> if (recipe.key.endsWith(":FIRE")) 3.0 else 2.0
        "cut-log" -> 3.0
        "arrow" -> 3.0 / game.skill.fletching.attachArrow.Arrow.SET_AMOUNT
        "soft-clay", "assemble", "dough", "cut-food", "identify", "runecraft" -> 1.0
        "tan" -> 1.0 / 27
        "enchant" -> 3.0
        "charge-orb", "superheat" -> 5.0
        "cannonballs" -> 9.0
        "burn", "bury", "ammunition", "ogre" -> 3.0
        else -> 5.0
    }
}
