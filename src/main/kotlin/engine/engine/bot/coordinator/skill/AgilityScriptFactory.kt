package engine.bot.coordinator.skill

import api.bot.script.BotScript
import api.bot.script.DynamicBotScript
import api.bot.zone.SubZone
import api.predef.*
import game.bot.scripts.skills.AgilityBotCourse
import game.bot.scripts.skills.AgilityBotScript
import io.luna.game.model.mob.bot.Bot

/**
 * Selects supported native Agility courses through the normal skilling coordinator.
 *
 * Gnome Stronghold is the first mapped course. New mapped courses use the same level/personality
 * selection and script. Profit selection completes without training because these courses produce
 * no saleable output. The factory remains registered normally alongside the other skills.
 *
 * @author lare96
 */
object AgilityScriptFactory : SkillingScriptFactory(SKILL_AGILITY) {
    override fun getTrainingScript(bot: Bot, level: Int, zones: MutableList<SubZone>): BotScript {
        val activity = getBestSkillingActivity(bot, level, { it.course.level }, AgilityBotCourse.entries.map {
            BotSkillingActivity(tokens = setOf(it), zones = setOf(it.zone))
        })
            ?: return object : DynamicBotScript(bot) {
                override suspend fun run() = true
            }
        zones.addAll(activity.zones)
        return AgilityBotScript(bot, activity.tokens.single(), getDuration(bot))
    }

    override fun getProfitScript(bot: Bot, level: Int, zones: MutableList<SubZone>): BotScript =
        object : DynamicBotScript(bot) {
            override suspend fun run() = true
        }
}
