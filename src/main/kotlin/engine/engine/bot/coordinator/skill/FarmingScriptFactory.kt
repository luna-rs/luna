package engine.bot.coordinator.skill

import api.bot.script.BotScript
import api.bot.script.DynamicBotScript
import api.bot.zone.SubZone
import api.predef.*
import api.predef.ext.*
import game.bot.scripts.skills.FarmingBotScript
import game.bot.scripts.skills.MakeCompostBotScript
import game.bot.scripts.skills.PrepareSaplingBotScript
import game.skill.farming.Farming
import game.skill.farming.allotment.AllotmentPatchLocation
import game.skill.farming.allotment.AllotmentSeed
import game.skill.farming.compostBin.CompostBinType
import game.skill.farming.herb.HerbPatchLocation
import game.skill.farming.seedling.TreeSeedling
import io.luna.game.model.mob.bot.Bot
import kotlin.time.Duration

/**
 * Selects useful visits to native farming plots in both training and profit mode.
 * Mature, diseased and dead crops are attended before starting another plot. Seed selection retains the normal
 * level and personality policy and prefers owned seed stock. Missing mandatory tools and care supplies are prepared
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
        select(bot, level, false) ?: empty(bot)
    override fun getProfitScript(bot: Bot, level: Int, zones: MutableList<SubZone>): BotScript =
        select(bot, level, true) ?: empty(bot)

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
    private fun select(bot: Bot, level: Int, profit: Boolean, returning: Boolean = false): BotScript? {
        Farming.update(bot)
        val duration = getDuration(bot)
        val saplings = TreeSeedling.entries.mapTo(HashSet()) { it.sapling }
        val candidates = FarmingBotScript.SITES.keys.mapNotNull { location ->
            val patch = Farming.patches(bot).getValue(location)
            val seeds = patch.crops.filter {
                bot.bank.computeAmountForId(it.seedId).toLong() + bot.inventory.computeAmountForId(it.seedId) >=
                    patch.seedsRequired(it)
            }
            if (returning && !patch.isOccupied() && seeds.none { it.seedId in saplings }) return@mapNotNull null
            val crop = getBestActivity(bot, minOf(level, bot.farming.level), { it.level }, seeds)
            FarmingBotScript(bot, location, crop?.seedId ?: 0, duration, replantTrees = !profit)
        }.filter { it.canPrepare() }
        val urgent = candidates.filter { it.patch.dead || it.patch.diseased || it.patch.harvestReady() }.ifEmpty { candidates }
        val preferred = if (profit) urgent.filter { it.location is HerbPatchLocation }.ifEmpty { urgent } else urgent
        val compost = MakeCompostBotScript.SITES.keys.flatMap { location ->
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
        if (selected != null) return grouped(bot, selected, candidates, duration, profit)
        return compost ?: getBestActivity(bot, minOf(level, bot.farming.level), { it.seedling.level },
                TreeSeedling.entries.mapNotNull { seedling ->
                    if (returning && bot.bank.computeAmountForId(seedling.seedling).toLong() +
                        bot.inventory.computeAmountForId(seedling.seedling) == 0L) return@mapNotNull null
                    val target = FarmingBotScript.SITES.keys.count {
                        Farming.patches(bot).getValue(it).findCrop(seedling.sapling) != null
                    }
                    if (target == 0) null else PrepareSaplingBotScript(bot, seedling, target, duration,
                        returnProfit = profit).takeIf { it.canPrepare() }
                }) ?: if (returning) null else starterRun(bot, duration, profit)
    }

    /**
     * Shares one bank and tool lifecycle across the selected area's useful plots. Other areas wait for another
     * activity boundary, so a return never expands into an unbounded cross-world tour.
     */
    private fun grouped(bot: Bot, selected: FarmingBotScript, candidates: List<FarmingBotScript>,
                        duration: Duration, profit: Boolean): FarmingBotScript {
        val zone = FarmingBotScript.SITES.getValue(selected.location)
        val nearby = candidates.filter {
            it.location != selected.location && FarmingBotScript.SITES.getValue(it.location) == zone
        }.sortedBy { if (it.patch.diseased || it.patch.dead || it.patch.harvestReady()) 0 else 1 }
        return FarmingBotScript(bot, selected.location, selected.seedId, duration, replantTrees = !profit,
            nearbyVisits = nearby.map { FarmingBotScript.Visit(it.location, it.seedId) })
    }

    /**
     * An ordinary selection with no owned crop inputs can request a small starter run instead of ending before
     * acquisition. Existing work, compost and owned sapling preparation always take precedence. Return checks
     * never call this path or request fresh seeds for unrelated empty plots.
     */
    private fun starterRun(bot: Bot, duration: Duration, profit: Boolean): FarmingBotScript? {
        if (bot.farming.level < AllotmentSeed.POTATO.level) return null
        val candidates = AllotmentPatchLocation.entries.mapNotNull { location ->
            val patch = Farming.patches(bot).getValue(location)
            if (patch.isOccupied()) null else FarmingBotScript(bot, location, AllotmentSeed.POTATO.seedId,
                duration, replantTrees = !profit).takeIf { it.canPrepare() }
        }
        val selected = candidates.minByOrNull {
            bot.position.computeLongestDistance(FarmingBotScript.SITES.getValue(it.location).inside)
        } ?: return null
        return grouped(bot, selected, candidates, duration, profit)
    }
}
