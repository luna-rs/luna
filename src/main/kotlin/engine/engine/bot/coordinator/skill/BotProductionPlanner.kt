package engine.bot.coordinator.skill

import api.attr.Attr
import api.bot.action.BotCombatActionHandler
import api.bot.action.SUPPLY_TRIP_TIMEOUT
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
import game.bot.scripts.skills.HerbloreVialRefill
import game.bot.scripts.skills.CutTreeBotScript
import game.skill.woodcutting.Woodcutting
import game.skill.woodcutting.cutTree.Axe
import game.skill.woodcutting.cutTree.Tree
import io.luna.Luna
import game.skill.herblore.makeUnfPotion.UnfPotion
import game.skill.smithing.BarType
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
import kotlin.math.roundToLong
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

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
     * Stocks a full training visit from native Woodcutting when the bot cannot buy logs and still has none to use.
     * The gathering script carries the exact recipe and resumes it only once the whole lot is owned.
     */
    internal fun collectTrainingLogs(bot: Bot, skill: Int, level: Int, duration: Duration,
                                     failed: ProductionPlan? = null): CutTreeBotScript? {
        if (skill != SKILL_FIREMAKING && skill != SKILL_FLETCHING) return null
        if (failed != null && (!failed.training || failed.recipes.size != 1 || trainingLogId(failed.recipe) == null))
            return null
        if (duration <= Duration.ZERO || !BotCombatActionHandler.wantsToTrain(bot, skill) ||
            !BotCombatActionHandler.mayGainExperience(bot, SKILL_WOODCUTTING)) return null
        val recipes = logRecipes(bot, skill, level).map { it.second }
        if (recipes.any { owned(bot, it.materials.keys.single()) > 0L }) return null
        val woodcuttingLevel = minOf(bot.woodcutting.level, bot.woodcutting.staticLevel)
        val axes = Axe.entries.filter { it.level <= woodcuttingLevel && owned(bot, it.id) > 0L }
            .ifEmpty { listOf(Axe.BRONZE) }
        val deferred = bot.attributes()[unavailableRecipes.attr]
        val now = System.currentTimeMillis()
        val ordered = recipes.sortedWith(compareByDescending<ProductionRecipe> { it.key == failed?.recipe?.key }
            .thenByDescending { it.experience })
        for (recipe in ordered) {
            if (deferred[recipe.key]?.after?.let { it > now } == true) continue
            val tree = Tree.entries.firstOrNull { it.logId in recipe.materials && it.level <= woodcuttingLevel }
                ?: continue
            if ((tree == Tree.TEAK || tree == Tree.MAHOGANY) && !CutTreeBotScript.groveSuppliesOwned(bot)) continue
            val zones = WoodcuttingScriptFactory.routes[tree].orEmpty().filter {
                it.entryBlocker(bot) == null && bot.recentEscape?.avoids(it) != true
            }
            if (zones.isEmpty()) continue
            val operations = fullLogOperations(bot, recipe, duration)
            if (operations < 1) continue
            val chance = axes.minOf { Woodcutting.successRate(woodcuttingLevel, tree, it) }
            if (chance <= 0.0) continue
            val ticks = Luna.settings().skills().woodcuttingSpeed() / chance + 3.0
            val gathering = SUPPLY_TRIP_TIMEOUT + ceil(operations * ticks * 1_200.0).toLong().milliseconds
            val plan = logWorkPlan(bot, recipe, operations, duration + gathering, failed) ?: continue
            return CutTreeBotScript(bot, setOf(tree), gathering, zones.toMutableList(), plan, duration)
                .also { it.trainingGoal = skill }
        }
        return null
    }

    /**
     * Gives Woodcutting a ready Firemaking follow-up when a single abundant log type covers the full visit.
     */
    internal fun stockedFiremaking(bot: Bot, duration: Duration =
        SkillingScriptFactory.getDuration(bot, SKILL_FIREMAKING)): InventoryBotScript? {
        if (duration <= Duration.ZERO || !BotCombatActionHandler.wantsToTrain(bot, SKILL_FIREMAKING)) return null
        for ((entry, recipe) in logRecipes(bot, SKILL_FIREMAKING, bot.firemaking.staticLevel).asReversed()) {
            val log = trainingLogId(recipe) ?: continue
            if (log != Tree.WILLOW.logId && log != Tree.MAPLE.logId) continue
            val operations = fullLogOperations(bot, recipe, duration)
            if (operations < 1 || owned(bot, log) < operations) continue
            val plan = logWorkPlan(bot, recipe, operations, duration, null, ownedLogs = true) ?: continue
            return entry.create(bot, duration).planProduction(plan)
        }
        return null
    }

    private fun trainingLogId(recipe: ProductionRecipe): Int? =
        if (recipe.key.startsWith("burn:") || recipe.key.startsWith("cut-log:")) recipe.materials.keys.single()
        else null

    private fun logRecipes(bot: Bot, skill: Int, level: Int): List<Pair<ProductionCatalogue.Entry, ProductionRecipe>> {
        if (skill != SKILL_FIREMAKING && skill != SKILL_FLETCHING) return emptyList()
        return ProductionCatalogue.bySkill[skill].orEmpty().mapNotNull { entry ->
            val recipe = entry.describe(bot)?.takeIf { trainingLogId(it) != null &&
                it.level <= minOf(level, bot.skill(skill).level) } ?: return@mapNotNull null
            entry to recipe
        }
    }

    private fun fullLogOperations(bot: Bot, recipe: ProductionRecipe, duration: Duration): Int {
        val load = batchCapacity(recipe, bot.inventory.capacity())
        val capacity = workCapacity(recipe, load, duration.inWholeMilliseconds, ProductionEconomics.MAX_OPERATIONS)
        return minOf(capacity, ceil(trainingExperienceRemaining(bot, recipe.skill) / recipe.experience).toInt())
    }

    private fun logWorkPlan(bot: Bot, recipe: ProductionRecipe, operations: Int, duration: Duration,
                            previous: ProductionPlan?, ownedLogs: Boolean = false): ProductionPlan? {
        val toolPrices = recipe.tools.filter { owned(bot, it) < 1L }.associateWith {
            BotSupplySources.quote(bot, it, 1)?.maximumUnitPrice ?: return null
        }
        val coins = BotTradeManager.transferableAmount(bot, 995).toLong()
        val available = (coins * BotTradeIntentions.budgetFraction(bot, BotTradePurpose.BUY)).toLong()
        val toolCost = toolPrices.values.sum()
        if (toolCost > minOf(available, previous?.remainingCoins ?: available)) return null
        val normalizeNotes = ownedLogs && recipe.materials.keys.any {
            bot.inventory.computeAmountForId(it).toLong() + bot.bank.computeAmountForId(it) < operations
        }
        val acquire = toolPrices.isNotEmpty() || normalizeNotes
        val ceilings = (recipe.materials.keys + recipe.tools).associateWith { toolPrices[it] ?: 1L }
        return ProductionPlan(recipe, true, operations, toolCost, ceilings,
            System.currentTimeMillis() + duration.inWholeMilliseconds,
            fixedTrainingBatch = acquire, allowAcquisition = acquire)
    }

    /**
     * Chooses a bounded, affordable native batch or a fully funded short native production chain.
     * Native preparation may support a later training recipe only when the complete chain's
     * external materials and tools fit its shared budget. Experience remains with each native action.
     */
    fun select(bot: Bot, skill: Int, level: Int, training: Boolean, duration: Duration,
               keyPrefix: String? = null, finalFood: Int? = null, merchantOutput: Int? = null,
               maximumCoinsLimit: Long = Long.MAX_VALUE,
               directOnly: Boolean = false,
               singlePurchaseBatch: Boolean = false,
               deadlineLimit: Long = Long.MAX_VALUE): InventoryBotScript? {
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
        if (deadlineLimit <= now) return rejected(Reason.EXPIRED)
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
                (!training || it.skill != skill || it.experience == 0.0 || BotCombatActionHandler.wantsToTrain(bot, skill)) }
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
        val trainingSupplies = ProductionTrainingSupplies(bot)
        val logIds = if (training) candidates.mapNotNull { (_, recipe) -> trainingLogId(recipe) }.toSet()
            else emptySet()
        val ownedLogs = logIds.filterTo(HashSet()) { owned(bot, it) > 0L }
        val logSources = if (ownedLogs.isEmpty()) logIds.mapNotNull { id ->
            trainingSupplies.firstSource(id)?.let { id to it }
        }.toMap() else emptyMap()
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
                if (ownedLogs.isNotEmpty() && trainingLogId(recipe)?.let { it !in ownedLogs } == true) continue
                val paths = if (directOnly || singlePurchaseBatch) listOf(listOf(ProductionStage(recipe, 1)))
                else ProductionRecipePaths.forRecipe(recipe, ::describe, allowOtherSkills,
                    trainingPrerequisites = training && ProductionTrainingPrerequisites.input(recipe) != null) {
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
                    val economy = if (training && ProductionTrainingPrerequisites.input(recipe) != null)
                        aggregate.copy(experience = recipe.experience * path.last().operations) else aggregate
                    add(Pipeline(firstEntry, path, economy))
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
        val selectSmithing = skill == SKILL_SMITHING && keyPrefix == null && merchantOutput == null
        fun smithWithOwnedSupplies(recipe: ProductionRecipe): Boolean =
            recipe.materials.all { (id, amount) -> owned(bot, id) >= amount } &&
                recipe.tools.all { owned(bot, it) > 0L } && recipe.coinCost <= owned(bot, 995)
        val ownedSmithing = selectSmithing && pipelines.any { smithWithOwnedSupplies(it.economy) }
        val products = pipelines.flatMap { it.economy.products.entries }.groupBy { it.key }
            .mapValues { (_, entries) -> entries.maxOf { it.value } }
        val recoverable = HashMap<Int, Long>()
        if (merchantOutput == null) products.entries.chunked(BotProductionUses.MAX_PRODUCT_TYPES).forEach { chunk ->
            recoverable.putAll(BotProductionUses.recoverableValues(bot, chunk.associate { it.key to it.value }))
        }
        val prices = HashMap<Int, Long>()
        val runecrafting = skill == SKILL_RUNECRAFTING
        val bankedRunecrafting = runecrafting && training
        val operationLimit = ProductionEconomics.operationLimit(skill, training)
        val workMillis = minOf(duration.inWholeMilliseconds, deadlineLimit - now)
        val runeSources = HashMap<Int, RuneSupply?>()
        fun runeSource(id: Int): RuneSupply? {
            if (!runeSources.containsKey(id)) runeSources[id] = runeSupply(bot, id)
            return runeSources[id]
        }
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
        val barSources = if (selectSmithing && !ownedSmithing) pipelines.asSequence()
            .flatMap { it.stages.first().recipe.materials.keys.asSequence() }
            .filter { it in BarType.ID_TO_BAR }
            .distinct()
            .mapNotNull { id ->
                val price = smithingBarPrice(bot, id) ?: return@mapNotNull null
                val offers = trainingSupplies.affordableSources(id, price)
                if (offers.isEmpty()) null else id to offers
            }.toMap() else emptyMap()
        fun ceiling(id: Int): Long = prices.getOrPut(id) {
            logSources[id]?.let { return@getOrPut it.unitPrice }
            if (runecrafting) runeSource(id)?.let { return@getOrPut it.unitPrice }
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
        val barBudget by lazy {
            val transferable = BotTradeManager.transferableAmount(bot, 995).toLong()
            minOf(budget, (transferable * BotTradeIntentions.budgetFraction(bot, BotTradePurpose.BUY)).toLong())
        }
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
                             val saleGuard: ProductionSaleGuard?, val refillVials: Boolean, val fixedTrainingBatch: Boolean,
                             val barPurchase: Int?)
        val water = UnfPotion.VIAL_OF_WATER
        val emptyVial = HerbloreVialRefill.EMPTY_VIAL
        val vialShops by lazy { BotSupplySources.generalStores(bot, emptyVial, bot.inventory.capacity()) }
        val scored = pipelines.mapNotNull { path ->
            val native = path.stages.first().recipe
            val recipe = path.economy
            val barPurchase = if (selectSmithing && !ownedSmithing)
                path.stages.singleOrNull()?.recipe?.materials?.entries?.firstOrNull { (id, amount) ->
                    id in BarType.ID_TO_BAR && owned(bot, id) < amount
                }?.key else null
            if (barPurchase != null && barPurchase !in barSources) {
                lastRejection = Reason.MATERIALS_UNAVAILABLE
                return@mapNotNull null
            }
            val fixedTrainingBatch = training && ProductionTrainingPrerequisites.input(path.stages.last().recipe) != null
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
            } else ids.associateWith { id -> if (fixedTrainingBatch) 1L else ceiling(id) }.toMutableMap()
            if (barPurchase != null) inputPrices[barPurchase] = barSources.getValue(barPurchase).maxOf { it.unitPrice }
            val inventorySize = bot.inventory.capacity()
            val capacity = if (path.stages.size == 1) {
                val load = batchCapacity(recipe, inventorySize)
                if (bankedRunecrafting) workCapacity(recipe, load, workMillis, operationLimit) else load
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
            } ?: operationLimit
            var requested = minOf(capacity, stockLimit ?: operationLimit, useLimit)
            if (singlePurchaseBatch) requested = minOf(requested, 1)
            if (barPurchase != null) {
                val offered = barSources.getValue(barPurchase).sumOf { it.quantity.toLong() }
                val available = (count(barPurchase) + offered) / recipe.materials.getValue(barPurchase)
                requested = minOf(requested.toLong(), available).toInt()
            }
            if (training && requested > 0) {
                requested = ProductionRecipePaths.trainingOperationLimit(path.stages, requested,
                    trainingExperienceRemaining(bot, skill))
                if (requested == 0) {
                    lastRejection = Reason.NO_EXPERIENCE
                    return@mapNotNull null
                }
            }
            val stock = ids.associateWith { if (fixedTrainingBatch) owned(bot, it) else count(it) }.toMutableMap()
            var refillVials = false
            if (fixedTrainingBatch) {
                requested = trainingSupplies.limit(recipe, stock, requested)
                if (requested == 0) {
                    lastRejection = Reason.MATERIALS_UNAVAILABLE
                    return@mapNotNull null
                }
                inputPrices.putAll(trainingSupplies.prices(recipe, stock, requested))
            } else if (training && skill == SKILL_HERBLORE && path.stages.size == 2 &&
                path.stages.all { it.operations == 1 } && native.key.startsWith("unfinished:") &&
                path.stages.last().recipe.key.startsWith("potion:") && recipe.materials[water] == 1) {
                val filled = HerbloreVialRefill.owned(bot, water)
                val empties = HerbloreVialRefill.owned(bot, emptyVial)
                val useful = recipe.materials.filterKeys { it != water }.entries.fold(requested) { limit, (id, amount) ->
                    minOf(limit.toLong(), HerbloreVialRefill.owned(bot, id) / amount).toInt()
                }
                if (useful > filled && recipe.tools.all { HerbloreVialRefill.owned(bot, it) > 0L }) {
                    val missing = useful - filled - empties
                    val shop = if (missing > 0 && budget > 0L) vialShops.firstOrNull { (_, quote) ->
                        quote.maximumUnitPrice <= budget
                    }?.second else null
                    if (empties > 0L || shop != null) {
                        refillVials = true
                        requested = minOf(useful.toLong(), filled + empties + (shop?.quantity ?: 0)).toInt()
                        stock[water] = filled + empties
                        inputPrices[water] = shop?.maximumUnitPrice ?: bot.world.economy.getPrice(emptyVial).coerceAtLeast(1L)
                    }
                }
            }
            if (!fixedTrainingBatch)
                ProductionRecipePaths.pricePreparations(path.stages.map { it.recipe }, inputPrices)
            if (runecrafting) {
                for ((id, amount) in recipe.materials) {
                    val available = (stock[id] ?: 0L) + (runeSource(id)?.quantity ?: 0L)
                    requested = minOf(requested, (available / amount).coerceAtMost(operationLimit.toLong()).toInt())
                }
                if (requested < 1) {
                    lastRejection = Reason.MATERIALS_UNAVAILABLE
                    return@mapNotNull null
                }
                if (recipe.tools.any { (stock[it] ?: 0L) < 1L && runeSource(it) == null }) {
                    lastRejection = Reason.TOOL_UNAVAILABLE
                    return@mapNotNull null
                }
            }
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
            val exits = if (merchantOutput == null) recipe.products.keys.mapNotNull { id ->
                recoverable[id]?.takeUnless { personalOutput(bot, id) }?.let { id to it }
            }.toMap() else mapOf(merchantOutput to merchantPrice)
            val quote = ProductionEconomics.quote(recipe, training, requested, remainingXp,
                stock, if (barPurchase != null) barBudget else budget, inputPrices, exits,
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
            val score = if (fixedTrainingBatch) {
                val travel = path.stages.sumOf {
                    ProductionTrainingPrerequisites.stationTicks(bot, it.recipe) ?: return@mapNotNull null
                } + trainingSupplies.travelTicks(recipe, stock, quote.operations)
                val xp = recipe.experience * quote.operations
                xp / (ticks * quote.operations + 25.0 + travel) / (1.0 + quote.maximumCoins / xp)
            } else if (training) recipe.experience / ticks /
                (1.0 + netCost / recipe.experience.coerceAtLeast(1.0))
            else if (guard != null) (quote.outputValue.toDouble() * quote.operations - guard.batchCost) /
                (ticks * quote.operations + NativeProductionOpportunities.overhead(bot, recipe))
            else (quote.outputValue - quote.inputValue).toDouble() / ticks
            val stocked = recipe.materials.all { (id, amount) -> (stock[id] ?: 0L) >= amount } &&
                recipe.tools.all { (stock[it] ?: 0L) > 0L }
            Candidate(path, quote, inputPrices, usePrices, stockLimit != null, stocked, score, guard,
                refillVials && quote.operations > HerbloreVialRefill.owned(bot, water), fixedTrainingBatch, barPurchase)
        }
        val compared = scored.filterNot { it.fixedTrainingBatch } + scored.filter { it.fixedTrainingBatch }
            .groupBy { it.path.stages.last().recipe.key }.values.map { choices -> choices.maxBy { it.score } }
        fun soldLog(candidate: Candidate): Boolean =
            trainingLogId(candidate.path.stages.first().recipe) in logSources
        val smithingCandidates = when {
            ownedSmithing -> compared.filter { smithWithOwnedSupplies(it.path.economy) }
            selectSmithing -> compared.filter { it.barPurchase != null }
            else -> emptyList()
        }
        val available = if (smithingCandidates.isNotEmpty()) smithingCandidates else if (compared.any(::soldLog)) compared.filter {
            trainingLogId(it.path.stages.first().recipe) == null || soldLog(it)
        } else compared
        val preferred = if (merchantOutput != null) available else {
            val suppliedPaths = available.filter { it.stocked }.groupBy { it.path.stages.last().recipe.key }
            val executable = if (available.any { it.refillVials })
                available.filter { it.stocked || it.refillVials } else available
            executable.filterNot { candidate ->
                !candidate.fixedTrainingBatch && ProductionRecipePaths.hasSuppliedSuffix(candidate.path.stages,
                    suppliedPaths[candidate.path.stages.last().recipe.key].orEmpty().map { it.path.stages })
            }.sortedWith(compareByDescending<Candidate> { it.useful }.thenByDescending { it.stocked }.thenByDescending { it.score }
                .thenByDescending { it.path.stages.last().recipe.level })
        }
        val chosen = when {
            preferred.isEmpty() -> return rejected(lastRejection)
            merchantOutput != null -> preferred.maxBy { it.score }
            bot.personality.isIntelligent || bot.personality.isDextrous -> preferred.first()
            bot.personality.isDumb && randBoolean() -> preferred.last()
            else -> preferred.take(8).random()
        }
        val deadline = now + if (bankedRunecrafting) workMillis else minOf(workMillis, 10 * 60_000L)
        val plan = ProductionPlan(chosen.path.stages.first().recipe, training, chosen.quote.operations,
            chosen.quote.maximumCoins, chosen.caps, deadline,
            continuationRecipes = chosen.path.stages.drop(1).map { it.recipe }, personalUsePrices = chosen.usePrices,
            stageRatios = chosen.path.stages.map { it.operations }, saleGuard = chosen.saleGuard,
            singlePurchaseBatch = singlePurchaseBatch, refillVials = chosen.refillVials,
            fixedTrainingBatch = chosen.fixedTrainingBatch, barPurchase = chosen.barPurchase)
        chosen.saleGuard?.let { guard ->
            val quantity = chosen.path.economy.valuedProducts.getValue(guard.itemId).toLong() * plan.operations
            if (!NativeOutputDemand.reserve(bot, guard.identity, mapOf(guard.itemId to quantity), deadline,
                    mapOf(guard.itemId to guard.minimumPrice))) return rejected(Reason.CROWDING)
        }
        BotProductionMetrics.recordDecision(bot, Source.PLANNER, Reason.SELECTED, plan.recipe.key, skill, plan = plan)
        return chosen.path.entry.create(bot, duration).planProduction(plan)
    }

    private fun smithingBarPrice(bot: Bot, id: Int): Long? {
        val economy = bot.world.economy
        val recent = economy.getRecentTradeReference(id)?.price()?.takeIf { it.isFinite() && it > 0.0 }
        return (recent?.roundToLong()?.coerceAtLeast(1L) ?: economy.getPrice(id))
            .takeIf { it in 1L..Int.MAX_VALUE.toLong() }
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
            plan.training && !BotCombatActionHandler.wantsToTrain(bot, plan.goalSkill)) return false
        for (remaining in plan.recipes.drop(plan.currentStageIndex)) {
            if (bot.skill(remaining.skill).staticLevel < remaining.level || bot.skill(remaining.skill).level < remaining.level ||
                !BotCombatActionHandler.mayGainExperience(bot, remaining.skill) ||
                plan.training && remaining.experience > 0.0 &&
                    remaining.skill == plan.goalSkill && !BotCombatActionHandler.wantsToTrain(bot, remaining.skill) ||
                nativeEntry(bot, remaining) == null) return false
        }
        if (plan.training) {
            if (!ProductionRecipePaths.trainingReachable(plan.remainingStages(),
                    trainingExperienceRemaining(bot, plan.goalSkill))) return false
            if (plan.fixedTrainingBatch) return true
        }
        val recipe = plan.economicRecipe() ?: return false
        if (!plan.allowAcquisition) return plan.targets().all { (id, target) ->
            bot.inventory.computeAmountForId(id).toLong() + bot.bank.computeAmountForId(id) >= target
        } && recipe.coinCost <= minOf(plan.remainingCoins, owned(bot, 995))
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
            if (id == plan.barPurchase || plan.refillVials) return@mapValues ceiling
            val basis = carried[id]?.takeIf { id in prepared }
            if (basis != null) maxOf(basis, disposal[id] ?: 0L)
            else if (plan.singlePurchaseBatch) {
                val missing = ((recipe.materials[id] ?: 0) - owned(bot, id)).coerceAtLeast(0L).toInt()
                if (missing == 0) return@mapValues ceiling
                val price = singlePurchasePrice(bot, id, missing) ?: return false
                if (price > ceiling) return false
                ceiling
            } else {
                if (native.skill == SKILL_RUNECRAFTING) return@mapValues ceiling
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

    /**
     * Native executable supply covered by a single unit ceiling.
     *
     * @author lare96
     */
    internal data class RuneSupply(val quantity: Long, val unitPrice: Long)

    internal fun runeSupply(bot: Bot, id: Int): RuneSupply? {
        val shops = BotSupplySources.visits(bot, id, BotSupplySources.MAX_QUANTITY)
        val trades = NativeOutputDemand.traders(bot).mapNotNull { it.profitGatheringSupply(id) }
            .filter { it.price in 1L..Int.MAX_VALUE.toLong() &&
                it.price * it.item.amount >= BotTradeManager.MINIMUM_OFFER_VALUE }
            .map { it.price to it.item.amount }.toList()
        val prices = buildList {
            for (shop in shops) add(shop.unitPrice to shop.quantity)
            addAll(trades)
        }
        val price = prices.minOfOrNull { it.first } ?: return null
        return RuneSupply(prices.filter { it.first <= price }.sumOf { it.second.toLong() }, price)
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
                (it.key == recipe.key || recipe.skill == SKILL_RUNECRAFTING &&
                    it.key.replace("runecraft:pure:", "runecraft:") == recipe.key) &&
                    it.level == recipe.level && it.materials == recipe.materials &&
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

    private fun workCapacity(recipe: ProductionRecipe, load: Int, millis: Long, maximum: Int): Int {
        if (load <= 0) return 0
        val ticks = if (recipe.skill == SKILL_FIREMAKING) 6.0 else processingTicks(recipe)
        return (millis / (600.0 * (ticks + 30.0 / load))).toInt().coerceIn(1, maximum)
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
