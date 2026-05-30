package engine.bot.coordinator.skill

import api.bot.script.BotScript
import api.bot.zone.SubZone
import api.predef.*
import game.bot.scripts.skills.CutTreeBotScript
import game.skill.woodcutting.cutTree.Tree
import io.luna.game.model.mob.bot.Bot
import java.util.*

/**
 * Creates Woodcutting scripts based on the bot's level, personality, and objective.
 *
 * Training scripts favor experience progression. Low-level bots start with normal
 * trees and oaks, then branch into more efficient tree choices once willows are
 * unlocked. Dexterous bots prefer focused willow training, while less capable bots
 * may use broader and less optimized tree pools.
 *
 * Profit scripts favor higher-value logs when the bot's Woodcutting level allows it.
 * Dumb bots may include lower-value trees as fallback options, producing a broader
 * but less profit-focused route.
 *
 * @author lare96
 */
object WoodcuttingScriptFactory : SkillingScriptFactory(SKILL_WOODCUTTING) {

    /**
     * Creates a Woodcutting training script for [bot].
     *
     * The selected trees and zones are chosen mainly for experience gain and account
     * progression. Lower-level bots use simple starter trees, while higher-level bots
     * branch into personality-driven routes.
     *
     * @param bot The bot that will train Woodcutting.
     * @param level The bot's current Woodcutting level.
     * @param zones The mutable zone list to populate with valid Woodcutting areas.
     * @return A Woodcutting script configured for training.
     */
    override fun getTrainingScript(bot: Bot, level: Int, zones: MutableList<SubZone>): BotScript {
        val trees = EnumSet.noneOf(Tree::class.java)

        if (level >= Tree.WILLOW.level) {
            if (bot.personality.isDextrous) {
                // Dexterous bots focus on efficient willow training.
                trees += Tree.WILLOW
                zones += SubZone.DRAYNOR_MAIN
            } else if (!bot.personality.isDumb) {
                // Non-dumb bots use stronger training trees at Seers' Village.
                trees += Tree.MAPLE
                trees += Tree.WILLOW
                zones += SubZone.SEERS_VILLAGE_MAIN
            } else {
                // Dumb bots use any accessible tree type, regardless of efficiency.
                for (tree in Tree.ALL.values) {
                    trees += tree
                }

                zones += SubZone.EAST_DRAYNOR_YEWS
                zones += SubZone.LUMBER_YARD_YEWS
                zones += SubZone.SOUTH_FALADOR_YEWS
                zones += SubZone.WEST_CATHERBY_YEWS
                zones += SubZone.SOUTH_SEERS_VILLAGE_YEWS
                zones += SubZone.LUMBRIDGE_RIVER
                zones += SubZone.SEERS_VILLAGE_MAIN
            }
        } else {
            // Starter bots cut basic trees until better routes are unlocked.
            trees += Tree.NORMAL
            trees += Tree.OAK

            zones += SubZone.EAST_DRAYNOR_YEWS
            zones += SubZone.DRAYNOR_MAIN
            zones += SubZone.LUMBER_YARD_YEWS
            zones += SubZone.SOUTH_FALADOR_YEWS
        }

        trees.removeIf { level < it.level }

        require(trees.isNotEmpty()) {
            "No tree types were selected from WoodcuttingScriptFactory."
        }

        return CutTreeBotScript(bot, trees, getDuration(bot), zones)
    }

    override fun getProfitScript(bot: Bot, level: Int, zones: MutableList<SubZone>): BotScript {

        /**
         * Adds common yew-cutting zones used as general-purpose profit locations.
         */
        fun defaultZones() {
            zones += SubZone.LUMBER_YARD_YEWS
            zones += SubZone.EAST_DRAYNOR_YEWS
            zones += SubZone.VARROCK_PALACE_YEWS
            zones += SubZone.SOUTH_FALADOR_YEWS
            zones += SubZone.SOUTH_SEERS_VILLAGE_YEWS
            zones += SubZone.WEST_CATHERBY_YEWS
        }

        val trees = EnumSet.noneOf(Tree::class.java)

        if (level < Tree.WILLOW.level) {
            trees += Tree.NORMAL
            defaultZones()
        } else if (level < Tree.YEW.level) {
            trees += Tree.WILLOW
            trees += Tree.MAPLE

            zones += SubZone.SEERS_VILLAGE_MAIN

            if (bot.personality.isDumb) {
                trees += Tree.OAK
                defaultZones()
            }
        } else if (level < Tree.MAGIC.level) {
            trees += Tree.YEW

            zones += SubZone.VARROCK_PALACE_YEWS
            zones += SubZone.WEST_CATHERBY_YEWS

            if (bot.personality.isDumb) {
                trees += Tree.WILLOW
                trees += Tree.MAPLE
                defaultZones()
            }
        } else {
            trees += Tree.MAGIC
            zones += SubZone.SORCERERS_TOWER_MAGICS

            if (bot.personality.isDumb) {
                trees += Tree.WILLOW
                trees += Tree.MAPLE
                trees += Tree.YEW
                defaultZones()
            }
        }

        trees.removeIf { level < it.level }
        require(trees.isNotEmpty()) { "No tree types were selected." }
        return CutTreeBotScript(bot, trees, getDuration(bot), zones)
    }
}