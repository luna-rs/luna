package engine.bot.coordinator.skill

import api.bot.script.BotScript
import api.bot.script.ProductionMode
import api.bot.script.ProductionRecipe
import api.bot.script.DynamicBotScript
import api.bot.script.VoidBotScript
import api.bot.action.BotCombatActionHandler
import api.bot.action.BotFarmingActionHandler
import api.bot.action.trading.BotMerchantValuation
import api.bot.action.trading.BotTradeIntentions
import api.bot.action.trading.BotTradeManager
import api.bot.action.trading.BotTradePurpose
import api.bot.zone.SubZone
import api.predef.*
import api.predef.ext.*
import game.bot.scripts.skills.FarmingBotScript
import game.bot.scripts.skills.MakeCompostBotScript
import game.bot.scripts.skills.PrepareSaplingBotScript
import game.skill.farming.Farming
import game.skill.farming.compostBin.CompostBin
import game.skill.farming.compostBin.CompostBinType
import game.skill.farming.herb.HerbPatchLocation
import game.skill.farming.patch.CompostType
import game.skill.farming.patch.Crop
import game.skill.farming.patch.FarmingPatch
import game.skill.farming.patch.HealthCheckPatch
import game.skill.farming.special.BelladonnaPatch
import game.skill.farming.seedling.TreeSeedling
import game.skill.farming.tree.AbstractTreePatch
import game.skill.farming.tree.TreeSapling
import game.skill.woodcutting.cutTree.Axe
import io.luna.game.model.item.Equipment
import io.luna.game.model.mob.bot.Bot
import kotlin.math.ceil
import kotlin.math.pow
import kotlin.time.Duration

/**
 * Selects useful visits to native farming plots in both training and profit mode.
 * Mature, diseased and dead crops are attended before starting another plot. Seed selection retains the normal
 * level and personality policy. Training considers affordable seed goals before ownership; profit and return visits
 * retain owned seed stock. Missing mandatory tools and care supplies are prepared
 * before travel. Neighbouring plots in the same activity area share a run and a carried tool set.
 * Profit visits prefer herb patches among equally
 * urgent work; prices are not fabricated. With no available work the selection completes immediately, allowing
 * another activity while the native growth engine runs. No seeds, equipment or experience are granted.
 * When no plot needs a supplied visit, owned tree seeds or seedlings can be prepared up to one sapling per mapped
 * matching tree plot. Watered seedlings already count toward this reserve; native growth never blocks selection.
 * Compost visits recover ready fertilizer and finish existing batches before starting another reserve with owned
 * weeds, roots or farm produce. Owned tomatoes can supply one bin of rotten tomatoes. Closed bins are ignored
 * until their native rot deadline; herbs are not composted.
 *
 * @author lare96
 */
object FarmingScriptFactory : SkillingScriptFactory(SKILL_FARMING) {
    override fun getTrainingScript(bot: Bot, level: Int, zones: MutableList<SubZone>): BotScript =
        if (BotCombatActionHandler.wantsToTrain(bot, SKILL_FARMING)) select(bot, level, false) ?: empty(bot)
        else empty(bot)
    override fun getProfitScript(bot: Bot, level: Int, zones: MutableList<SubZone>): BotScript =
        select(bot, level, true) ?: VoidBotScript(bot)

    internal fun getProfitHarvestScript(bot: Bot): BotScript =
        select(bot, bot.farming.staticLevel, profit = true, returning = true, harvestOnly = true) ?: VoidBotScript(bot)

    /**
     * Quotes the actual planting or preparation basket before using the ordinary farming visit.
     * Native state owns plot and bin timers, and return visits handle their harvests.
     */
    internal fun getProductionScript(bot: Bot, mode: ProductionMode, duration: Duration,
                                     deficits: Map<Int, Int>): BotScript? {
        Farming.update(bot)
        val production = mode == ProductionMode.PRODUCTION
        val level = minOf(bot.farming.level, bot.farming.staticLevel)
        val pots = Farming.FILLED_PLANT_POTS.firstOrNull { BotFarmingActionHandler.stock(bot, it) > 0L }
            ?: Farming.PLANT_POT
        val opportunities = buildList<() -> BotScript?> {
            for (location in FarmingBotScript.SITES.keys.shuffled()) {
                val patch = Farming.patches(bot).getValue(location)
                if (patch.isOccupied() && !patch.dead) continue
                for (crop in patch.crops.shuffled()) {
                    if (crop.level > level || (production && crop.produce.id !in deficits)) continue
                    if (!production && BotFarmingActionHandler.stock(bot, crop.seedId) < patch.seedsRequired(crop)) continue
                    add {
                        val script = FarmingBotScript(bot, location, crop.seedId, duration)
                        if (!script.canPrepare()) null else {
                            val supplies = script.preparationSupplies().associate { it.id to it.amount }
                            // Price disease and the basic native harvest lives conservatively before planting.
                            val lives = if (patch is HealthCheckPatch || patch is AbstractTreePatch) 1
                                else FarmingPatch.BASE_HARVEST_LIVES
                            val probability = (1.0 - crop.baseDiseaseChance.coerceIn(0, 128) / 128.0)
                                .pow((crop.stages - 1).coerceAtLeast(0))
                            val requirements = supplies + (crop.seedId to patch.seedsRequired(crop))
                            val consumed = requirements.filterKeys { it == crop.seedId || it == Farming.PLANT_CURE ||
                                it == CompostType.COMPOST.itemId || it == CompostType.SUPERCOMPOST.itemId }
                            val recipe = ProductionRecipe("plant:${crop.seedId}", skillId, crop.level,
                                consumed, requirements.keys - consumed.keys,
                                mapOf(crop.produce.id to (crop.produce.amount * lives)), crop.plantXp, probability)
                            ProductionVisitEconomics.quote(bot, recipe, mode, 1) {
                                BotFarmingActionHandler.stock(bot, it)
                            }?.let { script.limitPurchases(it.maximumCoins, it.maximumUnitPrices) }
                        }
                    }
                }
            }
            for (seedling in TreeSeedling.entries.shuffled()) {
                val available = FarmingBotScript.SITES.keys.count { location ->
                    val patch = Farming.patches(bot).getValue(location)
                    (!patch.isOccupied() || patch.dead) && patch.findCrop(seedling.sapling) != null
                }
                if (seedling.level > level) continue
                val owned = BotFarmingActionHandler.stock(bot, seedling.sapling)
                val target = if (production) maxOf(available.toLong(), owned + (deficits[seedling.sapling] ?: 0))
                    else owned + 1
                if (target <= owned) continue
                add {
                    val script = PrepareSaplingBotScript(bot, seedling, target.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                        duration, returnProfit = true)
                    if (!script.canPrepare()) null else {
                        val recipe = ProductionRecipe("sapling:${seedling.sapling}", skillId, seedling.level,
                            mapOf(seedling.seed to 1, pots to 1), emptySet(), mapOf(seedling.sapling to 1), 0.0)
                        script.takeIf { production || ProductionVisitEconomics.quote(bot, recipe, mode, 1) != null }
                    }
                }
            }
            for (location in MakeCompostBotScript.SITES.keys.shuffled()) {
                for (type in listOf(CompostBinType.COMPOST, CompostBinType.SUPERCOMPOST)) {
                    val missing = deficits[type.product] ?: 0
                    if (production && missing < 1) continue
                    val target = BotFarmingActionHandler.stock(bot, type.product) +
                        if (production) missing else CompostBin.MAX_ITEMS
                    add {
                        val script = MakeCompostBotScript(bot, location, type, duration,
                            target.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(), returnProfit = true)
                        val supplies = script.preparationSupplies()
                        if (script.bin.amount != 0 || supplies.isNullOrEmpty()) null else {
                            val inputs = supplies.associate { it.id to it.amount }.toMutableMap()
                            inputs[Farming.BUCKET] = CompostBin.MAX_ITEMS
                            val recipe = ProductionRecipe("compost:${type.name}", skillId, 1, inputs,
                                emptySet(), mapOf(type.product to CompostBin.MAX_ITEMS), type.experience)
                            script.takeIf { ProductionVisitEconomics.quote(bot, recipe, mode, 1) != null }
                        }
                    }
                }
            }
        }
        // A full plot, a crop being protected, and a bin still rotting cannot start another batch.
        return opportunities.shuffled().firstNotNullOfOrNull { it() }
    }

    /**
     * Selects existing crop/bin work or owned sapling intermediates without starting another ordinary seed plot.
     * Eligibility remains in the native visit scripts, including flower protection and stored-tool requirements.
     */
    internal fun getReturnScript(bot: Bot, profit: Boolean): BotScript? =
        select(bot, bot.farming.staticLevel, profit, returning = true)

    private fun empty(bot: Bot): BotScript = object : DynamicBotScript(bot) {
        override suspend fun run() = true
    }

    /**
     * Builds one visit from current native state, with real elapsed growth processed before eligibility checks.
     */
    private fun select(bot: Bot, level: Int, profit: Boolean, returning: Boolean = false,
                       harvestOnly: Boolean = false): BotScript? {
        Farming.update(bot)
        val duration = getDuration(bot, skillId)
        val training = if (!profit && !returning) TrainingBudget(bot) else null
        val saplings = TreeSeedling.entries.mapTo(HashSet()) { it.sapling }
        val candidates = FarmingBotScript.SITES.keys.mapNotNull { location ->
            val patch = Farming.patches(bot).getValue(location)
            if (harvestOnly && !patch.isOccupied()) return@mapNotNull null
            val seeds = if (harvestOnly) emptyList() else patch.crops.filter { crop ->
                if (training != null) crop.level <= minOf(level, bot.farming.level) && training.affordable(patch, crop)
                else bot.bank.computeAmountForId(crop.seedId).toLong() + bot.inventory.computeAmountForId(crop.seedId) >=
                    patch.seedsRequired(crop)
            }
            if (returning && !patch.isOccupied() && seeds.none { it.seedId in saplings }) return@mapNotNull null
            val crop = getBestActivity(bot, minOf(level, bot.farming.level), { it.level }, seeds)
            FarmingBotScript(bot, location, crop?.seedId ?: 0, duration, replantTrees = !profit)
        }.filter { it.canPrepare() }
        val urgent = candidates.filter { it.patch.dead || it.patch.diseased || it.patch.harvestReady() }.ifEmpty { candidates }
        val preferred = if (profit) urgent.filter { it.location is HerbPatchLocation }.ifEmpty { urgent } else urgent
        val compost = if (harvestOnly) null else MakeCompostBotScript.SITES.keys.flatMap { location ->
            CompostBinType.entries.map {
                MakeCompostBotScript(bot, location, it, duration,
                    target = if (it == CompostBinType.TOMATOES) 15 else 30, returnProfit = profit)
            }
        }.filter { (!returning || it.bin.amount > 0) && it.canPrepare() }.shuffled().maxByOrNull {
            val stage = when {
                it.bin.canTakeOut() || it.bin.isRotted(System.currentTimeMillis()) -> 3
                it.bin.canClose() -> 2
                it.bin.amount > 0 -> 1
                else -> 0
            }
            stage * 2 + if (it.type == CompostBinType.SUPERCOMPOST) 1 else 0
        }
        val selected = getBestActivity(bot, 99,
            { it.patch.crop?.level ?: it.patch.findCrop(it.seedId)?.level ?: 1 }, preferred)
        if (selected != null) return grouped(bot, selected, candidates, duration, profit, training)
        if (harvestOnly) return null
        return compost ?: getBestActivity(bot, minOf(level, bot.farming.level), { it.seedling.level },
                TreeSeedling.entries.mapNotNull { seedling ->
                    if (returning && bot.bank.computeAmountForId(seedling.seedling).toLong() +
                        bot.inventory.computeAmountForId(seedling.seedling) == 0L) return@mapNotNull null
                    val target = FarmingBotScript.SITES.keys.count {
                        Farming.patches(bot).getValue(it).findCrop(seedling.sapling) != null
                    }
                    if (target == 0) null else PrepareSaplingBotScript(bot, seedling, target, duration,
                        returnProfit = profit).takeIf { it.canPrepare() }
                })
    }

    /**
     * Shares one bank and tool lifecycle across the selected area's useful plots. Other areas wait for another
     * activity boundary, so a return never expands into an unbounded cross-world tour.
     */
    private fun grouped(bot: Bot, selected: FarmingBotScript, candidates: List<FarmingBotScript>,
                        duration: Duration, profit: Boolean, training: TrainingBudget? = null): FarmingBotScript {
        val zone = FarmingBotScript.SITES.getValue(selected.location)
        val nearby = candidates.filter {
            it.location != selected.location && FarmingBotScript.SITES.getValue(it.location) == zone
        }.sortedBy { if (it.patch.diseased || it.patch.dead || it.patch.harvestReady()) 0 else 1 }
        fun build(visits: List<FarmingBotScript>) = FarmingBotScript(bot, selected.location, visits.first().seedId,
            duration, replantTrees = !profit,
            nearbyVisits = visits.drop(1).map { FarmingBotScript.Visit(it.location, it.seedId) })
        if (training == null) return build(listOf(selected) + nearby)
        val accepted = arrayListOf(selected)
        for (candidate in nearby) {
            val proposed = accepted + candidate
            if (proposed.none(training::plants) || training.limit(build(proposed), proposed) != null) accepted += candidate
        }
        val result = build(accepted)
        val limit = training.limit(result, accepted)
        if (limit != null) return result.limitPurchases(limit.first, limit.second)
        if (accepted.none(training::plants)) return result
        // Existing care remains useful when a complete planting basket cannot be justified.
        return FarmingBotScript(bot, selected.location, 0, duration, replantTrees = !profit,
            nearbyVisits = accepted.drop(1).map { FarmingBotScript.Visit(it.location, 0) })
    }

    /**
     * Captures one decision's prices, native ownership and ordinary purse once. Crop checks reuse these small maps;
     * no player, world stock or shop scans occur while comparing the fixed native seed catalogue.
     *
     * @author lare96
     */
    private class TrainingBudget(private val bot: Bot) {
        private val stock = HashMap<Int, Long>()
        private val prices = HashMap<Int, Long>()
        private val purse = (BotTradeManager.transferableAmount(bot, 995).toDouble() *
            BotTradeIntentions.budgetFraction(bot, BotTradePurpose.BUY)).toLong()

        private fun owned(id: Int): Long = stock.getOrPut(id) { BotFarmingActionHandler.stock(bot, id) }
        private fun price(id: Int): Long = prices.getOrPut(id) {
            val guide = world.economy.prices[id]?.price?.takeIf { it.isFinite() && it > 0.0 }
                ?: BotMerchantValuation.peekGuide(id).toDouble()
            ceil(guide.coerceAtLeast(1.0) * 2.0).toLong().coerceIn(1L, Int.MAX_VALUE.toLong())
        }

        fun affordable(patch: FarmingPatch, crop: Crop): Boolean {
            val requirements = linkedMapOf(crop.seedId to patch.seedsRequired(crop), patch.plantTool to 1)
            if (patch.needsRaking() || patch.dead) requirements[Farming.RAKE] = 1
            patch.harvestTool?.let { requirements[it] = 1 }
            if (patch is BelladonnaPatch && bot.equipment[Equipment.HANDS] == null) requirements[1059] = 1
            if (patch is AbstractTreePatch && patch.harvestReady() && !patch.dead) {
                if ((patch.crop as? TreeSapling)?.let { bot.woodcutting.level < it.tree.level } == true) return false
                val axes = Axe.entries.filter { bot.woodcutting.level >= it.level }
                if (axes.none { bot.equipment[Equipment.WEAPON]?.id == it.id }) {
                    val axe = axes.firstOrNull { owned(it.id) > 0L } ?: axes.minByOrNull { it.level } ?: return false
                    requirements[axe.id] = 1
                }
            }
            return FarmingTrainingEconomics.maximumPurchase(requirements, setOf(crop.seedId),
                FarmingTrainingEconomics.experience(patch, crop), ::owned, ::price, purse) != null
        }

        fun plants(visit: FarmingBotScript): Boolean = visit.seedId > 0 &&
            visit.preparationSupplies().any { it.id == visit.seedId }

        fun limit(script: FarmingBotScript, visits: List<FarmingBotScript>): Pair<Long, Map<Int, Long>>? {
            val planting = visits.filter(::plants)
            if (planting.isEmpty()) return null
            val requirements = script.preparationSupplies().associate { it.id to it.amount }.toMutableMap()
            var experience = 0.0
            for (visit in planting) {
                val crop = visit.patch.findCrop(visit.seedId) ?: return null
                experience += FarmingTrainingEconomics.experience(visit.patch, crop)
                visit.patch.harvestTool?.let { requirements[it] = maxOf(requirements[it] ?: 0, 1) }
                if (visit.patch is BelladonnaPatch && bot.equipment[Equipment.HANDS] == null) requirements[1059] = 1
            }
            val consumed = planting.mapTo(HashSet()) { it.seedId } + setOf(Farming.PLANT_CURE,
                Farming.SCARECROW, CompostType.COMPOST.itemId, CompostType.SUPERCOMPOST.itemId)
            val maximum = FarmingTrainingEconomics.maximumPurchase(requirements, consumed, experience,
                ::owned, ::price, purse) ?: return null
            return maximum to requirements.keys.associateWith(::price)
        }
    }
}
