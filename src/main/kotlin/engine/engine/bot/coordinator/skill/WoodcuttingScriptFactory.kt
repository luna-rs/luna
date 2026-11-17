package engine.bot.coordinator.skill

import api.bot.script.BotScript
import api.bot.script.ProfitGatheringGoal
import api.bot.script.VoidBotScript
import api.bot.script.recentEscape
import api.bot.action.trading.ProfitGatheringDemand
import game.skill.Skills
import game.skill.woodcutting.cutTree.Axe
import io.luna.Luna
import game.content.hardwoodGrove.HardwoodGrove
import api.bot.zone.SubZone
import api.predef.*
import game.bot.scripts.skills.CutTreeBotScript
import game.skill.firemaking.Log
import game.skill.woodcutting.cutTree.Tree
import game.skill.woodcutting.cutTree.Tree.*
import io.luna.game.model.mob.bot.Bot
import kotlin.time.Duration

/**
 * Selects tree-specific methods, preserving willow training and level-based profit progression.
 * Each route carries only the trees it supports; casual bots retain a broader range of methods.
 *
 * @author lare96
 */
object WoodcuttingScriptFactory : SkillingScriptFactory(SKILL_WOODCUTTING) {
    /**
     * Dedicated native routes for each supported resource. Hardwood sessions retain the grove's paid admission
     * and only select it with owned trading sticks or gems accepted by Gabooty.
     */
    private val routes = mapOf(
        NORMAL to setOf(SubZone.WEST_VARROCK_TREES, SubZone.EAST_EDGEVILLE_TREES, SubZone.NORTH_CATHERBY_TREES,
            SubZone.LUMBRIDGE_WOODLAND),
        OAK to setOf(SubZone.WEST_VARROCK_TREES, SubZone.EAST_EDGEVILLE_TREES, SubZone.NORTH_CATHERBY_TREES,
            SubZone.LUMBRIDGE_WOODLAND),
        ACHEY to setOf(SubZone.FELDIP_ACHEY_TREES),
        TEAK to setOf(SubZone.TAI_BWO_WANNAI_HARDWOOD_GROVE),
        MAHOGANY to setOf(SubZone.TAI_BWO_WANNAI_HARDWOOD_GROVE),
        WILLOW to setOf(SubZone.DRAYNOR_MAIN, SubZone.WEST_DRAYNOR_WILLOWS, SubZone.CATHERBY_BEACH_WILLOWS,
            SubZone.SOUTH_ARDOUGNE_WILLOWS, SubZone.NORTH_ARDOUGNE_WILLOWS,
            SubZone.EAST_ARDOUGNE_WILLOWS, SubZone.SOUTH_YANILLE_WILLOWS,
            SubZone.RIMMINGTON_COAST_WILLOWS, SubZone.PORT_SARIM_COAST_WILLOWS,
            SubZone.GNOME_STRONGHOLD_WEST_WILLOWS, SubZone.GNOME_STRONGHOLD_NORTH_WEST_WILLOW,
            SubZone.GNOME_STRONGHOLD_EAST_WILLOWS, SubZone.GNOME_STRONGHOLD_NORTH_EAST_WILLOW),
        MAPLE to setOf(SubZone.SEERS_VILLAGE_MAIN, SubZone.MCGRUBORS_NORTH_WEST_MAPLES,
            SubZone.MCGRUBORS_NORTH_MAPLES, SubZone.MCGRUBORS_NORTH_EAST_MAPLES,
            SubZone.MCGRUBORS_EAST_MAPLES, SubZone.SEERS_NORTH_WEST_FOREST_MAPLES,
            SubZone.SEERS_NORTH_CENTRAL_FOREST_MAPLES, SubZone.SEERS_NORTH_EAST_FOREST_MAPLES,
            SubZone.SEERS_NORTHERN_RIDGE_MAPLES, SubZone.SOUTH_SORCERERS_TOWER_MAPLES,
            SubZone.SEERS_SOUTH_WEST_MAPLE, SubZone.SEERS_SOUTH_EAST_MAPLES, SubZone.BARBARIAN_OUTPOST_MAPLE),
        HOLLOW to setOf(SubZone.PORT_PHASMATYS_HOLLOW_TREES),
        YEW to setOf(SubZone.LUMBER_YARD_YEWS, SubZone.EAST_DRAYNOR_YEWS, SubZone.VARROCK_PALACE_YEWS,
            SubZone.SOUTH_FALADOR_YEWS, SubZone.SOUTH_SEERS_VILLAGE_YEWS, SubZone.WEST_CATHERBY_YEWS,
            SubZone.EDGEVILLE_YEWS, SubZone.RIMMINGTON_YEWS, SubZone.GNOME_STRONGHOLD_BANK_YEWS,
            SubZone.GNOME_STRONGHOLD_SOUTH_YEWS, SubZone.GNOME_STRONGHOLD_EAST_YEW),
        MAGIC to setOf(SubZone.SORCERERS_TOWER_MAGICS, SubZone.SOUTH_SEERS_VILLAGE_MAGICS,
            SubZone.GNOME_STRONGHOLD_WEST_MAGIC, SubZone.GNOME_STRONGHOLD_BANK_MAGIC,
            SubZone.GNOME_STRONGHOLD_EAST_MAGIC, SubZone.MTA_WEST_MAGIC_TREE, SubZone.MTA_EAST_MAGIC_TREE)
    )

    override fun getTrainingScript(bot: Bot, level: Int, zones: MutableList<SubZone>): BotScript =
        createScript(bot, level, zones)

    override fun getProfitScript(bot: Bot, level: Int, zones: MutableList<SubZone>): BotScript =
        profitScript(bot, level, zones)

    private fun profitScript(bot: Bot, level: Int, zones: MutableList<SubZone>): BotScript {
        val axes = Axe.entries.filter { it.level <= bot.woodcutting.level &&
            ProfitGatheringGoal.physical(bot, it.id) > 0L }
        if (axes.isEmpty()) return VoidBotScript(bot)
        val options = linkedMapOf<Tree, ProfitGatheringDemand.Quote>()
        for ((tree, locations) in routes) {
            if (tree.level > minOf(level, bot.woodcutting.level) ||
                tree in setOf(TEAK, MAHOGANY) &&
                    ProfitGatheringGoal.physical(bot, HardwoodGrove.STICKS) < HardwoodGrove.FEE ||
                locations.none { it.entryBlocker(bot) == null && bot.recentEscape?.avoids(it) != true }) continue
            val chance = axes.maxOf { axe ->
                val rates = axe.chances.getValue(tree)
                Skills.successRate(rates.first, rates.second, minOf(99, bot.woodcutting.level)).coerceIn(0.0, 1.0)
            }
            if (chance <= 0.0) continue
            val ticks = Luna.settings().skills().woodcuttingSpeed().toDouble() / chance
            val preliminary = ProfitGatheringDemand.quote(bot, tree.logId, ticks) ?: continue
            val cost = if (tree in setOf(TEAK, MAHOGANY)) HardwoodGrove.FEE *
                ProfitGatheringDemand.inputPrice(bot, HardwoodGrove.STICKS) / minOf(28, preliminary.quantity) else 0.0
            ProfitGatheringDemand.quote(bot, tree.logId, ticks, cost)?.let { options[tree] = it }
        }
        val tree = ProfitGatheringDemand.select(bot, options) ?: return VoidBotScript(bot)
        zones.clear()
        zones.addAll(routes.getValue(tree).filter { it.entryBlocker(bot) == null &&
            bot.recentEscape?.avoids(it) != true })
        val duration = getDuration(bot, skillId)
        return CutTreeBotScript(bot, setOf(tree), duration, zones).also {
            it.profitGathering = ProfitGatheringGoal.create(bot, options.getValue(tree), duration)
        }
    }

    /**
     * Gathers mapped logs usable by both skills and retains Firemaking as the continuation's purpose.
     */
    fun getFiremakingScript(bot: Bot, firemakingLevel: Int, duration: Duration,
                            zones: MutableList<SubZone>): CutTreeBotScript {
        val options = routes.filterKeys { tree ->
            Log.ID_TO_LOG[tree.logId]?.let { it.level <= firemakingLevel } == true &&
                (tree !in setOf(TEAK, MAHOGANY) || CutTreeBotScript.groveSuppliesOwned(bot))
        }.map { (tree, locations) -> BotSkillingActivity(setOf(tree), locations) }
        val activity = getBestSkillingActivity(bot, bot.woodcutting.staticLevel, { it.level }, options)
            ?: error("No Firemaking log gathering activity is available.")
        zones.clear()
        zones.addAll(activity.zones)
        return CutTreeBotScript(bot, activity.tokens, duration, zones, FiremakingScriptFactory.GATHER_TARGET)
    }

    /**
     * Keeps the existing training/profit tree choices attached to their supported routes.
     */
    private fun createScript(bot: Bot, level: Int, zones: MutableList<SubZone>): CutTreeBotScript {
        val trees = trainingTrees(bot, level)
        val activities = ArrayList<BotSkillingActivity<Tree>>()
        for (tree in trees) {
            if (tree in setOf(TEAK, MAHOGANY) && !CutTreeBotScript.groveSuppliesOwned(bot)) continue
            activities.add(BotSkillingActivity(tokens = setOf(tree), zones = routes.getValue(tree)))
        }
        val activity = getBestSkillingActivity(bot, level, { it.level }, activities)
            ?: error("No woodcutting activity is available at level $level.")
        zones.clear()
        zones.addAll(activity.zones)
        return CutTreeBotScript(bot, activity.tokens, getDuration(bot, skillId), zones)
    }

    /**
     * Keeps efficient training focused on willows and gives casual bots more variety.
     */
    private fun trainingTrees(bot: Bot, level: Int): Set<Tree> {
        if (level < WILLOW.level) {
            return if (bot.personality.isDumb) setOf(NORMAL, OAK, ACHEY) else setOf(NORMAL, OAK)
        }
        if (bot.personality.isDextrous) {
            return setOf(WILLOW)
        }
        if (!bot.personality.isDumb) {
            return setOf(WILLOW, TEAK, MAPLE, MAHOGANY)
        }
        return routes.keys
    }

}
