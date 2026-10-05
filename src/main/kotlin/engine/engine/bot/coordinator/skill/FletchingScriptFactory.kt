package engine.bot.coordinator.skill

import api.bot.script.BotScript
import api.bot.zone.SubZone
import api.predef.*
import engine.bot.gear.BotItemTracker.Companion.itemTracker
import game.bot.scripts.skills.CutLogBotScript
import game.bot.scripts.skills.MakeArrowBotScript
import game.bot.scripts.skills.StringBowBotScript
import game.skill.fletching.attachArrow.Arrow
import game.skill.fletching.cutLog.Log
import game.skill.fletching.stringBow.Bow.*
import game.skill.fletching.stringBow.Bow.Companion.BOW_STRING
import io.luna.game.model.mob.bot.Bot

/**
 * Creates fletching scripts for bots.
 *
 * Fletching is handled as a production skill where bots either cut logs into unstrung bows or string existing bows.
 * Training and profit behaviour currently share the same selection logic, choosing the highest practical log tier for
 * the bot's level and randomly mixing cutting/stringing so bot behaviour is less uniform.
 *
 * @author lare96
 */
object FletchingScriptFactory : SkillingScriptFactory(SKILL_FLETCHING) {

    /**
     * Creates a fletching training script for the bot's current level.
     *
     * The selected script is based on the best unlocked bow tier. Dextrous bots prefer longbows when the matching
     * longbow level is unlocked, while less dextrous bots are more likely to make shortbows. Once a log tier is selected,
     * the bot randomly chooses between cutting logs and stringing bows.
     *
     * @param bot The bot that will run the script.
     * @param level The bot's current fletching level.
     * @param zones The candidate zones available to the factory.
     * @return A fletching script suitable for the bot's level.
     */
    override fun getTrainingScript(
        bot: Bot,
        level: Int,
        zones: MutableList<SubZone>
    ): BotScript {
        return resolveScript(bot, level, false)
    }

    /**
     * Creates a fletching profit script.
     *
     * Profit behaviour currently reuses the normal training script selection. This keeps fletching simple until a
     * dedicated profit strategy is added, such as selecting items by live market value, material stock, or margin.
     *
     * @param bot The bot that will run the script.
     * @param level The bot's current fletching level.
     * @param zones The candidate zones available to the factory.
     *
     * @return A fletching script suitable for profit-oriented activity.
     */
    override fun getProfitScript(
        bot: Bot,
        level: Int,
        zones: MutableList<SubZone>
    ): BotScript {
        return resolveScript(bot, level, true)
    }

    private fun resolveScript(bot: Bot, level: Int, randomized: Boolean): BotScript {

        /**
         * Creates an arrow-making script when the bot has headless arrows, matching arrowtips, and the required level.
         *
         * Arrow tiers scale using the same randomized progression as bow tiers. When not randomized, the highest available
         * arrow tier is selected.
         */
        fun getArrowScript(): BotScript? {
            if (!bot.itemTracker.contains(Arrow.HEADLESS)) {
                return null
            }

            fun canMake(arrow: Arrow): Boolean {
                return level >= arrow.level && bot.itemTracker.contains(arrow.tip)
            }

            val arrow = when {
                canMake(Arrow.RUNE_ARROW) && (!randomized || rand(2) == 0) -> Arrow.RUNE_ARROW
                canMake(Arrow.ADAMANT_ARROW) && (!randomized || rand(4) == 0) -> Arrow.ADAMANT_ARROW
                canMake(Arrow.MITHRIL_ARROW) && (!randomized || rand(8) == 0) -> Arrow.MITHRIL_ARROW
                canMake(Arrow.STEEL_ARROW) && (!randomized || rand(16) == 0) -> Arrow.STEEL_ARROW
                canMake(Arrow.IRON_ARROW) && (!randomized || rand(32) == 0) -> Arrow.IRON_ARROW
                canMake(Arrow.BRONZE_ARROW) && (!randomized || rand(64) == 0) -> Arrow.BRONZE_ARROW
                else -> return null
            }

            return MakeArrowBotScript(bot, arrow, getDuration(bot))
        }

        /**
         * Creates either a cutting or stringing script for the supplied log tier.
         *
         * Dextrous bots roll for the longbow variant first. If the bot cannot make that tier's longbow yet, this falls
         * back to the shortbow variant.
         */
        fun getScriptForLog(log: Log): BotScript {
            var index = if (rand(bot.personality.dexterity)) 1 else 0
            if (index == 1 && level < log.bows[1].level) {
                index = 0
            }

            return if (randBoolean() && bot.itemTracker.contains(BOW_STRING) &&
                log.unstrungIds.find { bot.itemTracker.contains(it) } != null) {
                StringBowBotScript(bot, log.bows[index], getDuration(bot))
            } else {
                CutLogBotScript(bot, log, index, getDuration(bot))
            }
        }

        /*
         * Give arrow-making a chance before falling back to the normal bow/log selection.
         */
        if (randBoolean()) {
            getArrowScript()?.let { return it }
        }

        if (level >= MAGIC_SHORTBOW.level && (!randomized || rand(2) == 0)) {
            return getScriptForLog(Log.MAGIC)
        } else if (level >= YEW_SHORTBOW.level && (!randomized || rand(4) == 0)) {
            return getScriptForLog(Log.YEW)
        } else if (level >= MAPLE_SHORTBOW.level && (!randomized || rand(8) == 0)) {
            return getScriptForLog(Log.MAPLE)
        } else if (level >= WILLOW_SHORTBOW.level && (!randomized || rand(16) == 0)) {
            return getScriptForLog(Log.WILLOW)
        } else if (level >= OAK_SHORTBOW.level && (!randomized || rand(32) == 0)) {
            return getScriptForLog(Log.OAK)
        } else if (level >= SHORTBOW.level && (!randomized || rand(64) == 0)) {
            return if (level >= LONGBOW.level && (!randomized || randBoolean())) {
                if (rand(2) == 0) {
                    StringBowBotScript(bot, LONGBOW, getDuration(bot))
                } else {
                    CutLogBotScript(bot, Log.NORMAL, 2, getDuration(bot))
                }
            } else {
                if (rand(2) == 0) {
                    StringBowBotScript(bot, SHORTBOW, getDuration(bot))
                } else {
                    CutLogBotScript(bot, Log.NORMAL, 1, getDuration(bot))
                }
            }
        } else {
            return CutLogBotScript(bot, Log.NORMAL, 0, getDuration(bot))
        }
    }
}