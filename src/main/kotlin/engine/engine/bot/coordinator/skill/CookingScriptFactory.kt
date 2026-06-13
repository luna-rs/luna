package engine.bot.coordinator.skill

import api.bot.script.BotScript
import api.bot.zone.SubZone
import api.predef.SKILL_COOKING
import game.bot.scripts.skills.CookFoodBotScript
import game.skill.cooking.cookFood.Food
import io.luna.game.model.mob.bot.Bot

/**
 * A [SkillingScriptFactory] that creates cooking bot scripts.
 *
 * Cooking bots train by selecting the best cookable food for their current Cooking level, then running
 * a [CookFoodBotScript] for a generated duration.
 *
 * Zone selection is personality-based:
 *
 * - Dumb and non-dextrous bots use less optimal banking locations:
 *   - Al Kharid Bank
 *   - Varrock East Bank
 * - All other bots use Rogues' Den, which is the more efficient cooking spot.
 *
 * Profit cooking currently uses the same behavior as training cooking.
 *
 * @author lare96
 */
object CookingScriptFactory : SkillingScriptFactory(SKILL_COOKING) {

    /**
     * Builds a Cooking training script for the bot.
     *
     * The bot's personality determines which cooking zones are added:
     *
     * - Dumb, non-dextrous bots are sent to normal banks.
     * - Smarter or more dextrous bots are sent to Rogues' Den.
     *
     * The selected food is the best available [Food] entry for the bot's current Cooking level.
     */
    override fun getTrainingScript(
        bot: Bot,
        level: Int,
        zones: MutableList<SubZone>
    ): BotScript {
        if (bot.personality.isDumb && !bot.personality.isDextrous) {
            zones += SubZone.AL_KHARID_BANK
            zones += SubZone.VARROCK_EAST_BANK
        } else {
            zones += SubZone.ROGUES_DEN
        }

        return CookFoodBotScript(
            bot,
            getBestActivity(bot, level, { it.lvl }, Food.entries),
            getDuration(bot),
            zones
        )
    }

    /**
     * Builds a Cooking profit script.
     *
     * Cooking profit behavior currently matches training behavior, meaning profit bots still cook the best available
     * food for their level using the same personality-based zone selection.
     */
    override fun getProfitScript(
        bot: Bot,
        level: Int,
        zones: MutableList<SubZone>
    ): BotScript {
        return getTrainingScript(bot, level, zones)
    }
}