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
import game.skill.fletching.stringBow.Bow
import game.skill.fletching.stringBow.Bow.*
import io.luna.game.model.mob.bot.Bot

/**
 * Creates Fletching activities from owned arrow supplies or matching unstrung bows and strings, then
 * falls back to log cutting. Bow selection considers every unlocked owned recipe rather than assuming
 * that the bot owns the highest unlocked log tier or randomly choosing a different short/longbow variant.
 * Training and profit selection retain the existing arrow opportunity and personality-based recipe rules.
 * Profit mode does not guarantee a market margin; log-cutting fallback retains its existing acquisition behavior.
 *
 * @author lare96
 */
object FletchingScriptFactory : SkillingScriptFactory(SKILL_FLETCHING) {

    override fun getTrainingScript(
        bot: Bot,
        level: Int,
        zones: MutableList<SubZone>
    ): BotScript {
        return resolveScript(bot, level, false)
    }

    override fun getProfitScript(
        bot: Bot,
        level: Int,
        zones: MutableList<SubZone>
    ): BotScript {
        return resolveScript(bot, level, true)
    }

    /**
     * Selects an unlocked bow recipe with at least one owned matching pair. Both dispatch modes use
     * the inherited personality selector; current-level and safety checks occur again during execution.
     */
    internal fun getOwnedBowScript(bot: Bot, level: Int): StringBowBotScript? {
        val duration = getDuration(bot)
        val candidates = Bow.entries.filter { it != ARROW_SHAFT }
            .map { StringBowBotScript(bot, it, duration) }.filter { it.isEligible() }
        return getBestActivity(bot, level, { it.requiredLevel }, candidates)
    }

    /** Keeps owned production ahead of the existing tier-based log-cutting fallback. */
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
         * Creates a cutting script for the supplied log tier.
         *
         * Dextrous bots roll for the longbow variant first. If the bot cannot make that tier's longbow yet, this falls
         * back to the shortbow variant.
         */
        fun getScriptForLog(log: Log): BotScript {
            var index = if (rand(bot.personality.dexterity)) 1 else 0
            if (index == 1 && level < log.bows[1].level) {
                index = 0
            }

            return CutLogBotScript(bot, log, index, getDuration(bot))
        }

        /*
         * Give arrow-making a chance before falling back to the normal bow/log selection.
         */
        if (randBoolean()) {
            getArrowScript()?.let { return it }
        }

        getOwnedBowScript(bot, level)?.let { return it }

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
                CutLogBotScript(bot, Log.NORMAL, 2, getDuration(bot))
            } else {
                CutLogBotScript(bot, Log.NORMAL, 1, getDuration(bot))
            }
        } else {
            return CutLogBotScript(bot, Log.NORMAL, 0, getDuration(bot))
        }
    }
}