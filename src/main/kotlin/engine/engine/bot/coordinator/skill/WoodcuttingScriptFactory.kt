package engine.bot.coordinator.skill

import api.bot.script.BotScript
import api.bot.zone.SubZone
import api.predef.*
import game.bot.scripts.skills.CutTreeBotScript
import game.skill.woodcutting.cutTree.Tree
import game.skill.woodcutting.cutTree.Tree.*
import io.luna.game.model.mob.bot.Bot

/**
 * Selects tree-specific methods, preserving willow training and level-based profit progression.
 * Each route carries only the trees it supports; casual bots retain a broader range of methods.
 *
 * @author lare96
 */
object WoodcuttingScriptFactory : SkillingScriptFactory(SKILL_WOODCUTTING) {
    /**
     * Dedicated routes for each supported resource. Unmapped exotic trees are excluded.
     */
    private val routes = mapOf(
        NORMAL to setOf(SubZone.WEST_VARROCK_TREES, SubZone.EAST_EDGEVILLE_TREES, SubZone.NORTH_CATHERBY_TREES),
        OAK to setOf(SubZone.WEST_VARROCK_TREES, SubZone.EAST_EDGEVILLE_TREES, SubZone.NORTH_CATHERBY_TREES),
        WILLOW to setOf(SubZone.DRAYNOR_MAIN, SubZone.WEST_DRAYNOR_WILLOWS, SubZone.CATHERBY_BEACH_WILLOWS,
            SubZone.SOUTH_ARDOUGNE_WILLOWS, SubZone.NORTH_ARDOUGNE_WILLOWS,
            SubZone.EAST_ARDOUGNE_WILLOWS, SubZone.SOUTH_YANILLE_WILLOWS),
        MAPLE to setOf(SubZone.SEERS_VILLAGE_MAIN, SubZone.NORTH_WEST_SEERS_VILLAGE_MAPLES,
            SubZone.NORTH_SEERS_VILLAGE_MAPLES, SubZone.SOUTH_SORCERERS_TOWER_MAPLES),
        YEW to setOf(SubZone.LUMBER_YARD_YEWS, SubZone.EAST_DRAYNOR_YEWS, SubZone.VARROCK_PALACE_YEWS,
            SubZone.SOUTH_FALADOR_YEWS, SubZone.SOUTH_SEERS_VILLAGE_YEWS, SubZone.WEST_CATHERBY_YEWS),
        MAGIC to setOf(SubZone.SORCERERS_TOWER_MAGICS, SubZone.SOUTH_SEERS_VILLAGE_MAGICS)
    )

    override fun getTrainingScript(bot: Bot, level: Int, zones: MutableList<SubZone>): BotScript =
        createScript(bot, level, zones, true)

    override fun getProfitScript(bot: Bot, level: Int, zones: MutableList<SubZone>): BotScript =
        createScript(bot, level, zones, false)

    /**
     * Keeps the existing training/profit tree choices attached to their supported routes.
     */
    private fun createScript(bot: Bot, level: Int, zones: MutableList<SubZone>, training: Boolean): CutTreeBotScript {
        val trees = if (training) trainingTrees(bot, level) else profitTrees(bot, level)
        val activities = ArrayList<BotSkillingActivity<Tree>>()
        for (tree in trees) {
            activities.add(BotSkillingActivity(tokens = setOf(tree), zones = routes.getValue(tree)))
        }
        val activity = getBestSkillingActivity(bot, level, { it.level }, activities)
            ?: error("No woodcutting activity is available at level $level.")
        zones.clear()
        zones.addAll(activity.zones)
        return CutTreeBotScript(bot, activity.tokens, getDuration(bot), zones)
    }

    /**
     * Keeps efficient training focused on willows and gives casual bots more variety.
     */
    private fun trainingTrees(bot: Bot, level: Int): Set<Tree> {
        if (level < WILLOW.level) {
            return setOf(NORMAL, OAK)
        }
        if (bot.personality.isDextrous) {
            return setOf(WILLOW)
        }
        if (!bot.personality.isDumb) {
            return setOf(WILLOW, MAPLE)
        }
        return routes.keys
    }

    /**
     * Advances profit targets by level while retaining the casual alternatives.
     */
    private fun profitTrees(bot: Bot, level: Int): Set<Tree> {
        if (level < WILLOW.level) {
            return setOf(NORMAL)
        }
        val trees = linkedSetOf<Tree>()
        if (level < YEW.level) {
            trees.addAll(setOf(WILLOW, MAPLE))
            if (bot.personality.isDumb) {
                trees.add(OAK)
            }
        } else if (level < MAGIC.level) {
            trees.add(YEW)
            if (bot.personality.isDumb) {
                trees.addAll(setOf(WILLOW, MAPLE))
            }
        } else {
            trees.add(MAGIC)
            if (bot.personality.isDumb) {
                trees.addAll(setOf(WILLOW, MAPLE, YEW))
            }
        }
        return trees
    }
}
