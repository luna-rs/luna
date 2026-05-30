package engine.bot.coordinator.skill

import api.bot.script.BotScript
import api.bot.zone.SubZone
import api.predef.*
import game.bot.scripts.skills.CutLogBotScript
import game.bot.scripts.skills.StringBowBotScript
import game.skill.fletching.cutLog.Log
import game.skill.fletching.stringBow.Bow.*
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
            return if (rand(2) == 0) {
                StringBowBotScript(bot, log.bows[index], getDuration(bot))
            } else {
                CutLogBotScript(bot, log, index, getDuration(bot))
            }
        }

        if (level < SHORTBOW.level) {
            return CutLogBotScript(bot, Log.NORMAL, 0, getDuration(bot))
        } else if (level < OAK_SHORTBOW.level) {
            return if (level >= LONGBOW.level) {
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
        } else if (level < WILLOW_SHORTBOW.level) {
            return getScriptForLog(Log.OAK)
        } else if (level < MAPLE_SHORTBOW.level) {
            return getScriptForLog(Log.WILLOW)
        } else if (level < YEW_SHORTBOW.level) {
            return getScriptForLog(Log.MAPLE)
        } else if (level < MAGIC_SHORTBOW.level) {
            return getScriptForLog(Log.YEW)
        } else {
            return getScriptForLog(Log.MAGIC)
        }
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
        return getTrainingScript(bot, level, zones)
    }
}