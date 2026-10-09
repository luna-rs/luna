package engine.bot.coordinator.skill

import io.luna.game.model.mob.bot.Bot
import api.bot.script.BotScript
import api.bot.zone.SubZone
import api.predef.SKILL_COOKING
import engine.bot.gear.BotItemTracker.Companion.itemTracker
import game.bot.scripts.skills.CookFoodBotScript
import game.skill.cooking.cookFood.Food
import game.bot.scripts.skills.MakeDoughBotScript
import game.obj.resource.fillable.WaterResource
import game.skill.cooking.prepareFood.IncompleteFood
import api.predef.rand

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
 * Non-training Cooking can prepare dough from owned flour and water, including a water-filling prerequisite.
 * These recipes grant no experience. Ordinary food cooking remains the fallback activity.
 *
 * @author lare96
 */
object CookingScriptFactory : SkillingScriptFactory(SKILL_COOKING) {

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
            getBestActivity(bot, level, { it.lvl }, Food.entries.filter { bot.itemTracker.count(it.raw) > 0 }),
            getDuration(bot),
            zones
        )
    }

    override fun getProfitScript(
        bot: Bot,
        level: Int,
        zones: MutableList<SubZone>
    ): BotScript {
        if (rand(0.25)) getPreparationScript(bot, level)?.let { return it }
        return getTrainingScript(bot, level, zones)
    }

    /** Selects a level-appropriate owned dough recipe using the existing personality rules. */
    internal fun getPreparationScript(bot: Bot, level: Int): MakeDoughBotScript? {
        val options = IncompleteFood.DOUGH.values.flatMap { food ->
            WaterResource.FILLED_IDS.map { MakeDoughBotScript(bot, food, it, getDuration(bot)) }
        }.filter { it.isEligible() || it.canPrepareWater() }
        return getBestActivity(bot, level, { it.requiredLevel }, options)
    }
}