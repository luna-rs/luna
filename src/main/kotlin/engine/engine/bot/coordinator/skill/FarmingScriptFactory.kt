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
import game.skill.farming.compostBin.CompostBinType
import game.skill.farming.herb.HerbPatchLocation
import game.skill.farming.seedling.TreeSeedling
import io.luna.game.model.mob.bot.Bot

/**
 * Selects useful visits to native farming plots in both training and profit mode.
 * Mature, diseased and dead crops are attended before starting another plot. Seed selection retains the normal
 * level and personality policy and requires owned stock and tools. Profit visits prefer herb patches among equally
 * urgent work; prices are not fabricated. With no available work the selection completes immediately, allowing
 * another activity while the native growth engine runs. No seeds, equipment or experience are granted.
 * When no plot needs a supplied visit, owned tree seeds or seedlings can be prepared up to one sapling per mapped
 * matching tree plot. Watered seedlings already count toward this reserve; native growth never blocks selection.
 * Compost visits recover ready fertilizer and finish existing batches before starting another reserve with owned
 * weeds, roots or farm produce. Closed bins are ignored until their native rot deadline; herbs are not composted.
 *
 * @author lare96
 */
object FarmingScriptFactory : SkillingScriptFactory(SKILL_FARMING) {
    override fun getTrainingScript(bot: Bot, level: Int, zones: MutableList<SubZone>): BotScript = select(bot, level, false)
    override fun getProfitScript(bot: Bot, level: Int, zones: MutableList<SubZone>): BotScript = select(bot, level, true)

    /**
     * Builds one visit from current native state, with real elapsed growth processed before eligibility checks.
     */
    private fun select(bot: Bot, level: Int, profit: Boolean): BotScript {
        Farming.update(bot)
        val duration = getDuration(bot)
        val candidates = FarmingBotScript.SITES.keys.map { location ->
            val patch = Farming.patches(bot).getValue(location)
            val seeds = patch.crops.filter {
                bot.bank.computeAmountForId(it.seedId).toLong() + bot.inventory.computeAmountForId(it.seedId) >=
                    patch.seedsRequired(it)
            }
            val crop = getBestActivity(bot, minOf(level, bot.farming.level), { it.level }, seeds)
            FarmingBotScript(bot, location, crop?.seedId ?: 0, duration, replantTrees = !profit)
        }.filter { it.isEligible() }
        val urgent = candidates.filter { it.patch.dead || it.patch.diseased || it.patch.harvestReady() }.ifEmpty { candidates }
        val preferred = if (profit) urgent.filter { it.location is HerbPatchLocation }.ifEmpty { urgent } else urgent
        val compost = MakeCompostBotScript.SITES.keys.flatMap { location ->
            listOf(CompostBinType.COMPOST, CompostBinType.SUPERCOMPOST).map {
                MakeCompostBotScript(bot, location, it, duration)
            }
        }.filter { it.isEligible() }.shuffled().maxByOrNull {
            val stage = when {
                it.bin.canTakeOut() || it.bin.isRotted(System.currentTimeMillis()) -> 3
                it.bin.canClose() -> 2
                it.bin.amount > 0 -> 1
                else -> 0
            }
            stage * 2 + if (it.type == CompostBinType.SUPERCOMPOST) 1 else 0
        }
        return getBestActivity(bot, 99, { it.patch.crop?.level ?: it.patch.findCrop(it.seedId)?.level ?: 1 }, preferred)
            ?: compost
            ?: getBestActivity(bot, minOf(level, bot.farming.level), { it.seedling.level },
                TreeSeedling.entries.mapNotNull { seedling ->
                    val target = FarmingBotScript.SITES.keys.count {
                        Farming.patches(bot).getValue(it).findCrop(seedling.sapling) != null
                    }
                    if (target == 0) null else PrepareSaplingBotScript(bot, seedling, target, duration).takeIf { it.isEligible() }
                })
            ?: object : DynamicBotScript(bot) {
                override suspend fun run() = true
            }
    }
}
