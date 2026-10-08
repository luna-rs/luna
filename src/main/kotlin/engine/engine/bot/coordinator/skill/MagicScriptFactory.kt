package engine.bot.coordinator.skill

import api.bot.script.BotScript
import api.bot.zone.SubZone
import api.predef.*
import game.bot.scripts.skills.AlchemyBotScript
import game.bot.scripts.skills.SplashBotScript
import game.skill.magic.lowHighAlch.AlchemyType
import io.luna.game.model.mob.bot.Bot

/**
 * Creates bot scripts used to train Magic.
 *
 * Magic does not currently distinguish between normal training and profit-oriented training. Bots prefer alchemy when
 * they have the required Magic level and own suitable items to alch, otherwise they fall back to splashing.
 *
 * TODO Add teleportation-based Magic training. Bots should be able to repeatedly cast suitable teleport spells when
 *      their level and rune supply make teleporting a reasonable training method.
 *
 * @author lare96
 */
object MagicScriptFactory : SkillingScriptFactory(SKILL_MAGIC) {

    override fun getTrainingScript(bot: Bot, level: Int, zones: MutableList<SubZone>): BotScript {
        return getProfitScript(bot, level, zones)
    }

    override fun getProfitScript(bot: Bot, level: Int, zones: MutableList<SubZone>): BotScript {
        val alchemy = getAlchemyScript(bot, level)
        if (alchemy != null) {
            return alchemy
        }
        return SplashBotScript(bot, getDuration(bot))
    }

    /**
     * Creates the highest-level alchemy script currently available to [bot].
     *
     * High alchemy is preferred from level 55 onward and low alchemy is used from level 21. The bot must also own at
     * least one suitable alchemy target. Rune requirements are handled later by [AlchemyBotScript].
     *
     * @param bot The bot that will run the script.
     * @param level The bot's current Magic level.
     * @return An alchemy script, or `null` when alchemy is unavailable.
     */
    private fun getAlchemyScript(bot: Bot, level: Int): AlchemyBotScript? {
        if (!AlchemyBotScript.hasAlchableItems(bot)) {
            return null
        }

        val type = when {
            level >= AlchemyType.HIGH.level -> AlchemyType.HIGH
            level >= AlchemyType.LOW.level -> AlchemyType.LOW
            else -> return null
        }

        return AlchemyBotScript(bot, type, getDuration(bot))
    }
}