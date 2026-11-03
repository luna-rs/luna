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
 * All six native courses use the same level/personality selection and script. Owned admission equipment and
 * survivable fall damage are checked before selection. Profit visits use the Pyramid's ordinary artefact sale.
 *
 * @author lare96
 */
object AgilityScriptFactory : SkillingScriptFactory(SKILL_AGILITY) {
    override fun getTrainingScript(bot: Bot, level: Int, zones: MutableList<SubZone>): BotScript {
        val activity = getBestSkillingActivity(bot, level, { it.course.level }, AgilityBotCourse.entries
            .filter { AgilityBotScript.canTrain(bot, it) }.map {
            BotSkillingActivity(tokens = setOf(it), zones = setOf(it.zone))
        })
            ?: return object : DynamicBotScript(bot) {
                override suspend fun run() = true
            }
        zones.addAll(activity.zones)
        return AgilityBotScript(bot, activity.tokens.single(), getDuration(bot))
    }

    override fun getProfitScript(bot: Bot, level: Int, zones: MutableList<SubZone>): BotScript {
        val pyramid = AgilityBotCourse.AGILITY_PYRAMID
        if (level >= pyramid.course.level && AgilityBotScript.canTrain(bot, pyramid)) {
            zones += pyramid.zone
            return AgilityBotScript(bot, pyramid, getDuration(bot))
        }
        return object : DynamicBotScript(bot) {
            override suspend fun run() = true
        }
    }
}
