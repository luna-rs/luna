package engine.bot.coordinator.skill

import api.attr.Attr
import api.bot.action.BotCombatActionHandler
import api.bot.action.BotSupplySources
import api.bot.action.trading.BotTradeIntentions
import api.bot.action.trading.BotFoodDemand
import api.bot.action.trading.BotGearDemand
import api.bot.action.trading.BotTradePurpose
import api.bot.action.trading.BotTradeManager
import api.bot.action.trading.NativeOutputDemand
import api.bot.script.*
import api.predef.*
import api.predef.ext.totalItemAmount
import engine.bot.economy.BotProductionUses
import engine.bot.economy.BotProductionMetrics
import engine.bot.economy.BotProductionMetrics.Reason
import engine.bot.economy.BotProductionMetrics.Source
import engine.bot.gear.BotItemTracker.Companion.itemOwnershipRevision
import engine.bot.gear.BotItemTracker.Companion.itemTracker
import io.luna.game.model.def.ItemDefinition
import io.luna.game.model.def.EquipmentDefinition
import io.luna.game.model.mob.Skill
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
     * Native preparation may use a later same-skill recipe's real XP only when the complete chain's
     * external materials and tools fit its shared budget. Experience remains with each native action.
     */
    fun select(bot: Bot, skill: Int, level: Int, training: Boolean, duration: Duration,
               keyPrefix: String? = null, finalFood: Int? = null, merchantOutput: Int? = null,
               maximumCoinsLimit: Long = Long.MAX_VALUE,
               directOnly: Boolean = false,
               singlePurchaseBatch: Boolean = false): InventoryBotScript? {
        fun rejected(reason: Reason): InventoryBotScript? {
            val previous = BotProductionMetrics.latestDecision(bot)
            if (reason == Reason.COOLDOWN && previous?.source == Source.PLANNER &&
                previous.reason == reason && previous.skill == skill && previous.recipeKey == keyPrefix) return null
            BotProductionMetrics.recordDecision(bot, Source.PLANNER, reason, keyPrefix, skill)
            return null
        }
        var lastRejection = Reason.NO_ELIGIBLE_RECIPE
        if (finalFood != null && (directOnly || skill != SKILL_COOKING || !CookingProductionPaths.isFood(finalFood)))
            return rejected(Reason.INVALID_REQUEST)
        if (singlePurchaseBatch && (training || keyPrefix == null || finalFood != null || merchantOutput == null))
            return rejected(Reason.INVALID_REQUEST)
        val now = System.currentTimeMillis()
        val key = "$skill:${keyPrefix.orEmpty()}:$training:${finalFood ?: -1}:${merchantOutput ?: -1}:$directOnly:$singlePurchaseBatch"
        val history = bot.attributes()[retries.attr]
        val retry = history[key]
        if (retry != null && retry.revision == bot.itemOwnershipRevision && retry.level == level && retry.after > now)
            return rejected(Reason.COOLDOWN)
        if (duration <= Duration.ZERO) return rejected(Reason.INVALID_REQUEST)
        if (!BotCombatActionHandler.mayGainExperience(bot, skill) ||
            training && !BotCombatActionHandler.wantsToTrain(bot, skill)) return rejected(Reason.ACCOUNT_RESTRICTION)
        history[key] = Retry(bot.itemOwnershipRevision, level, now + 30_000L)
        if (history.size > 24) history.remove(history.minBy { it.value.after }.key)

        val ownership = HashMap<Int, Long>()
        fun count(id: Int): Long = ownership.getOrPut(id) { owned(bot, id) }
        val described = IdentityHashMap<ProductionCatalogue.Entry, ProductionRecipe?>()
        fun describe(entry: ProductionCatalogue.Entry): ProductionRecipe? {
            if (described.containsKey(entry)) return described[entry]
            val native = entry.describe(bot)
            val recipe = native?.takeIf { (!entry.profitOnly || !training) && bot.skill(it.skill).staticLevel >= it.level &&
                bot.skill(it.skill).level >= it.level && (it.skill != skill || it.level <= level) &&
                BotCombatActionHandler.mayGainExperience(bot, it.skill) &&
                (!training || it.experience == 0.0 || BotCombatActionHandler.wantsToTrain(bot, it.skill)) }
            if (native != null && recipe == null && (keyPrefix == null || native.key.startsWith(keyPrefix)))
                lastRejection = if (bot.skill(native.skill).staticLevel < native.level ||
                    bot.skill(native.skill).level < native.level || native.skill == skill && native.level > level)
                    Reason.LEVEL else Reason.ACCOUNT_RESTRICTION
            described[entry] = recipe
            return recipe
        }
        val failedRecipes = bot.attributes()[unavailableRecipes.attr]
        val entriesForDecision = if (merchantOutput != null) ProductionCatalogue.byOutput[merchantOutput]
            .orEmpty().asSequence().filter { it.skill == skill }.take(16).toList()
        else ProductionCatalogue.bySkill[skill].orEmpty()
        val candidates = entriesForDecision.mapNotNull { entry ->
            val native = describe(entry) ?: return@mapNotNull null
            val failure = failedRecipes[native.key]
            val deferred = failure != null && failure.after > now
            val ready = failure?.requirements?.all { (id, amount) -> count(id) >= amount } ?: true
            if (native.level > level || keyPrefix != null && !native.key.startsWith(keyPrefix)) null
            else if (deferred && !ready) null
            else entry to native
        }
        if (candidates.isEmpty()) return rejected(lastRejection)
        val goals = if (merchantOutput == null) usefulGoals(bot, ::count, ::describe)
            else UseGoals(emptyMap(), emptyMap(), emptyMap())

        /**
         * A native execution path and the complete economic basket that justifies starting it.
         *
         * @author lare96
         */
        data class Pipeline(val entry: ProductionCatalogue.Entry, val stages: List<ProductionStage>,
                            val economy: ProductionRecipe)
        val allowOtherSkills = merchantOutput != null && !training
        val pipelines = buildList {
            if (finalFood == null) for ((entry, recipe) in candidates) {
                val paths = if (directOnly || singlePurchaseBatch) listOf(listOf(ProductionStage(recipe, 1)))
                else ProductionRecipePaths.forRecipe(recipe, ::describe, allowOtherSkills) {
                    allowOtherSkills || skill != SKILL_COOKING || it.experience == 0.0
                }
                for (path in paths) {
                    if (path.any { stage ->
                        failedRecipes[stage.recipe.key]?.let { failure ->
                            failure.after > now && failure.requirements.any { (id, amount) -> count(id) < amount }
                        } == true
                    }) continue
                    val first = path.first().recipe
                    val firstEntry = if (first.key == recipe.key) entry else nativeEntry(bot, first) ?: continue
                    val aggregate = if (path.size == 1) recipe else
                        ProductionChainEconomics.aggregate(path) ?: continue
                    add(Pipeline(firstEntry, path, aggregate))
                }
            }
            val foods = if (finalFood != null) listOf(finalFood) else if (!directOnly && !singlePurchaseBatch && skill == SKILL_COOKING)
                goals.prices.keys.filter(CookingProductionPaths::isFood).take(8) else emptyList()
            val entries = candidates.associate { it.second.key to it.first }
            for (food in foods) for (path in CookingProductionPaths.forOutput(food, allowOtherSkills, ::describe)) {
                val first = path.first().recipe
                val entry = entries[first.key] ?: nativeEntry(bot, first) ?: continue
                val aggregate = ProductionChainEconomics.aggregate(path) ?: continue
                add(Pipeline(entry, path, aggregate))
            }
        }.distinctBy { it.stages.map { stage -> stage.recipe.key to stage.operations } }
        val products = pipelines.flatMap { it.economy.products.entries }.groupBy { it.key }
            .mapValues { (_, entries) -> entries.maxOf { it.value } }
        val recoverable = HashMap<Int, Long>()
        if (merchantOutput == null) products.entries.chunked(BotProductionUses.MAX_PRODUCT_TYPES).forEach { chunk ->
            recoverable.putAll(BotProductionUses.recoverableValues(bot, chunk.associate { it.key to it.value }))
        }
        val prices = HashMap<Int, Long>()
        val cookingSources = HashMap<Int, BotSupplySources.Quote?>()
        fun source(id: Int): BotSupplySources.Quote? {
            if (!cookingSources.containsKey(id)) cookingSources[id] = BotSupplySources.quote(bot, id, 100)
            return cookingSources[id]
        }
        val retained = if (merchantOutput != null) BotTradeIntentions.retainedStock(bot) else emptyMap()
        val funded = merchantOutput?.let { NativeOutputDemand.depth(bot, it, now) }.orEmpty()
        if (merchantOutput != null && (funded.isEmpty() || personalOutput(bot, merchantOutput)))
            return rejected(Reason.CROWDING)
        val directSources = HashMap<Int, List<Pair<Long, Int>>>()
        fun direct(id: Int): List<Pair<Long, Int>> = directSources.getOrPut(id) {
            NativeOutputDemand.traders(bot).mapNotNull { it.profitGatheringSupply(id) }
                .map { it.price to it.item.amount }.toList()
        }
        fun ceiling(id: Int): Long = prices.getOrPut(id) {
            if (merchantOutput != null) {
                val possible = listOfNotNull(source(id)?.maximumUnitPrice, direct(id).minOfOrNull { it.first })
                return@getOrPut possible.minOrNull() ?: bot.world.economy.getPrice(id).coerceAtLeast(1L)
            }
            if (skill == SKILL_COOKING) source(id)?.let { return@getOrPut it.maximumUnitPrice }
            val guide = bot.world.economy.getPrice(id).coerceAtLeast(1L)
            if (guide > Long.MAX_VALUE / 2) Long.MAX_VALUE else guide * 2
        }
        val coins = count(995)
        val budget = minOf(maximumCoinsLimit,
            (coins * BotTradeIntentions.budgetFraction(bot, BotTradePurpose.BUY)).toLong())
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
                             val useful: Boolean, val stocked: Boolean, val score: Double,
                             val saleGuard: ProductionSaleGuard?)
        val scored = pipelines.mapNotNull { path ->
            val native = path.stages.first().recipe
            val recipe = path.economy
            val stockLimit = goals.operations[native.key]?.div(path.stages.first().operations)
            if (!training && stockLimit == null && recipe.products.any { (id, amount) ->
                    count(id) >= amount.toLong() * 56 }) return@mapNotNull null
            val ids = path.stages.flatMapTo(hashSetOf()) { it.recipe.materials.keys + it.recipe.tools }
            val inputPrices = if (singlePurchaseBatch) {
                val (id, amount) = recipe.materials.entries.singleOrNull() ?: return@mapNotNull null
                val missing = amount - count(id)
                if (missing !in 1L..amount.toLong()) return@mapNotNull null
                val price = singlePurchasePrice(bot, id, missing.toInt()) ?: return@mapNotNull null
                mutableMapOf(id to price)
            } else ids.associateWith(::ceiling).toMutableMap()
            ProductionRecipePaths.pricePreparations(path.stages.map { it.recipe }, inputPrices)
            val inventorySize = bot.inventory.capacity()
            val capacity = if (path.stages.size == 1) {
                batchCapacity(recipe, inventorySize)
            } else {
                // A chain can bank between operations and stages. No single native operation can exceed one load.
                if (path.stages.any { batchCapacity(it.recipe, inventorySize) == 0 }) 0
                else minOf(ProductionEconomics.MAX_OPERATIONS / path.stages.maxOf { it.operations },
                    batchCapacity(recipe, inventorySize).coerceAtLeast(1))
            }
            val usePrices = recipe.products.filter { (id, amount) ->
                (goals.prices[id] ?: 0L) > 0 && (goals.quantities[id] ?: 0) >= amount
            }.keys.associateWith { goals.prices.getValue(it) }
            val useLimit = usePrices.keys.minOfOrNull { id ->
                goals.quantities.getValue(id) / recipe.products.getValue(id)
            } ?: ProductionEconomics.MAX_OPERATIONS
            var requested = minOf(capacity, stockLimit ?: ProductionEconomics.MAX_OPERATIONS, useLimit)
            if (singlePurchaseBatch) requested = minOf(requested, 1)
            if (training && requested > 0) {
                requested = ProductionRecipePaths.trainingOperationLimit(path.stages, requested,
                    trainingExperienceRemaining(bot, skill))
                if (requested == 0) {
                    lastRejection = Reason.NO_EXPERIENCE
                    return@mapNotNull null
                }
            }
            val stock = ids.associateWith(::count)
            val merchantAmount = if (merchantOutput != null) {
                if (recipe.valuedProducts.keys != setOf(merchantOutput)) return@mapNotNull null
                recipe.valuedProducts.getValue(merchantOutput)
            } else 0
            if (merchantOutput != null) {
                requested = minOf(requested, 4 + (8 * bot.personality.intelligence.coerceIn(0.0, 1.0)).toInt())
                for ((id, amount) in recipe.materials) {
                    val limit = inputPrices.getValue(id)
                    val reserved = retained[id] ?: 0
                    if (singlePurchaseBatch && reserved <= 0) continue
                    val onHand = ((stock[id] ?: 0L) - reserved).coerceAtLeast(0)
                    val shop = source(id)?.takeIf { it.maximumUnitPrice <= limit }?.quantity ?: 0
                    val available = if (reserved > 0) onHand else onHand + shop +
                        direct(id).filter { it.first <= limit }.sumOf { it.second.toLong() }
                    requested = minOf(requested, (available / amount).coerceAtMost(100L).toInt())
                }
                if (recipe.tools.any { (stock[it] ?: 0L) < 1 && source(it) == null && direct(it).isEmpty() })
                    return@mapNotNull null
            } else if (skill == SKILL_COOKING) {
                for ((id, amount) in recipe.materials) {
                    val available = (stock[id] ?: 0L) + (source(id)?.quantity ?: 0)
                    if (available < amount && itemDef(id).isTradeable) {
                        // A bounded direct trade can supply a recipe without current shop stock.
                        requested = minOf(requested, if (training) 28 else 1)
                    } else {
                        requested = minOf(requested, (available / amount).coerceAtMost(100L).toInt())
                    }
                }
                if (recipe.tools.any { (stock[it] ?: 0L) == 0L && source(it) == null && !itemDef(it).isTradeable })
                    return@mapNotNull null
            }
            var merchantPrice = 0L
            if (merchantOutput != null && requested > 0) {
                val cost = recipe.materials.entries.sumOf { (id, amount) -> inputPrices.getValue(id).toDouble() * amount } +
                    recipe.coinCost
                val existing = BotTradeManager.saleableAmount(bot, merchantOutput)
                val bid = funded.mapNotNull { depth ->
                    val count = minOf(requested, (depth.quantity - existing).coerceAtLeast(0) / merchantAmount)
                    val margin = depth.unitPrice.toDouble() * merchantAmount * recipe.productProbability - cost
                    if (count < 1 || margin <= 0) null else Triple(depth.unitPrice, count, margin * count)
                }.maxByOrNull { it.third } ?: return@mapNotNull null
                merchantPrice = bid.first
                requested = bid.second
            }
            if (requested < 1) {
                lastRejection = if (capacity < 1) Reason.NO_SPACE else Reason.MATERIALS_UNAVAILABLE
                return@mapNotNull null
            }
            val exits = if (merchantOutput == null) recoverable.filterKeys { !personalOutput(bot, it) }
                else mapOf(merchantOutput to merchantPrice)
            val quote = ProductionEconomics.quote(recipe, training, requested, remainingXp,
                stock, budget, inputPrices, exits,
                usePrices, rejected = { lastRejection = it }) ?: return@mapNotNull null
            val guard = merchantOutput?.let { id ->
                val toolCost = recipe.tools.filter { (stock[it] ?: 0L) == 0L }.fold(0L) { total, tool ->
                    ProductionEconomics.saturatingAdd(total, inputPrices.getValue(tool))
                }
                val batchCost = ProductionEconomics.saturatingAdd(toolCost,
                    ProductionEconomics.saturatingMultiply(quote.inputValue, quote.operations.toLong()))
                val units = (merchantAmount * quote.operations * recipe.productProbability).toLong()
                val profitFloor = ProductionSaleGuard.minimumGross(batchCost, units, taxed = false)
                val floor = if (singlePurchaseBatch)
                    maxOf(profitFloor, (BotTradeManager.MINIMUM_OFFER_VALUE + units - 1L) / units)
                else profitFloor
                if (floor > minOf(merchantPrice, Int.MAX_VALUE.toLong()) ||
                    (!singlePurchaseBatch && !BotTradeManager.hasNativeSaleStock(bot, id, units.toInt())))
                    return@mapNotNull null
                if (singlePurchaseBatch && !hasSingleBatchBuyer(bot, id, units.toInt(), floor)) return@mapNotNull null
                ProductionSaleGuard(id, floor, batchCost, now)
            }
            val ticks = path.stages.sumOf { processingTicks(it.recipe) * it.operations }
            val netCost = (quote.inputValue - quote.outputValue).coerceAtLeast(0L).toDouble()
            val score = if (training) recipe.experience / ticks /
                (1.0 + netCost / recipe.experience.coerceAtLeast(1.0))
            else if (guard != null) (quote.outputValue.toDouble() * quote.operations - guard.batchCost) /
                (ticks * quote.operations + NativeProductionOpportunities.overhead(bot, recipe))
            else (quote.outputValue - quote.inputValue).toDouble() / ticks
            val stocked = recipe.materials.all { (id, amount) -> (stock[id] ?: 0L) >= amount } &&
                recipe.tools.all { (stock[it] ?: 0L) > 0L }
            Candidate(path, quote, inputPrices, usePrices, stockLimit != null, stocked, score, guard)
        }
        val preferred = if (merchantOutput != null) scored else {
            val suppliedPaths = scored.filter { it.stocked }.groupBy { it.path.stages.last().recipe.key }
            scored.filterNot { candidate ->
                ProductionRecipePaths.hasSuppliedSuffix(candidate.path.stages,
                    suppliedPaths[candidate.path.stages.last().recipe.key].orEmpty().map { it.path.stages })
            }.sortedWith(compareByDescending<Candidate> { it.useful }.thenByDescending { it.stocked }.thenByDescending { it.score }
                .thenByDescending { it.path.stages.first().recipe.level })
        }
        val chosen = when {
            preferred.isEmpty() -> return rejected(lastRejection)
            merchantOutput != null -> preferred.maxBy { it.score }
            bot.personality.isIntelligent || bot.personality.isDextrous -> preferred.first()
            bot.personality.isDumb && randBoolean() -> preferred.last()
            else -> preferred.take(8).random()
        }
        val deadline = now + minOf(duration.inWholeMilliseconds, 10 * 60_000L)
        val plan = ProductionPlan(chosen.path.stages.first().recipe, training, chosen.quote.operations,
            chosen.quote.maximumCoins, chosen.caps, deadline,
            continuationRecipes = chosen.path.stages.drop(1).map { it.recipe }, personalUsePrices = chosen.usePrices,
            stageRatios = chosen.path.stages.map { it.operations }, saleGuard = chosen.saleGuard,
            singlePurchaseBatch = singlePurchaseBatch)
        chosen.saleGuard?.let { guard ->
            val quantity = chosen.path.economy.valuedProducts.getValue(guard.itemId).toLong() * plan.operations
            if (!NativeOutputDemand.reserve(bot, guard.identity, mapOf(guard.itemId to quantity), deadline,
                    mapOf(guard.itemId to guard.minimumPrice))) return rejected(Reason.CROWDING)
        }
        BotProductionMetrics.recordDecision(bot, Source.PLANNER, Reason.SELECTED, plan.recipe.key, skill, plan = plan)
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
            if (bot.skill(remaining.skill).staticLevel < remaining.level || bot.skill(remaining.skill).level < remaining.level ||
                !BotCombatActionHandler.mayGainExperience(bot, remaining.skill) ||
                plan.training && remaining.experience > 0.0 &&
                    !BotCombatActionHandler.wantsToTrain(bot, remaining.skill) ||
                nativeEntry(bot, remaining) == null) return false
        }
        if (plan.training) {
            if (!ProductionRecipePaths.trainingReachable(plan.remainingStages(),
                    trainingExperienceRemaining(bot, native.skill))) return false
        }
        val recipe = plan.economicRecipe() ?: return false
        val guard = plan.saleGuard
        val fundedPrice = guard?.let {
            if (personalOutput(bot, it.itemId)) return false
            val amount = recipe.products[it.itemId] ?: return false
            val desired = amount.toLong() + plan.guardedOutputProduced
            if (plan.singlePurchaseBatch && !hasSingleBatchBuyer(bot, it.itemId, amount, it.minimumPrice)) return false
            if (!NativeOutputDemand.reserve(bot, it.identity, mapOf(it.itemId to desired), plan.deadline,
                    mapOf(it.itemId to it.minimumPrice))) return false
            NativeOutputDemand.depth(bot, it.itemId, excluding = it.identity)
                .firstOrNull { depth -> depth.quantity >= amount && depth.unitPrice >= it.minimumPrice }
                ?.unitPrice ?: return false
        }
        val carried = ProductionRecipePaths.carriedInputPrices(plan.recipes,
            plan.currentStageIndex, plan.maximumUnitPrices)
        val prepared = carried.keys.filter { id ->
            val required = recipe.materials[id] ?: 0
            required > 0 && owned(bot, id) >= required
        }.associateWith { recipe.materials.getValue(it) }
        val disposal = BotProductionUses.recoverableValues(bot, prepared, operations = 1)
        val inputPrices = plan.maximumUnitPrices.mapValues { (id, ceiling) ->
            val basis = carried[id]?.takeIf { id in prepared }
            if (basis != null) maxOf(basis, disposal[id] ?: 0L)
            else if (plan.singlePurchaseBatch) {
                val missing = ((recipe.materials[id] ?: 0) - owned(bot, id)).coerceAtLeast(0L).toInt()
                if (missing == 0) return@mapValues ceiling
                val price = singlePurchasePrice(bot, id, missing) ?: return false
                if (price > ceiling) return false
                ceiling
            } else {
                val replacement = if (native.skill == SKILL_COOKING || guard != null) {
                    BotSupplySources.quote(bot, id, 100)?.maximumUnitPrice
                        ?: ceiling.takeIf { owned(bot, id) >= (recipe.materials[id] ?: 1) }
                } else null
                maxOf(ceiling, replacement ?: bot.world.economy.getPrice(id).coerceAtLeast(1L))
            }
        }
        val goals = if (plan.personalUsePrices.isEmpty()) null else usefulGoals(bot) { entry ->
            entry.describe(bot)?.takeIf { bot.skill(it.skill).staticLevel >= it.level &&
                BotCombatActionHandler.mayGainExperience(bot, it.skill) }
        }
        val usePrices = plan.personalUsePrices.mapValues { (id, captured) ->
            if ((goals?.quantities?.get(id) ?: 0) < (recipe.products[id] ?: 0)) 0L
            else minOf(captured, goals?.prices?.get(id) ?: 0L)
        }
        val exits = if (guard != null && fundedPrice != null) mapOf(guard.itemId to fundedPrice)
            else BotProductionUses.recoverableValues(bot, recipe.products, operations = 1)
                .filterKeys { !personalOutput(bot, it) }
        return ProductionEconomics.quote(recipe, plan.training, 1, Double.MAX_VALUE,
            inputPrices.keys.associateWith { owned(bot, it) }, minOf(plan.remainingCoins, owned(bot, 995)),
            inputPrices, exits, usePrices) != null
    }

    private fun singlePurchasePrice(bot: Bot, id: Int, amount: Int): Long? {
        val shop = BotSupplySources.quote(bot, id, amount)?.takeIf { it.quantity >= amount }?.maximumUnitPrice
        val direct = NativeOutputDemand.traders(bot).mapNotNull { it.profitGatheringSupply(id) }
            .filter { it.item.amount >= amount && it.price in 1L..Int.MAX_VALUE.toLong() &&
                it.price * amount >= BotTradeManager.MINIMUM_OFFER_VALUE }
            .minOfOrNull { it.price }
        return listOfNotNull(shop, direct).filter { it > 0L }.minOrNull()
    }

    private fun hasSingleBatchBuyer(bot: Bot, id: Int, amount: Int, minimumPrice: Long): Boolean =
        NativeOutputDemand.traders(bot).any { trader ->
            trader.profitGatheringPurchase(id)?.let { it.item.amount >= amount && it.price >= minimumPrice } == true
        }

    private fun personalFoodOutput(bot: Bot, id: Int): Boolean = CookingProductionPaths.isFood(id) &&
        (BotFoodDemand.wouldRetain(bot, id) || BotTradeIntentions.hasUnmetPersonalSupplyDemand(bot, id))

    private fun personalOutput(bot: Bot, id: Int): Boolean = personalFoodOutput(bot, id) ||
        EquipmentDefinition.ALL.get(id).isPresent &&
            (BotTradeIntentions.hasUnmetPersonalSupplyDemand(bot, id) ||
                BotTradeIntentions.wouldRetainFirstEquipment(bot, id) ||
                BotGearDemand.forItem(bot, id)?.alternatives?.none { bot.totalItemAmount(it) > 0 } == true)

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
                    it.zeroValueByproducts == recipe.zeroValueByproducts &&
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
     * Uses the actual training termination policy rather than promising experience after its native stop.
     */
    private fun trainingExperienceRemaining(bot: Bot, skill: Int): Double {
        val combat = Skill.isCombatSkill(skill)
        val target = if (!combat && bot.preferences.isSkiller) SkillSet.MAXIMUM_EXPERIENCE.toDouble() else {
            val level = if (combat) bot.preferences.pk?.build()?.targetLevel(skill) ?: 99 else 99
            SkillSet.experienceForLevel(level).toDouble()
        }
        return (target - bot.skill(skill).experience).coerceAtLeast(0.0)
    }

    private fun batchCapacity(recipe: ProductionRecipe, inventorySize: Int): Int {
        val loose = recipe.materials.filterKeys { !itemDef(it).isStackable }.values.sumOf { it.toLong() }
        if (loose == 0L) return ProductionEconomics.MAX_OPERATIONS
        val stackSlots = recipe.materials.keys.count { itemDef(it).isStackable }
        return ((inventorySize - recipe.tools.size - stackSlots - 1).coerceAtLeast(0) / loose).toInt()
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
